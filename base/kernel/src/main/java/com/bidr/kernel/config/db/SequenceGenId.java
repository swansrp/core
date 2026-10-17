package com.bidr.kernel.config.db;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.incrementer.IKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Title: SequenceGenId
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2022/12/13 14:37
 */
@Slf4j
@Component
public class SequenceGenId implements IKeyGenerator {

    public static final String SQL = "SELECT f_nextval('%s')";

    @Override
    public String executeSql(String incrementerName) {
        return String.format(SQL, incrementerName);
    }

    @Override
    public DbType dbType() {
        return DbType.MYSQL;
    }
}
