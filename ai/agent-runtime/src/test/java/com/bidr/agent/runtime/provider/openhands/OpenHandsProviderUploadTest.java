package com.bidr.agent.runtime.provider.openhands;

import com.bidr.kernel.exception.ServiceException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Title: OpenHandsProviderUploadTest
 * Description: 附件/底稿写入沙箱工作区的**路径口径**测试。该路径会被拼进沙箱绝对路径交给上游写文件
 * 接口，不清洗等于把"任意路径写"开放给调用方（上游那一把 session key 只准入、不认身份）。
 * <p>
 * 🔴 写侧与读侧现在是<b>同一把尺子</b>（都走 {@link OpenHandsProvider#safeRelPath}）：旧写实现"只取
 * basename"看着更安全，实际会把多文件底稿压平——{@code templates/x.md} 与 {@code examples/x.md}
 * 落成同一个文件、后者覆盖前者。因此本测试锁两件事：① 合法嵌套相对路径必须<b>原样保留</b>目录；
 * ② 绝对路径／盘符／{@code ..} 段一律拒。
 * <p>
 * "浏览器只许交一个展示名"的收口不在这里，在 relay 层（见 {@code AgentRuntimeRelayUploadNameTest}）。
 * <p>
 * ⚠️ 本模块跑在 JUnit 5（surefire-junit-platform）上：写成 JUnit 4 会被**静默跳过**
 * （表现为 Tests run: 0 + BUILD SUCCESS），故一律用 jupiter API。
 *
 * @author sharp
 * @since 2026/9/24
 */
public class OpenHandsProviderUploadTest {

    @Test
    public void 嵌套相对路径保留目录不被压平() {
        Assertions.assertEquals("SKILL.md", OpenHandsProvider.safeRelPath("SKILL.md"));
        Assertions.assertEquals("templates/报告模板.md",
                OpenHandsProvider.safeRelPath("templates/报告模板.md"));
        // 不同子目录的同名成员必须落到不同路径（压平就会互相顶掉，这是本次改口径的唯一动机）
        Assertions.assertNotEquals(
                OpenHandsProvider.safeRelPath("templates/report.md"),
                OpenHandsProvider.safeRelPath("examples/report.md"));
        // 反斜杠归一为分隔符，不是拒绝理由
        Assertions.assertEquals("a/b.md", OpenHandsProvider.safeRelPath("a\\b.md"));
    }

    @Test
    public void 绝对路径盘符与穿越一律拒() {
        for (String risky : new String[]{"/etc/passwd", "C:/windows/x.ini", "\\workspace\\x",
                "../../etc/passwd", "..\\..\\windows\\evil.txt", "out/../../x", "a/./b", "./a",
                "/", "//", "", "   ", null}) {
            Assertions.assertThrows(ServiceException.class,
                    () -> OpenHandsProvider.safeRelPath(risky), "应拒绝：" + risky);
        }
    }

    @Test
    public void 交给上游的展示名取末段() {
        Assertions.assertEquals("SKILL.md", OpenHandsProvider.basename("SKILL.md"));
        Assertions.assertEquals("报告模板.md", OpenHandsProvider.basename("templates/报告模板.md"));
        Assertions.assertEquals("a.txt", OpenHandsProvider.basename("docs/sub/a.txt"));
    }

    @Test
    public void 清洗结果必然相对且逐段无穿越() {
        for (String raw : new String[]{"out/skill.zip", "templates/报告.md", "a b/c.txt", "...", "..x", "a/...b"}) {
            String safe = OpenHandsProvider.safeRelPath(raw);
            Assertions.assertFalse(safe.startsWith("/"), "不得变成绝对路径：" + raw);
            Assertions.assertFalse(safe.startsWith("../"), "不得以穿越段开头：" + raw);
            for (String segment : safe.split("/")) {
                // 判据是"段"而不是"字符串里有没有两个点"：... 与 ..x 都是合法文件名（DownloadTest 同口径）
                Assertions.assertNotEquals("..", segment, "不得残留穿越段：" + raw + " → " + safe);
                Assertions.assertNotEquals(".", segment, "不得残留空转段：" + raw + " → " + safe);
                Assertions.assertFalse(segment.isEmpty(), "不得残留空段：" + raw + " → " + safe);
            }
        }
    }
}
