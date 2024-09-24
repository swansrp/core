package com.bidr.platform.controller;

import com.bidr.platform.fsm.StateMachineProvider;
import com.bidr.platform.fsm.bo.StateMachine;
import com.bidr.platform.vo.fsm.StateMachineOperationVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: SystemFsmController
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/9/23 15:37
 */
@Api(tags = "系统基础 - 状态机")
@RestController
@RequestMapping(path = {"/web/fsm"})
@RequiredArgsConstructor
public class SystemFsmController {
    private final StateMachineProvider stateMachineProvider;

    @ApiOperation(value = "获取状态机配置")
    @RequestMapping(value = "/config", method = RequestMethod.GET)
    public List<StateMachineOperationVO> getFsmConfig(String fsm) {
        StateMachine sm = stateMachineProvider.getStateMachineByType(fsm);
        return sm.getStateMachineOperationConfig();
    }
}
