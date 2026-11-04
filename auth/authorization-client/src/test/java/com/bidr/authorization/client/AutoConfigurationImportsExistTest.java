package com.bidr.authorization.client;

import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * `AutoConfiguration.imports` 条目可解析守卫。
 *
 * <p>🔴 背景（2026-11-04 实测）：本文件曾把 `AuthzClientAutoConfiguration` 写成
 * `com.bidr.authorization.client.config.…`，而类的真实包名是 `com.bidr.authorization.client`
 * —— 条目不存在的类不会编译报错，却在宿主启动时由 Spring Boot 的
 * `AutoConfigurationSorter` 抛 `Unable to read meta-data for class …`
 * （`FileNotFoundException`）导致**整个应用起不来**。测试面必须把它钉住。</p>
 *
 * @author sharp
 * @since 2026-11-04
 */
public class AutoConfigurationImportsExistTest {

    private static final String RESOURCE =
            "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    @Test
    public void everyImportsEntryResolvesToAClass() throws Exception {
        List<String> entries = readEntries();
        Assert.assertFalse("imports 条目为空（资源丢失也算红）", entries.isEmpty());

        for (String entry : entries) {
            // 加载失败即抛 ClassNotFoundException —— 幻影 FQN 在这里必须翻红
            Class.forName(entry, false, getClass().getClassLoader());
        }
    }

    @Test
    public void phantomPackageIsRejected() {
        // 负向合成腿：历史错误包名必须不存在；判据本身也可能失效，用 assert 显式钉住
        try {
            Class.forName("com.bidr.authorization.client.config.AuthzClientAutoConfiguration",
                    false, getClass().getClassLoader());
            Assert.fail("历史错误 FQN（client.config.*）不应存在——它会让宿主启动直接失败");
        } catch (ClassNotFoundException expected) {
            // 期望路径
        }
    }

    private static List<String> readEntries() throws Exception {
        InputStream in = AutoConfigurationImportsExistTest.class.getResourceAsStream(RESOURCE);
        Assert.assertNotNull("找不到 " + RESOURCE, in);
        List<String> out = new ArrayList<String>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    out.add(trimmed);
                }
            }
        }
        return out;
    }
}