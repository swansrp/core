package com.bidr.blob;

import com.bidr.blob.dao.entity.SaBlob;
import com.bidr.blob.dao.repository.SaBlobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 内容块台账（sa_blob）写入的公共件——M4 Step3 从上传链路抽出，上传/人写页/渲染共用；
 * M16 棒 1 自 inkhub-file 的 BlobLedgerService 下沉 core/store/blob。
 *
 * <p><b>口径</b>：对象按 sha256 内容寻址（先写对象再落行，对象失败整笔失败，不留
 * "有台账没内容"的悬空行）；跨空间按内容去重、引用计数 +1；并发撞 {@code uk_sha256}
 * 时让出本次写入、改为引用既有行。</p>
 *
 * <p><b>引用判据只有一份</b>：加号在 {@link #ensureBlob}，减号在 {@link #releaseBlob}，
 * 两侧都以"业务侧还有多少行引用它"为准（{@link BlobReferenceSource}，不是靠内存里的
 * 计数器加减——那会漂，M10 修前实测就地更新每次保存只加不减，81 行有效 blob 里 67 行
 * 早已无任何版本引用）。任何"把业务行的锚挪走"的动作都必须在挪完之后释放旧锚。</p>
 *
 * <p><b>按 kind 分口的回收判据（D7 已拍 10-19，H3）</b>：
 * <ul>
 *   <li>{@code original}（原件锚）与存量行（{@code kind} NULL，升级不回填）→ 走引用源计数，
 *       归零才删行＋删字节，仍被引用则把计数自愈为实际值——与下沉前行为逐字相同；</li>
 *   <li>{@code render}（可交付件）/ {@code image}（裸图，无引用源）→ <b>永不删字节＋WARN</b>
 *       （缺省档：安全但会攒，配额与清理任务不在本批）；</li>
 *   <li>未注入 {@link BlobReferenceSource} → 同缺省档，永不删字节＋WARN。</li>
 * </ul></p>
 *
 * @author sharp
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlobLedger {

    /** 内容种类（D7 甲口径）：原件锚——上账主体，回收判据＝引用源计数 */
    public static final String KIND_ORIGINAL = "original";

    /** 内容种类（D7 甲口径）：渲染可交付件——上账主体，无引用源，缺省档永不删 */
    public static final String KIND_RENDER = "render";

    /** 内容种类（D7 甲口径）：人贴裸图——不上账的保留值（现网裸图本就无台账行），防误删护栏 */
    public static final String KIND_IMAGE = "image";

    private final SaBlobService blobService;

    private final BlobStore storage;

    private final ObjectProvider<BlobReferenceSource> referenceSource;

    /** 按 sha 查既有内容块（NULL=未登过账） */
    public SaBlob findBySha(String sha256) {
        return blobService.lambdaQuery()
                .eq(SaBlob::getSha256, sha256)
                .eq(SaBlob::getValid, "1")
                .last("limit 1")
                .one();
    }

    /** 按台账主键读行（回读产物用；可空=行不存在） */
    public SaBlob findByBlobId(Long blobId) {
        return blobId == null ? null : blobService.getById(blobId);
    }

    /**
     * 落内容块（原件档）：已存在则引用计数 +1，不存在则写对象存储后建行。
     *
     * @param bytes       内容字节（sha256 由本方法计算，保证指纹与字节同源）
     * @param ext         扩展名（对象 key 与 content-type 判定用）
     * @param contentType 上传声明的类型（可空，空则按 key 推断）
     */
    public SaBlob ensureBlob(byte[] bytes, String ext, String contentType) {
        return ensureBlob(bytes, ext, contentType, KIND_ORIGINAL);
    }

    /**
     * 落内容块（带 kind）：与 {@link #ensureBlob(byte[], String, String)} 同一流程，
     * 新建行多落 kind 与 storage_type 快照两列；既有行命中复用时不动它的 kind（存量行不回填）。
     */
    public SaBlob ensureBlob(byte[] bytes, String ext, String contentType, String kind) {
        String sha256 = Digests.sha256Hex(bytes);
        SaBlob existing = findBySha(sha256);
        if (existing != null) {
            incrementRef(existing);
            return existing;
        }
        String key = BlobStore.rawKey(sha256, ext);
        String mime = (contentType == null || contentType.trim().isEmpty())
                ? BlobStore.contentTypeOf(key) : contentType;
        // 🔴 后端类型快照必须在写对象之前取（M16 修复轮 B1）：取不到类型的失败若发生在 put 之后，
        // 对象已落存储但台账行没写——重试一次就多一份无人引用的孤儿字节
        String storageType = storage.currentStorageType();
        storage.put(key, bytes, mime);

        SaBlob blob = new SaBlob();
        blob.setSha256(sha256);
        blob.setStorageKey(key);
        blob.setFileSize((long) bytes.length);
        blob.setMimeType(mime);
        blob.setRefCount(1);
        blob.setKind(kind);
        blob.setStorageType(storageType);
        try {
            blobService.save(blob);
            return blob;
        } catch (DuplicateKeyException e) {
            SaBlob raced = findBySha(sha256);
            if (raced == null) {
                throw e;
            }
            log.info("内容块并发落库冲突，改为引用既有行: sha={} blob={}", sha256, raced.getId());
            incrementRef(raced);
            return raced;
        }
    }

    /**
     * 落渲染可交付件（M16 棒 1 收编旧 DocumentRenderService 自落账的第二正源，行为逐字复刻）：
     * 按 sha 查既有（<b>不滤 valid</b>，与旧渲染侧 findBlob 同形）→ 命中即幂等复用；
     * 未命中则先写对象（key＝{@link BlobStore#renderKey}）再建行，并发撞唯一键时让行复用既有内容。
     *
     * @param format 输出格式（md/html/pdf/docx/xlsx，render key 的扩展段）
     * @param content 产物字节
     * @param mime    产物声明的 Content-Type
     */
    public SaBlob ensureRenderBlob(String format, byte[] content, String mime) {
        String sha256 = Digests.sha256Hex(content);
        SaBlob existing = findAnyBySha(sha256);
        if (existing != null) {
            log.info("渲染产物命中既有 blob（内容幂等）: sha={}", sha256);
            return existing;
        }
        String key = BlobStore.renderKey(sha256, format);
        // 同 ensureBlob：后端类型快照先取、再写对象（B1）
        String storageType = storage.currentStorageType();
        storage.put(key, content, mime);

        SaBlob blob = new SaBlob();
        blob.setSha256(sha256);
        blob.setStorageKey(key);
        blob.setFileSize((long) content.length);
        blob.setMimeType(mime);
        blob.setRefCount(1);
        blob.setValid("1");
        blob.setKind(KIND_RENDER);
        blob.setStorageType(storageType);
        try {
            blobService.save(blob);
            log.info("渲染产物已落库: format={} sha={} bytes={}", format, sha256, content.length);
            return blob;
        } catch (DuplicateKeyException e) {
            SaBlob raced = findAnyBySha(sha256);
            if (raced != null) {
                return raced;
            }
            throw e;
        }
    }

    private void incrementRef(SaBlob blob) {
        blob.setRefCount((blob.getRefCount() == null ? 0 : blob.getRefCount()) + 1);
        blobService.updateById(blob);
        log.info("内容块复用，引用计数 +1: blob={} refCount={}", blob.getId(), blob.getRefCount());
    }

    /**
     * 释放内容块（{@link #ensureBlob} 的减法侧）：按"业务侧还有多少行引用它"判定——
     * 归零则删行＋删原件对象；仍被引用则把计数自愈为实际值。
     *
     * <p>为什么不直接 {@code refCount - 1}：业务行才是引用的正源，计数器只是缓存。
     * 就地改版/挪指针这类动作先加后减、且可能同一内容被多行共用，纯做算术迟早漂；
     * 以行数为准顺带把历史漂移修掉（M10 实测就是纯加法把 blob 攒成了无引用行）。</p>
     *
     * <p>kind 分口见类注释：render/image 与无引用源两档永不删字节、只 WARN（D7-② 缺省档）。</p>
     *
     * @param blobId 内容块 id（可空＝该版本行本就没有正源，直接跳过）
     */
    public void releaseBlob(Long blobId) {
        if (blobId == null) {
            return;
        }
        SaBlob blob = blobService.getById(blobId);
        if (blob == null) {
            return;
        }
        String kind = blob.getKind();
        if (KIND_RENDER.equals(kind) || KIND_IMAGE.equals(kind)) {
            log.warn("内容块 kind={} 无引用源，按缺省档保留字节不回收（D7-②；配额与清理任务不在本批）: "
                    + "blob={} sha={} key={}", kind, blobId, blob.getSha256(), blob.getStorageKey());
            return;
        }
        BlobReferenceSource source = referenceSource.getIfUnique();
        if (source == null) {
            log.warn("未注入 BlobReferenceSource，无法判定引用，按缺省档保留字节不回收: "
                    + "blob={} sha={} key={}", blobId, blob.getSha256(), blob.getStorageKey());
            return;
        }
        long remaining = source.countByBlobId(blobId);
        if (remaining == 0) {
            storage.remove(blob.getStorageKey());
            blobService.removeById(blobId);
            log.info("内容块无剩余引用，已回收: blob={} sha={} key={}",
                    blobId, blob.getSha256(), blob.getStorageKey());
            return;
        }
        if (blob.getRefCount() == null || blob.getRefCount() != remaining) {
            SaBlob patch = new SaBlob();
            patch.setId(blobId);
            patch.setRefCount((int) remaining);
            blobService.updateById(patch);
            log.info("内容块仍被引用，计数自愈: blob={} {} -> {}", blobId, blob.getRefCount(), remaining);
        }
    }

    /**
     * 该解析锚是否仍被任何业务行引用（解析锚＝parse_sha 优先、无 parse_sha 的行回落 source_sha）。
     *
     * <p>内容寻址产物（正文快照/页码映射/页图）没有台账行，唯一的"还有没有人用"判据就是这个
     * 反查——同一份内容被多个文档共用时，按锚删产物会打坏别人的预览，故删除前必须过这道判据。
     * 未注入引用源时一律视为仍被占用（保守：宁可攒、不可误删）。</p>
     */
    public boolean anchorStillUsed(String anchor) {
        BlobReferenceSource source = referenceSource.getIfUnique();
        return source == null || source.anchorStillUsed(anchor);
    }

    /** 渲染侧幂等查找：只按 sha 取行、不滤 valid（复刻旧 DocumentRenderService.findBlob 的形状） */
    private SaBlob findAnyBySha(String sha256) {
        return blobService.lambdaQuery()
                .eq(SaBlob::getSha256, sha256)
                .last("LIMIT 1")
                .one();
    }
}
