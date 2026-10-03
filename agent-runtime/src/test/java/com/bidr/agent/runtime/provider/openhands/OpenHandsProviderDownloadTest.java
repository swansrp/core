package com.bidr.agent.runtime.provider.openhands;

import com.bidr.kernel.exception.ServiceException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Title: OpenHandsProviderDownloadTest
 * Description: 产物回读的**相对路径清洗**测试。该名字会被拼进沙箱绝对路径交给上游读文件接口，
 * 不清洗就等于把"读工作区外任意文件"开放给调用方（上游那一把 session key 本就只准入不认身份）。
 * <p>
 * 读侧与写侧现在共用同一个清洗方法（写侧旧实现"只取 basename"已于带底稿派发时改掉了）：
 * 两侧都必须放过嵌套相对路径（产物按派单约定落在 {@code out/} 前缀下），
 * 故这里既锁"放过合法嵌套"，也锁"逃逸一律拒"。
 * <p>
 * ⚠️ 本模块跑在 JUnit 5（surefire-junit-platform）上：写成 JUnit 4 会被**静默跳过**
 * （表现为 Tests run: 0 + BUILD SUCCESS），故一律用 jupiter API。
 *
 * @author sharp
 * @since 2026/10/2
 */
public class OpenHandsProviderDownloadTest {

    @Test
    public void 合法嵌套相对路径原样放过() {
        Assertions.assertEquals("out/skill.zip", OpenHandsProvider.safeRelPath("out/skill.zip"));
        // 反斜杠归一为分隔符，不是拒绝理由（Windows 侧拼出来的路径要能用）
        Assertions.assertEquals("out/skill.zip", OpenHandsProvider.safeRelPath("out\\skill.zip"));
        Assertions.assertEquals("SKILL.md", OpenHandsProvider.safeRelPath("  SKILL.md  "));
        // 尾随分隔符被 split 吃掉，结果即目录名本身（读它必然 404，但不是逃逸，故不拒）
        Assertions.assertEquals("out", OpenHandsProvider.safeRelPath("out/"));
        // 沙箱产物的文件名不受写侧白名单约束（中文、空格、多点扩展名都可读）
        Assertions.assertEquals("出稿 说明.v1.2.tar.gz", OpenHandsProvider.safeRelPath("出稿 说明.v1.2.tar.gz"));
        Assertions.assertEquals("out/...", OpenHandsProvider.safeRelPath("out/..."));
    }

    @Test
    public void 绝对路径与盘符一律拒() {
        for (String risky : new String[]{"/etc/passwd", "/workspace", "C:/windows/system.ini",
                "\\workspace\\x.zip", "../escape.zip"}) {
            Assertions.assertThrows(ServiceException.class,
                    () -> OpenHandsProvider.safeRelPath(risky), "应拒绝：" + risky);
        }
    }

    @Test
    public void 逃逸出工作目录的路径段一律拒() {
        for (String risky : new String[]{"out/../../etc/passwd", "out/../x", "a/./b", "./a/b/c.md",
                "out//x"}) {
            Assertions.assertThrows(ServiceException.class,
                    () -> OpenHandsProvider.safeRelPath(risky), "应拒绝：" + risky);
        }
    }

    @Test
    public void 空路径不得退化成工作目录本身() {
        // 空/纯分隔符若放过，拼出来就是 workspaceDir + "/"，等于让调用方读目录
        for (String blank : new String[]{null, "", "   ", "/", "\\", "//"}) {
            Assertions.assertThrows(ServiceException.class,
                    () -> OpenHandsProvider.safeRelPath(blank), "应拒绝空路径：" + blank);
        }
    }

    @Test
    public void 清洗结果必然仍是相对且无穿越() {
        String safe = OpenHandsProvider.safeRelPath("out/sub/产物.zip");
        Assertions.assertFalse(safe.startsWith("/"), "不得变成绝对路径");
        Assertions.assertFalse(safe.contains(".."), "不得残留穿越");
        Assertions.assertEquals("out/sub/产物.zip", safe);
    }
}
