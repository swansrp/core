package com.bidr.llm.skill;

import com.bidr.platform.redis.service.RedisService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: SkillRatingServiceTest
 * Description: 评价单条读口（get）的行为锁：写读对称、点亮态回显的数据源口径。
 * 关键约束是"读不到 ≠ 出错"——未评价、快照已过保留期、应用未接入 Redis、参数空白四种情形
 * 都必须回 null 让前端按"未评价"渲染，不能把运营辅助链路打成页面报错。
 *
 * @author sharp
 * @since 2026/9/25
 */
public class SkillRatingServiceTest {

    private static final String SKILL = "inkhub-check";

    private static final String OTHER_SKILL = "chatbi-route";

    private static final String RATING_ID = "sess-001";

    private static RedisService fakeRedis(Map<String, String> backing) {
        return (RedisService) Proxy.newProxyInstance(
                RedisService.class.getClassLoader(), new Class<?>[]{RedisService.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "get":
                            return args == null || args.length == 0 ? null : backing.get((String) args[0]);
                        case "set":
                            if (args != null && args.length == 3) {
                                backing.put((String) args[0], String.valueOf(args[2]));
                            }
                            return null;
                        case "delete":
                            if (args != null && args.length >= 1) {
                                backing.remove(String.valueOf(args[0]));
                            }
                            return Boolean.TRUE;
                        default:
                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class || rt == Boolean.class) {
                                return Boolean.FALSE;
                            }
                            if (rt == long.class || rt == Long.class) {
                                return 0L;
                            }
                            if (rt == int.class || rt == Integer.class) {
                                return 0;
                            }
                            if (rt == double.class || rt == Double.class) {
                                return 0d;
                            }
                            return null;
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private static SkillRatingService service(Map<String, String> backing) {
        RedisService redis = backing == null ? null : fakeRedis(backing);
        return new SkillRatingService((ObjectProvider<RedisService>) Proxy.newProxyInstance(
                ObjectProvider.class.getClassLoader(), new Class<?>[]{ObjectProvider.class},
                (proxy, method, args) -> "getIfAvailable".equals(method.getName()) ? redis : null));
    }

    private static SkillRatingRecord record(String rating) {
        SkillRatingRecord record = new SkillRatingRecord();
        record.setRatingId(RATING_ID);
        record.setConversationId(RATING_ID);
        record.setMessageId(RATING_ID);
        record.setOperator("000001");
        record.setQuestion("张某是不是财务部经理");
        record.setAnswer("是，依据任命文件第 2 页");
        record.setRating(rating);
        record.getExt().put("subject", "39");
        record.getExt().put("agentKey", SKILL);
        return record;
    }

    @Test
    public void 写入后按同口径读回评价与业务维度() {
        Map<String, String> backing = new ConcurrentHashMap<>();
        SkillRatingService service = service(backing);
        service.save(SKILL, record("dislike"), 30);

        SkillRatingRecord read = service.get(SKILL, RATING_ID);
        Assert.assertNotNull("写读须同口径命中", read);
        Assert.assertEquals("dislike", read.getRating());
        Assert.assertEquals("000001", read.getOperator());
        Assert.assertEquals("39", read.getExt().get("subject"));
        Assert.assertNotNull(read.getRatingTime());
    }

    @Test
    public void 未评价返回空而非异常() {
        SkillRatingService service = service(new ConcurrentHashMap<>());
        Assert.assertNull(service.get(SKILL, "never-rated"));
    }

    @Test
    public void 快照已过期返回空() {
        Map<String, String> backing = new ConcurrentHashMap<>();
        SkillRatingService service = service(backing);
        service.save(SKILL, record("like"), 30);
        // 模拟保留期到点：Redis 按 TTL 清了快照键（索引成员残留由 list 侧自清理）
        backing.remove("llm:skill:rating:" + SKILL + ":" + RATING_ID);
        Assert.assertNull(service.get(SKILL, RATING_ID));
    }

    @Test
    public void 未接入redis时读口降级为空() {
        Assert.assertNull(service(null).get(SKILL, RATING_ID));
    }

    @Test
    public void 参数空白按未评价处理() {
        SkillRatingService service = service(new ConcurrentHashMap<>());
        Assert.assertNull(service.get(null, RATING_ID));
        Assert.assertNull(service.get("", RATING_ID));
        Assert.assertNull(service.get(SKILL, null));
        Assert.assertNull(service.get(SKILL, "  "));
    }

    @Test
    public void 同ratingId在不同skill下互不串() {
        Map<String, String> backing = new ConcurrentHashMap<>();
        SkillRatingService service = service(backing);
        service.save(SKILL, record("dislike"), 30);
        service.save(OTHER_SKILL, record("like"), 30);

        Assert.assertEquals("dislike", service.get(SKILL, RATING_ID).getRating());
        Assert.assertEquals("like", service.get(OTHER_SKILL, RATING_ID).getRating());
    }

    @Test
    public void 重复评价原地覆盖读回最新态() {
        Map<String, String> backing = new ConcurrentHashMap<>();
        SkillRatingService service = service(backing);
        service.save(SKILL, record("like"), 30);
        service.save(SKILL, record("dislike"), 30);
        Assert.assertEquals("dislike", service.get(SKILL, RATING_ID).getRating());

        service.remove(SKILL, RATING_ID);
        Assert.assertNull("取消评价后不得残留点亮态", service.get(SKILL, RATING_ID));
    }
}
