package com.bidr.llm.agent;

import org.junit.Assert;
import org.junit.Test;

/**
 * Title: PagedTextTest
 * Description: 分页语义件的正反腿：offset/limit 归一（含硬上限收口）、progress 两分支措辞、
 * beyond 越界信号。另带一条防漂移自证（措辞里必须含下一页 offset 指令——这是统一时
 * "留 read_file 长处"的那条判据，丢了它等于退回"还有后续"式的含糊）。
 *
 * @author Sharp
 * @since 2026/10/18
 */
public class PagedTextTest {

    @Test
    public void offset归一() {
        Assert.assertEquals(0, PagedText.offset(null));
        Assert.assertEquals(0, PagedText.offset(-5));
        Assert.assertEquals(7, PagedText.offset(7));
    }

    @Test
    public void limit归一与硬上限收口() {
        Assert.assertEquals(4000, PagedText.limit(null, 4000, 8000));
        Assert.assertEquals(4000, PagedText.limit(0, 4000, 8000));
        Assert.assertEquals(8000, PagedText.limit(999999, 4000, 8000));
        Assert.assertEquals(5000, PagedText.limit(5000, 4000, 8000));
    }

    @Test
    public void 未到底注记带下一页offset指令() {
        String note = PagedText.progress(100, 200, 300);
        Assert.assertEquals("本次返回 offset=100..200 / 全文 300字；继续读下一段传 offset=200", note);
    }

    @Test
    public void 到底注记明说() {
        Assert.assertEquals("已到文件末尾：本次返回 offset=200..300 / 全文 300字", PagedText.progress(200, 300, 300));
    }

    @Test
    public void 越界信号带重读指路() {
        Assert.assertEquals("offset=999 已超出末尾（全文 300字）；如需重读传 offset=0", PagedText.beyond(999, 300));
    }

    /** 措辞防漂移针：续读指令丢了＝退回含糊注记，统一就失去意义 */
    @Test
    public void 续读指令不许漂移() {
        Assert.assertTrue(PagedText.progress(0, 10, 20).contains("传 offset=10"));
        Assert.assertTrue(PagedText.beyond(99, 20).contains("传 offset=0"));
    }
}
