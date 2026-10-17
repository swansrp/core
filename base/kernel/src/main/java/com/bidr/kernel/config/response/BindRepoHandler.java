package com.bidr.kernel.config.response;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link BindRepo} 注解的通用处理器。
 * <p>
 * 启动时扫描所有 {@link BaseSqlRepo} Bean，按 entityClass 建立索引。
 * 运行时根据注解上的 entity() 自动定位 Repo，执行批量查询 + 字段提取。
 * <p>
 * 一个 Handler 处理所有 @BindRepo 注解（包括 @BindUser、@BindDept 等通过元注解桥接的快捷注解），
 * 无需为每种实体编写子类。
 *
 * @author Sharp
 * @since 2026/07/09
 */
@Slf4j
@Component
public class BindRepoHandler {

    private static final String VALID_FIELD = "valid";

    /**
     * condition 中代表源 VO 的节点名
     */
    private static final String THIS_NODE = "this";

    /**
     * condition 中代表目标 entity 的内部节点名（sentinel，避免与中间实体简单类名冲突）
     */
    private static final String TARGET_NODE = "@target";

    /**
     * entityClass → BaseSqlRepo 的索引，延迟初始化
     */
    private volatile Map<Class<?>, BaseSqlRepo<?, ?>> repoIndex;

    /**
     * 实体简单类名 → entityClass 的索引，与 {@link #repoIndex} 同时初始化，用于解析 condition 中间实体
     */
    private volatile Map<String, Class<?>> nameIndex;

    /**
     * condition 字符串 → 解析结果的缓存，避免每次转换重复解析
     */
    private final Map<String, ConditionChain> conditionCache = new ConcurrentHashMap<>();


    /**
     * 批量转换：根据 @BindRepo 注解配置，查询实体并提取目标字段值。
     *
     * @param annotation   字段上的 @BindRepo 注解（可能是 @BindUser/@BindDept 通过 findMergedAnnotation 合成的）
     * @param sourceValues 去重后的源字段值集合
     * @return 源值→目标值 的映射
     */
    public Map<Object, Object> batchConvert(BindRepo annotation, Set<Object> sourceValues) {
        Map<Object, Object> result = new HashMap<>();
        if (FuncUtil.isEmpty(sourceValues)) {
            return result;
        }

        Class<?> entityClass = annotation.entity();
        BaseSqlRepo<?, ?> repo = getRepo(entityClass);
        if (repo == null) {
            log.warn("@BindRepo: 未找到 {} 对应的 BaseSqlRepo Bean，跳过绑定", entityClass.getName());
            return result;
        }

        String matchField = annotation.matchField();
        String matchField2 = annotation.matchField2();
        boolean dual = FuncUtil.isNotEmpty(matchField2);
        String extractField = annotation.extractField();
        String matchColumn = resolveColumnName(entityClass, matchField);

        // 构建 WHERE matchColumn IN (sourceValues) [AND matchColumn2 IN (...)] [AND valid = '1']
        QueryWrapper wrapper = new QueryWrapper();
        if (dual) {
            // 复合匹配：sourceValues 为 "v1||v2" 组合键，拆分为两列的值集合
            Set<Object> values1 = new HashSet<>();
            Set<Object> values2 = new HashSet<>();
            for (Object combined : sourceValues) {
                String[] parts = String.valueOf(combined).split("\\|\\|", -1);
                values1.add(parts[0]);
                if (parts.length > 1) {
                    values2.add(parts[1]);
                }
            }
            wrapper.in(matchColumn, values1);
            wrapper.in(resolveColumnName(entityClass, matchField2), values2);
        } else {
            wrapper.in(matchColumn, sourceValues);
        }
        if (ReflectionUtil.existedField(entityClass, VALID_FIELD)) {
            String validColumn = resolveColumnName(entityClass, VALID_FIELD);
            wrapper.eq(validColumn, CommonConst.YES);
        }

        // 批量查询（通过反射调用 select(Wrapper) 绕过泛型约束）
        // 注意：必须用 Wrapper.class 查找方法，不能用 param.getClass()（QueryWrapper.class），
        //       因为 ReflectionUtils.findMethod 做精确类型匹配，select 的参数类型是 Wrapper 而非 QueryWrapper
        Method selectMethod = ReflectionUtil.getMethod(repo.getClass(), "select", Wrapper.class);
        if (selectMethod == null) {
            log.warn("@BindRepo: 未找到 {} 的 select(Wrapper) 方法，跳过绑定", entityClass.getName());
            return result;
        }
        List<?> entities = (List<?>) ReflectionUtil.invoke(repo, selectMethod, wrapper);
        if (FuncUtil.isNotEmpty(entities)) {
            for (Object entity : entities) {
                Object matchValue = ReflectionUtil.getValue(entity, matchField, Object.class);
                Object key = matchValue;
                if (dual) {
                    Object matchValue2 = ReflectionUtil.getValue(entity, matchField2, Object.class);
                    key = combinedKey(matchValue, matchValue2);
                }
                Object extractValue = ReflectionUtil.getValue(entity, extractField, Object.class);
                result.put(key, extractValue);
            }
        }
        return result;
    }

