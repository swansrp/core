package com.bidr.llm.skill;

import com.bidr.platform.redis.service.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Title: SkillRatingService
 * Description: skill 回答评价 Redis 存储（skill 管理平台底座的评价统计能力）——按 skillCode 隔离：
 * 快照 key llm:skill:rating:{skill}:{ratingId}，全局索引 llm:skill:rating:{skill}:index
 * （zset，member=ratingId，score=评价时间，跨访问人聚合）。与按访问人隔离的对话索引不同，
 * 评价索引跨人共享给运营统计筛选；ratingId 业务方自定（建议 conversationId:messageId），
 * 同一回答重复评价原地覆盖，取消评价由业务方调 remove 移除。
 * <p>
 * 保留天数由业务方按自己的保留策略传入（评价记录的生存期语义业务方最清楚，如 chatbi 与对话共用参数）；
 * 业务维度走 {@link SkillRatingRecord#getExt()}，筛选走 {@link SkillRatingFilter#getExtEquals()} 精确匹配。
 * 应用未接入 Redis 时全部操作降级为空操作（评价是运营辅助链路，不打断业务交互）。
 * 读-改-写窗口同对话存储，Redis 异常只记日志。
 * </p>
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Slf4j
@Service
public class SkillRatingService {

    /**
     * 快照与索引 key 共用前缀：llm:skill:rating:{skill}:{ratingId} / llm:skill:rating:{skill}:index
     */
    private static final String KEY_PREFIX = "llm:skill:rating:";

    /**
     * 快照内提问/回答摘要截断长度（字）——save 前截断，业务方只管传原文
     */
    private static final int SNAPSHOT_MAX_CHARS = 200;

    /**
     * 统计返回明细上限（保留天数内的评价量级有限，上限仅防御异常膨胀）
     */
    private static final int MAX_RECORDS = 1000;

    private final ObjectProvider<RedisService> redisProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SkillRatingService(ObjectProvider<RedisService> redisProvider) {
        this.redisProvider = redisProvider;
    }

    /**
     * 保存评价快照并挂入 skill 全局索引（同 ratingId 覆盖写）；摘要截断在此统一处理
     */
    public void save(String skillCode, SkillRatingRecord record, int retentionDays) {
        if (!StringUtils.hasText(skillCode) || !StringUtils.hasText(record.getRatingId())) {
            return;
        }
        if (!"like".equals(record.getRating()) && !"dislike".equals(record.getRating())) {
            throw new IllegalArgumentException("评价取值非法：" + record.getRating());
        }
        RedisService redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        if (record.getRatingTime() == null) {
            record.setRatingTime(System.currentTimeMillis());
        }
        record.setQuestion(truncate(record.getQuestion()));
        record.setAnswer(truncate(record.getAnswer()));
        try {
            int ttlSeconds = retentionDays * 24 * 3600;
            redis.set(detailKey(skillCode, record.getRatingId()), ttlSeconds, objectMapper.writeValueAsString(record));
            redis.zSetAdd(indexKey(skillCode), record.getRatingId(), record.getRatingTime());
            redis.expire(indexKey(skillCode), ttlSeconds);
        } catch (Exception e) {
            log.warn("保存 skill 评价失败, skill={}, ratingId={}, error={}", skillCode, record.getRatingId(), e.getMessage());
        }
    }

    /**
     * 移除评价（取消评价）：删快照 + 出索引；未接入 Redis 时空操作
     */
    public void remove(String skillCode, String ratingId) {
        RedisService redis = redisProvider.getIfAvailable();
        if (redis == null || !StringUtils.hasText(ratingId)) {
            return;
        }
        try {
            redis.delete(detailKey(skillCode, ratingId));
            redis.zSetRemove(indexKey(skillCode), ratingId);
        } catch (Exception e) {
            log.warn("移除 skill 评价失败, skill={}, ratingId={}, error={}", skillCode, ratingId, e.getMessage());
        }
    }

    /**
     * 运营统计：该 skill 全量评价按筛选条件内存过滤（通用维度 + extEquals），汇总随筛选联动；
     * 索引里快照已过期的成员（TTL 先到）顺手清掉
     */
    public SkillRatingStatRes list(String skillCode, SkillRatingFilter filter) {
        SkillRatingStatRes result = new SkillRatingStatRes();
        RedisService redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return result;
        }
        Set<String> ratingIds = redis.zSetRange(indexKey(skillCode), 0, -1, String.class);
        if (ratingIds == null || ratingIds.isEmpty()) {
            return result;
        }
        // zSetRange 返回无序 Set，读出快照后按评价时间倒序（新→旧）再截断
        List<SkillRatingRecord> collected = new ArrayList<>();
        Set<String> stale = new HashSet<>();
        for (String ratingId : ratingIds) {
            SkillRatingRecord record = readRecord(redis, skillCode, ratingId);
            if (record == null) {
                stale.add(ratingId);
                continue;
            }
            if (matches(record, filter)) {
                collected.add(record);
            }
        }
        if (!stale.isEmpty()) {
            redis.zSetRemoveBySet(indexKey(skillCode), stale);
        }
        collected.sort(Comparator.comparingLong(SkillRatingRecord::getRatingTime).reversed());
        for (SkillRatingRecord record : collected) {
            if (result.getRecords().size() >= MAX_RECORDS) {
                break;
            }
            result.getRecords().add(record);
            result.setTotal(result.getTotal() + 1);
            if ("like".equals(record.getRating())) {
                result.setLikeCount(result.getLikeCount() + 1);
            } else {
                result.setDislikeCount(result.getDislikeCount() + 1);
            }
        }
        return result;
    }

    /**
     * 筛选匹配：通用维度（类型/评价人/时间段/关键词）+ 业务维度（extEquals 精确匹配，空值条件跳过）
     */
    private boolean matches(SkillRatingRecord record, SkillRatingFilter filter) {
        if (filter == null) {
            return true;
        }
        if (StringUtils.hasText(filter.getRating()) && !filter.getRating().equals(record.getRating())) {
            return false;
        }
        if (StringUtils.hasText(filter.getOperator())
                && (record.getOperator() == null || !filter.getOperator().equals(record.getOperator()))) {
            return false;
        }
        if (filter.getStartTime() != null && (record.getRatingTime() == null || record.getRatingTime() < filter.getStartTime())) {
            return false;
        }
        if (filter.getEndTime() != null && (record.getRatingTime() == null || record.getRatingTime() > filter.getEndTime())) {
            return false;
        }
        if (StringUtils.hasText(filter.getKeyword())) {
            String question = record.getQuestion() == null ? "" : record.getQuestion();
            String answer = record.getAnswer() == null ? "" : record.getAnswer();
            if (!question.contains(filter.getKeyword()) && !answer.contains(filter.getKeyword())) {
                return false;
            }
        }
        for (Map.Entry<String, String> entry : filter.getExtEquals().entrySet()) {
            if (!StringUtils.hasText(entry.getValue())) {
                continue;
            }
            String value = record.getExt() == null ? null : record.getExt().get(entry.getKey());
            if (value == null || !entry.getValue().equals(value)) {
                return false;
            }
        }
        return true;
    }

    private SkillRatingRecord readRecord(RedisService redis, String skillCode, String ratingId) {
        try {
            String json = redis.get(detailKey(skillCode, ratingId), String.class);
            return json == null ? null : objectMapper.readValue(json, SkillRatingRecord.class);
        } catch (Exception e) {
            log.warn("读取 skill 评价失败, skill={}, ratingId={}, error={}", skillCode, ratingId, e.getMessage());
            return null;
        }
    }

    private String detailKey(String skillCode, String ratingId) {
        return KEY_PREFIX + skillCode + ":" + ratingId;
    }

    private String indexKey(String skillCode) {
        return KEY_PREFIX + skillCode + ":index";
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= SNAPSHOT_MAX_CHARS ? text : text.substring(0, SNAPSHOT_MAX_CHARS) + "…";
    }
}
