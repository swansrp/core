package com.bidr.agent.console.dao.mapper;

import com.bidr.agent.console.dao.entity.ModelUsage;
import com.bidr.agent.console.dto.ModelUsageSummary;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

/**
 * Title: ModelUsageMapper
 * Description: 模型调用用量流水数据访问（M14 棒5）。
 *
 * <p>聚合走注解 SQL（core 内正源＝{@code SaSequenceDao}/{@code RecursionDao}），不落 XML mapper：
 * 本项目曾踩"XML 落错目录扫不到"的坑，注解形态没有这个问题。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
public interface ModelUsageMapper extends MyBaseMapper<ModelUsage> {

    /**
     * 按「日 × 用途档 × agent × 模型」聚合用量事实（时间窗左闭右开）。
     *
     * <p>agentKey／purposeType 传 null＝该维不限——用 {@code (? IS NULL OR col = ?)} 写成静态 SQL，
     * 免得为一条读腿引入动态 SQL 段。<b>本表不参与空间行级隔离</b>（隔离清单是白名单制，
     * {@code sys_model_usage} 不在其中），所以这里没有 space_id 条件；按空间拆账的口径本来就不在
     * core（要等模型 key 分家），上层要限空间得先拿到该空间的 agentKey 集合再来。</p>
     *
     * <p>{@code usage_missing} 数的是"网关没回 usage"的行：非零时同一行的 token 合计只是<b>下限</b>，
     * 这个缺口必须跟着读数一起交出去，否则少计会被读成少用。</p>
     *
     * <p>{@code maxRows} 是组数硬上限：排序是「日新→旧、调用次数大→小」，被截掉的必然是<b>最旧、
     * 调用最少</b>的那几组——诊断页要的是头部用量，全量对账不该走这条读腿。</p>
     */
    @Select("SELECT DATE(call_at)                                            AS stat_date, "
            + "       purpose_type                                             AS purpose_type, "
            + "       agent_key                                                AS agent_key, "
            + "       model_name                                               AS model_name, "
            + "       COUNT(*)                                                 AS call_count, "
            + "       SUM(prompt_tokens)                                       AS prompt_tokens, "
            + "       SUM(completion_tokens)                                   AS completion_tokens, "
            + "       SUM(reasoning_tokens)                                    AS reasoning_tokens, "
            + "       SUM(total_tokens)                                        AS total_tokens, "
            + "       SUM(cost_ms)                                             AS cost_ms, "
            + "       SUM(CASE WHEN prompt_tokens IS NULL THEN 1 ELSE 0 END)   AS usage_missing "
            + "FROM sys_model_usage "
            + "WHERE call_at >= #{from} AND call_at < #{to} "
            + "  AND (#{agentKey, jdbcType=VARCHAR} IS NULL OR agent_key = #{agentKey, jdbcType=VARCHAR}) "
            + "  AND (#{purposeType, jdbcType=VARCHAR} IS NULL OR purpose_type = #{purposeType, jdbcType=VARCHAR}) "
            + "GROUP BY DATE(call_at), purpose_type, agent_key, model_name "
            + "ORDER BY stat_date DESC, call_count DESC "
            + "LIMIT #{maxRows}")
    List<ModelUsageSummary> summarize(@Param("from") Date from, @Param("to") Date to,
                                      @Param("agentKey") String agentKey,
                                      @Param("purposeType") String purposeType,
                                      @Param("maxRows") int maxRows);
}