    /**
     * 构造复合匹配键：v1 + "||" + v2（null 归一为空串）
     *
     * @param value1 第一匹配值
     * @param value2 第二匹配值
     * @return 组合键
     */
    private static String combinedKey(Object value1, Object value2) {
        return (value1 == null ? "" : String.valueOf(value1)) + "||" + (value2 == null ? "" : String.valueOf(value2));
    }

    /**
     * 获取实体类对应的 BaseSqlRepo，延迟初始化索引
     */
    private BaseSqlRepo<?, ?> getRepo(Class<?> entityClass) {
        if (repoIndex == null) {
            synchronized (this) {
                if (repoIndex == null) {
                    Map<Class<?>, BaseSqlRepo<?, ?>> index = new ConcurrentHashMap<>();
                    Map<String, Class<?>> names = new ConcurrentHashMap<>();
                    String[] beanNames = BeanUtil.getBeanNamesForType(BaseSqlRepo.class);
                    if (FuncUtil.isNotEmpty(beanNames)) {
                        for (String beanName : beanNames) {
                            Object bean = BeanUtil.getBean(beanName);
                            if (bean instanceof BaseSqlRepo) {
                                BaseSqlRepo<?, ?> repo = (BaseSqlRepo<?, ?>) bean;
                                Class<?> ec = repo.getEntityClass();
                                if (ec != null) {
                                    index.put(ec, repo);
                                    String simpleName = ec.getSimpleName();
                                    Class<?> exists = names.get(simpleName);
                                    if (exists != null && exists != ec) {
                                        log.warn("@BindRepo: 实体简单类名冲突 {} ({} vs {})，condition 取首个",
                                                simpleName, exists.getName(), ec.getName());
                                    } else {
                                        names.put(simpleName, ec);
                                    }
                                }
                            }
                        }
                    }
                    nameIndex = names;
                    repoIndex = index;
                    log.debug("@BindRepo 索引初始化完成，共 {} 个 BaseSqlRepo", index.size());
                }
            }
        }
        return repoIndex.get(entityClass);
    }

    /**
     * Java 属性名 → 数据库列名
     */
    private String resolveColumnName(Class<?> entityClass, String fieldName) {
        try {
            Field field = ReflectionUtil.getField(entityClass, fieldName);
            TableField tableField = field.getAnnotation(TableField.class);
            if (tableField != null) {
                return tableField.value();
            }
            TableId tableId = field.getAnnotation(TableId.class);
            if (tableId != null) {
                return tableId.value();
            }
        } catch (Exception e) {
            // 字段不存在，走兜底
        }
        return StringUtil.camelToUnderline(fieldName);
    }

    // ======================== 一对多 List 绑定 ========================

