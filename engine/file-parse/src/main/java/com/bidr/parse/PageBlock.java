package com.bidr.parse;

import lombok.Data;

/**
 * Title: PageBlock
 * Description: 页码块：解析产物中一个内容块的定位信息（M17 棒 3 自业务 engine-api 迁入——
 * 引擎层出现后 pagemap 是引擎产物的组成部分，D-3 边界随层改判）。
 * <p>页码定位算法（D8）：检索命中的 start_at + 命中词在片段内的偏移 → 在本列表中按
 * start 有序查找所属块 → 得到 pageIdx 与 bbox。块按 start 升序。</p>
 *
 * @author Sharp
 * @since 2026-10-21
 */
@Data
public class PageBlock {

    /** 块类型：text / image / table / list 等 */
    private String type;

    /** 块文本（与 markdown 正文对齐：table 块为 HTML 原文，list 块为条目按行拼接） */
    private String text;

    /** 页内坐标 [x0, y0, x1, y1]（可为 null；依解析通道而定，office 直解析通道无） */
    private double[] bbox;

    /** 标题层级（1=H1…，来自 content_list 的 text_level；非标题块为 null——章节切分的输入） */
    private Integer textLevel;

    /** PDF 页码，从 0 起 */
    private int pageIdx;

    /** 该块文本在 markdown 全文中的起始字符偏移（由归一化层计算并填充） */
    private int start;
}
