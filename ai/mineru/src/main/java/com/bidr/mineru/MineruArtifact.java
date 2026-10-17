package com.bidr.mineru;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: MineruArtifact
 * Description: MinerU 结果 zip 的归一化产物（两条腿的 zip 内容物与字段名逐字相同，故解码共用一份）。
 * <p>
 * 只承载<b>原始材料</b>：markdown 正文、content_list v1 原文 JSON、图片字节。
 * <b>不做</b> PageBlock/bbox/字符偏移那套引用定位归一——那是 InkHub 原文预览框选高亮的私产
 * （需要 block 文本在 markdown 中的偏移探测），其他消费方（如判分喂模型）用不到，
 * 谁要谁在自己的引擎/业务层做，core 不背这层语义。
 *
 * @author Sharp
 * @since 2026/10/17
 */
@Data
public class MineruArtifact {

    /** full.md 正文（两腿必产，缺失＝解码失败） */
    private String markdown;

    /** content_list v1 原文 JSON（数组：type/text/img_path/bbox/page_idx 块）；缺席为 null 不判失败 */
    private String contentList;

    /** zip 里的图片条目（images/*.jpg 等；提交关 return_images 时为空表） */
    private List<MineruImage> images = new ArrayList<>();

    /**
     * Title: MineruImage
     * Description: zip 内图片条目：name 已归一为 markdown 里的引用形态（剥掉打包外层目录）
     */
    @Data
    public static class MineruImage {

        /** markdown 引用形态的名字（如 images/x.jpg） */
        private String name;

        /** 图片字节 */
        private byte[] data;
    }
}
