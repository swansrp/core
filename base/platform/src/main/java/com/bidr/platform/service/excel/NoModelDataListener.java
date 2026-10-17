package com.bidr.platform.service.excel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Title: NoModelDataListener
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/10/10 11:41
 */
@Slf4j
public class NoModelDataListener<T> extends ModelDataListener<T, Map<Integer, String>> {

    public NoModelDataListener(EasyExcelHandler<T, Map<Integer, String>> handler, PlatformTransactionManager transactionManager) {
        super(handler, new HashMap<>(), transactionManager);
    }
}
