package com.bidr.platform.fsm.basic;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.platform.fsm.bo.state.MachineState;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;

/**
 * Title: BasicStateMachineState
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2021/4/2 14:03
 */
@Getter
@AllArgsConstructor
public enum BasicStateMachineState implements Dict, MachineState {
    /**
     * 基础状态
     */
    INIT("1", "初始化", ""),
    DRAFT("2", "暂存", ""),
    START("3", "待审核", ""),
    PASS("4", "通过", ""),
    REJECT("5", "拒绝", ""),
    CLOSE("6", "关闭", ""),
    FINISH("7", "完成", "");


    private final String value;
    private final String label;
    private final String remark;
    private final HashMap<Object, Enum<?>> map = new HashMap<>();


}
