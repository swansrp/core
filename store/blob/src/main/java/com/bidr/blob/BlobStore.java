package com.bidr.blob;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.DictEnumUtil;
import com.bidr.oss.constant.dict.OssServiceTypeDict;
import com.bidr.oss.service.ObjectStorageService;
import com.bidr.oss.service.OssServiceSelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 对象存储门面（M2 起；M16 棒 1 自 inkhub-file 的 BlobStorageService 下沉 core/store/blob）：
 * 原件 / 解析产物 / 页图 / 渲染可交付件各类产物的写入与预签名 URL，8 个 key 前缀的正源。
 *
 * <p><b>通道定案（2026-09-18 用户拍板）</b>：走框架 core/oss 的实现 + 预签名 URL，
 * 不新开端点、不改框架。代价已记录：预签名默认 1 小时有效，落库产物中的图片链接会过期，
 * 重解析须按 key 重新签发（{@link #presign(String)}），不得把签名 URL 当永久地址存。</p>
 *
 * <p><b>走选型路由（2026-10-15）</b>：实现按 sys_config {@code OSS_SERVER_TYPE} 每次现读
 * （{@link OssServiceSelector}，与 skill 包通道同一正源），本类不再直连
 * {@code MinioTemplate}——底座是 MinIO 还是火山 TOS 由装机参数决定，改参数即换腿
 * （10-15 zz 实测：TOS 底座下直连 MinIO SDK 会被原生端点拒签，草稿保存整腿瘫痪）。
 * 框架 upload 的对象名是 {@code {project}/{folder}/{type}/{yyyyMMdd}/{fileName}}，
 * 产不出 M2 需要的定位形态（{@code raw/{sha256}.{ext}}、{@code thumb/{sha256}/p{n}.png}），
 * 故对象名仍由本类按 key 规则自拼、经 {@code putBytes/readBytes} 直读直写——
 * 仍只用 core/oss 的能力，未自写任何 SDK 客户端。</p>
 *
 * <p>key 规则是页码定位与视觉工具的共同契约，改动即破坏既有产物，不要随手里改。
 * 🔴 行为逐字不变（M16 棒 1 判据①）：8 个前缀方法自旧 {@code BlobStorageService}
 * 原样迁入＋{@link #renderKey} 收编渲染侧自拼前缀，同输入产出 key 串逐字相等（golden 对照腿钉住）。</p>
 *
 * @author sharp
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlobStore {

    /** 预签名有效期：1 小时（框架各实现 READ_EXPIRE_SECONDS 的固定口径） */
    public static final int DEFAULT_EXPIRE_SECONDS = 3600;

    /**
     * 正文里图片引用的稳定占位 scheme（2026-09-19 定案的占位协议词法）。
     * 🔴 收在 BlobStore 私有（R-1 禁词白名单只认本文件）：scheme 属业务协议认识，
     * core 不以公开常量的形式向框架各模块扩散——构建走 {@link #imagePlaceholder}、
     * 判别走 {@link #isImagePlaceholder}。
     */
    private static final String IMAGE_PLACEHOLDER_SCHEME = "inkhub-image://";

    private static final Map<String, String> CONTENT_TYPES = new HashMap<>();

    static {
        CONTENT_TYPES.put("pdf", "application/pdf");
        CONTENT_TYPES.put("md", "text/markdown; charset=utf-8");
        CONTENT_TYPES.put("json", "application/json; charset=utf-8");
        CONTENT_TYPES.put("txt", "text/plain; charset=utf-8");
        CONTENT_TYPES.put("png", "image/png");
        CONTENT_TYPES.put("jpg", "image/jpeg");
        CONTENT_TYPES.put("jpeg", "image/jpeg");
        CONTENT_TYPES.put("gif", "image/gif");
        CONTENT_TYPES.put("bmp", "image/bmp");
        CONTENT_TYPES.put("webp", "image/webp");
        CONTENT_TYPES.put("tif", "image/tiff");
        CONTENT_TYPES.put("tiff", "image/tiff");
        CONTENT_TYPES.put("doc", "application/msword");
        CONTENT_TYPES.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        CONTENT_TYPES.put("xls", "application/vnd.ms-excel");
        CONTENT_TYPES.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        CONTENT_TYPES.put("ppt", "application/vnd.ms-powerpoint");
        CONTENT_TYPES.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    }

    private final OssServiceSelector ossServiceSelector;

    @Value("${oss.bucket}")
    private String bucket;

    @Value("${oss.appKey:}")
    private String appKey;

    @Value("${oss.appSecret:}")
    private String appSecret;

    @Value("${oss.endpoint:}")
    private String endpoint;

    /** 原件（全文唯一一份，按内容哈希定位） */
    public static String rawKey(String sha256, String ext) {
        return "raw/" + sha256 + (isBlank(ext) ? "" : "." + normalizeExt(ext));
    }

    /** 解析产物 markdown（按版本留档，便于回溯"当时用的是哪份解析结果"） */
    public static String parsedMarkdownKey(String sha256, Integer versionNo) {
        return "parsed/" + sha256 + "/v" + versionNo + ".md";
    }

    /**
     * office 转换产物 PDF（M3/T1）。按<b>原件</b>内容哈希寻址：同一份 docx 转出的 PDF 恒定相同，
     * 重试/重传覆盖同一 key 天然幂等。它是 office 文件的<b>解析锚</b>——正文/pagemap/页图
     * 按 {@code parse_sha}（=这份 PDF 的 sha）寻址，原件本身仍按 {@code source_sha} 留档。
     */
    public static String convertedPdfKey(String sourceSha256) {
        return "convert/" + sourceSha256 + ".pdf";
    }

    /** 页码映射 JSON（D8：有序页边界，引用定位的输入） */
    public static String pageMapKey(String sha256, Integer versionNo) {
        return "pagemap/" + sha256 + "/v" + versionNo + ".json";
    }

    /** 表册索引 JSON（T3-5：附录目录表确定性抽取的 表号→表名→页码 清单） */
    public static String formIndexKey(String sha256, Integer versionNo) {
        return "form-index/" + sha256 + "/v" + versionNo + ".json";
    }

    /** 页图（PDF 逐页渲染产物，为视觉工具铺路），页码从 1 起（对齐"第 N 页"话术） */
    public static String thumbKey(String sha256, int pageNo) {
        return "thumb/" + sha256 + "/p" + pageNo + ".png";
    }

    /**
     * 内嵌图片对象。
     *
     * <p><b>两种 sha 语义共用本 key（是刻意的，区分靠 sha 归属）</b>：解析产物内嵌图的 sha 传
     * <b>文档解析锚</b>（该图随文档生，删除文档时回收）；人贴裸图（编辑页拖图/粘贴）传
     * <b>图片自身内容 sha</b>——它是跨文档资产，同一张图被多个页面引用只存一份，
     * 且不随任何单一文档删除而被清理（否则别人的页面图裂）。</p>
     */
    public static String imageKey(String sha256, String imageName) {
        return "image/" + sha256 + "/" + sanitizeName(imageName);
    }

    /**
     * 渲染可交付件（F8 文档出件；M16 棒 1 收编渲染侧自拼的 {@code render/} 第二正源）。
     * 按产物内容哈希寻址：同内容幂等复用对象与台账行，不产生重复副本。
     */
    public static String renderKey(String sha256, String format) {
        return "render/" + sha256 + "." + format;
    }

    /**
     * 图片对象名（= {@link #imageKey} 里名字那一段的清洗结果）。
     *
     * <p>展示端点按 <code>/file/image/{sha}/{fileName}</code> 寻址，前端拿到的名字必须与
     * 落库 key 里的名字逐字相同，否则 404。故上传侧回名字时走这同一个函数，不自己拼清洗逻辑。</p>
     */
    public static String imageObjectName(String imageName) {
        return sanitizeName(imageName);
    }

    /**
     * 正文里引用图片用的<b>稳定占位</b>（2026-09-19 定案，取代早期的"改写为预签名 URL"）。
     *
     * <p>实测结论（docs/poc/placement_matrix.md）：正文里放可拉取的签名 URL，知识库引擎会拉图、
     * 缩写引用并注入自己的描述，导致两边文本不一致、引擎偏移 83% 不可用，且签名会过期、
     * 重签即得到不同正文。改存不可拉取的占位后两边文本逐字相同、偏移 100% 可用；
     * 真正的可访问 URL 由展示/使用方在读取边缘调用 {@link #presign(String)} 现签。</p>
     */
    public static String imagePlaceholder(String sha256, String imageName) {
        return IMAGE_PLACEHOLDER_SCHEME + sha256 + "/" + sanitizeName(imageName);
    }

    /**
     * 判别引用目标是否<b>已经是</b>稳定占位形态（自家导出件/历史产物，直通腿据此原样保留）。
     * scheme 词法只活在 {@link #imagePlaceholder} 一处，消费方判别走本方法、不写字面量。
     */
    public static boolean isImagePlaceholder(String ref) {
        return ref != null && ref.startsWith(IMAGE_PLACEHOLDER_SCHEME);
    }

    /**
     * 写字节；key 由 {@link #rawKey} 等规则产出。
     * 建桶由框架实现负责（MinIO 实现首写自建；TOS/云厂商桶装机预建）。
     */
    public void put(String key, byte[] bytes, String contentType) {
        ensureConfigured();
        String mime = isBlank(contentType) ? contentTypeOf(key) : contentType;
        storage().putBytes(key, bytes, mime);
        log.info("对象已写入: bucket={} key={} bytes={} contentType={}", bucket, key, bytes.length, mime);
    }

    /** 按 key 签发可读 URL（默认 1 小时） */
    public String presign(String key) {
        ensureConfigured();
        ObjectStorageService storage = storage();
        return storage.getReadUrl(storage.buildAccessUrl(key));
    }

    /** 读回对象字节（重解析、摘要取正文、页图校验等内部用途） */
    public byte[] get(String key) {
        ensureConfigured();
        return storage().readBytes(key);
    }

    /** 删除对象（版本清理用；失败只告警，不回滚调用方事务） */
    public void remove(String key) {
        ensureConfigured();
        try {
            ObjectStorageService storage = storage();
            storage.deleteObject(storage.buildAccessUrl(key));
        } catch (Exception e) {
            log.warn("对象删除失败（忽略）: key={} {}", key, e.getMessage());
        }
    }

    /** 按扩展名推断 Content-Type（未知扩展名回落 octet-stream） */
    public static String contentTypeOf(String name) {
        String ext = extOf(name);
        String mime = CONTENT_TYPES.get(ext);
        return mime == null ? "application/octet-stream" : mime;
    }

    /**
     * 当前存储后端快照（选型时点值，落 {@code sa_blob.storage_type} 用）：
     * 枚举名 LOCAL/MINIO/ALI/VOLCANO，与 {@link OssServiceSelector} 同一条取值链（每次现读）。
     */
    public String currentStorageType() {
        return DictEnumUtil.getEnumByValue(ossServiceSelector.currentType(), OssServiceTypeDict.class).name();
    }

    private ObjectStorageService storage() {
        return ossServiceSelector.select();
    }

    private void ensureConfigured() {
        if (isBlank(appKey) || isBlank(appSecret)) {
            throw new NoticeException("未配置对象存储（oss.endpoint/oss.appKey/oss.appSecret）"
                    + "，无法落原件与解析产物；当前 endpoint=" + endpoint + " bucket=" + bucket);
        }
    }

    private static String extOf(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalizeExt(String ext) {
        return ext.startsWith(".") ? ext.substring(1).toLowerCase(Locale.ROOT) : ext.toLowerCase(Locale.ROOT);
    }

    /** 对象名只保留安全字符，避免路径穿越与签名串里的非法字符 */
    private static String sanitizeName(String name) {
        String value = name == null ? "unnamed" : name.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        return value.replaceAll("[^A-Za-z0-9._\\-]", "_");
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
