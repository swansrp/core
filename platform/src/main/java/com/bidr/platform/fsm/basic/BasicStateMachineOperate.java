package com.bidr.platform.fsm.basic;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.platform.fsm.bo.operate.MachineOperate;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;

/**
 * Title: BasicStateMachineOperate
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2021/4/2 14:03
 */
@Getter
@AllArgsConstructor
public enum BasicStateMachineOperate implements Dict, MachineOperate {
    /**
     * 基础状态
     */
    SAVE("1", 1, "保存"),
    APPLY("2", 2, "申请"),
    REJECT("3", 3, "拒绝"),
    PASS("4", 4, "通过"),
    CLOSE("5", 5, "关闭"),
    FINISH("6", 6, "完成");


    private final String value;
    private final Integer order;
    private final String label;
    private final HashMap<Object, Enum<?>> map = new HashMap<>();

}
