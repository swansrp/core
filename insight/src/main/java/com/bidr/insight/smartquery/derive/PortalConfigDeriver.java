package com.bidr.insight.smartquery.derive;

import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.vo.PortalWithColumnsRes;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.layer.ValueDomainDef;
import com.bidr.insight.smartquery.model.GenResult;
import com.bidr.insight.smartquery.model.SemanticQuery;
import com.bidr.insight.smartquery.semantic.SemanticLayerRegistry;
import com.bidr.kernel.vo.common.KeyValueResVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: PortalConfigDeriver
 * Description: semantic_query + SQL 输出列 → portalConfig 同源推导器（§46.1）。
 * 产物为 PortalWithColumnsRes 形状：url 指向 smart-query 端点（前端取数 url 后
 * 拼 /advanced/statistic 与 /advanced/query，正好命中本模块两个取数端点），
 * 列的英文名/中文列头/类型全部来自 GenResult.ColumnInfo（语义层 label），
 * associates 恒空（穿透明细走 advanced/query，不依赖跨 portal 关联）。
 * 码值域列（storedAs=code 值域）推导为 SELECT 字典列：reference 指向推导字典名
 * sq_dict_&lt;域key&gt;，码值对随 deriveDicts 下发，前端注册进 dictStore 即用
 * （不落后端字典表——推导字典仅此通路消费）。码列由 portal parse 翻译后，
 * 筛选条件值列与码列同为码值，合并链路 resolveValue 双向兼容
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Component
@RequiredArgsConstructor
public class PortalConfigDeriver {

    /** 一次性查询的固定 portal 名（前端 url 拼接基准） */
    public static final String PORTAL_NAME = "sq_oneshot";

    /** portalConfig.url：前端自动拼 /advanced/statistic、/advanced/query；
     *  不含 web 前缀（前端 request 层 baseURL 已固定拼 /web） */
    public static final String PORTAL_URL = "insight/smart-query";

    /** 推导字典名前缀（前端 dictStore 注册 key，与 sys_biz_dict/sys_dict_type 命名空间区隔） */
    public static final String DICT_PREFIX = "sq_dict_";

    private final SemanticLayerRegistry layers;

    public PortalWithColumnsRes derive(SemanticQuery sq, List<GenResult.ColumnInfo> columns, String title) {
        PortalWithColumnsRes res = new PortalWithColumnsRes();
        res.setName(PORTAL_NAME);
        res.setDisplayName(title);
        res.setUrl(PORTAL_URL);
        res.setReadOnly("1");
        res.setAdvanced("0");
        res.setColumns(toColumns(columns));
        res.setAssociates(new ArrayList<>());
        return res;
    }

    /** 输出列 → SysPortalColumn（只读展示用，编辑/新增类开关全部关闭）。
     *  fieldType 用前端 FIELD_TYPE 码值：1=INPUT 文本、3=NUMBER、4=SELECT 字典列
     *  （与 sys_portal_column 存量配置同口径）；storedAs=code 值域列推成 SELECT，
     *  reference 指向推导字典名，码值对由 deriveDicts 随产物下发前端注册 */
    private List<SysPortalColumn> toColumns(List<GenResult.ColumnInfo> columns) {
        List<SysPortalColumn> list = new ArrayList<>();
        int order = 1;
        for (GenResult.ColumnInfo c : columns) {
            SysPortalColumn col = new SysPortalColumn();
            col.setProperty(c.getAlias());
            col.setDbField(c.getAlias());
            col.setDisplayName(c.getDisplay() != null ? c.getDisplay() : c.getAlias());
            col.setFieldType("metric".equals(c.getKind()) ? "3" : "1");
            ValueDomainDef domain = columnDomain(c);
            if (domain != null && "code".equals(domain.getStoredAs())) {
                col.setFieldType("4");
                col.setReference(dictCodeOf(c.getAlias()));
            }
            col.setDisplayOrder(order++);
            col.setAlign("metric".equals(c.getKind()) ? "right" : "left");
            col.setWidth(150);
            col.setFixed("0");
            col.setTooltip("0");
            col.setEnable("1");
            col.setShow("1");
            // 字典列（SELECT）同时开放表头筛选：筛选值即码值，合并链路 resolveValue 兼容
            col.setFilterAble("dimension".equals(c.getKind())
                    || (domain != null && "code".equals(domain.getStoredAs())) ? "1" : "0");
            col.setSortAble("1");
            col.setSummaryAble("metric".equals(c.getKind()) ? "1" : "0");
            col.setEditAble("0");
            col.setDetailShow("1");
            col.setDetailSize(12);
            col.setDetailPadding(0);
            col.setAddShow("0");
            col.setAddSize(12);
            col.setAddPadding(0);
            col.setAddDisabled("1");
            col.setEditShow("0");
            col.setEditSize(12);
            col.setEditPadding(0);
            col.setEditDisabled("1");
            col.setRequired("0");
            col.setMobileDisplayType(col.getFieldType());
            list.add(col);
        }
        return list;
    }

    /** 推导产物随附字典：storedAs=code 值域列的码值对（dictName → value/label 列表），
     *  前端渲染前注册进 dictStore，SELECT 列翻译/筛选下拉即生效，无需后端字典表 */
    public Map<String, List<KeyValueResVO>> deriveDicts(List<GenResult.ColumnInfo> columns) {
        Map<String, List<KeyValueResVO>> dicts = new LinkedHashMap<>();
        for (GenResult.ColumnInfo c : columns) {
            ValueDomainDef domain = columnDomain(c);
            if (domain == null || !"code".equals(domain.getStoredAs())) {
                continue;
            }
            List<KeyValueResVO> items = new ArrayList<>();
            for (ValueDomainDef.DomainValue v : domain.getValues() == null
                    ? new ArrayList<ValueDomainDef.DomainValue>() : domain.getValues()) {
                KeyValueResVO kv = new KeyValueResVO();
                kv.setValue(v.getCode());
                kv.setLabel(v.getLabel());
                items.add(kv);
            }
            dicts.put(dictCodeOf(c.getAlias()), items);
        }
        return dicts;
    }

    /** 推导字典名：按列别名（维度名/字段名即值域 key 口径） */
    public static String dictCodeOf(String columnAlias) {
        return DICT_PREFIX + columnAlias;
    }

    /** 输出列对应的 storedAs=code 值域：维度列按维度名查域；字段列（list 明细）
     *  按字段名扫实体声明（同名字段取首个带域声明的，POC 口径） */
    private ValueDomainDef columnDomain(GenResult.ColumnInfo c) {
        if ("dimension".equals(c.getKind())) {
            return layers.current().domainOfDim(c.getAlias());
        }
        if ("field".equals(c.getKind())) {
            for (EntityDef ent : layers.current().entities()) {
                for (EntityDef.EntityFieldDef f : ent.getFields() == null
                        ? new ArrayList<EntityDef.EntityFieldDef>() : ent.getFields()) {
                    if (c.getAlias().equals(f.getName()) && f.getValueDomain() != null) {
                        ValueDomainDef d = layers.current().domains().get(f.getValueDomain());
                        if (d != null) {
                            return d;
                        }
                    }
                }
            }
        }
        return null;
    }
}
