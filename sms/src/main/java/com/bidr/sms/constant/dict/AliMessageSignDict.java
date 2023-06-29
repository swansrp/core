package com.bidr.sms.constant.dict;

import com.bidr.kernel.constant.dict.MetaDict;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.constant.dict.IDynamicDict;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.sms.service.message.SmsManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Title: AliMessageSignDict
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 15:10
 */
@RequiredArgsConstructor
@Component
@MetaDict(value = "ALI_SMS_SIGN_DICT", remark = "短信签名")
public class AliMessageSignDict implements IDynamicDict {

    private final SmsManageService smsManageService;

    @Override
    public Collection<SysDict> generate() {
        List<SysDict> resList = new ArrayList<>();
        List<String> allSign = smsManageService.getSmsSignList();
        if (FuncUtil.isNotEmpty(allSign)) {
            int i = 0;
            for (String sign : allSign) {
                SysDict dict = buildSysDict(sign, sign, i++);
                resList.add(dict);
            }
        }
        return resList;
    }
}
