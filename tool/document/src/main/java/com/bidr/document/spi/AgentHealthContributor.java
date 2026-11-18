package com.bidr.document.spi;

import java.net.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.function.Predicate;

/**
 * Title: AgentHealthContributor
 * Description: agent 依赖底座的<b>健康自报端口</b>（2026-11-18 [U61] 后续，用户拍板「每个子模块
 * 自己探自己的，中央只收集；没有这个子模块就不收集」）——每条腿在自己最内聚的模块里实现本端口：
 * 探测地址、路径、鉴权头、代理、可达判定全部由腿自己写（与真实客户端同源同式，消灭中央探活件
 * 手抄客户端调用方式的双轨漂移——11-14 探活件恒探 OpenHands 而 11-16 才有 Cube 腿的误报即漂移实证）；
 * 中央探活件（agent-capability {@code AgentCapabilityHealthProbe}）只做聚合：收集容器里全部
 * 本端口 bean（{@code List} 注入按 {@link #order()} 稳序），模块不在场＝bean 不存在＝该能力位
 * 自然缺席快照与日志行（前端缺键＝按可用处理，历史兼容）。
 * <p>装配约定：contributor bean 的注册条件<b>必须与该腿真实客户端 bean 同源</b>（同处注册或同款
 * {@code @ConditionalOnProperty}）——腿在探活才在，腿不在探活不误报。同名 capability 的多条
 * contributor 依赖互斥装配保证至多一条（cube/openhands 两腿由 provider 条件互斥）。
 * <p>实现约定：{@link #probe()} 不得抛异常（内部兜底转 UNREACHABLE）；「本腿没配齐」返回
 * {@link HealthReport#notConfigured()} 而非 UNREACHABLE（缺配置是部署形态不是故障）；
 * 鉴权类敏感值只进请求头，不得进原因短语/日志。
 * <p>⚠ 本文件在 tool 家 JDK8 编译级别模块（document 是各腿模块的传递依赖交集），全程 Java 8
 * 语法（无 record/var）。
 *
 * @author Sharp
 * @since 2026/11/18
 */
public interface AgentHealthContributor {

    /** 能力位名＝健康快照键（与 {@code com.bidr.llm.agent.file.AgentCapabilityHealthProvider} 契约同源，如 ocr/artifacts.doc/exec.sandbox） */
    String capability();

    /** 探针标签（探活日志行括号内名，如 mineru/gotenberg/cube/openhands） */
    String probeName();

    /** 探自己的健康：实现方内聚全部探测细节（地址/路径/鉴权/代理/判定），每次调用独立探测 */
    HealthReport probe();

    /** 探测序（中央聚合按此稳定排序；数字越小越靠前——历史日志顺序 mineru→gotenberg→沙箱） */
    int order();

    /** 单腿探测结果（三态＋原因短语；原因只进日志，不做机器消费）。不可变对象 */
    final class HealthReport {

        public enum Status { REACHABLE, NOT_CONFIGURED, UNREACHABLE }

        private final Status status;
        private final String reason;

        private HealthReport(Status status, String reason) {
            this.status = status;
            this.reason = reason;
        }

        public static HealthReport reachable() {
            return new HealthReport(Status.REACHABLE, null);
        }

        public static HealthReport notConfigured() {
            return new HealthReport(Status.NOT_CONFIGURED, null);
        }

        public static HealthReport unreachable(String reason) {
            return new HealthReport(Status.UNREACHABLE, reason == null ? "原因未知" : reason);
        }

        public Status getStatus() {
            return status;
        }

        public String getReason() {
            return reason;
        }

        public boolean isReachable() {
            return status == Status.REACHABLE;
        }

        @Override
        public String toString() {
            return String.valueOf(status);
        }
    }

    /**
     * 各腿共用的轻量 GET 探针（JDK HttpClient，超时 3s，零新依赖——与历史中央探活件同式）：
     * {@code okStatus} 为可达判定（如 {@code s -> s == 200}）；{@code proxy} 非 null＝本次探测走该
     * HTTP 代理（外网段底座直连恒 timeout 误报的场景，探测须与业务链同代理口径）；任意异常
     * （连接拒绝/超时/URL 非法）＝ UNREACHABLE（异常简单名＋首段消息截 80 字符入原因）。
     */
    static HealthReport httpGet(String url, Proxy proxy, Predicate<Integer> okStatus) {
        try {
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(URI.create(url.trim()))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            java.net.http.HttpClient client = proxy != null
                    ? java.net.http.HttpClient.newBuilder().proxy(new java.net.ProxySelector() {
                        @Override
                        public java.util.List<java.net.Proxy> select(URI uri) {
                            return java.util.Collections.singletonList(proxy);
                        }
                        @Override
                        public void connectFailed(URI uri, java.net.SocketAddress sa, java.io.IOException ioe) {
                        }
                    }).build()
                    : java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpResponse<Void> response = client
                    .send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();
            return okStatus.test(code) ? HealthReport.reachable() : HealthReport.unreachable("HTTP " + code);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return HealthReport.unreachable(reasonOf(e));
        } catch (Exception e) {
            return HealthReport.unreachable(reasonOf(e));
        }
    }

    /** 异常原因短语：简单名＋首段消息，截 80 字符（只进日志） */
    static String reasonOf(Exception e) {
        String message = e.getMessage();
        String reason = e.getClass().getSimpleName()
                + (message != null && !message.trim().isEmpty() ? ": " + message.split("\n")[0].trim() : "");
        return reason.length() > 80 ? reason.substring(0, 80) : reason;
    }
}
