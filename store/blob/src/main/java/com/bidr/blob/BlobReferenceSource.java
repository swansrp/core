package com.bidr.blob;

/**
 * blob 引用判据 SPI（M16 棒 1）：core 的 {@link BlobLedger} 只管台账与字节，
 * "还有多少业务行引用这份内容"归业务侧回答——core 不认识任何业务表。
 *
 * <p>缺省（未注入实现）＝按 D7-② 缺省档处理：{@code releaseBlob} 永不删字节、只 WARN，
 * {@code anchorStillUsed} 一律视为仍被占用（安全但不回收）。</p>
 *
 * <p>实现侧注意：两条判据的"引用正源"语义由实现定义（InkHub 现网实现＝业务侧的文件版本引用行），
 * 必须与 {@code BlobLedger#ensureBlob} 的加号侧同源，否则计数会漂。</p>
 *
 * @author sharp
 */
public interface BlobReferenceSource {

    /** 该台账行（blob_id）还被多少业务行引用 */
    long countByBlobId(Long blobId);

    /**
     * 该解析锚（parse_sha 优先、无 parse_sha 的行回落 source_sha）是否仍被任何业务行引用。
     * 内容寻址产物（正文快照/页码映射/页图）没有台账行，唯一的"还有没有人用"判据就是这个反查。
     */
    boolean anchorStillUsed(String anchor);
}