    /**
     * 批量一对多转换：根据 @BindRepo（字段类型为 List）配置，查询目标实体列表并按源键分组。
     * <p>
     * 直接一对多（无中间表）：目标 matchField IN (源键)，按 matchField 分组。
     * 涉及中间表或多级关联时改用 {@code condition}，见 {@link #batchConvertByCondition}。
     *
     * @param annotation   字段上的 @BindRepo 注解
     * @param sourceValues 去重后的源键集合（复合时为 "v1||v2" 组合键）
     * @return 源键 → 目标实体列表 的映射
     */
    public Map<Object, List<Object>> batchConvertList(BindRepo annotation, Set<Object> sourceValues) {
        Map<Object, List<Object>> result = new HashMap<>();
        if (FuncUtil.isEmpty(sourceValues)) {
            return result;
        }
        Class<?> entityClass = annotation.entity();
        String matchField = annotation.matchField();
        boolean dual = FuncUtil.isNotEmpty(annotation.sourceField2());

        // 直接一对多：目标 matchField IN (源键)
        List<?> targets = selectIn(entityClass, matchField, annotation.matchField2(), dual, sourceValues);
        for (Object target : targets) {
            Object matchValue = ReflectionUtil.getValue(target, matchField, Object.class);
            Object key = dual
                    ? combinedKey(matchValue, ReflectionUtil.getValue(target, annotation.matchField2(), Object.class))
                    : matchValue;
            result.computeIfAbsent(key, k -> new ArrayList<>()).add(target);
        }
        return result;
    }

    /**
     * 通用 IN 查询：WHERE matchColumn IN (values) [AND matchColumn2 IN (values2)] [AND valid = '1']。
     * <p>
     * dual 为 true 时，values 为 "v1||v2" 组合键，按 {@code \\|\\|} 拆分为两列的值集合。
     *
     * @param entityClass 实体类
     * @param matchField  匹配字段（Java 属性名）
     * @param matchField2 第二匹配字段（Java 属性名，dual 时使用）
     * @param dual        是否复合匹配
     * @param values      值集合（dual 时为组合键集合）
     * @return 查询结果列表，永不为 null
     */
    private List<?> selectIn(Class<?> entityClass, String matchField, String matchField2, boolean dual, Set<Object> values) {
        BaseSqlRepo<?, ?> repo = getRepo(entityClass);
        if (repo == null) {
            log.warn("@BindRepo(List): 未找到 {} 对应的 BaseSqlRepo Bean，跳过绑定", entityClass.getName());
            return Collections.emptyList();
        }
        QueryWrapper wrapper = new QueryWrapper();
        if (dual) {
            Set<Object> values1 = new HashSet<>();
            Set<Object> values2 = new HashSet<>();
            for (Object combined : values) {
                String[] parts = String.valueOf(combined).split("\\|\\|", -1);
                values1.add(parts[0]);
                if (parts.length > 1) {
                    values2.add(parts[1]);
                }
            }
            wrapper.in(resolveColumnName(entityClass, matchField), values1);
            wrapper.in(resolveColumnName(entityClass, matchField2), values2);
        } else {
            wrapper.in(resolveColumnName(entityClass, matchField), values);
        }
        if (ReflectionUtil.existedField(entityClass, VALID_FIELD)) {
            wrapper.eq(resolveColumnName(entityClass, VALID_FIELD), CommonConst.YES);
        }
        Method selectMethod = ReflectionUtil.getMethod(repo.getClass(), "select", Wrapper.class);
        if (selectMethod == null) {
            log.warn("@BindRepo(List): 未找到 {} 的 select(Wrapper) 方法，跳过绑定", entityClass.getName());
            return Collections.emptyList();
        }
        List<?> list = (List<?>) ReflectionUtil.invoke(repo, selectMethod, wrapper);
        return list != null ? list : Collections.emptyList();
    }

    // ======================== condition 关联表达式绑定 ========================

    /**
     * 解析失败链的哨兵，用于缓存非法 condition 避免重复告警
     */
    private static final ConditionChain INVALID_CHAIN = new ConditionChain();

