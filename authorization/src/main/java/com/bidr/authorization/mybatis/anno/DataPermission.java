package com.bidr.authorization.mybatis.anno;

import com.bidr.authorization.mybatis.permission.DataPermissionInf;
import com.bidr.authorization.mybatis.permission.NoDataPermission;

import java.lang.annotation.*;

/**
 * Title: DataPermission
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/02 21:12
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface DataPermission {

    /**
     * 需要追加的数据权限
     *
     * @return
     */
    Class<? extends DataPermissionInf>[] value() default NoDataPermission.class;
}
