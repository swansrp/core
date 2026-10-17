/**
 * Title: BaseResVO.java Description: Copyright: Copyright (c) 2019 Company: Sharp
 *
 * @author sharuopeng
 * @since 2019-02-19 13:52:13
 */
package com.bidr.kernel.vo.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.utils.DbUtil;
import com.bidr.kernel.utils.JsonUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author sharuopeng
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryRespVO<T> extends QueryReqVO {
    private List<T> info;
    private Long totalPages;
    private Long totalSize;

    public QueryRespVO(Page<T> pageInfo) {
        this();
        buildPageInfo(pageInfo);
    }

    public QueryRespVO() {
        info = new ArrayList<>();
    }

    public QueryRespVO(Page<T> pageInfo, Class<T> clazz) {
        this();
        buildPage(DbUtil.copy(pageInfo, clazz));
    }

    public <K> QueryRespVO(Class<T> clazz, Page<K> pageInfo) {
        this();
        buildPageInfo(DbUtil.copy(pageInfo, clazz));
    }

    public <K> QueryRespVO(Page<K> pageInfo, Class<T> clazz, boolean convert) {
        this();
        Page<T> info = DbUtil.copy(pageInfo, clazz);
        buildPageInfo(info);
    }

    public static <T, K> QueryRespVO<T> build(Page<K> pageInfo, Class<T> clazz) {
        QueryRespVO<T> res = new QueryRespVO<>();
        Page<T> info = DbUtil.copy(pageInfo, clazz);
        res.setTotalPages(info.getPages());
        res.setTotalSize(info.getTotal());
        res.setCurrentPage(info.getCurrent());
        res.setPageSize(info.getSize());
        if (CollectionUtils.isNotEmpty(info.getRecords())) {
            res.setInfo(info.getRecords());
        }
        return res;
    }

    public static <K> QueryRespVO<Map<String, String>> buildMap(Page<K> pageInfo) {
        QueryRespVO<Map<String, String>> res = new QueryRespVO<>();
        List<Map<String, String>> list = new ArrayList<>();
        res.setTotalPages(pageInfo.getPages());
        res.setTotalSize(pageInfo.getTotal());
        res.setCurrentPage(pageInfo.getCurrent());
        res.setPageSize(pageInfo.getSize());
        if (CollectionUtils.isNotEmpty(pageInfo.getRecords())) {
            for (K data : pageInfo.getRecords()) {
                Map<String, String> map = JsonUtil.readJson(data, Map.class, String.class, String.class);
                res.getInfo().add(map);
            }
        }
        return res;
    }

    public void buildPageInfo(Page<T> pageInfo) {
        this.setTotalPages(pageInfo.getPages());
        this.setTotalSize(pageInfo.getTotal());
        this.setCurrentPage(pageInfo.getCurrent());
        this.setPageSize(pageInfo.getSize());
        if (CollectionUtils.isNotEmpty(pageInfo.getRecords())) {
            info.addAll(pageInfo.getRecords());
        }
    }

    public void buildPage(Page<?> pageInfo) {
        this.setTotalPages(pageInfo.getPages());
        this.setTotalSize(pageInfo.getTotal());
        this.setCurrentPage(pageInfo.getCurrent());
        this.setPageSize(pageInfo.getSize());
    }

    public void buildPage(List<?> data) {
        if (data instanceof Page) {
            this.setTotalPages(((Page<?>) data).getPages());
            this.setTotalSize(((Page<?>) data).getTotal());
            this.setCurrentPage(((Page<?>) data).getCurrent());
            this.setPageSize(((Page<?>) data).getSize());
        }
    }
}