    /**
     * 批量 condition 关联绑定：解析 condition 表达式构建「this → 中间实体… → 目标 entity」join 链，
     * 逐级批量 IN 查询（一对多扇出保留全部路径），返回 源 VO → 目标实体列表 的映射。
     * <p>
     * 返回值以源 VO 实例本身为键（{@link IdentityHashMap}），上层按 VO 引用直接取回绑定结果，
     * 无需重复计算源键。解析非法或无匹配时返回空 Map（不抛异常中断整体转换）。
     *
     * @param annotation 字段上的 @BindRepo 注解（condition 非空）
     * @param entityList 源 VO 实体列表
     * @return 源 VO 实例 → 目标实体列表 的映射
     */
    public Map<Object, List<Object>> batchConvertByCondition(BindRepo annotation, List<?> entityList) {
        Map<Object, List<Object>> result = new IdentityHashMap<>();
        if (FuncUtil.isEmpty(entityList)) {
            return result;
        }
        ConditionChain chain = parseCondition(annotation.condition());
        if (chain == null) {
            return result;
        }
        Class<?> targetClass = annotation.entity();
        // 触发索引初始化（repoIndex + nameIndex）
        getRepo(targetClass);
        if (nameIndex == null) {
            return result;
        }
        for (String simpleName : chain.entityOrder) {
            if (nameIndex.get(simpleName) == null) {
                log.warn("@BindRepo(condition): 未解析到中间实体 {} 对应的 BaseSqlRepo，跳过绑定：{}",
                        simpleName, annotation.condition());
                return result;
            }
        }

        // 初始化路径：每个源 VO 一条
        List<Path> rows = new ArrayList<>();
        for (Object vo : entityList) {
            if (FuncUtil.isEmpty(vo)) {
                continue;
            }
            Path p = new Path();
            p.sourceVo = vo;
            rows.add(p);
        }

        Set<String> processed = new HashSet<>();
        processed.add(THIS_NODE);
        // 逐级处理中间实体
        for (String simpleName : chain.entityOrder) {
            Class<?> nodeClass = nameIndex.get(simpleName);
            List<Edge> incoming = chain.incomingEdges(simpleName, processed);
            rows = advance(rows, incoming, nodeClass, simpleName);
            if (rows.isEmpty()) {
                return result;
            }
            processed.add(simpleName);
        }

        // 处理目标 entity
        List<Edge> incoming = chain.incomingEdges(TARGET_NODE, processed);
        if (FuncUtil.isEmpty(incoming)) {
            log.warn("@BindRepo(condition): 目标实体无入边，跳过绑定：{}", annotation.condition());
            return result;
        }
        Map<String, List<Object>> byKey = queryAndGroup(rows, incoming, targetClass);
        if (byKey.isEmpty()) {
            return result;
        }
        for (Path row : rows) {
            String rk = rowKey(row, incoming);
            if (rk == null) {
                continue;
            }
            List<Object> matched = byKey.get(rk);
            if (FuncUtil.isNotEmpty(matched)) {
                result.computeIfAbsent(row.sourceVo, k -> new ArrayList<>()).addAll(matched);
            }
        }
        return result;
    }

    /**
     * 推进一层：按 incoming 边查询 nodeClass，将匹配到的实例挂载到克隆路径上（一对多扇出）。
     */
    private List<Path> advance(List<Path> rows, List<Edge> incoming, Class<?> nodeClass, String nodeName) {
        List<Path> next = new ArrayList<>();
        if (FuncUtil.isEmpty(incoming)) {
            log.warn("@BindRepo(condition): 中间实体 {} 无入边，跳过", nodeName);
            return next;
        }
        Map<String, List<Object>> byKey = queryAndGroup(rows, incoming, nodeClass);
        if (byKey.isEmpty()) {
            return next;
        }
        for (Path row : rows) {
            String rk = rowKey(row, incoming);
            if (rk == null) {
                continue;
            }
            List<Object> matched = byKey.get(rk);
            if (FuncUtil.isEmpty(matched)) {
                continue;
            }
            for (Object inst : matched) {
                Path np = new Path();
                np.sourceVo = row.sourceVo;
                np.nodes.putAll(row.nodes);
                np.nodes.put(nodeName, inst);
                next.add(np);
            }
        }
        return next;
    }

