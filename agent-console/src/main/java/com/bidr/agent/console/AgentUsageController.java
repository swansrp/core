package com.bidr.agent.console;

import com.bidr.agent.console.dao.entity.ModelUsage;
import com.bidr.agent.console.dao.repository.ModelUsageService;
import com.bidr.agent.console.dto.ModelUsageSummary;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.BeanUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Title: AgentUsageController
 * Description: 模型用量统计端点（<b>只读，M14 棒5 无写口</b>）：两条读腿——
 * {@code /summary} 按「日 × 用途档 × agent × 模型」聚合，{@code /session/{id}} 看一场会话的每一轮调用。
 *
 * <p><b>只有读数、没有金额</b>：core 交得出的是"用了哪个模型、多少 token、跑了多久"，
 * 单价与账单是运营政策（会变、各项目不同），一律不在本端点上算，也不进本模块的表。</p>
 *
 * <p><b>鉴权口径同装配单展开闸</b>：只给平台管理员（反射取 permitService，bean 取不到即按非管理员拒绝）。
 * 这条读腿是<b>跨空间</b>的全平台用量，还带 asked_by／session_id 这类"谁在哪场会话里跑了多少"的线索，
 * 放开给普通登录用户等于把别人的使用画像摊开。</p>
 *
 * <p><b>开关跟着写侧走</b>：挂 {@code agent-console.usage.enabled}（<b>默认关</b>）而不是
 * {@code agent-console.api.enabled}——表、落库腿、读腿三者同生同死。表没建（开关关着）却开着读腿，
 * 第一次调用就是一条 SQL 报错，那比"没有这个入口"更糟。</p>
 *
 * <p><b>读数的下限性质</b>：SSE 链路今天不回吐 usage（见 {@code ModelUsageRecorder} 与本模块 README），
 * 聚合行的 {@code usageMissing} 非零就说明这一组的 token 合计是<b>下限</b>而非真值；
 * 页面上不报这个数，会把"没拿到"读成"没用过"。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Api(tags = "系统管理 - 模型用量统计（按日/用途档/agent/模型聚合，仅平台管理员）")
@Slf4j
@RestController
@RequestMapping("/web/api/agent/usage")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent-console.usage", name = "enabled", havingValue = "true")
public class AgentUsageController {

    /** 默认统计窗口（天，含今日）：不给时间参数时读"最近一周"，比默认全表既快又符合直觉 */
    private static final int DEFAULT_WINDOW_DAYS = 7;

    /** 会话流水默认与硬上限（一次会话十几轮到上百轮，一次别拉太多） */
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final ModelUsageService repo;

    @ApiOperation(value = "用量聚合（日 × 用途档 × agent × 模型，只给平台管理员）",
            notes = "from/to 为 yyyy-MM-dd、按日左闭右开（to 当天整天含）；缺省＝最近 "
                    + DEFAULT_WINDOW_DAYS + " 天。agentKey/purposeType 传空＝该维不限。"
                    + "usage_missing 非零＝该组有调用拿不到 token，token 合计只是下限；金额与单价不在本端点。"
                    + "最多返回 500 组，排序＝日新→旧·次数大→小，超限被截的是最旧且调用最少的组"
                    + "（全量对账请直接查表，不要走这条读腿）")
    @GetMapping("/summary")
    public List<ModelUsageSummary> summary(
            @ApiParam(value = "起始日（yyyy-MM-dd，含）")
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") Date from,
            @ApiParam(value = "结束日（yyyy-MM-dd，含当天）")
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") Date to,
            @ApiParam(value = "agent 运行时注册键，空＝不限")
            @RequestParam(value = "agentKey", required = false) String agentKey,
            @ApiParam(value = "用途档（AGENT/VISION/DEFAULT/NODE…），空＝不限")
            @RequestParam(value = "purposeType", required = false) String purposeType) {
        requireAdmin();
        Date start = from != null ? from : daysAgo(DEFAULT_WINDOW_DAYS - 1);
        // to 给的是"哪天为止"，含当天 ⇒ 右开界取次日零点
        Date end = to == null ? tomorrow() : dayAfter(to);
        return repo.summarize(start, end, agentKey, purposeType);
    }

    @ApiOperation(value = "一场会话的模型调用流水（新→旧，一次调用一行）",
            notes = "M14 棒5 验收面：一场会话的每一轮模型调用各落一行。只给平台管理员；"
                    + "开关打开之前发起的历史会话没有流水，返回空数组")
    @GetMapping("/session/{sessionId}")
    public List<ModelUsage> bySession(
            @ApiParam(value = "会话标识", required = true) @PathVariable("sessionId") String sessionId,
            @ApiParam(value = "最多返回行数，默认 " + DEFAULT_LIMIT + "、上限 " + MAX_LIMIT)
            @RequestParam(value = "limit", required = false) Integer limit) {
        requireAdmin();
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return repo.listBySession(sessionId, size);
    }

    /** 管理员闸：与装配单展开原文同一口径——bean 取不到按非管理员处理，宁可少给一个入口 */
    private void requireAdmin() {
        boolean admin;
        try {
            admin = Boolean.TRUE.equals(BeanUtil.invoke("permitService", "isAdmin", Boolean.class));
        } catch (Exception e) {
            log.warn("用量统计取管理员身份失败（按无权处理）: error={}", e.getMessage());
            admin = false;
        }
        if (!admin) {
            throw new NoticeException("模型用量统计只给平台管理员读");
        }
    }

    private static Date daysAgo(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, -days);
        return startOfDay(calendar.getTime());
    }

    private static Date tomorrow() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        return startOfDay(calendar.getTime());
    }

    /** 入参日期的次日零点（＝"含当天"的右开界） */
    private static Date dayAfter(Date day) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(day);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        return calendar.getTime();
    }

    private static Date startOfDay(Date moment) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(moment);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
}
