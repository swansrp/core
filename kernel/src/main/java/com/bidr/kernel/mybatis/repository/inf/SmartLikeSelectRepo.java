package com.bidr.kernel.mybatis.repository.inf;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.kernel.utils.FuncUtil;
import com.github.yulichang.wrapper.MPJLambdaWrapper;

public interface SmartLikeSelectRepo<T> {
    /**
     * 增强LIKE条件
     *
     * @param wrapper wrapper
     * @param field   查询字段
     * @param value   查询内容
     */

    default void smartLike(LambdaQueryWrapper<T> wrapper, SFunction<T, String> field, String value) {
        final String[] andArray = value.split(" ");
        final String[] orArray = value.split("\\|");
        if (andArray.length > 1) {
            wrapper.nested(wr -> {
                for (String s : andArray) {
                    wr.like(FuncUtil.isNotEmpty(s), field, s);
                }
            });
        } else if (orArray.length > 1) {
            wrapper.nested(wr -> {
                for (String s : orArray) {
                    wr.like(FuncUtil.isNotEmpty(s), field, s).or();
                }
            });
        } else {
            wrapper.like(field, value);
        }
    }

    /**
     * 增强LIKE条件
     *
     * @param wrapper    wrapper
     * @param columnName 查询字段
     * @param value      查询内容
     */
    default void smartLike(QueryWrapper<T> wrapper, String columnName, String value) {
        final String[] andArray = value.split(" ");
        final String[] orArray = value.split("\\|");
        if (andArray.length > 1) {
            wrapper.nested(wr -> {
                for (String s : andArray) {
                    wr.like(FuncUtil.isNotEmpty(s), columnName, s);
                }
            });
        } else if (orArray.length > 1) {
            wrapper.nested(wr -> {
                for (String s : orArray) {
                    wr.like(FuncUtil.isNotEmpty(s), columnName, s).or();
                }
            });
        } else {
            wrapper.like(columnName, value);
        }
    }

    /**
     * 增强LIKE条件
     *
     * @param wrapper wrapper
     * @param field   查询字段
     * @param value   查询内容
     */
    default void smartLike(MPJLambdaWrapper<T> wrapper, SFunction<T, String> field, String value) {
        final String[] andArray = value.split(" ");
        final String[] orArray = value.split("\\|");
        if (andArray.length > 1) {
            wrapper.nested(wr -> {
                for (String s : andArray) {
                    wr.like(FuncUtil.isNotEmpty(s), field, s);
                }
            });
        } else if (orArray.length > 1) {
            wrapper.nested(wr -> {
                for (String s : orArray) {
                    wr.like(FuncUtil.isNotEmpty(s), field, s).or();
                }
            });
        } else {
            wrapper.like(field, value);
        }
    }

    /**
     * 增强LIKE条件
     *
     * @param wrapper    wrapper
     * @param columnName 查询字段
     * @param value      查询内容
     */
    default void smartLike(MPJLambdaWrapper<T> wrapper, String columnName, String value) {
        final String[] andArray = value.split(" ");
        final String[] orArray = value.split("\\|");
        if (andArray.length > 1) {
            wrapper.nested(wr -> {
                for (String s : andArray) {
                    wr.like(FuncUtil.isNotEmpty(s), columnName, s);
                }
            });
        } else if (orArray.length > 1) {
            wrapper.nested(wr -> {
                for (String s : orArray) {
                    wr.like(FuncUtil.isNotEmpty(s), columnName, s).or();
                }
            });
        } else {
            wrapper.like(columnName, value);
        }
    }
}
