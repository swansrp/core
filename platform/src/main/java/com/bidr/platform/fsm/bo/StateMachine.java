package com.bidr.platform.fsm.bo;

import com.bidr.platform.fsm.bo.operate.MachineOperate;
import com.bidr.platform.fsm.bo.role.MachineRole;
import com.bidr.platform.fsm.bo.state.MachineState;
import com.bidr.platform.fsm.bo.transition.MachineTransition;

import java.util.Set;

/**
 * Title: StateMachine
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/4/1 15:10
 * @description Project Name: Seed
 * @Package: com.srct.service.utils.fsm.bo
 */
public interface StateMachine {
    /**
     * 初始化状态机
     */
    void init();

    /**
     * 状态机类型
     *
     * @return 状态机类型
     */
    MachineType getMachineType();

    /**
     * 注册状态列表
     *
     * @return 状态枚举
     */
    Class<? extends Enum<?>> getStateEnum();

    /**
     * 注册动作列表
     *
     * @return 动作枚举
     */
    Class<? extends Enum<?>> getOperateEnum();

    /**
     * 获取支持指定动作状态
     *
     * @param operate 动作
     * @return
     */
    Set<MachineState> getAcceptMachineState(MachineOperate operate);

    /**
     * 获取支持指定动作支持角色
     *
     * @param operate 动作
     * @return
     */
    Set<MachineRole> getAcceptRoles(MachineOperate operate);

    /**
     * 注册转移动作
     */
    void regTransition();

    /**
     * 当前state下是否支持operate
     *
     * @param currentState 当前状态
     * @param operate      动作
     * @return
     */
    boolean checkTransfer(MachineState currentState, MachineOperate operate);

    /**
     * 尝试迁转到终态
     *
     * @param beforeChanged 当前状态
     * @param operate       动作
     * @return 终态
     */
    MachineTransition doTransfer(MachineState beforeChanged, MachineOperate operate);

    /**
     * 成功状态
     *
     * @param machineState 成功状态
     * @return 成功状态
     */
    boolean isSuccessState(MachineState machineState);

    /**
     * 失败状态
     *
     * @param machineState 失败状态
     * @return 失败状态
     */
    boolean isFailedState(MachineState machineState);

    /**
     * 初始状态
     *
     * @param machineState 初始状态
     * @return 初始状态
     */
    boolean isInitState(MachineState machineState);

    /**
     * 终结状态
     *
     * @param machineState 终结状态
     * @return 终结状态
     */
    boolean isEndState(MachineState machineState);

    /**
     * 生成plantUML文件
     *
     * @return
     */
    void generatePlantUmlFile(String filePath);

    /**
     * 生成plantUML文件
     *
     * @param enumName 是否包含枚举值
     * @return
     */
    void generatePlantUmlFile(String filePath, boolean enumName);
}
