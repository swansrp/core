package com.bidr.authorization.service.menu;

import com.bidr.authorization.service.menu.MenuDeclaration.Node;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Title: MenuDeclarationTest
 * Description: 菜单声明纯函数离线单测（纯 JUnit4、不起 Spring、不碰库）——
 * 覆盖：校验负/正对照（component 全局唯一、同父 title 唯一，针自证纪律：每个负例配一个正对照，
 * 证明校验器不是"逢配置必抛"）；嵌套树归并腿（同 topnav 按 title∨path 合并、子树并集、
 * 字段不一致 fail-fast）；两份真实 menu-seed.yml 的解析正对照腿（authorization classpath 一份
 * 必读，inkhub-common 一份按模块在场判定——独立构建 core 仓时该腿 assume 跳过并在报告注明）。
 * MenuSeeder 的 DB 写路径不在本测覆盖（交真机腿，见 MenuSeeder 类注释）。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-17
 */
public class MenuDeclarationTest {

    private static List<Node> one(String yaml) {
        return MenuDeclaration.parseTree(yaml, "unit-test");
    }

    private static void validate(List<Node> roots) {
        MenuDeclaration.validate(roots);
    }

    private static List<List<Node>> wrap(List<Node>... trees) {
        return new ArrayList<>(Arrays.asList(trees));
    }

    // ==================== 校验：负例（冲突即抛＝配置错误开发期炸） ====================

    @Test
    public void duplicateComponentAcrossTreeMustThrow() {
        List<Node> roots = one(""
                + "menus:\n"
                + "  - title: 导航甲\n"
                + "    path: navA\n"
                + "    children:\n"
                + "      - {title: 页一, path: p1, component: /views/dup/index.vue}\n"
                + "      - {title: 页二, path: p2, component: /views/dup/index.vue}\n");
        assertThrowsContains(() -> validate(roots), "component 全局唯一冲突");
    }

    @Test
    public void duplicateTitleUnderSameParentMustThrow() {
        List<Node> roots = one(""
                + "menus:\n"
                + "  - title: 导航甲\n"
                + "    path: navA\n"
                + "    children:\n"
                + "      - {title: 同名页, path: p1, component: /views/a/index.vue}\n"
                + "      - {title: 同名页, path: p2, component: /views/b/index.vue}\n");
        assertThrowsContains(() -> validate(roots), "同父 title 唯一冲突");
    }

    @Test
    public void multiSegmentPathMustThrow() {
        List<Node> roots = one(""
                + "menus:\n"
                + "  - title: 导航甲\n"
                + "    path: navA\n"
                + "    children:\n"
                + "      - {title: 坏页, path: a/b, component: /views/c/index.vue}\n");
        assertThrowsContains(() -> validate(roots), "path 非单段英文标识");
    }

    @Test
    public void danglingNodeWithoutComponentAndChildrenMustThrow() {
        List<Node> roots = one(""
                + "menus:\n"
                + "  - title: 导航甲\n"
                + "    path: navA\n"
                + "    children:\n"
                + "      - {title: 空挂, path: nothing}\n");
        assertThrowsContains(() -> validate(roots), "空挂节点");
    }

    // ==================== 校验：正对照（针自证——同骨架只差键值必须放行） ====================

    @Test
    public void distinctComponentsPassValidation() {
        List<Node> roots = one(""
                + "menus:\n"
                + "  - title: 导航甲\n"
                + "    path: navA\n"
                + "    children:\n"
                + "      - {title: 页一, path: p1, component: /views/one/index.vue}\n"
                + "      - {title: 页二, path: p2, component: /views/two/index.vue}\n");
        validate(roots);
    }

    @Test
    public void sameTitleUnderDifferentParentsPassesValidation() {
        List<Node> roots = one(""
                + "menus:\n"
                + "  - title: 导航甲\n"
                + "    path: navA\n"
                + "    children:\n"
                + "      - title: 目录一\n"
                + "        path: d1\n"
                + "        children:\n"
                + "          - {title: 日志, path: l1, component: /views/l1/index.vue}\n"
                + "      - title: 目录二\n"
                + "        path: d2\n"
                + "        children:\n"
                + "          - {title: 日志, path: l2, component: /views/l2/index.vue}\n");
        validate(roots);
    }

    @Test
    public void topNavWithComponentAndChildrenPassesValidation() {
        // 现库基线形态：id=1「系统管理」导航行 component=SystemManage 且带 children，必须放行
        List<Node> roots = one(""
                + "menus:\n"
                + "  - {title: 系统管理, path: SystemManage, component: SystemManage, children: []}\n");
        validate(roots);
    }

    // ==================== 归并：同 topnav 合并、子树并集、冲突 fail-fast ====================

    @Test
    public void sameTopNavByTitleMergesChildrenAsUnion() {
        List<Node> a = one(""
                + "menus:\n"
                + "  - title: 共享导航\n"
                + "    path: shared\n"
                + "    children:\n"
                + "      - {title: 页一, path: p1, component: /views/one/index.vue}\n");
        List<Node> b = one(""
                + "menus:\n"
                + "  - title: 共享导航\n"
                + "    path: shared\n"
                + "    children:\n"
                + "      - {title: 页二, path: p2, component: /views/two/index.vue}\n");
        List<Node> merged = MenuDeclaration.mergeAll(wrap(a, b));
        Assert.assertEquals("同 topnav 必须并成一棵", 1, merged.size());
        Assert.assertEquals("子树并集", 2, merged.get(0).children.size());
        validate(merged);
    }

