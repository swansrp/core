package com.bidr.blob;

import com.bidr.blob.dao.entity.KbBlob;
import com.bidr.blob.dao.repository.KbBlobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回收判据按 kind 分口的判别腿（M16 棒 1，D7 甲口径＋①档）：
 * <ul>
 *   <li>render／image（无引用源的两类）→ 永不删字节（不删行、不删对象）；</li>
 *   <li>original 与存量行（kind NULL）→ 走引用源计数：归零回收、仍被引用则计数自愈——
 *       与下沉前 {@code BlobLedgerService#releaseBlob} 行为逐字相同（现网删除腿的口径）；</li>
 *   <li>未注入 {@link BlobReferenceSource} → 缺省档永不删＋anchorStillUsed 保守视为仍被占用。</li>
 * </ul>
 *
 * @author sharp
 */
class BlobLedgerKindTest {

    private static final Long BLOB_ID = 100L;

    private final KbBlobService blobService = mock(KbBlobService.class);

    private final BlobStore storage = mock(BlobStore.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<BlobReferenceSource> emptyProvider = mock(ObjectProvider.class);

    private final BlobReferenceSource source = mock(BlobReferenceSource.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<BlobReferenceSource> sourceProvider = mock(ObjectProvider.class);

    private BlobLedger ledgerWithSource() {
        when(sourceProvider.getIfUnique()).thenReturn(source);
        return new BlobLedger(blobService, storage, sourceProvider);
    }

    private KbBlob blobOfKind(String kind) {
        KbBlob blob = new KbBlob();
        blob.setId(BLOB_ID);
        blob.setSha256("abc123");
        blob.setStorageKey("raw/abc123.pdf");
        blob.setRefCount(1);
        blob.setKind(kind);
        when(blobService.getById(BLOB_ID)).thenReturn(blob);
        return blob;
    }

    @Test
    void renderKindNeverRecycles() {
        blobOfKind(BlobLedger.KIND_RENDER);
        ledgerWithSource().releaseBlob(BLOB_ID);
        verify(blobService, never()).removeById(anyLong());
        verify(storage, never()).remove(anyString());
    }

    @Test
    void imageKindNeverRecycles() {
        blobOfKind(BlobLedger.KIND_IMAGE);
        ledgerWithSource().releaseBlob(BLOB_ID);
        verify(blobService, never()).removeById(anyLong());
        verify(storage, never()).remove(anyString());
    }

    /** 存量行（kind NULL，升级不回填）：保持旧口径＝引用源计数，归零才回收 */
    @Test
    void legacyNullKindStillRecyclesByReferenceCount() {
        blobOfKind(null);
        when(source.countByBlobId(BLOB_ID)).thenReturn(0L);
        ledgerWithSource().releaseBlob(BLOB_ID);
        verify(storage).remove("raw/abc123.pdf");
        verify(blobService).removeById(BLOB_ID);
    }

    /** original 归零回收（与旧 releaseBlob 行为逐字相同：删行＋删对象） */
    @Test
    void originalKindRecyclesWhenNoReferenceLeft() {
        blobOfKind(BlobLedger.KIND_ORIGINAL);
        when(source.countByBlobId(BLOB_ID)).thenReturn(0L);
        ledgerWithSource().releaseBlob(BLOB_ID);
        verify(storage).remove("raw/abc123.pdf");
        verify(blobService).removeById(BLOB_ID);
    }

    /** original 仍被引用则计数自愈（patch 只带 id＋refCount） */
    @Test
    void originalKindSelfHealsCountWhenStillReferenced() {
        blobOfKind(BlobLedger.KIND_ORIGINAL);
        when(source.countByBlobId(BLOB_ID)).thenReturn(2L);
        ledgerWithSource().releaseBlob(BLOB_ID);
        verify(storage, never()).remove(anyString());
        verify(blobService, never()).removeById(anyLong());
        verify(blobService).updateById(any(KbBlob.class));
    }

    @Test
    void absentReferenceSourceNeverRecycles() {
        blobOfKind(BlobLedger.KIND_ORIGINAL);
        new BlobLedger(blobService, storage, emptyProvider).releaseBlob(BLOB_ID);
        verify(blobService, never()).removeById(anyLong());
        verify(storage, never()).remove(anyString());
    }

    /** 无引用源时 anchorStillUsed 保守回答 true（宁可攒、不可误删） */
    @Test
    void absentReferenceSourceTreatsAnchorAsStillUsed() {
        assertTrue(new BlobLedger(blobService, storage, emptyProvider).anchorStillUsed("any"));
    }

    /** 有引用源时按实现回答（现网实现＝业务侧的文件版本引用行反查） */
    @Test
    void anchorStillUsedDelegatesToSource() {
        when(source.anchorStillUsed("anchor-x")).thenReturn(false);
        assertFalse(ledgerWithSource().anchorStillUsed("anchor-x"));
        when(source.anchorStillUsed("anchor-y")).thenReturn(true);
        assertTrue(ledgerWithSource().anchorStillUsed("anchor-y"));
    }
}
