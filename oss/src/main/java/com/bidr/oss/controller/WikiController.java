package com.bidr.oss.controller;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthNone;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.config.log.LogSilent;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.vo.common.IdOrderReqVO;
import com.bidr.oss.service.WikiCollaboratorPortalService;
import com.bidr.oss.service.WikiPagePortalService;
import com.bidr.oss.vo.OssWikiCollaboratorVO;
import com.bidr.oss.vo.OssWikiPageShareVO;
import com.bidr.oss.vo.OssWikiPageVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Wiki页面控制器
 *
 * @author sharp
 * @since 2025-12-12
 */
@Api(tags = "Wiki管理 - Wiki页面")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/wiki"})
public class WikiController {

    private final WikiPagePortalService wikiPagePortalService;
    private final WikiCollaboratorPortalService collaboratorPortalService;

    // ========== 页面相关接口 ==========

    /**
     * 获取Wiki页面树形结构
     */
    @ApiOperation("获取Wiki页面树形结构")
    @GetMapping("/tree")
    public List<OssWikiPageVO> getTree(
            @ApiParam("搜索关键词") @RequestParam(required = false) String keyword) {
        return wikiPagePortalService.getPageTree(keyword, AccountContext.getOperator());
    }

    /**
     * 获取Wiki页面详情
     */
    @LogSilent
    @ApiOperation("获取Wiki页面详情")
    @GetMapping("/page/{id}")
    public OssWikiPageVO getPage(@PathVariable Long id) {
        return wikiPagePortalService.getPageDetail(id, AccountContext.getOperator());
    }

    /**
     * 新增Wiki页面
     */
    @ApiOperation("新增Wiki页面")
    @PostMapping("/page")
    public OssWikiPageVO addPage(@RequestBody OssWikiPageVO vo) {
        vo.setId(null);
        return wikiPagePortalService.savePage(vo);
    }

    /**
     * 更新Wiki页面
     */
    @ApiOperation("更新Wiki页面")
    @PostMapping("/page/{id}")
    public OssWikiPageVO updatePage(@PathVariable Long id, @RequestBody OssWikiPageVO vo) {
        vo.setId(id);
        return wikiPagePortalService.savePage(vo);
    }

    /**
     * 删除Wiki页面
     */
    @ApiOperation("删除Wiki页面")
    @PostMapping("/page/delete/{id}")
    public boolean deletePage(@PathVariable Long id) {
        return wikiPagePortalService.deletePage(id);
    }

    /**
     * 更新页面排序/移动页面
     */
    @ApiOperation("更新页面排序/移动页面")
    @PostMapping("/page/sort")
    public void sortPage(@RequestBody List<IdOrderReqVO> voList) {
        wikiPagePortalService.updateSort(voList);
        Resp.notice("更新页面排序成功");
    }

    /**
     * 更新页面排序/移动页面
     */
    @ApiOperation("更新页面排序/移动页面")
    @PostMapping("/page/pid")
    public void setPid(Long id, Long pid) {
        wikiPagePortalService.setPid(id, pid);
        Resp.notice("变更父节点成功");
    }

    /**
     * 搜索Wiki页面
     */
    @ApiOperation("搜索Wiki页面")
    @GetMapping("/search")
    public List<OssWikiPageVO> searchPages(
            @ApiParam("搜索关键词") @RequestParam String keyword) {
        return wikiPagePortalService.searchPages(keyword, AccountContext.getOperator());
    }

    /**
     * 公开预览页面（无需登录）
     */
    @LogSilent
    @Auth(AuthNone.class)
    @ApiOperation("公开预览Wiki页面")
    @GetMapping("/public/{id}")
    public OssWikiPageVO getPublicPage(@PathVariable Long id) {
        return wikiPagePortalService.getPageDetail(id,  null);
    }

    /**
     * 公开预览页面树（无需登录）
     */
    @Auth(AuthNone.class)
    @ApiOperation("公开预览Wiki页面树")
    @GetMapping("/public/tree")
    public List<OssWikiPageVO> getPublicTree(
            @ApiParam("搜索关键词") @RequestParam(required = false) String keyword) {
        return wikiPagePortalService.getPageTree(keyword, null);
    }