    /**
     * 按 incoming 边构建 IN 查询获取 nodeClass 实例，并按「实例在各 toProp 上的值组合键」分组。
     *
     * @return 实例值组合键 → 实例列表；无入值或查询为空时返回空 Map
     */
    private Map<String, List<Object>> queryAndGroup(List<Path> rows, List<Edge> incoming, Class<?> nodeClass) {
        BaseSqlRepo<?, ?> repo = getRepo(nodeClass);
        if (repo == null) {
            log.warn("@BindRepo(condition): 未找到 {} 对应的 BaseSqlRepo Bean，跳过绑定", nodeClass.getName());
            return Collections.emptyMap();
        }
        QueryWrapper wrapper = new QueryWrapper();
        for (Edge e : incoming) {
            Set<Object> vals = new HashSet<>();
            for (Path row : rows) {
                Object v = fromValue(row, e);
                if (v != null) {
                    vals.add(v);
                }
            }
            if (vals.isEmpty()) {
                return Collections.emptyMap();
            }
            wrapper.in(resolveColumnName(nodeClass, e.toProp), vals);
        }
        if (ReflectionUtil.existedField(nodeClass, VALID_FIELD)) {
            wrapper.eq(resolveColumnName(nodeClass, VALID_FIELD), CommonConst.YES);
        }
        Method selectMethod = ReflectionUtil.getMethod(repo.getClass(), "select", Wrapper.class);
        if (selectMethod == null) {
            log.warn("@BindRepo(condition): 未找到 {} 的 select(Wrapper) 方法，跳过绑定", nodeClass.getName());
            return Collections.emptyMap();
        }
        List<?> list = (List<?>) ReflectionUtil.invoke(repo, selectMethod, wrapper);
        Map<String, List<Object>> byKey = new HashMap<>();
        if (FuncUtil.isNotEmpty(list)) {
            for (Object inst : list) {
                String k = instKey(inst, incoming);
                if (k == null) {
                    continue;
                }
                byKey.computeIfAbsent(k, x -> new ArrayList<>()).add(inst);
            }
        }
        return byKey;
    }

