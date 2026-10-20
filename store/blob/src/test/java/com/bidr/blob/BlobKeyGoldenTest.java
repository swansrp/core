package com.bidr.blob;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 8 个 key 前缀的 golden 对照腿（M16 棒 1 判据①：行为逐字不变）。
 *
 * <p>期望值是<b>旧实现的输出字面量</b>（inkhub-file 的 {@code BlobStorageService} 七个前缀静态方法
 * ＋渲染侧自拼的 {@code render/} 前缀），不是运行时新旧双跑（旧类已删）——同输入 sha/锚，
 * 新 {@link BlobStore} 产出的 key 串与这里的字面量<b>逐字相等</b>。key 规则是页码定位与
 * 视觉工具的共同契约，任何一处漂移都会让既有产物失联。</p>
 *
 * @author sharp
 */
class BlobKeyGoldenTest {

    private static final String SHA = "9f2c1d3e4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a6079";

    /** 前缀 1/8：原件（全文唯一一份，按内容哈希定位；扩展名归一化并去点，无扩展名不加点） */
    @Test
    void golden01_rawKey() {
        assertEquals("raw/9f2c1d3e4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a6079.pdf",
                BlobStore.rawKey(SHA, "pdf"));
        assertEquals("raw/9f2c1d3e4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a6079.pdf",
                BlobStore.rawKey(SHA, ".PDF"), "扩展名归一化并去点");
        assertEquals("raw/9f2c1d3e4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a6079",
                BlobStore.rawKey(SHA, null), "无扩展名不加点");
    }

    /** 前缀 2/8：解析产物 markdown（按版本留档） */
    @Test
    void golden02_parsedMarkdownKey() {
        assertEquals("parsed/9f2c1d3e4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a6079/v1.md",
                BlobStore.parsedMarkdownKey(SHA, 1));
    }

    /** 前缀 3/8：office 转换产物 PDF（按原件内容哈希寻址＝解析锚） */
    @Test
    void golden03_convertedPdfKey() {
        assertEquals("convert/9f2c1d3e4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a6079.pdf",
                BlobStore.convertedPdfKey(SHA));
    }

    /** 前缀 4/8：页码映射 JSON（与 markdown 同哈希同版本，便于成对回溯） */
    @Test
    void golden04_pageMapKey() {
        assertEquals("pagemap/9f2c1d3e4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a6079/v2.json",
                BlobStore.pageMapKey(SHA, 2));
    }

    /** 前缀 5/8：表册索引 JSON（表号→表名→页码 清单，按版本留档） */
    @Test
    void golden05_formIndexKey() {
        assertEquals("form-index/9f2c1d3e4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a6079/v1.json",
                BlobStore.formIndexKey(SHA, 1));
    }

    /** 前缀 6/8：页图（页码从 1 起，对齐"第 N 页"话术） */
    @Test
    void golden06_thumbKey() {
        assertEquals("thumb/9f2c1d3e4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a6079/p1.png",
                BlobStore.thumbKey(SHA, 1));
        assertEquals("thumb/9f2c1d3e4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a6079/p12.png",
                BlobStore.thumbKey(SHA, 12));
    }

    /** 前缀 7/8：内嵌图片（路径拍平为文件名、不安全字符换下划线；对象名与 key 尾段同源） */
    @Test
    void golden07_imageKey() {
        assertEquals("image/9f2c1d3e4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a6079/img_0.png",
                BlobStore.imageKey(SHA, "images/img 0.png"), "MinerU 产物的相对路径不能进 key，须拍平为文件名");
        assertEquals("image/9f2c1d3e4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a6079/unnamed",
                BlobStore.imageKey(SHA, null));
        assertEquals("img_0.png", BlobStore.imageObjectName("images/img 0.png"));
        String name = BlobStore.imageObjectName("../../evil$/a b.png");
        assertEquals("image/" + SHA + "/" + name, BlobStore.imageKey(SHA, "../../evil$/a b.png"),
                "imageKey 传清洗后的名字须与传原始名等价（幂等）");
        assertEquals("inkhub-image://" + SHA + "/" + name, BlobStore.imagePlaceholder(SHA, name),
                "占位与 key 用同一个对象名");
    }

    /**
     * 前缀 8/8：渲染可交付件（收编旧 DocumentRenderService 自拼的 {@code "render/" + sha + "." + format}
     * 第二正源；同 sha 同 format 逐字相等，同内容幂等复用依赖这条）。
     */
    @Test
    void golden08_renderKey() {
        assertEquals("render/9f2c1d3e4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a6079.md",
                BlobStore.renderKey(SHA, "md"));
        assertEquals("render/9f2c1d3e4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a60798f1e2d3c4b5a6079.xlsx",
                BlobStore.renderKey(SHA, "xlsx"));
    }

    /** 按扩展名推断 Content-Type（未知扩展名回落 octet-stream；扩展名大小写不敏感） */
    @Test
    void contentTypeOf_mapsKnownExtensions() {
        assertEquals("application/pdf", BlobStore.contentTypeOf("raw/x.pdf"));
        assertEquals("image/png", BlobStore.contentTypeOf("thumb/x/p1.PNG"), "扩展名大小写不敏感");
        assertEquals("text/markdown; charset=utf-8", BlobStore.contentTypeOf("parsed/x/v1.md"));
        assertEquals("application/json; charset=utf-8", BlobStore.contentTypeOf("render/x.json"));
        assertEquals("application/octet-stream", BlobStore.contentTypeOf("x.weird"));
    }
}
