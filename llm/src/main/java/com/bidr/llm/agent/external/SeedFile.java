package com.bidr.llm.agent.external;

/**
 * Title: SeedFile
 * Description: 派发外部任务时要先放进沙箱工作区的<b>背景文件</b>（底稿）。
 *
 * <p>默认派发是"空白开工"：上游会话新建即开轮，沙箱里除了提示词什么都没有。带上本类的清单后，
 * 框架会在<b>开轮之前</b>把它们逐个写进该会话的工作目录，模型于是是在既有文件上改，
 * 而不是凭转述重造——产物契约（路径与打包形态）与空白开工完全一致。</p>
 *
 * <p>只描述"文件"，不解释业务语义：技能包的解包、清单核对与版本归属都归调用方，
 * 本类因此对任何"带底稿的外部任务"通用（改技能、按模板出文书、复核既有产物…）。</p>
 *
 * @author sharp
 * @since 2026-10-03
 */
public final class SeedFile {

    private final String relativePath;

    private final byte[] content;

    public SeedFile(String relativePath, byte[] content) {
        this.relativePath = relativePath;
        this.content = content;
    }

    /** 工作目录内的相对路径（可含子目录，如 {@code templates/report.md}）；绝对路径与 {@code ..} 由实现方拒绝 */
    public String getRelativePath() {
        return relativePath;
    }

    /** 文件字节（原样写入，不做文本转换——二进制资源也走这一条路） */
    public byte[] getContent() {
        return content;
    }
}
