package com.bidr.agent.runtime.provider.openhands;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Title: OpenHandsProviderUploadTest
 * Description: 附件代收转推的**文件名清洗**测试：该名字会被拼进沙箱绝对路径交给上游写文件接口，
 * 不清洗就等于把"任意路径写文件"开放给浏览器。锁死目录穿越、绝对路径、空名与超长四类。
 * <p>
 * ⚠️ 本模块跑在 JUnit 5（surefire-junit-platform）上：写成 JUnit 4 会被**静默跳过**
 * （表现为 Tests run: 0 + BUILD SUCCESS），故一律用 jupiter API。
 *
 * @author sharp
 * @since 2026/9/24
 */
public class OpenHandsProviderUploadTest {

    @Test
    public void 目录穿越与绝对路径只能剩下文件名() {
        Assertions.assertEquals("passwd", OpenHandsProvider.safeFileName("../../etc/passwd"));
        Assertions.assertEquals("passwd", OpenHandsProvider.safeFileName("/etc/passwd"));
        Assertions.assertEquals("evil.txt", OpenHandsProvider.safeFileName("..\\..\\windows\\evil.txt"));
        Assertions.assertEquals("a.txt", OpenHandsProvider.safeFileName("docs/a.txt"));
    }

    @Test
    public void 空名与点开头不得产生隐藏文件或空路径段() {
        Assertions.assertEquals("file", OpenHandsProvider.safeFileName(null));
        Assertions.assertEquals("file", OpenHandsProvider.safeFileName("   "));
        // 穿越与纯点号名不报错，但结果必须既不含路径分隔符也不含 ".."，且非空
        for (String risky : new String[]{"..", ".", "...", "..%2f", "./.."}) {
            String safe = OpenHandsProvider.safeFileName(risky);
            Assertions.assertFalse(safe.isEmpty(), "不得为空名：" + risky);
            Assertions.assertFalse(safe.contains(".."), "不得残留穿越：" + risky + " → " + safe);
            Assertions.assertFalse(safe.contains("/") || safe.contains("\\"),
                    "不得残留路径分隔：" + risky + " → " + safe);
        }
        // 隐藏文件（以点开头）被前缀化，不再以点起始
        String hidden = OpenHandsProvider.safeFileName(".secret");
        Assertions.assertFalse(hidden.startsWith("."), "不得留隐藏文件：" + hidden);
    }

    @Test
    public void 非法字符替换且中文与常规扩展名保留() {
        Assertions.assertEquals("a_b_c.txt", OpenHandsProvider.safeFileName("a b;c.txt"));
        Assertions.assertEquals("施工方案.md", OpenHandsProvider.safeFileName("施工方案.md"));
        Assertions.assertEquals("v1.2-final_.tar.gz", OpenHandsProvider.safeFileName("v1.2-final$.tar.gz"));
    }

    @Test
    public void 超长名截断到尾部且保留扩展名() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            builder.append('x');
        }
        builder.append(".txt");
        String safe = OpenHandsProvider.safeFileName(builder.toString());
        Assertions.assertTrue(safe.length() <= 120, "应限长");
        Assertions.assertTrue(safe.endsWith(".txt"), "应保留扩展名");
    }
}
