package com.bidr.agent.console.skill;

import java.util.List;

/**
 * Title: SkillCatalogPort
 * Description: 技能目录读取契约（M14 棒 3）：把"会话运行期读技能包"这件事从某张具体表上摘下来。
 *
 * <p><b>为什么只有四个方法、且一个空间参数都没有</b>：契约下沉≠消灭空间语义——"这一轮到底读得到哪些包"
 * 本来就是实现方的业务口径（InkHub＝本空间已安装 ∧ 已发布，再并上作者本人的未发布草稿测试旁路），
 * 框架既不认空间也不该认。把 spaceId 写进签名，等于替所有形态预设"一定有空间"这一件事，
 * 而系统级技能（棒 4）与无空间后台会话恰恰没有它。所以这里只按名字读，可见集由实现决定。</p>
 *
 * <p><b>报错语义归实现</b>：三种"读不到"（没有这个包／有包但当前不可读／包在存储里缺失）要分开报，
 * 因为只有实现方知道差在哪一层。实现直接抛业务异常即可，工具外壳不包装、不吞——
 * 框架工具层会把异常文本原样回给模型自纠。</p>
 *
 * <p>实现方缺席时本契约没有任何默认行为：谁要用谁注入，注入不到就少挂那几只工具，
 * 不影响会话其余链路（与能力包登记 SPI 同一套"包缺席则工具降级不挂"的口径）。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
public interface SkillCatalogPort {

    /**
     * 本轮读得到的技能集（名字＋版本＋是否已发布），用于装配单登记与"清单＝可读集"对账。
     * 判据必须与 {@link #read(String)} 的闸门<b>同一处实现</b>——否则会出现"清单里有、读了报错"。
     */
    List<SkillCatalogEntry> visible();

    /** SKILL.md 全文（该技能的流程说明与产出契约） */
    String read(String skillName);

    /** 包内文件清单（相对包根的路径） */
    List<String> listFiles(String skillName);

    /** 包内单个文件的原始字节（路径须在包契约内；文本解码由消费方定） */
    byte[] readFile(String skillName, String path);
}
