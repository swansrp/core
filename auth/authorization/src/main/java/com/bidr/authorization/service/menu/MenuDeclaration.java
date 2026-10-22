package com.bidr.authorization.service.menu;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Title: MenuDeclaration
 * Description: 菜单声明文件（menu-seed.yml）的解析/归并/校验纯函数——不依赖 Spring、不查库，可离线单测。
 * 声明即嵌套树：根节点＝顶级导航（topnav，menu_type=0），children 递归；父子关系来自 yml 结构，
 * 不做库反查（定案见 dev-logs M-20/M-21/M-22）。
 * 多模块同名 topnav 按结构并集合并，同节点字段不一致 fail-fast；跨模块锚点骨架
 * （无 component 无 children 的同名占位节点）由宿主模块声明补全，宿主缺席时
 * pruneAnchorSkeletons 整支剪除（不因他人模块未打包而炸）；
 * 校验＝component 全局唯一（非空者）＋同父 title 唯一＋path 单段合法——冲突即抛，配置错误开发期炸。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-17
 */
public final class MenuDeclaration {

    /** 声明文件名：固定 menu-seed.yml，不带业务名；classpath 上有多少份就解析多少份 */
    public static final String FILE_NAME = "menu-seed.yml";
    /** 顶层键 */
    public static final String ROOT_KEY = "menus";
    /** 路由段形态：单段英文标识（字母打头，限 ac_menu.path 列宽内） */
    static final Pattern PATH_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,49}$");
    /** 列宽（与 ac_menu DDL 对齐，声明超宽在解析期即拦） */
    static final int TITLE_MAX = 50;
    static final int PATH_MAX = 200;
    static final int COMPONENT_MAX = 255;
    static final int ICON_MAX = 100;

    private MenuDeclaration() {
    }

    /** 声明树节点（可变结构，仅在本类归并/校验中写字段）。 */
    public static class Node {
        /** 顶级导航标志（深度 0）＝menu_type 0；其余一律 1 */
        public final boolean nav;
        /** 来源标识（classpath 资源 URL 或逻辑名），报错与日志用 */
        public final String source;
        public String title;
        public String path;
        public String component;
        public String icon;
        public final List<Node> children = new ArrayList<>();

        Node(boolean nav, String source) {
            this.nav = nav;
            this.source = source;
        }

        public boolean hasComponent() {
            return component != null && !component.isEmpty();
        }

        /** 目录＝无 component 且有 children（ac_menu 里 component 空、按结构判重的行） */
        public boolean isDirectory() {
            return !hasComponent() && !children.isEmpty();
        }

        @Override
        public String toString() {
            return (nav ? "nav[" : "node[") + title + "/" + path + "]@" + source;
        }
    }

    /**
     * 扫 classpath 全部 menu-seed.yml，逐份解析后归并为单一声明集。
     * classpath 没有该模块 ⇒ 没有声明 ⇒ 不种（动态路由不生成）。
     */
    public static List<Node> parseAll(ClassLoader classLoader) throws IOException {
        ClassLoader cl = classLoader == null ? MenuDeclaration.class.getClassLoader() : classLoader;
        List<List<Node>> perSource = new ArrayList<>();
        Enumeration<URL> urls = cl.getResources(FILE_NAME);
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            try (InputStream in = url.openStream()) {
                String text = readFully(in);
                perSource.add(parseTree(text, url.toString()));
            }
        }
        return mergeAll(perSource);
    }

    /** 从 yml 文本解析出声明树（每份文件一棵棵 topnav 的森林）。SafeConstructor＝只产出纯数据，防任意对象构造。 */
    public static List<Node> parseTree(String yamlText, String source) {
        Object doc = new Yaml(new SafeConstructor()).load(yamlText);
        if (!(doc instanceof Map)) {
            throw declError(source, "顶层必须是 map（含 " + ROOT_KEY + ": 键），实际为 " + typeName(doc));
        }
        Object menus = ((Map<?, ?>) doc).get(ROOT_KEY);
        if (!(menus instanceof List)) {
            throw declError(source, "缺 " + ROOT_KEY + " 列表或其为空");
        }
        List<Node> roots = new ArrayList<>();
        for (Object item : (List<?>) menus) {
            roots.add(parseNode(item, source, true));
        }
        if (roots.isEmpty()) {
            throw declError(source, ROOT_KEY + " 列表为空");
        }
        return roots;
    }

    @SuppressWarnings("unchecked")
    private static Node parseNode(Object obj, String source, boolean nav) {
        if (!(obj instanceof Map)) {
            throw declError(source, "菜单节点必须是 map，实际为 " + typeName(obj));
        }
        Map<String, Object> map = (Map<String, Object>) obj;
        Node node = new Node(nav, source);
        node.title = trimToNull(map.get("title"));
        node.path = trimToNull(map.get("path"));
        node.component = trimToNull(map.get("component"));
        node.icon = trimToNull(map.get("icon"));
        if (node.title == null) {
            throw declError(source, "菜单节点缺 title");
        }
        if (node.path == null) {
            throw declError(source, "菜单节点缺 path：" + node.title);
        }
        Object children = map.get("children");
        if (children instanceof List) {
            for (Object child : (List<?>) children) {
                node.children.add(parseNode(child, source, false));
            }
        } else if (children != null && !(children instanceof List)) {
            throw declError(source, "children 必须是列表：" + node);
        }
        return node;
    }

    /**
     * 多份声明归并：平铺全部顶级节点后按「title∨path 命中同一 topnav」并集子树；
     * 同节点（同 path）字段不一致 ⇒ fail-fast。
     */
    public static List<Node> mergeAll(List<List<Node>> perSourceTrees) {
        List<Node> mergedRoots = new ArrayList<>();
        for (List<Node> roots : perSourceTrees) {
            for (Node root : roots) {
                mergeRootInto(mergedRoots, root);
            }
        }
        return mergedRoots;
    }

    private static void mergeRootInto(List<Node> mergedRoots, Node incoming) {
        for (Node existing : mergedRoots) {
            if (sameTopNav(existing, incoming)) {
                mergeNodeInto(existing, incoming);
                return;
            }
        }
        mergedRoots.add(cloneTree(incoming));
    }

    /** 顶级 topnav 同一性（声明集内）：title 或 path 命中即同一棵 */
    static boolean sameTopNav(Node a, Node b) {
        return eq(a.title, b.title) || eq(a.path, b.path);
    }

    /** 子节点同一性：同父下 path 命中即同一节点（下钻判重的声明侧镜像） */
    private static boolean sameChild(Node a, Node b) {
        return eq(a.path, b.path);
    }

    /** 把 incoming 并入 existing（就地）：标量字段一致性校验，children 按 path 并集。 */
    private static void mergeNodeInto(Node existing, Node incoming) {
        if (existing.nav != incoming.nav) {
            throw declError(incoming.source, "topnav/子节点身份冲突：" + existing + " vs " + incoming);
        }
        checkFieldConsistent(existing, "title", existing.title, incoming.title);
        checkFieldConsistent(existing, "path", existing.path, incoming.path);
        checkFieldConsistent(existing, "component", existing.component, incoming.component);
        checkFieldConsistent(existing, "icon", existing.icon, incoming.icon);
        for (Node child : incoming.children) {
            boolean hit = false;
            for (Node existChild : existing.children) {
                if (sameChild(existChild, child)) {
                    mergeNodeInto(existChild, child);
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                existing.children.add(cloneTree(child));
            }
        }
    }

    private static void checkFieldConsistent(Node node, String field, String held, String incoming) {
        if (incoming == null || held == null) {
            return; // 一方缺省 ⇒ 以另一方为准（并集语义，不算冲突）
        }
        if (!held.equals(incoming)) {
            throw declError(node.source, "字段冲突（fail-fast）：" + node + " 的 " + field
                    + " 两处声明不一致：" + held + " vs " + incoming);
        }
    }

    /**
     * 校验（纯函数）：component 全局唯一（非空者）＋同父 title 唯一＋path 形态/列宽；
     * 目录行（无 component 且有 children）合法，叶子行必须有 component，二者皆空＝空挂节点报错。
     */
    public static void validate(List<Node> roots) {
        Set<String> components = new HashSet<>();
        for (Node root : roots) {
            validateRoot(root);
            validateTree(root, components);
        }
    }

    private static void validateRoot(Node root) {
        if (!root.nav) {
            throw declError(root.source, "顶级必须是 topnav（nav）：" + root);
        }
    }

    private static void validateTree(Node node, Set<String> components) {
        if (node.title == null || node.title.length() > TITLE_MAX) {
            throw declError(node.source, "title 缺省或超宽 " + TITLE_MAX + "：" + node);
        }
        if (!PATH_PATTERN.matcher(node.path).matches() || node.path.length() > PATH_MAX) {
            throw declError(node.source, "path 非单段英文标识或超宽 " + PATH_MAX + "：" + node
                    + "（path=" + node.path + "）");
        }
        if (node.icon != null && node.icon.length() > ICON_MAX) {
            throw declError(node.source, "icon 超宽 " + ICON_MAX + "：" + node);
        }
        if (node.hasComponent()) {
            if (node.component.length() > COMPONENT_MAX) {
                throw declError(node.source, "component 超宽 " + COMPONENT_MAX + "：" + node);
            }
            if (!node.children.isEmpty() && !node.nav) {
                // topnav 例外：现库基线 id=1「系统管理」导航行 component=SystemManage 且带 children
                throw declError(node.source, "有 component 的叶子不得再挂 children：" + node);
            }
            if (!components.add(node.component)) {
                throw declError(node.source, "component 全局唯一冲突：" + node.component + "（" + node + "）");
            }
        } else if (node.children.isEmpty()) {
            throw declError(node.source, "空挂节点（既无 component 又无 children）：" + node);
        }
        Set<String> titlesUnderParent = new HashSet<>();
        for (Node child : node.children) {
            if (!titlesUnderParent.add(child.title)) {
                throw declError(child.source, "同父 title 唯一冲突：父=" + node + "，title=" + child.title);
            }
            validateTree(child, components);
        }
    }

    /** 深拷贝一棵子树（合入未命中的节点用，隔离跨份声明的对象引用） */
    private static Node cloneTree(Node src) {
        Node copy = new Node(src.nav, src.source);
        copy.title = src.title;
        copy.path = src.path;
        copy.component = src.component;
        copy.icon = src.icon;
        for (Node child : src.children) {
            copy.children.add(cloneTree(child));
        }
        return copy;
    }

    /** 深度优先展平（menu-audit 与日志计数共用口径） */
    public static List<Node> flatten(List<Node> roots) {
        List<Node> out = new ArrayList<>();
        for (Node root : roots) {
            dfs(root, out);
        }
        return out;
    }

    /**
     * 锚点骨架的在场剪枝：跨模块分布后，一份 yml 里可能只写"他人子树的骨架"
     * （既无 component 又无 children 的节点，靠宿主模块同名声明合并补全）。
     * 宿主模块 jar 缺席（该模块不在场的部署形态）时骨架不会被补全——整支移除，
     * 返回被移除节点的深度优先序（调用方逐条告警）。绝不因别人的模块没打进来而炸启动。
     */
    public static List<Node> pruneAnchorSkeletons(List<Node> roots) {
        List<Node> pruned = new ArrayList<>();
        for (Node root : roots) {
            pruneChildren(root, pruned);
        }
        return pruned;
    }

    private static void pruneChildren(Node parent, List<Node> pruned) {
        java.util.Iterator<Node> it = parent.children.iterator();
        while (it.hasNext()) {
            Node child = it.next();
            pruneChildren(child, pruned);
            if (!child.hasComponent() && child.children.isEmpty()) {
                pruned.add(child);
                it.remove();
            }
        }
    }

    private static void dfs(Node node, List<Node> out) {
        out.add(node);
        for (Node child : node.children) {
            dfs(child, out);
        }
    }

    static IllegalStateException declError(String source, String msg) {
        return new IllegalStateException("[menu-seed] 声明错误（" + source + "）：" + msg);
    }

    private static String readFully(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) > 0) {
            buf.write(chunk, 0, n);
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String trimToNull(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean eq(String a, String b) {
        return a != null && a.equals(b);
    }

    private static String typeName(Object o) {
        return o == null ? "null" : o.getClass().getSimpleName();
    }
}
