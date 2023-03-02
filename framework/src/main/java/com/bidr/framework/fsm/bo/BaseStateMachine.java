package com.bidr.framework.fsm.bo;

import com.bidr.framework.fsm.bo.operate.MachineOperate;
import com.bidr.framework.fsm.bo.role.MachineRole;
import com.bidr.framework.fsm.bo.state.MachineState;
import com.bidr.framework.fsm.bo.transition.MachineTransition;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import org.apache.commons.collections4.CollectionUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: BaseStateMachine
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/4/1 14:57
 */
public abstract class BaseStateMachine implements StateMachine {

    /**
     * 合法的迁转路径(from+operate-->to)
     */
    protected Map<String, MachineTransition> acceptTransitions = new ConcurrentHashMap<>();
    protected Map<MachineOperate, Set<MachineState>> acceptStates = new ConcurrentHashMap<>();
    protected Map<MachineOperate, Set<MachineRole>> acceptRoles = new ConcurrentHashMap<>();
    private final Set<MachineState> machineStates = new HashSet<>();
    private final Set<MachineOperate> machineOperates = new HashSet<>();

    @Override
    public void init() {
        regStateSet(getStateEnum());
        regOperateSet(getOperateEnum());
        regTransition();
    }

    private void regStateSet(Class<? extends Enum<?>> stateEnumClazz) {
        Validator.assertNotNull(stateEnumClazz, ErrCodeSys.SYS_CONFIG_NOT_EXIST, "状态机状态列表");
        Enum<?>[] stateEnums = stateEnumClazz.getEnumConstants();
        for (Enum<?> e : stateEnums) {
            if (MachineState.class.isAssignableFrom(e.getClass())) {
                machineStates.add((MachineState) e);
            }
        }
    }

    private void regOperateSet(Class<? extends Enum<?>> operateEnumClazz) {
        Validator.assertNotNull(operateEnumClazz, ErrCodeSys.SYS_CONFIG_NOT_EXIST, "状态机动作列表");
        Enum<?>[] operateEnums = operateEnumClazz.getEnumConstants();
        for (Enum<?> e : operateEnums) {
            if (MachineOperate.class.isAssignableFrom(e.getClass())) {
                machineOperates.add((MachineOperate) e);
            }
        }
    }

    @Override
    public void generatePlantUmlFile(String filePath) {
        generatePlantUmlFile(filePath + getMachineType().getLabel() + ".puml", false);
    }

    @Override
    public void generatePlantUmlFile(String filePath, boolean enumName) {
        String uml = generatePlantUML(enumName);
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                file.createNewFile();
            } else {
                file.delete();
            }
            FileOutputStream out = new FileOutputStream(filePath);
            PrintStream p = new PrintStream(out);
            p.println(uml);
        } catch (IOException e) {

        }
    }

    @Override
    public Set<MachineState> getAcceptMachineState(MachineOperate operate) {
        return acceptStates.get(operate);
    }

    @Override
    public Set<MachineRole> getAcceptRoles(MachineOperate operate) {
        return acceptRoles.get(operate);
    }

    @Override
    public boolean checkTransfer(MachineState from, MachineOperate operate) {
        return acceptTransitions.containsKey(MachineTransition.genInnerKey(from, operate));
    }

    @Override
    public MachineTransition doTransfer(MachineState from, MachineOperate operate) {
        return acceptTransitions.get(MachineTransition.genInnerKey(from, operate));
    }

    public String generatePlantUML() {
        return generatePlantUML(false);
    }

    public String generatePlantUML(boolean enumName) {
        char LF = '\n';
        StringBuilder sb = new StringBuilder();
        Set<MachineState> needStart = new HashSet<>();
        Set<MachineState> needFinish = new HashSet<>();
        sb.append(LF).append("@startuml").append(LF);
        for (MachineTransition transition : acceptTransitions.values()) {
            if (isInitState(transition.getFrom())) {
                if (!needStart.contains(transition.getFrom())) {
                    sb.append("[*] --> ")
                            .append(transition.getFrom().getLabel())
                            .append(LF);
                    needStart.add(transition.getFrom());
                }
            }
            sb.append(transition.getFrom().getLabel())
                    .append(" --> ")
                    .append(transition.getTo().getLabel())
                    .append(" : ")
                    .append(transition.getOperate().getLabel())
                    .append(LF);
            if (isEndState(transition.getTo())) {
                if (!needFinish.contains(transition.getTo())) {
                    sb.append(transition.getTo().getLabel())
                            .append("--> [*]")
                            .append(LF);
                    needFinish.add(transition.getTo());
                }
            }
        }

        for (MachineState state : machineStates) {
            if (enumName) {
                sb.append(state.getLabel())
                        .append(" : ")
                        .append(state.name())
                        .append(LF);
            }
            sb.append(state.getLabel())
                    .append(" : ")
                    .append(state.getRemark())
                    .append(LF);

        }
        sb.append("@enduml");
        return sb.toString();
    }

    protected void regTransition(MachineState from, MachineState to, MachineOperate... operates) {
        if (operates != null) {
            for (MachineOperate operate : operates) {
                Validator.assertTrue(machineStates.contains(from), ErrCodeSys.SYS_CONFIG_NOT_EXIST,
                        "状态:" + from.getLabel());
                Validator.assertTrue(machineStates.contains(to), ErrCodeSys.SYS_CONFIG_NOT_EXIST,
                        "状态:" + to.getLabel());
                Validator.assertTrue(machineOperates.contains(operate), ErrCodeSys.SYS_CONFIG_NOT_EXIST,
                        "动作:" + operate.getLabel());
                MachineTransition transition = new MachineTransition(from, operate, to);
                String key = transition.genInnerKey();
                acceptTransitions.put(key, transition);
                Set<MachineState> acceptMachineStates = acceptStates.get(operate);
                if (CollectionUtils.isEmpty(acceptMachineStates)) {
                    acceptMachineStates = new HashSet<>();
                    acceptStates.put(operate, acceptMachineStates);
                }
                acceptMachineStates.add(from);
            }
        }
    }

    protected void regMachineOperateRole(MachineOperate operate, MachineRole... roles) {
        if (roles != null) {
            for (MachineRole role : roles) {
                Validator.assertTrue(machineOperates.contains(operate), ErrCodeSys.SYS_CONFIG_NOT_EXIST,
                        "动作:" + operate.getLabel());
                Set<MachineRole> acceptRoleSet = acceptRoles.get(operate);
                if (CollectionUtils.isEmpty(acceptRoleSet)) {
                    acceptRoleSet = new HashSet<>();
                    acceptRoles.put(operate, acceptRoleSet);
                }
                acceptRoleSet.add(role);
            }
        }
    }
}
