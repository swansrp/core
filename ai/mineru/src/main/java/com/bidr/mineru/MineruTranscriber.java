package com.bidr.mineru;

import com.bidr.llm.parse.DocumentTranscriber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Title: MineruTranscriber
 * Description: {@link DocumentTranscriber} 的 MinerU 实现（同步门面）。腿选择口径
 * （用户 2026-10-17 定案）：<b>云腿令牌配了→云优先，失败回落本地；没配→只走本地；
 * 两腿都没配→本腿整体不认领</b>（门面回落既有本地解析路径）。
 * <p>
 * 同步等待预算＝{@code mineru.max-wait-seconds}（默认 300，对齐 pm-hse 判分实测）：
 * 轮询超 deadline 抛异常由门面回落，任务不吊死。终局失败（如公网令牌过期 A0211）立即抛，
 * 不吃满预算。要异步/自管轮询的后台任务链消费方直接用 {@link MineruLocalClient}/
 * {@link MineruCloudClient}，别走本类。
 * <p>
 * 认领类型＝pdf＋常见图片（这是 MinerU 的强项、也是既有本地路径最弱的地方——扫描 PDF 逐页
 * 渲染交多模态、图片直转多模态）；office 仍走门面的 POI 本地路径（确定性强、零外部依赖）。
 *
 * @author Sharp
 * @since 2026/10/17
 */
@Slf4j
public class MineruTranscriber implements DocumentTranscriber {

    /** 认领的扩展名：pdf ＋ 图片（MinerU 接受的输入面） */
    private static final List<String> SUPPORTED_EXTS = Arrays.asList(
            "pdf", "png", "jpg", "jpeg", "gif", "webp", "bmp", "tif", "tiff");

    private final MineruProperties properties;

    private final MineruLocalClient localClient;

    private final MineruCloudClient cloudClient;

    /**
     * 两腿按配置就绪度构造：local.base-url 空＝无本地腿（null）；cloud.token 空＝无云腿（null）。
     * 调用方（自动装配）据此传 null，本类不重复判配置
     */
    public MineruTranscriber(MineruProperties properties, MineruLocalClient localClient, MineruCloudClient cloudClient) {
        this.properties = properties;
        this.localClient = localClient;
        this.cloudClient = cloudClient;
    }

    @Override
    public boolean isAvailable(String filename) {
        if (!properties.isEnabled() || (localClient == null && cloudClient == null)) {
            return false;
        }
        return SUPPORTED_EXTS.contains(extensionOf(filename));
    }

    @Override
    public String transcribe(byte[] content, String filename) throws IOException {
        if (content == null || content.length == 0) {
            throw new IOException("MinerU 转写内容为空: " + filename);
        }
        if (cloudClient != null) {
            try {
                return await(cloudClient.submit(content, filename, null), "云", filename, cloudClient::poll,
                        id -> cloudClient.fetch(id).getMarkdown());
            } catch (IOException e) {
                if (localClient == null) {
                    throw e;
                }
                log.warn("MinerU 云腿失败，回落本地腿: {} 原因: {}", filename, e.getMessage());
            }
        }
        if (localClient == null) {
            throw new IOException("MinerU 两腿均未配置（mineru.local.base-url / mineru.cloud.token）: " + filename);
        }
        String taskId = localClient.submit(content, filename, null, false);
        return await(taskId, "本地", filename, localClient::poll, id -> localClient.fetch(id).getMarkdown());
    }

    /**
     * 同步等待预算内的轮询：PENDING/RUNNING 继续等，FAILED/超时抛异常（门面回落）。
     * Thread.sleep 被中断＝恢复中断位后按失败抛（不吞中断信号）
     */
    private String await(String taskId, String leg, String filename, Poller poller, Fetcher fetcher) throws IOException {
        long deadline = System.currentTimeMillis() + properties.getMaxWaitSeconds() * 1000L;
        while (true) {
            MineruTaskStatus status = poller.poll(taskId);
            if (MineruTaskStatus.COMPLETED.equals(status.getStatus())) {
                String markdown = fetcher.fetch(taskId);
                if (!StringUtils.hasText(markdown)) {
                    throw new IOException("MinerU " + leg + "腿结果缺 markdown: " + filename);
                }
                return markdown;
            }
            if (MineruTaskStatus.FAILED.equals(status.getStatus())) {
                throw new IOException("MinerU " + leg + "腿解析失败: " + status.getErrorMessage());
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new IOException("MinerU " + leg + "腿超时（" + properties.getMaxWaitSeconds() + "s）: " + filename);
            }
            try {
                Thread.sleep(properties.getPollIntervalMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("MinerU " + leg + "腿等待被中断: " + filename);
            }
        }
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** await 的轮询面（避免为两条腿抽公共基类） */
    private interface Poller {
        MineruTaskStatus poll(String taskId);
    }

    /** await 的取货面 */
    private interface Fetcher {
        String fetch(String taskId);
    }
}
