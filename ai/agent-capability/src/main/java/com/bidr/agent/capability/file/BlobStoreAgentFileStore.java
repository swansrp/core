package com.bidr.agent.capability.file;

import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.blob.BlobStore;
import com.bidr.llm.agent.file.AgentFileStore;
import com.bidr.llm.agent.file.FileRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.UUID;

/**
 * Title: BlobStoreAgentFileStore
 * Description: {@link AgentFileStore} 的对象存储实现——会话附件字节经 core/store/blob 的
 * {@link BlobStore} 落盘（走 {@code oss.*} 选型路由，MinIO/火山 TOS 由装机参数决定）：
 * <ul>
 *     <li>对象键：{@code agent-session/{tenantOrOp}/{sessionId}/{fileId}.{ext}}——会话前缀隔离，
 *     跨会话猜 fileId 也读不到别人的对象；tenantOrOp 取当前租户，无租户上下文回落操作人，
 *     两者都无（内部任务/异步链路）回落 anonymous。</li>
 *     <li>元数据侧车：内容对象之外另写 {@code agent-session/{sessionId}/{fileId}.meta.json}
 *     （FileRef 全字段）——<b>侧车键不带归属段</b>：上传发生在 HTTP 请求线程（租户/操作人上下文
 *     在场，内容键落 {@code agent-session/{tenantOrOp}/...}），而模型读附件发生在 run 线程
 *     （无租户上下文，归属段会退化成 anonymous）——侧车若跟着内容键走，run 线程就永远找不到，
 *     {@code get} 必须与线程上下文无关。会话前缀本身已隔离（sessionId 全局唯一），
 *     侧车不含内容字节，不带归属段不降低隔离性。</li>
 *     <li>读回：{@link #read(FileRef)}／{@link #presignedUrl(FileRef, long)} 都只认完整引用的 key；
 *     事件流重建的引用 key 为 null，调用方须先 {@link #get} 换回完整引用。</li>
 * </ul>
 * 预签名有效期沿用 {@link BlobStore#presign(String)} 的存储后端固定口径（当前 1 小时），
 * 入参 ttlSeconds 仅作协议保留位——不把签名 URL 当永久地址存。
 *
 * @author Sharp
 * @since 2026/11/13
 */
@Slf4j
public class BlobStoreAgentFileStore implements AgentFileStore {

    /** 会话附件对象键前缀（8 个业务前缀之外的新命名空间，blob 侧无同类前缀） */
    private static final String KEY_PREFIX = "agent-session/";

    /** 扩展名清洗后的最大长度（防御超长伪扩展名撑大对象键） */
    private static final int MAX_EXT_LENGTH = 16;

    /** 元数据侧车后缀 */
    private static final String META_SUFFIX = ".meta.json";

    /** 无租户/无操作人时的归属段（内部任务、异步链路的确定值） */
    private static final String ANONYMOUS = "anonymous";

    private final BlobStore blobStore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public BlobStoreAgentFileStore(BlobStore blobStore) {
        this.blobStore = blobStore;
    }

    @Override
    public FileRef put(String sessionId, String filename, String mime, byte[] bytes) {
        requireText(sessionId, "sessionId");
        if (bytes == null) {
            throw new IllegalArgumentException("附件字节不能为空");
        }
        String name = safeFilename(filename);
        String ext = extOf(name);
        String fileId = UUID.randomUUID().toString().replace("-", "");
        String key = contentKey(sessionId, fileId, ext);
        String contentType = isBlank(mime) ? BlobStore.contentTypeOf(name) : mime;
        blobStore.put(key, bytes, contentType);
        FileRef ref = new FileRef(fileId, name, bytes.length, contentType, key);
        // 侧车是 get(sessionId,fileId) 的还原依据，模型读取/清单回显都只认它；写失败必须让上传失败，
        // 否则会出现"上传 200 但模型永远读不到"（m-r2-2）。清理已写内容对象后抛人话错误，由端点做错误信封
        try {
            blobStore.put(metaKey(sessionId, fileId), objectMapper.writeValueAsBytes(ref),
                    "application/json; charset=utf-8");
        } catch (Exception e) {
            log.warn("附件元数据侧车写入失败，回滚内容对象: key={} {}", key, e.getMessage());
            blobStore.remove(key);
            throw new IllegalStateException("附件元数据写入失败，上传未完成，请重试", e);
        }
        return ref;
    }

