package com.bidr.kernel.config.db;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.github.jeffreyning.mybatisplus.base.DeleteByMultiIdMethod;
import com.github.jeffreyning.mybatisplus.base.SelectByMultiIdMethod;
import com.github.jeffreyning.mybatisplus.base.UpdateByMultiIdMethod;
import com.github.yulichang.injector.MPJSqlInjector;

import java.util.List;

/**
 * Title: MPJConfig
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2022/12/09 10:49
 */
public class MPJConfig extends MPJSqlInjector {

    @Override
    public List<AbstractMethod> getMethodList(Class<?> mapperClass, TableInfo tableInfo) {
        List<AbstractMethod> methodList = super.getMethodList(mapperClass, tableInfo);
        methodList.add(new SelectByMultiIdMethod());
        methodList.add(new UpdateByMultiIdMethod());
        methodList.add(new DeleteByMultiIdMethod());
        return methodList;
    }
}


