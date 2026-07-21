package com.bidr.platform.service.cache.dict;

import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.bo.tree.TreeDict;
import com.bidr.platform.dao.entity.SysBizDict;
import com.bidr.platform.dao.repository.SysBizDictService;
import com.bidr.platform.vo.dict.DictRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 业务树形字典缓存服务
 * <p>
 * 识别 sys_biz_dict 中 parent_dict_code == dict_code 的自引用树形字典，
 * 按 parent_value 组装成树结构并缓存在内存中。
 *
 * @author Sharp
 * @since 2026-07-21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizDictTreeCacheService {

    private final SysBizDictService sysBizDictService;

    /**
     * 树形字典缓存：dictCode -> 树结构
     */
    private final Map<String, List<TreeDict>> treeCache = new ConcurrentHashMap<>();

    /**
     * 获取树形字典（带缓存）
     *
     * @param dictCode 字典编码
     * @return 树形结构列表，不存在则返回空列表
     */
    public List<TreeDict> getTreeDict(String dictCode) {
        List<TreeDict> cached = treeCache.get(dictCode);
        if (cached != null) {
            return cached;
        }
        // 缓存未命中，从数据库加载
        return refreshSingle(dictCode);
    }

    /**
     * 获取所有树形字典的编码和名称列表
     */
    public List<DictRes> getTreeDictList() {
        List<SysBizDict> treeDictCodes = sysBizDictService.getTreeDictCodes();
        List<DictRes> resList = new ArrayList<>();
        if (FuncUtil.isNotEmpty(treeDictCodes)) {
            for (SysBizDict dict : treeDictCodes) {
                DictRes res = new DictRes();
                res.setValue(dict.getDictCode());
                res.setLabel(dict.getDictName());
                resList.add(res);
            }
        }
        return resList;
    }

    /**
     * 刷新单个树形字典的缓存
     *
     * @param dictCode 字典编码
     * @return 刷新后的树结构
     */
    public List<TreeDict> refreshSingle(String dictCode) {
        List<SysBizDict> items = sysBizDictService.getTreeDictItemsByCode(dictCode);
        List<TreeDict> tree = buildTree(items, dictCode);
        treeCache.put(dictCode, tree);
        log.debug("树形字典[{}]缓存刷新完成，共{}个节点", dictCode, items != null ? items.size() : 0);
        return tree;
    }

    /**
     * 刷新所有树形字典缓存
     */
    public void refreshAll() {
        List<SysBizDict> treeDictCodes = sysBizDictService.getTreeDictCodes();
        if (FuncUtil.isNotEmpty(treeDictCodes)) {
            for (SysBizDict dict : treeDictCodes) {
                try {
                    refreshSingle(dict.getDictCode());
                } catch (Exception e) {
                    log.error("刷新树形字典缓存失败: dictCode={}", dict.getDictCode(), e);
                }
            }
        }
    }

    /**
     * 移除缓存（字典被删除时调用）
     */
    public void evict(String dictCode) {
        treeCache.remove(dictCode);
    }

    /**
     * 将平铺的字典项列表按 parent_value 组装成树
     */
    private List<TreeDict> buildTree(List<SysBizDict> items, String dictCode) {
        if (FuncUtil.isEmpty(items)) {
            return new ArrayList<>();
        }

        // 转换为 TreeDict 节点
        List<TreeDict> allNodes = items.stream().map(item -> {
            TreeDict node = new TreeDict();
            node.setId(item.getValue());
            node.setKey(item.getValue());
            node.setValue(item.getValue());
            node.setLabel(item.getLabel());
            node.setPid(item.getParentValue());
            node.setOrder(item.getSort());
            node.setTreeType(dictCode);
            node.setTreeTitle(item.getDictName());
            node.setChildren(new ArrayList<>());
            return node;
        }).collect(Collectors.toList());

        // 按 value 索引
        Map<String, TreeDict> nodeMap = allNodes.stream()
                .collect(Collectors.toMap(
                        n -> String.valueOf(n.getValue()),
                        n -> n,
                        (a, b) -> a
                ));

        // 组装树：根节点 parent_value 为 null
        List<TreeDict> roots = new ArrayList<>();
        for (TreeDict node : allNodes) {
            Object pid = node.getPid();
            if (pid == null || String.valueOf(pid).isEmpty()) {
                roots.add(node);
            } else {
                TreeDict parent = nodeMap.get(String.valueOf(pid));
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    // 找不到父节点，作为根节点处理（容错）
                    roots.add(node);
                }
            }
        }

        // 标记叶子节点
        markLeaf(roots);

        return roots;
    }

    /**
     * 递归标记叶子节点
     */
    private void markLeaf(List<TreeDict> nodes) {
        if (FuncUtil.isEmpty(nodes)) {
            return;
        }
        for (TreeDict node : nodes) {
            node.setIsLeaf(FuncUtil.isEmpty(node.getChildren()));
            markLeaf(node.getChildren());
        }
    }
}
