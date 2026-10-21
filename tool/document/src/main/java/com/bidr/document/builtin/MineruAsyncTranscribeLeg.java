package com.bidr.document.builtin;

import com.bidr.document.spi.AsyncTranscribeLeg;
import com.bidr.mineru.MineruArtifact;
import com.bidr.mineru.MineruCloudClient;
import com.bidr.mineru.MineruLocalClient;
import com.bidr.mineru.MineruProperties;
import com.bidr.mineru.MineruTaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: MineruAsyncTranscribeLeg
 * Description: {@link AsyncTranscribeLeg} 的 MinerU 实现（M17 棒 1，自 InkHub
 * {@code MineruAsyncGateway} 迁入为 tool/document 的异步腿）：core {@code tool/mineru}
 * 两条客户端腿（{@link MineruLocalClient} 自建／{@link MineruCloudClient} 公网）按
 * 「云优先、提交失败回落本地」的口径收拢——与同步门面 {@code MineruTranscribeLeg} 的
 * 腿选择口径同源（core javadoc：要异步/自管轮询的消费方直接用两客户端，别走同步门面）。
 *
 * <p><b>子路线记账</b>：提交成功的那条物理腿记进 {@link Submission#getRoute()}（{@code cloud}/
 * {@code local}），编排层存进任务行快照、轮询/取件按原串回传——两腿的任务 id 不在同一命名空间，
 * 混用会 404。快照缺席的续跑推导归编排层（它持有快照读写口），本腿只认显式路线串。</p>
 *
 * <p><b>配置可缺席</b>：两腿全空＝{@link #hasLeg()} false，调用方走内置三态（缺席不抛）。
 * 目标路线的客户端为空（配置被撤）＝可读 ISE、不回落另一条腿——串号只会把错误指向无关的地方。</p>
 *
 * @author Sharp
 * @since 2026-10-21
 */
@Slf4j
public class MineruAsyncTranscribeLeg implements AsyncTranscribeLeg {

    /** 腿名（编排层选型键 {@code engine.parse.transcriber} 点名用） */
    public static final String LEG_NAME = "mineru";

    /** 子路线词表（任务行快照兼容口径：历史行快照的 {@code leg} 字段即此值） */
    public static final String ROUTE_CLOUD = "cloud";

    public static final String ROUTE_LOCAL = "local";

    private final MineruLocalClient local;

    private final MineruCloudClient cloud;

    /**
     * 全参构造（null 客户端＝该腿未配置）：客户端的构造判据在装配侧
     * （base-url/token 有值才建，与 core 装配同口径），本类只认现成客户端。
     */
    public MineruAsyncTranscribeLeg(MineruLocalClient local, MineruCloudClient cloud) {
        this.local = local;
        this.cloud = cloud;
    }

    @Override
    public String legName() {
        return LEG_NAME;
    }

    @Override
    public boolean hasLeg() {
        return cloud != null || local != null;
    }

    /**
     * 提交解析任务：云优先、提交失败回落本地（与同步门面同口径）；
     * 两腿全空抛 {@link IllegalStateException}（调用方应先 {@link #hasLeg()} 分流，这里到不了）。
     *
     * @param extractImages 是否要图片条目（本地腿 {@code return_images} 开关；公网腿恒回图）
     */
    @Override
    public Submission submit(byte[] content, String filename, boolean extractImages) {
        if (cloud != null) {
            try {
                return new Submission(ROUTE_CLOUD, cloud.submit(content, filename, null), now());
            } catch (RuntimeException e) {
                if (local == null) {
                    throw e;
                }
                log.warn("MinerU 云腿提交失败，回落本地腿: {} 原因: {}", filename, e.getMessage());
            }
        }
        if (local == null) {
            throw new IllegalStateException("MinerU 两腿均未配置（mineru.local.base-url / mineru.cloud.token）: "
                    + filename);
        }
        return new Submission(ROUTE_LOCAL, local.submit(content, filename, null, extractImages), now());
    }

    /**
     * 轮询（归一四态＋排队数；公网腿无排队数＝null）。
     * 🔴 目标路线的客户端为空（配置被撤）＝可读 ISE、不回落另一条腿。
     */
    @Override
    public Progress poll(String route, String externalId) {
        requireClient(route, "轮询");
        MineruTaskStatus status = ROUTE_CLOUD.equals(route) ? cloud.poll(externalId) : local.poll(externalId);
        return new Progress(status.getStatus(), status.getPhase(), status.getQueueAhead(),
                status.getErrorMessage());
    }

    /** 取结果 zip 并归一（markdown/content_list 原文/图片字节；归一化归编排层） */
    @Override
    public Artifact fetch(String route, String externalId) {
        requireClient(route, "取结果");
        MineruArtifact artifact = ROUTE_CLOUD.equals(route) ? cloud.fetch(externalId) : local.fetch(externalId);
        List<Image> images = new ArrayList<>();
        if (artifact.getImages() != null) {
            for (MineruArtifact.MineruImage image : artifact.getImages()) {
                if (image != null && StringUtils.hasText(image.getName()) && image.getData() != null) {
                    images.add(new Image(image.getName(), image.getData()));
                }
            }
        }
        return new Artifact(artifact.getMarkdown(), artifact.getContentList(), images);
    }

    /** 目标路线的客户端必须就位：配置被撤＝可读 ISE，让运维一眼看出"重试也没用、要人看配置" */
    private void requireClient(String route, String action) {
        if (ROUTE_CLOUD.equals(route)) {
            if (cloud == null) {
                throw unavailable(route, action);
            }
            return;
        }
        if (ROUTE_LOCAL.equals(route)) {
            if (local == null) {
                throw unavailable(route, action);
            }
            return;
        }
        throw new IllegalStateException("MinerU 外部任务缺路线标识（route 为空），无法" + action
                + "；请人工重试该任务");
    }

    private static IllegalStateException unavailable(String route, String action) {
        return new IllegalStateException("MinerU " + route + " 腿已不可用（配置被撤，mineru."
                + route + " 未就绪），无法" + action + "外部任务；请人工重试该任务"
                + "（两腿任务 id 不在同一空间，不会自动换腿）");
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
