package com.bidr.platform.fsm;

import com.bidr.platform.fsm.bo.MachineType;
import com.bidr.platform.fsm.bo.StateMachine;
import com.bidr.platform.fsm.bo.operate.MachineOperate;
import com.bidr.platform.fsm.bo.role.MachineRole;
import com.bidr.platform.fsm.bo.state.MachineState;
import com.bidr.platform.fsm.bo.transition.MachineTransition;
import com.bidr.kernel.constant.err.ErrCode;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.DictEnumUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: StateMachineProvider
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/4/1 14:27
 */
@Component
public class StateMachineProvider implements ApplicationContextAware {

    private static final Map<MachineType, StateMachine> STATE_MACHINE_MAP = new ConcurrentHashMap<>();

    @SuppressWarnings("rawtypes, unchecked")
    public static MachineTransition doTransfer(MachineType machineType,
                                               String stateFromValue,
                                               Class machineStateClazz,
                                               MachineOperate operate) {
        Enum machineState = DictEnumUtil.getEnumByValue(stateFromValue, machineStateClazz);
        return doTransfer(machineType, (MachineState) machineState, operate, null);
    }

    public static MachineTransition doTransfer(MachineType machineType,
                                               MachineState from,
                                               MachineOperate operate,
                                               List<String> roleList) {
        return doTransfer(machineType, from, operate, roleList,
                ErrCodeSys.STATE_MACHINE_TRANSFER_NOT_ALLOW, from.getLabel(), operate.getLabel());
    }

    public static MachineTransition doTransfer(MachineType machineType,
                                               MachineState from,
                                               MachineOperate operate,
                                               List<String> roleList,
                                               ErrCode tipCode,
                                               String... tipMsg) {
        StateMachine stateMachine = STATE_MACHINE_MAP.get(machineType);

        Validator.assertNotNull(stateMachine, ErrCodeSys.SYS_CONFIG_NOT_EXIST, "状态机");
        Validator.assertNotNull(from, ErrCodeSys.SYS_CONFIG_NOT_EXIST, "当前状态");
        Validator.assertNotNull(operate, ErrCodeSys.SYS_CONFIG_NOT_EXIST, "当前操作");

        validateMachineRoleOperate(machineType, operate, roleList);
        Validator.assertTrue(stateMachine.checkTransfer(from, operate), tipCode, tipMsg);
        return STATE_MACHINE_MAP.get(machineType).doTransfer(from, operate);
    }

    public static void validateMachineRoleOperate(MachineType machineType,
                                                  MachineOperate operate,
                                                  List<String> roleIdList) {
        StateMachine stateMachine = STATE_MACHINE_MAP.get(machineType);
        validateMachineRoleOperate(stateMachine, operate, roleIdList);
    }

    public static void validateMachineRoleOperate(StateMachine stateMachine,
                                                  MachineOperate operate,
                                                  List<String> roleIdList) {
        Set<MachineRole> acceptRoleSet = stateMachine.getAcceptRoles(operate);
        if (CollectionUtils.isNotEmpty(acceptRoleSet)) {
            Map<String, MachineRole> acceptRoleMap = ReflectionUtil.reflectToMap(acceptRoleSet, "value");
            boolean allow = false;
            for (String role : roleIdList) {
                MachineRole machineRole = acceptRoleMap.get(role);
                if (machineRole != null) {
                    allow = true;
                    break;
                }
            }
            Validator.assertTrue(allow, ErrCodeSys.STATE_MACHINE_TRANSFER_ROLE_NOT_ALLOW, operate.getLabel());
        }
    }

    public static MachineTransition doTransfer(MachineType machineType,
                                               MachineState from,
                                               MachineOperate operate) {
        return doTransfer(machineType, from, operate, null,
                ErrCodeSys.STATE_MACHINE_TRANSFER_NOT_ALLOW, from.getLabel(), operate.getLabel());
    }

    @SuppressWarnings("rawtypes, unchecked")
    public static MachineTransition doTransfer(MachineType machineType,
                                               String stateFromValue,
                                               Class machineStateClazz,
                                               MachineOperate operate,
                                               List<String> roleList) {
        Enum machineState = DictEnumUtil.getEnumByValue(stateFromValue, machineStateClazz);
        return doTransfer(machineType, (MachineState) machineState, operate, roleList);
    }

    public static MachineTransition doTransfer(MachineType machineType,
                                               MachineState from,
                                               MachineOperate operate,
                                               ErrCode tipCode,
                                               String... tipMsg) {
        return doTransfer(machineType, from, operate, null, tipCode, tipMsg);
    }

    public static void validateMachineRoleOperate(MachineType machineType,
                                                  MachineOperate operate,
                                                  String role) {
        StateMachine stateMachine = STATE_MACHINE_MAP.get(machineType);
        validateMachineRoleOperate(stateMachine, operate, role);
    }

    public static void validateMachineRoleOperate(StateMachine stateMachine,
                                                  MachineOperate operate,
                                                  String role) {
        Set<MachineRole> acceptRoleSet = stateMachine.getAcceptRoles(operate);
        if (CollectionUtils.isNotEmpty(acceptRoleSet)) {
            boolean allow = false;
            for (MachineRole acceptRole : acceptRoleSet) {
                if (StringUtils.equals(role, acceptRole.getValue())) {
                    allow = true;
                    break;
                }
            }
            Validator.assertTrue(allow, ErrCodeSys.STATE_MACHINE_TRANSFER_NOT_ALLOW, operate.getLabel());
        }
    }

    /**
     * 根据类型获取对应状态机
     *
     * @param type 状态机类型
     * @return 状态机
     */
    public StateMachine getStateMachineByType(MachineType type) {
        StateMachine stateMachineDef = STATE_MACHINE_MAP.get(type);
        Validator.assertNotNull(stateMachineDef, ErrCodeSys.SYS_CONFIG_NOT_EXIST, type.getLabel());
        return stateMachineDef;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        for (Map.Entry<String, StateMachine> entry : applicationContext
                .getBeansOfType(StateMachine.class).entrySet()) {
            registerStateMachine(entry.getValue());
        }
    }

    /**
     * 状态机注册
     *
     * @param stateMachine 状态机
     */
    private void registerStateMachine(StateMachine stateMachine) {
        STATE_MACHINE_MAP.put(stateMachine.getMachineType(), stateMachine);
        stateMachine.init();
    }

    public List<String> getAcceptMachineStateValue(StateMachine stateMachine, MachineOperate operate) {
        List<String> acceptMachineStateValues = new ArrayList<>();
        Set<MachineState> machineStates = stateMachine.getAcceptMachineState(operate);
        if (CollectionUtils.isNotEmpty(machineStates)) {
            for (MachineState machineState : machineStates) {
                acceptMachineStateValues.add(machineState.getValue());
            }

        }
        return acceptMachineStateValues;
    }
}
