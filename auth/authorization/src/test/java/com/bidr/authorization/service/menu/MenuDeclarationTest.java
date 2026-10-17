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
 * 字段不一致 fail-fast）；跨模块锚点骨架的在场剪枝腿（宿主缺席整支移除、在场不误删）；
 * menu-seed.yml 分布正对照腿（按模块拆十份：authorization 份走 classpath 必读，
 * platform/redis/forge/admin/agent-console/insight/oss/mcp 与 inkhub-common 九份按磁盘路径在场判定——
 * 独立构建 core 仓时缺模块的腿 assume 跳过并在报告注明）。
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

    // ==================== 锚点骨架：在场剪枝（宿主缺席整支移除＝薄装机不炸） ====================

    @Test
    public void anchorSkeletonWithoutHostIsPruned() {
        List<Node> skeleton = one(""
                + "menus:\n"
                + "  - title: 系统管理\n"
                + "    path: SystemManage\n"
                + "    component: SystemManage\n"
                + "    children:\n"
                + "      - {title: Agent, path: agent, icon: SettingOutlined}\n");
        List<Node> pruned = MenuDeclaration.pruneAnchorSkeletons(skeleton);
        Assert.assertEquals("无宿主骨架应剪除 1 支", 1, pruned.size());
        Assert.assertEquals("Agent/agent", pruned.get(0).title + "/" + pruned.get(0).path);
        Assert.assertTrue("剪后导航 children 应空", skeleton.get(0).children.isEmpty());
        // 剪后必须能过 validate（不剪则"空挂节点"必抛——danglingNode 腿已证）
        validate(skeleton);
    }

    @Test
    public void mergedSkeletonWithHostIsNotPruned() {
        List<Node> skeleton = one(""
                + "menus:\n"
                + "  - title: 系统管理\n"
                + "    path: SystemManage\n"
                + "    component: SystemManage\n"
                + "    children:\n"
                + "      - {title: Agent, path: agent, icon: SettingOutlined}\n");
        List<Node> host = one(""
                + "menus:\n"
                + "  - title: 系统管理\n"
                + "    path: SystemManage\n"
                + "    component: SystemManage\n"
                + "    children:\n"
                + "      - title: Agent\n"
                + "        path: agent\n"
                + "        icon: SettingOutlined\n"
                + "        children:\n"
                + "          - {title: 技能装配, path: capability, component: /framework/views/a.vue}\n");
        List<Node> merged = MenuDeclaration.mergeAll(wrap(skeleton, host));
        Assert.assertEquals("宿主在场不应剪", 0, MenuDeclaration.pruneAnchorSkeletons(merged).size());
        Assert.assertEquals("合并后 Agent 一棵带一页", 1, merged.get(0).children.size());
        validate(merged);
    }

    // ==================== 真实声明文件：解析＋校验正对照 ====================

    @Test
    public void classpathDeclarationParsesAndPassesValidation() throws IOException {
        // authorization 单测 classpath＝auth 份＋其依赖模块（platform/redis）份；
        // 全八份分布的合并全集断言在 fullDistributionMergesToCompleteTree 腿（磁盘路径读，不吃 classpath 差异）。
        List<Node> roots = MenuDeclaration.parseAll(MenuDeclarationTest.class.getClassLoader());
        Assert.assertFalse("authorization classpath 上必须能扫到 menu-seed.yml（打包资源断言）", roots.isEmpty());
        MenuDeclaration.pruneAnchorSkeletons(roots);
        validate(roots);
        Node sysNav = findRootByPath(roots, "SystemManage");
        Assert.assertEquals("顶级导航", "系统管理", sysNav.title);
        Assert.assertTrue("系统管理导航行保留库基线形态 component=SystemManage",
                sysNav.hasComponent());
        // auth 自有页与棒4 补缺挂必须在册
        Assert.assertNotNull("租户管理页（棒4 交付）", findNode(sysNav, "TenantManage"));
        Assert.assertNotNull("权限管理七页之首：菜单管理", findNode(sysNav, "MenuMaintenance"));
        Assert.assertNotNull("账号管理", findNode(sysNav, "account"));
    }

    @Test
    public void fullDistributionMergesToCompleteTree() throws IOException {
        // 拆分回归针：框架九份按归属模块分布（2026-10-17 目录分组后 owners 带组前缀）后，磁盘全集合并
        // 必须仍是"一棵系统管理"（现库 26 行＋4 补缺挂＋2 点名入册（MCP配置、智能问数）＝32）；缺任一宿主模块即 assume 跳过（core 独立构建形态不判死）。
        String[] owners = {"base/authorization", "base/platform", "store/redis", "lowcode/forge", "auth/admin",
                "ai/agent-console", "lowcode/insight", "store/oss", "ai/mcp"};
        List<List<Node>> framework = new ArrayList<>();
        for (String module : owners) {
            File pom = new File("../" + module + "/pom.xml");
            Assume.assumeTrue("宿主模块不在场：" + module, pom.isFile());
            File yml = new File("../" + module + "/src/main/resources/menu-seed.yml");
            Assert.assertTrue("模块在场则声明文件必须存在：" + yml.getAbsolutePath(), yml.isFile());
            framework.add(MenuDeclaration.parseTree(readAll(yml), yml.getPath()));
        }
        List<Node> merged = MenuDeclaration.mergeAll(framework);
        Assert.assertEquals("全部骨架归并后仍是一棵顶级导航", 1, merged.size());
        Assert.assertEquals("prune 不应误删任何补全骨架", 0, MenuDeclaration.pruneAnchorSkeletons(merged).size());
        validate(merged);
        Node sysNav = merged.get(0);
        // 现库系统管理子树 26 行全部在册＋4 补缺挂＋2 点名入册（MCP配置、智能问数，库均无基线行）＝32
        Assert.assertEquals(32, MenuDeclaration.flatten(Collections.singletonList(sysNav)).size());
        // 跨模块归属逐一定位（每处都是"骨架在 auth、内容在宿主"的接缝）
        Node agent = findNode(sysNav, "agent");
        Assert.assertEquals("Agent 目录四页（agent-console 三页＋insight 一页）", 4, agent.children.size());
        Node portal = findNode(sysNav, "portal");
        Assert.assertEquals("表格配置四页（admin 表单配置＋forge 动态三页）", 4, portal.children.size());
        Assert.assertNotNull("platform：参数管理", findNode(sysNav, "ParamsManage"));
        Assert.assertNotNull("platform：字典维护", findNode(sysNav, "DictionaryMaintenance"));
        Assert.assertNotNull("platform：日志", findNode(sysNav, "log"));
        Assert.assertNotNull("redis：缓存管理", findNode(sysNav, "redis"));
        Assert.assertNotNull("admin：渠道管理", findNode(sysNav, "Channel"));
        Assert.assertNotNull("admin：渠道历史", findNode(sysNav, "channelHistory"));
        Assert.assertNotNull("oss：对象存储管理", findNode(sysNav, "SaObjectStorage"));
        Assert.assertNotNull("mcp：MCP配置（独立页、点名入册）", findNode(sysNav, "Mcp"));
        Assert.assertNotNull("insight：智能问数（独立全宽页、点名入册）", findNode(sysNav, "ChatBiAsk"));
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
        // 与 MenuSeeder 运行序一致：先剪无宿主骨架（core 独立构建＝forge/admin 等缺席的薄形态），再校验
        MenuDeclaration.pruneAnchorSkeletons(merged);
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
