package com.bidr.platform.fsm.bo.transition;

import com.bidr.kernel.constant.err.ErrCode;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.platform.fsm.bo.operate.MachineOperate;
import com.bidr.platform.fsm.bo.state.MachineState;
import lombok.Data;

/**
 * Title: MachineTransition
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2021/4/1 15:09
 * @description Project Name: Seed
 * @Package: com.srct.service.utils.fsm.bo
 */
@Data
public class MachineTransition {
    private final MachineState from;
    private final MachineOperate operate;
    private final MachineState to;

    private ErrCode tipCode;
    private String tipMsg;


    public String genInnerKey() {
        return genInnerKey(from, operate);
    }

    public static String genInnerKey(MachineState from, MachineOperate operate) {
        return from.getValue() + StringUtil.SPLITTER + operate.getValue();
    }
}
