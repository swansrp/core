package com.bidr.kernel.mybatis.anno;

/**
 * Title: AutoInsertInf
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/01 09:23
 */
public interface AutoInsertInf {

    /**
     * 生成方法
     *
     * @param sequenceName 序列名
     * @return 生成数据
     */
    String exec(String sequenceName);
}
