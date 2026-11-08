package com.bidr.feishu.service.contact;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.FuncUtil;
import com.lark.oapi.Client;
import com.lark.oapi.service.contact.v3.model.FindByDepartmentUserReq;
import com.lark.oapi.service.contact.v3.model.FindByDepartmentUserResp;
import com.lark.oapi.service.contact.v3.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Title: FeishuUserService
 * Description: 飞书通讯录 - 用户拉取服务，按部门遍历用户（含 page_token 分页）
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/01/01 00:00
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "feishu", name = "app-id")
public class FeishuUserService {

    private static final int PAGE_SIZE = 50;

    @Resource
    private Client feishuClient;

    /**
     * 拉取指定部门下的直属用户
     *
     * @param departmentId 部门 id（open_department_id）
     * @return 用户列表
     */
    public List<User> listByDepartment(String departmentId) {
        List<User> result = new ArrayList<>();
        String pageToken = null;
        boolean hasMore = true;
        while (hasMore) {
            FindByDepartmentUserReq.Builder builder = FindByDepartmentUserReq.newBuilder()
                    .departmentId(departmentId)
                    .pageSize(PAGE_SIZE)
                    .userIdType("open_id");
            if (FuncUtil.isNotEmpty(pageToken)) {
                builder.pageToken(pageToken);
            }
            FindByDepartmentUserResp resp;
            try {
                resp = feishuClient.contact().v3().user().findByDepartment(builder.build());
            } catch (Exception e) {
                throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "拉取飞书用户失败: " + e.getMessage());
            }
            if (!resp.success()) {
                log.error("拉取飞书用户失败, dept: {}, code: {}, msg: {}", departmentId, resp.getCode(), resp.getMsg());
                throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "拉取飞书用户失败: " + resp.getMsg());
            }
            if (resp.getData() != null && FuncUtil.isNotEmpty(resp.getData().getItems())) {
                for (User item : resp.getData().getItems()) {
                    // 基础只读权限下 find_by_department 不返回 department_ids/status，
                    // 用"当前遍历部门"补位，否则同步会把全员兜底归入根部门
                    if (FuncUtil.isEmpty(item.getDepartmentIds())) {
                        item.setDepartmentIds(new String[]{departmentId});
                    }
                    result.add(item);
                }
            }
            hasMore = resp.getData() != null && Boolean.TRUE.equals(resp.getData().getHasMore());
            pageToken = resp.getData() == null ? null : resp.getData().getPageToken();
        }
        return result;
    }

    /**
     * 拉取部门树下的全部用户（逐部门遍历，按 open_id 去重）
     *
     * @param departmentIds 部门 id 列表
     * @return 用户列表
     */
    public List<User> listByDepartments(List<String> departmentIds) {
        List<User> result = new ArrayList<>();
        List<String> existed = new ArrayList<>();
        if (FuncUtil.isNotEmpty(departmentIds)) {
            for (String departmentId : departmentIds) {
                for (User user : listByDepartment(departmentId)) {
                    if (FuncUtil.isNotEmpty(user.getOpenId()) && !existed.contains(user.getOpenId())) {
                        existed.add(user.getOpenId());
                        result.add(user);
                    }
                }
            }
        }
        return result;
    }
}
