package com.bidr.authorization.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthAdmin;
import com.bidr.authorization.dao.entity.AcSubsystem;
import com.bidr.authorization.dao.repository.AcSubsystemService;
import com.bidr.authorization.service.admin.AdminSubsystemService;
import com.bidr.authorization.vo.subsystem.PartnerOptionRes;
import com.bidr.authorization.vo.subsystem.QuerySubsystemReq;
import com.bidr.authorization.vo.subsystem.QuerySubsystemRes;
import com.bidr.authorization.vo.subsystem.SubsystemReq;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.vo.common.IdReqVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: AcSubsystemController
 * Description: 系统管理 - 应用管理（子系统注册表 admin 面，方案B 棒2）。
 * 通用 CRUD 面继承 BaseAdminController（/insert /update /delete /general/query…）；
 * 本类收口业务端点：/add（partner 绑定校验在仓储腿）、/update（beforeUpdate 钉死 app_code 不可改）、
 * /enable /disable（status '1'/'0'）、/delete（菜单引用守卫）、/query（行带 partner_app_key）、
 * /partner-options（渠道下拉，绝不含密钥）。
 *
 * @author Sharp
 * @since 2026-11-06
 */
@Api(tags = "系统管理 - 应用管理")
@RestController("AcSubsystemController")
@RequestMapping(value = "/web/admin/subsystem")
@Auth(AuthAdmin.class)
public class AcSubsystemController extends BaseAdminController<AcSubsystem, QuerySubsystemRes> {

    @Resource
    private AdminSubsystemService adminSubsystemService;

    @Resource
    private AcSubsystemService acSubsystemService;

    @ApiOperation(value = "添加应用")
    @RequestMapping(value = "/add", method = RequestMethod.POST)
    public void addSubsystem(@RequestBody SubsystemReq req) {
        adminSubsystemService.add(req);
        Resp.notice("添加应用成功");
    }

    @Override
    public void beforeUpdate(AcSubsystem entity) {
        // app_code 是 PK/业务主键，不可改——但绝不能在此置 null：updateById 的 WHERE 依赖主键字段，
        // 置 null ⇒ WHERE 失配 ⇒ Updates:0「更新失败」（2026-11-07 e2e② 实锤；前端 appCode 输入框
        // disabled 仍回传原值，SET 无害）。表单层不可改已足够。
        // 更新腿换绑 partner 同样过绑定校验（插腿在仓储 insert 内）
        acSubsystemService.validatePartnerBound(entity.getPartnerId());
    }

    @ApiOperation(value = "获取应用列表")
    @RequestMapping(value = "/query", method = RequestMethod.POST)
    public Page<QuerySubsystemRes> querySubsystem(@RequestBody QuerySubsystemReq req) {
        return adminSubsystemService.query(req);
    }

    @ApiOperation(value = "获取可绑定渠道下拉项（不含密钥）")
    @RequestMapping(value = "/partner-options", method = RequestMethod.GET)
    public List<PartnerOptionRes> partnerOptions() {
        return adminSubsystemService.partnerOptions();
    }

    @RequestMapping(value = "/enable", method = RequestMethod.POST)
    public Boolean enable(@RequestBody IdReqVO vo) {
        return update(vo, AcSubsystem::getStatus, CommonConst.YES);
    }

    @RequestMapping(value = "/disable", method = RequestMethod.POST)
    public Boolean disable(@RequestBody IdReqVO vo) {
        return update(vo, AcSubsystem::getStatus, CommonConst.NO);
    }

    @Override
    @ApiOperation("删除数据")
    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public void delete(@RequestBody IdReqVO vo) {
        adminSubsystemService.delete(vo.getId());
        Resp.notice("删除成功");
    }
}
