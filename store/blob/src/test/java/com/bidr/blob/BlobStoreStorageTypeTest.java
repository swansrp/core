package com.bidr.blob;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.bidr.blob.dao.entity.SaBlob;
import com.bidr.blob.dao.repository.SaBlobService;
import com.bidr.oss.service.OssServiceSelector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code sa_blob.storage_type} 快照列的机器腿（M16 修复轮 B1/B19）：
 * <ul>
 *   <li>{@link BlobStore#currentStorageType()} 四值映射（0/1/2/3 → LOCAL/MINIO/ALI/VOLCANO），
 *       与 {@code OssServiceTypeDict} 同一条取值链；</li>
 *   <li>{@link BlobLedger#ensureBlob} 落行时 storageType 取自 {@link BlobStore}，
 *       且<b>快照取在写对象之前</b>——取不到类型的失败不许留在「对象已写、台账没行」的孤儿态。</li>
 * </ul>
 *
 * @author sharp
 */
class BlobStoreStorageTypeTest {

    @Test
    @DisplayName("B19：currentStorageType 四值映射（Local/Minio/Ali/Volc）")
    void currentStorageTypeMapsFourDictValues() {
        OssServiceSelector selector = mock(OssServiceSelector.class);
        BlobStore store = new BlobStore(selector);

        String[][] cases = {{"0", "LOCAL"}, {"1", "MINIO"}, {"2", "ALI"}, {"3", "VOLCANO"}};
        for (String[] c : cases) {
            when(selector.currentType()).thenReturn(c[0]);
            assertEquals(c[1], store.currentStorageType(), "选型值 " + c[0] + " 的枚举名");
        }
    }

    @Test
    @DisplayName("B1：ensureBlob 先取存储后端快照、再写对象，落行用该快照")
    @SuppressWarnings("unchecked")
    void ensureBlobSnapshotsStorageTypeBeforePut() {
        SaBlobService blobService = mock(SaBlobService.class);
        LambdaQueryChainWrapper<SaBlob> query = mock(LambdaQueryChainWrapper.class);
        when(blobService.lambdaQuery()).thenReturn(query);
        when(query.eq(any(), any())).thenReturn(query);
        when(query.last(anyString())).thenReturn(query);
        when(query.one()).thenReturn(null); // 未登过账：走「写对象＋建行」分支

        BlobStore storage = mock(BlobStore.class);
        when(storage.currentStorageType()).thenReturn("MINIO");
        ObjectProvider<BlobReferenceSource> provider = mock(ObjectProvider.class);

        SaBlob blob = new BlobLedger(blobService, storage, provider)
                .ensureBlob("hello".getBytes(StandardCharsets.UTF_8), "txt", "text/plain");

        assertEquals("MINIO", blob.getStorageType(), "落行取的是 BlobStore 的快照值");
        InOrder order = inOrder(storage);
        order.verify(storage).currentStorageType();
        order.verify(storage).put(anyString(), any(byte[].class), anyString());
    }
}
