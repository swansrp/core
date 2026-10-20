package com.bidr.kernel.architecture;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Title: VoidEndpointNoticeConventionTest
 * Description: void 端点必须回封套的收口守卫（离线：只扫磁盘源码文本，不起 Spring、不连库）。
 * <p>
 * 框架的统一响应契约是 {@code ResponseExceptionHandler#beforeBodyWrite} 把返回值包成
 * {@code {status, payload}} 封套；但 {@code void} 方法选不出消息转换器，Spring 直接静默结束、
 * advice 根本不进 ⇒ HTTP 200 + 0 字节 body。前端 {@code parseBigInt} 对空串 {@code JSON.parse}
 * 会抛 {@code Unexpected end of JSON input}，且读不到 status＝"假成功"通路。
 * 裁定（用户 2026-10-20）：<b>返回 void 的 controller 端点一律要 {@code Resp.notice}，不允许真空 body</b>。
 * 需要回数据的断言型端点（如 {@code /user/existed}）改为非 void 返回值，同样不落空体。
 * </p>
 * <p>
 * 判定只看方法体文本，不解析 AST——够抓"忘了补"，不追求精确。三类合法豁免按文本识别：
 * ① 写二进制/流的端点（签名或体内出现 {@code HttpServletResponse}／{@code getOutputStream}／
 * {@code getWriter}／{@code sendRedirect}／SSE），body 本来就不是封套；
 * ② 直接 {@code throw new NoticeException}（等价于 {@code Resp.notice} 的实现）；
 * ③ 方法体只有一条 {@code throw new}（未实现桩／参数直拒）——走不到"200 + 空 body"，由异常处理器回错误封套；
 * ④ 紧邻上方没有 mapping 注解的方法——那是基类模板钩子（{@code beforeAdd}/{@code afterLogin} 等），
 * 不是 HTTP 端点，补 notice 反而会给非端点方法加弹窗。
 * </p>
 * <p>
 * 🔴 针自证纪律：负向腿把合成"void 无 notice"源码喂给同一判定函数、断言判红，再拿既有真端点与
 * 合成合规样本判绿；端点数与文件数设下限防空扫描假绿（改动扫描规则必须先让这两条腿还亮着）。
 * </p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-20
 */
public class VoidEndpointNoticeConventionTest {

    /** 合法形态：方法体文本出现此字面量即视为"回了封套" */
    private static final String NOTICE_NEEDLE = "Resp.notice(";
    private static final String NOTICE_THROW_NEEDLE = "throw new NoticeException";
    /** 合法形态：二进制/流端点，body 不可能是封套 */
    private static final String[] BINARY_NEEDLES = {
            "HttpServletResponse", "getOutputStream", "getWriter", "sendRedirect", "text/event-stream"};
    private static final String[] MAPPING_ANNOTATIONS = {
            "@RequestMapping", "@GetMapping", "@PostMapping", "@PutMapping", "@DeleteMapping"};

    /** 实况下限（2026-10-20 收口后框架侧 159 个 void 端点／约 60 个文件），防"扫了个空目录"假绿 */
    private static final int MIN_VOID_ENDPOINTS = 120;
    private static final int MIN_SCANNED_FILES = 40;

    @Test
    public void everyMappedVoidEndpointAnnouncesEnvelope() throws IOException {
        Path root = frameworkRoot();
        Scan scan = scanDir(root);
        Assert.assertTrue("扫描到的 void 端点仅 " + scan.endpoints + " 个，低于下限 " + MIN_VOID_ENDPOINTS
                        + "——多半是根目录定位失败或扫描规则被改坏，本守卫已失效",
                scan.endpoints >= MIN_VOID_ENDPOINTS);
        Assert.assertTrue("扫描到的 controller 文件仅 " + scan.files + " 个，低于下限 " + MIN_SCANNED_FILES,
                scan.files >= MIN_SCANNED_FILES);
        Assert.assertTrue("以下 void 端点没补 Resp.notice（HTTP 200 + 空 body，前端判不出成功）：\n            "
                        + String.join("\n            ", scan.violations),
                scan.violations.isEmpty());
    }

    /** 负向腿：合成违规样本必须被判红，否则上面的断言是恒真 */
    @Test
    public void probeFlagsSyntheticViolationAndIgnoresHook() {
        String violation = String.join("\n",
                "package demo;",
                "import org.springframework.web.bind.annotation.RequestMapping;",
                "class DemoController {",
                "    @RequestMapping(value = \"/save\", method = RequestMethod.POST)",
                "    public void save(String id) {",
                "        service.save(id);",
                "    }",
                "    @RequestMapping(value = \"/mixed\", method = RequestMethod.POST)",
                "    public void mixed(String id) {",
                "        throw new IllegalArgumentException(\"bad id\");",
                "        service.save(id);",
                "    }",
                "}");
        Scan probe = new Scan();
        probeSource(probe, violation, "demo/DemoController.java");
        Assert.assertEquals("合成违规样本必须被抓到（针自证，含'先 throw 后仍有正常通路'那条）", 2, probe.violations.size());
        Assert.assertTrue("违规定位须含文件与行号", probe.violations.get(0).contains("DemoController.java:5"));

        String compliant = String.join("\n",
                "package demo;",
                "class DemoController {",
                "    @RequestMapping(value = \"/save\", method = RequestMethod.POST)",
                "    public void save(String id) {",
                "        service.save(id);",
                "        Resp.notice(\"保存成功\");",
                "    }",
                "    @RequestMapping(value = \"/export\", method = RequestMethod.GET)",
                "    public void export(HttpServletResponse response) {",
                "        response.getWriter();",
                "    }",
                "    @RequestMapping(value = \"/csv\", method = RequestMethod.POST)",
                "    public void importCsv(String subTableName) {",
                "        throw new UnsupportedOperationException(\"CSV import not yet implemented\");",
                "    }",
                "    protected void beforeAdd(Entity e) {",
                "        e.setId(null);",
                "    }",
                "}");
        Scan ok = new Scan();
        probeSource(ok, compliant, "demo/OkController.java");
        Assert.assertEquals("合规样本（notice／二进制腿／只有一条 throw 的桩）不该判红", 0, ok.violations.size());
        Assert.assertEquals("非端点的模板钩子不该进扫描面", 3, ok.endpoints);
    }

    /** 真端点抽样判绿：收口后的三条，必须仍然被判定为"已回封套" */
    @Test
    public void knownEndpointsStillPass() throws IOException {
        Path root = frameworkRoot();
        assertEndpointClean(root.resolve("base/kernel/src/main/java/com/bidr/kernel/controller/BaseAdminController.java"),
                "Resp.notice(\"导入任务已提交\")");
        assertEndpointClean(root.resolve("auth/authorization/src/main/java/com/bidr/authorization/controller/login/LogoffController.java"),
                "Resp.notice(\"已退出登录\")");
        // 断言型端点改成了非 void 返回值，不再是扫描面里的 void 端点
        String userInfo = new String(Files.readAllBytes(root.resolve(
                "auth/authorization/src/main/java/com/bidr/authorization/controller/login/UserInfoController.java")),
                StandardCharsets.UTF_8);
        Assert.assertFalse("两条 existed 断言端点不得再是 void", userInfo.contains("public void userExisted"));
        Assert.assertFalse("两条 existed 断言端点不得再是 void", userInfo.contains("public void userNotExisted"));
    }

    private void assertEndpointClean(Path file, String expectedNeedle) throws IOException {
        Assert.assertTrue("文件不存在：" + file, Files.isRegularFile(file));
        Scan scan = new Scan();
        probeSource(scan, new String(Files.readAllBytes(file), StandardCharsets.UTF_8), file.toString());
        Assert.assertTrue(file.getFileName() + " 仍有未补 notice 的 void 端点：" + scan.violations,
                scan.violations.isEmpty());
        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        Assert.assertTrue("应包含补上的提示文案：" + expectedNeedle, content.contains(expectedNeedle));
    }

    private static class Scan {
        int endpoints;
        int files;
        final List<String> violations = new ArrayList<>();
    }

    private Scan scanDir(Path root) throws IOException {
        Scan scan = new Scan();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> sources = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().endsWith("Test.java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/controller/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .collect(Collectors.toList());
            for (Path p : sources) {
                scan.files++;
                probeSource(scan, new String(Files.readAllBytes(p), StandardCharsets.UTF_8),
                        root.relativize(p).toString().replace('\\', '/'));
            }
        }
        return scan;
    }

    /**
     * 判定主体：逐行找 {@code public|protected void name(}，要求紧邻上方的连续注解块里有 mapping 注解，
     * 再取配对的方法体，缺 {@code Resp.notice(} 且非二进制/非 NoticeException 腿即记违规。
     */
    private void probeSource(Scan scan, String source, String fileLabel) {
        String content = source.replace("\r\n", "\n");
        String[] lines = content.split("\n", -1);
        int offset = 0;
        for (int idx = 0; idx < lines.length; idx++) {
            String line = lines[idx];
            int lineStart = offset;
            offset += line.length() + 1;
            if (!line.matches("^\\s*(public|protected)\\s+void\\s+\\w+\\s*\\(.*")) {
                continue;
            }
            boolean mapped = false;
            for (int k = idx - 1; k >= 0; k--) {
                String above = lines[k].trim();
                if (above.isEmpty() || above.startsWith("*") || above.startsWith("/*") || above.startsWith("//")) {
                    continue;
                }
                if (above.startsWith("@")) {
                    for (String annotation : MAPPING_ANNOTATIONS) {
                        if (above.contains(annotation)) {
                            mapped = true;
                        }
                    }
                    continue;
                }
                break;
            }
            if (!mapped) {
                continue;
            }
            scan.endpoints++;
            String signature = block(content, content.indexOf('(', lineStart), '(', ')');
            String body = block(content, content.indexOf('{', lineStart + signature.length() + 1), '{', '}');
            if (body.contains(NOTICE_NEEDLE) || body.contains(NOTICE_THROW_NEEDLE)) {
                continue;
            }
            String context = signature + body;
            if (containsAny(context, BINARY_NEEDLES)) {
                continue;
            }
            if (isThrowOnlyBody(body)) {
                continue;
            }
            scan.violations.add(fileLabel + ":" + (idx + 1) + "  " + line.trim());
        }
    }

    /**
     * 只有一条 throw 的方法体＝端点故意失败（未实现桩、参数非法直拒），根本走不到"200 + 空 body"，
     * 由异常处理器回错误封套，不算违约。注意必须"只有一条"：体内既有正常通路又有 throw 的，
     * 正常通路照样会回空 body，得留在违规面里。
     */
    private boolean isThrowOnlyBody(String body) {
        String inner = body.replaceFirst("^\\{", "").replaceFirst("\\}$", "").trim();
        int firstSemicolon = inner.indexOf(';');
        return inner.startsWith("throw new")
                && firstSemicolon > 0
                && inner.substring(firstSemicolon + 1).trim().isEmpty();
    }

    private boolean containsAny(String text, String[] needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String block(String content, int from, char open, char close) {
        int start = content.indexOf(open, from);
        if (start < 0) {
            return "";
        }
        int depth = 0;
        for (int j = start; j < content.length(); j++) {
            if (content.charAt(j) == open) {
                depth++;
            } else if (content.charAt(j) == close && --depth == 0) {
                return content.substring(start, j + 1);
            }
        }
        return content.substring(start);
    }

    /**
     * 从模块工作目录上溯找框架根＝名为 core 且含 {@code base/kernel/pom.xml} 的那一层。
     * 不用 .git 判定：core 是子模块，其 .git 是文件而非目录，且源码打包环境可能根本没有 .git。
     */
    private Path frameworkRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if ("core".equals(dir.getFileName().toString())
                    && Files.isRegularFile(dir.resolve("base/kernel/pom.xml"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("定位不到框架仓根（需为名为 core 且含 base/kernel/pom.xml 的目录）：从 "
                + Paths.get("").toAbsolutePath() + " 上溯失败");
    }
}
