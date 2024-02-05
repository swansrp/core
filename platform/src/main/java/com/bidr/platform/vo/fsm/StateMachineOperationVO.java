package com.bidr.platform.vo.fsm;

import com.bidr.platform.fsm.bo.operate.MachineOperate;
import lombok.Data;

import java.util.List;

/**
 * Title: StateMachineVO
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/05 13:19
 */
@Data
public class StateMachineOperationVO implements MachineOperate {
    private String value;
    private String label;
    private List<String> supportRoleList;
    private List<String> supportStatusList;
}
