package com.bidr.authorization.controller.admin;

import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.vo.admin.UserRes;
import com.bidr.kernel.controller.BaseAdminController;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: AdminUserController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/12/29 10:31
 */
@Api(tags = "系统管理 - 用户管理")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/portal/user"})
public class AdminUserController extends BaseAdminController<AcUser, UserRes> {


}