    @Override
    public FileRef get(String sessionId, String fileId) {
        if (isBlank(sessionId) || isBlank(fileId)) {
            return null;
        }
        try {
            byte[] meta = blobStore.get(metaKey(sessionId, fileId));
            return meta == null ? null : objectMapper.readValue(meta, FileRef.class);
        } catch (Exception e) {
            // 对象不存在/存储未配置/侧车损坏：读取口按"没有该附件"处理，不上抛（清单端点不因一条坏附件 500）
            log.warn("附件元数据读取失败（按不存在处理）: session={} fileId={} {}", sessionId, fileId, e.getMessage());
            return null;
        }
    }

    @Override
    public byte[] read(FileRef ref) {
        requireKey(ref);
        return blobStore.get(ref.key());
    }

    @Override
    public String presignedUrl(FileRef ref, long ttlSeconds) {
        requireKey(ref);
        // BlobStore 的签名有效期是存储实现固定口径（DEFAULT_EXPIRE_SECONDS），ttl 仅协议保留
        return blobStore.presign(ref.key());
    }

    @Override
    public void delete(FileRef ref) {
        if (ref == null || isBlank(ref.key())) {
            return;
        }
        blobStore.remove(ref.key());
        blobStore.remove(metaKeyFromContentKey(ref));
    }

    // ---- 对象键 ----

    private String contentKey(String sessionId, String fileId, String ext) {
        StringBuilder key = new StringBuilder(KEY_PREFIX)
                .append(ownerSegment()).append('/')
                .append(safeSegment(sessionId)).append('/')
                .append(fileId);
        if (!ext.isEmpty()) {
            key.append('.').append(ext);
        }
        return key.toString();
    }

    /** 侧车键：只按 sessionId 定位、不带归属段（run 线程无租户上下文，见类注释）。
     *  fileId 同样过 {@link #safeSegment}（m-r2-6 防御纵深：即便调用方传入任意 fileId，
     *  也不让对象键出现路径分隔符/控制字符） */
    private static String metaKey(String sessionId, String fileId) {
        return KEY_PREFIX + safeSegment(sessionId) + "/" + safeSegment(fileId) + META_SUFFIX;
    }

    /** 由内容键推侧车键：内容键形如 agent-session/{owner}/{sessionId}/{fileId}.{ext}，
     *  侧车恒为 agent-session/{sessionId}/{fileId}.meta.json（删除路径不依赖线程上下文） */
    private static String metaKeyFromContentKey(FileRef ref) {
        String key = ref.key();
        String[] parts = key.split("/");
        if (parts.length >= 4) {
            return KEY_PREFIX + parts[2] + "/" + safeSegment(ref.fileId()) + META_SUFFIX;
        }
        return KEY_PREFIX + safeSegment(ref.fileId()) + META_SUFFIX;
    }

    /** 归属段：当前租户 → 操作人 → anonymous（llm 不依赖 authorization；此处只用 api holder） */
    private static String ownerSegment() {
        Long tenantId = TenantContext.currentTenantId();
        if (tenantId != null) {
            return String.valueOf(tenantId);
        }
        String operator = AccountContext.getOperator();
        return isBlank(operator) ? ANONYMOUS : safeSegment(operator.trim());
    }

    // ---- 清洗 ----

    /** 文件名只取路径末段（兼容旧客户端带全路径），空名回落 file */
    private static String safeFilename(String filename) {
        String name = filename == null ? "" : filename.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.isEmpty() ? "file" : name;
    }

    /** 扩展名：小写、仅安全字符、限长；无扩展名返回空串（对象键不带点） */
    private static String extOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (ext.isEmpty()) {
            return "";
        }
        return ext.length() > MAX_EXT_LENGTH ? ext.substring(0, MAX_EXT_LENGTH) : ext;
    }

    /** 对象键段清洗：只留安全字符，避免路径穿越与签名串非法字符 */
    private static String safeSegment(String value) {
        return value.replaceAll("[^A-Za-z0-9._\\-]", "_");
    }

    private static void requireKey(FileRef ref) {
        if (ref == null || isBlank(ref.key())) {
            throw new IllegalArgumentException("附件引用缺少存储键（事件重建的引用请先经 get(sessionId,fileId) 还原）");
        }
    }

    private static void requireText(String value, String name) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}