    /**
     * 路径在 incoming 边上的组合键（各 fromProp 值以 "||" 连接）；任一值为 null 时返回 null（不参与匹配）。
     */
    private String rowKey(Path row, List<Edge> incoming) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < incoming.size(); i++) {
            Object v = fromValue(row, incoming.get(i));
            if (v == null) {
                return null;
            }
            if (i > 0) {
                sb.append("||");
            }
            sb.append(v);
        }
        return sb.toString();
    }

    /**
     * 实例在 incoming 边上的组合键（各 toProp 值以 "||" 连接）；任一值为 null 时返回 null。
     */
    private String instKey(Object inst, List<Edge> incoming) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < incoming.size(); i++) {
            Object v = ReflectionUtil.getValue(inst, incoming.get(i).toProp, Object.class);
            if (v == null) {
                return null;
            }
            if (i > 0) {
                sb.append("||");
            }
            sb.append(v);
        }
        return sb.toString();
    }

    /**
     * 取路径在边 fromNode 上的属性值：this → 源 VO；其余 → 已挂载的中间实体实例。
     */
    private Object fromValue(Path row, Edge e) {
        Object src = THIS_NODE.equals(e.fromNode) ? row.sourceVo : row.nodes.get(e.fromNode);
        if (src == null) {
            return null;
        }
        return ReflectionUtil.getValue(src, e.fromProp, Object.class);
    }

    /**
     * 解析并缓存 condition 表达式；非法时告警并返回 null。
     */
    private ConditionChain parseCondition(String condition) {
        if (FuncUtil.isEmpty(condition)) {
            return null;
        }
        ConditionChain cached = conditionCache.get(condition);
        if (cached != null) {
            return cached == INVALID_CHAIN ? null : cached;
        }
        ConditionChain chain = doParseCondition(condition);
        conditionCache.put(condition, chain == null ? INVALID_CHAIN : chain);
        return chain;
    }

    private ConditionChain doParseCondition(String condition) {
        String[] exprs = condition.split("\\s+(?i:AND)\\s+");
        ConditionChain chain = new ConditionChain();
        for (String expr : exprs) {
            String trimmed = expr.trim();
            int eq = trimmed.indexOf('=');
            if (eq <= 0 || eq >= trimmed.length() - 1) {
                log.warn("@BindRepo(condition): 非法 joinExpr（需形如 a.x = b.y）：{}", trimmed);
                return null;
            }
            Ref left = parseRef(trimmed.substring(0, eq).trim(), condition);
            Ref right = parseRef(trimmed.substring(eq + 1).trim(), condition);
            if (left == null || right == null) {
                return null;
            }
            chain.addEdge(left, right);
        }
        if (chain.edges.isEmpty() || !chain.buildOrder()) {
            log.warn("@BindRepo(condition): 无法构建从 this 到目标实体的关联链：{}", condition);
            return null;
        }
        return chain;
    }

    /**
     * 解析单个 ref：{@code this.prop} → THIS 节点；{@code SimpleName.prop} → 中间实体节点；裸 {@code prop} → 目标实体节点。
     */
    private Ref parseRef(String s, String condition) {
        if (FuncUtil.isEmpty(s)) {
            log.warn("@BindRepo(condition): 空 ref：{}", condition);
            return null;
        }
        if (s.startsWith(THIS_NODE + ".")) {
            String prop = s.substring(THIS_NODE.length() + 1);
            if (FuncUtil.isEmpty(prop)) {
                log.warn("@BindRepo(condition): 非法 this ref：{}", s);
                return null;
            }
            return new Ref(THIS_NODE, prop);
        }
        int dot = s.indexOf('.');
        if (dot > 0 && dot < s.length() - 1) {
            return new Ref(s.substring(0, dot), s.substring(dot + 1));
        }
        if (dot >= 0) {
            log.warn("@BindRepo(condition): 非法 ref：{}", s);
            return null;
        }
        return new Ref(TARGET_NODE, s);
    }

    // ======================== condition 内部数据结构 ========================

    private static class Ref {
        final String node;
        final String prop;

        Ref(String node, String prop) {
            this.node = node;
            this.prop = prop;
        }
    }

    /**
     * 无向原始边（解析期，方向未知）
     */
    private static class RawEdge {
        final String aNode;
        final String aProp;
        final String bNode;
        final String bProp;

        RawEdge(String aNode, String aProp, String bNode, String bProp) {
            this.aNode = aNode;
            this.aProp = aProp;
            this.bNode = bNode;
            this.bProp = bProp;
        }
    }

    /**
     * 有向入边（处理期）：fromNode.fromProp == 当前节点.toProp
     */
    private static class Edge {
        final String fromNode;
        final String fromProp;
        final String toProp;

        Edge(String fromNode, String fromProp, String toProp) {
            this.fromNode = fromNode;
            this.fromProp = fromProp;
            this.toProp = toProp;
        }
    }

    /**
     * 一条 join 路径：源 VO + 已挂载的各中间实体实例（简单类名 → 实例）
     */
    private static class Path {
        Object sourceVo;
        final Map<String, Object> nodes = new HashMap<>();
    }

    /**
     * condition 解析结果：无向边集合 + BFS 得到的中间实体处理顺序
     */
    private static class ConditionChain {
        final List<RawEdge> edges = new ArrayList<>();
        final List<String> entityOrder = new ArrayList<>();

        void addEdge(Ref l, Ref r) {
            edges.add(new RawEdge(l.node, l.prop, r.node, r.prop));
        }

        /**
         * 以 node 为终点、来源已处理（processed）的有向入边集合。
         */
        List<Edge> incomingEdges(String node, Set<String> processed) {
            List<Edge> list = new ArrayList<>();
            for (RawEdge e : edges) {
                if (e.aNode.equals(node) && processed.contains(e.bNode)) {
                    list.add(new Edge(e.bNode, e.bProp, e.aProp));
                } else if (e.bNode.equals(node) && processed.contains(e.aNode)) {
                    list.add(new Edge(e.aNode, e.aProp, e.bProp));
                }
            }
            return list;
        }

        /**
         * 从 this 出发 BFS，确定中间实体处理顺序；目标实体不可达时返回 false。
         */
        boolean buildOrder() {
            Set<String> visited = new HashSet<>();
            visited.add(THIS_NODE);
            Deque<String> queue = new ArrayDeque<>();
            queue.add(THIS_NODE);
            boolean targetReached = false;
            while (!queue.isEmpty()) {
                String cur = queue.poll();
                for (RawEdge e : edges) {
                    String other = null;
                    if (e.aNode.equals(cur)) {
                        other = e.bNode;
                    } else if (e.bNode.equals(cur)) {
                        other = e.aNode;
                    }
                    if (other == null || visited.contains(other)) {
                        continue;
                    }
                    visited.add(other);
                    if (TARGET_NODE.equals(other)) {
                        targetReached = true;
                    } else {
                        entityOrder.add(other);
                        queue.add(other);
                    }
                }
            }
            return targetReached;
        }
    }
}