    @Test
    public void sameTopNavByPathMatchedWhenTitleDiffers() {
        List<Node> a = one(""
                + "menus:\n"
                + "  - {title: 甲名, path: shared, children: []}\n");
        List<Node> b = one(""
                + "menus:\n"
                + "  - {title: 乙名, path: shared, children: []}\n");
        // title 不同但 path 相同 ⇒ 仍按同一 topnav 处理；title 冲突交给归并 fail-fast
        assertThrowsContains(() -> MenuDeclaration.mergeAll(wrap(a, b)), "字段冲突");
    }

    @Test
    public void conflictingComponentOnSameNodeMustFailFast() {
        List<Node> a = one(""
                + "menus:\n"
                + "  - title: 共享导航\n"
                + "    path: shared\n"
                + "    children:\n"
                + "      - {title: 页一, path: p1, component: /views/one/index.vue}\n");
        List<Node> b = one(""
                + "menus:\n"
                + "  - title: 共享导航\n"
                + "    path: shared\n"
                + "    children:\n"
                + "      - {title: 页一, path: p1, component: /views/other/index.vue}\n");
        assertThrowsContains(() -> MenuDeclaration.mergeAll(wrap(a, b)), "字段冲突");
    }

    @Test
    public void nonEmptyDefaultsMergeWithoutConflict() {
        // 一方缺省 icon、另一方给出 ⇒ 并集语义放行，不算冲突
        List<Node> a = one(""
                + "menus:\n"
                + "  - title: 共享导航\n"
                + "    path: shared\n"
                + "    children:\n"
                + "      - {title: 页一, path: p1, component: /views/one/index.vue}\n");
        List<Node> b = one(""
                + "menus:\n"
                + "  - title: 共享导航\n"
                + "    path: shared\n"
                + "    children:\n"
                + "      - {title: 页一, path: p1, icon: SettingOutlined}\n");
        List<Node> merged = MenuDeclaration.mergeAll(wrap(a, b));
        Assert.assertEquals(1, merged.size());
        validate(merged);
    }

    // ==================== 真实声明文件：解析＋校验正对照 ====================

    @Test
    public void classpathDeclarationParsesAndPassesValidation() throws IOException {
        List<Node> roots = MenuDeclaration.parseAll(MenuDeclarationTest.class.getClassLoader());
        Assert.assertFalse("authorization classpath 上必须能扫到 menu-seed.yml（打包资源断言）", roots.isEmpty());
        validate(roots);
        Node sysNav = findRootByPath(roots, "SystemManage");
        Assert.assertEquals("顶级导航", "系统管理", sysNav.title);
        Assert.assertTrue("系统管理导航行保留库基线形态 component=SystemManage",
                sysNav.hasComponent());
        // 补缺挂三块必须在声明里
        Assert.assertNotNull("租户管理页（棒4 交付）", findNode(sysNav, "TenantManage"));
        Assert.assertNotNull("partner 页一：渠道管理", findNode(sysNav, "Channel"));
        Assert.assertNotNull("partner 页二：渠道历史", findNode(sysNav, "channelHistory"));
        Node agent = findNode(sysNav, "agent");
        Assert.assertNotNull("Agent 目录", agent);
        Assert.assertNotNull("agent 第 4 页：智能体管理", findNode(agent, "AgentAdmin"));
        Assert.assertEquals("Agent 目录四页", 4, agent.children.size());
        // 现库系统管理子树 26 行全部在册：展平后节点数＝26 基线＋4 补缺挂＝30
        Assert.assertEquals(30, MenuDeclaration.flatten(Collections.singletonList(sysNav)).size());
    }

    @Test
    public void businessDeclarationMergesWithFrameworkWithoutConflict() throws IOException {
        File commonPom = new File("../../../inkhub-common/pom.xml");
        Assume.assumeTrue("inkhub-common 模块不在场（core 独立构建形态）——该腿跳过，报告已注明",
                commonPom.isFile());
        File yml = new File("../../../inkhub-common/src/main/resources/menu-seed.yml");
        Assert.assertTrue("模块在场则业务声明文件必须存在：" + yml.getAbsolutePath(), yml.isFile());
        List<Node> biz = MenuDeclaration.parseTree(readAll(yml), yml.getPath());
        validate(biz);
        List<Node> framework = MenuDeclaration.parseAll(MenuDeclarationTest.class.getClassLoader());
        List<Node> merged = MenuDeclaration.mergeAll(wrap(framework, biz));
        validate(merged);
        Assert.assertEquals("框架 topnav＋墨枢 topnav", 2, merged.size());
        Node moShu = findRootByPath(merged, "inkhub");
        Assert.assertEquals("墨枢", moShu.title);
        Assert.assertNotNull("EngineParams 页", findNode(moShu, "EngineParams"));
        Assert.assertNotNull("SystemHealth 页", findNode(moShu, "SystemHealth"));
    }

    // ==================== helpers ====================

    private static String readAll(File f) throws IOException {
        try (InputStreamReader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        }
    }

    private static Node findRootByPath(List<Node> roots, String path) {
        for (Node r : roots) {
            if (path.equals(r.path)) {
                return r;
            }
        }
        throw new AssertionError("缺 topnav：" + path);
    }

    /** 深度优先找 path 命中节点（限定在 subtree 内） */
    private static Node findNode(Node subtree, String path) {
        for (Node child : subtree.children) {
            if (path.equals(child.path)) {
                return child;
            }
            Node deeper = findNode(child, path);
            if (deeper != null) {
                return deeper;
            }
        }
        return null;
    }

    private static void assertThrowsContains(Runnable action, String fragment) {
        try {
            action.run();
        } catch (IllegalStateException e) {
            Assert.assertTrue("报错应含「" + fragment + "」，实际：" + e.getMessage(),
                    e.getMessage().contains(fragment));
            return;
        }
        Assert.fail("预期抛 IllegalStateException（含 " + fragment + "），实际未抛");
    }
}
