package com.bidr.kernel.config.response;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.exception.NoticeException;
import com.diboot.core.binding.Binder;
import lombok.Data;

import java.util.List;

/**
 * Title: R
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/13 13:42
 */
@Data
public class Resp {
    public static void notice(String noticeFormat, Object... parameter) {
        throw new NoticeException(String.format(noticeFormat, parameter));
    }

    public static <T, VO> VO convert(T entity, Class<VO> voClass) {
        return Binder.convertAndBindRelations(entity, voClass);
    }

    public static <T, VO> List<VO> convert(List<T> entity, Class<VO> voClass) {
        return Binder.convertAndBindRelations(entity, voClass);
    }

    public static <T, R> Page<R> convert(IPage<T> page, Class<R> clazz) {
        Page<R> res = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<R> targetList = Binder.convertAndBindRelations(page.getRecords(), clazz);
        res.setRecords(targetList);
        return res;
    }

    public static <T, R> Page<R> convert(IPage<T> page, List<R> targetList) {
        Page<R> res = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        res.setRecords(targetList);
        return res;
    }
}