    /**
     * 搜索Wiki页面
     */
    @Auth(AuthNone.class)
    @ApiOperation("搜索公开Wiki页面")
    @GetMapping("/public/search")
    public List<OssWikiPageVO> searchPublicPages(
            @ApiParam("搜索关键词") @RequestParam String keyword) {
        return wikiPagePortalService.searchPages(keyword, null);
    }


    // ========== 协作者相关接口 ==========


    /**
     * 获取权限分享码
     */
    @ApiOperation("获取只读权限分享码")
    @GetMapping("/collaborator/share")
    public OssWikiPageShareVO getPermissionKey(Long pageId, String permission, String password, Integer expiredSeconds) {
        return collaboratorPortalService.getPermissionKey(pageId, permission, password, expiredSeconds);
    }

    /**
     * 解析分享码获取权限
     */
    @ApiOperation("解析分享码获取权限")
    @PostMapping("/collaborator/share")
    public void parsePermission(String shareCode, String password) {
        collaboratorPortalService.parsePermission(shareCode, password);
        Resp.notice("已获取相关页面权限");
    }

    /**
     * 申请编辑权限
     */
    @ApiOperation("申请编辑权限")
    @PostMapping("/collaborator/request")
    public void requestAccess(@RequestBody Map<String, Object> params) {
        Long pageId = Long.valueOf(params.get("pageId").toString());
        String permission = params.get("permission") != null ? params.get("permission").toString() : "2";
        String requestMsg = params.get("requestMsg") != null ? params.get("requestMsg").toString() : null;
        collaboratorPortalService.requestAccess(pageId, permission, requestMsg);
        Resp.notice("申请成功，请等待审批");
    }

    /**
     * 获取页面协作者列表
     */
    @ApiOperation("获取页面协作者列表")
    @GetMapping("/collaborator/list/{pageId}")
    public List<OssWikiCollaboratorVO> getCollaborators(@PathVariable Long pageId) {
        return collaboratorPortalService.getCollaborators(pageId);
    }

    /**
     * 获取待审批的申请
     */
    @ApiOperation("获取待审批的申请")
    @GetMapping("/collaborator/pending/{pageId}")
    public List<OssWikiCollaboratorVO> getPendingRequests(@PathVariable Long pageId) {
        return collaboratorPortalService.getPendingRequests(pageId);
    }

    /**
     * 审批申请
     */
    @ApiOperation("审批协作申请")
    @PostMapping("/collaborator/approve")
    public boolean approveRequest(@RequestBody Map<String, Object> params) {
        Long pageId = Long.valueOf(params.get("pageId").toString());
        String userId = params.get("userId").toString();
        boolean approved = Boolean.parseBoolean(params.get("approved").toString());
        return collaboratorPortalService.approveRequest(pageId, userId, approved);
    }

    /**
     * 移除协作者
     */
    @ApiOperation("移除协作者")
    @PostMapping("/collaborator/delete/{pageId}/{userId}")
    public boolean removeCollaborator(@PathVariable Long pageId, @PathVariable String userId) {
        return collaboratorPortalService.removeCollaborator(pageId, userId);
    }

    /**
     * 更新协作者权限
     */
    @ApiOperation("更新协作者权限")
    @PostMapping("/collaborator/permission")
    public boolean updatePermission(@RequestBody Map<String, Object> params) {
        Long pageId = Long.valueOf(params.get("pageId").toString());
        String userId = params.get("userId").toString();
        String permission = params.get("permission").toString();
        return collaboratorPortalService.updatePermission(pageId, userId, permission);
    }

    /**
     * 获取我的协作页面
     */
    @ApiOperation("获取我的协作页面")
    @GetMapping("/collaborator/my")
    public List<OssWikiCollaboratorVO> getMyCollaborations() {
        return collaboratorPortalService.getMyCollaborations();
    }
}
