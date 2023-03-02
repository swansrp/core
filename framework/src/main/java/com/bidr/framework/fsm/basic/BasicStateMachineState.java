package com.bidr.framework.fsm.basic;

import com.bidr.framework.fsm.bo.state.MachineState;
import com.bidr.kernel.constant.dict.Dict;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Title: BasicStateMachineState
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/4/2 14:03
 */
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

    @Getter
    @Setter
    private String value;
    @Getter
    @Setter
    private String label;
    @Getter
    @Setter
    private String remark;
}
