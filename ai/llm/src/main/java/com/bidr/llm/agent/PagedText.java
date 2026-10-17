package com.bidr.llm.agent;

/**
 * Title: PagedText
 * Description: 「分页读正文」的通用语义件（窗口数学＋到底/续读/越界注记的统一口径）。
 * 之前同一套逻辑在三处各写一遍（core read_file、墨枢 fetch_document、FilePreviewService.read
 * 的 offset 归一），注记措辞还各自漂移（“还有后续”vs“传 offset=Y”）——2026-10-18 用户拍板
 * “同样的逻辑不要写两遍，哪个好留哪个”：本件取两家所长＝fetch 的<b>长度硬上限防线</b>
 * （模型可请求大窗口但工具侧收口，防提示词注入自放大灌爆上下文）＋read_file 的
 * <b>注记带下一页 offset 指令</b>（模型照抄续读，不用自己算数）。
 * <p>
 * 默认页长与上限是<b>调用方策略</b>不入本件（各工具场景不同）；本件只钉语义不钉数值。
 *
 * @author Sharp
 * @since 2026/10/18
 */
public final class PagedText {

    private PagedText() {
    }

    /** offset 归一：null/负数→0 */
    public static int offset(Integer offset) {
        return offset == null || offset < 0 ? 0 : offset;
    }

    /**
     * 长度归一＋硬上限：null/非正→defaultLen；超过 maxLen 收口到 maxLen。
     * 🔴 maxLen 必须由工具侧给定且<b>不暴露在注记里</b>——防线收在服务端，模型只能撞上限不能拆上限
     */
    public static int limit(Integer requested, int defaultLen, int maxLen) {
        int len = requested == null || requested <= 0 ? defaultLen : requested;
        return Math.min(len, maxLen);
    }

    /**
     * 进度注记（未到底/到底统一措辞，各工具原样引用）：
     * 未到底＝「本次返回 offset={from}..{to} / 全文 {total} 字；继续读下一段传 offset={to}」；
     * 已到底＝「已到文件末尾：本次返回 offset={from}..{to} / 全文 {total} 字」
     */
    public static String progress(int from, int to, int total) {
        if (to < total) {
            return "本次返回 offset=" + from + ".." + to + " / 全文 " + total + "字；继续读下一段传 offset=" + to;
        }
        return "已到文件末尾：本次返回 offset=" + from + ".." + to + " / 全文 " + total + "字";
    }

    /** 越界到底信号：offset 超出全文时给明确回话（带重读指路），别让模型对着空窗猜 */
    public static String beyond(int offset, int total) {
        return "offset=" + offset + " 已超出末尾（全文 " + total + "字）；如需重读传 offset=0";
    }
}
