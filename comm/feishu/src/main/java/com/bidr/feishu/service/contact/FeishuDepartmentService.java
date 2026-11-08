package com.bidr.feishu.service.contact;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.FuncUtil;
import com.lark.oapi.Client;
import com.lark.oapi.service.contact.v3.model.Department;
import com.lark.oapi.service.contact.v3.model.ListDepartmentReq;
import com.lark.oapi.service.contact.v3.model.ListDepartmentResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Title: FeishuDepartmentService
 * Description: 飞书通讯录 - 部门拉取服务，递归拉取全部部门（含 page_token 分页）
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/01/01 00:00
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "feishu", name = "app-id")
public class FeishuDepartmentService {

    /**
     * 飞书根部门 id
     */
    public static final String ROOT_DEPT_ID = "0";

    private static final int PAGE_SIZE = 50;

    @Resource
    private Client feishuClient;

    /**
     * 拉取指定父部门下的全部子部门（含所有层级）
     *
     * @param parentDeptId 父部门 id，根部门传 "0"
     * @return 部门列表
     */
    public List<Department> listAll(String parentDeptId) {
        List<Department> result = new ArrayList<>();
        String pageToken = null;
        boolean hasMore = true;
        while (hasMore) {
            ListDepartmentReq.Builder builder = ListDepartmentReq.newBuilder()
                    .parentDepartmentId(parentDeptId)
                    .fetchChild(true)
                    .pageSize(PAGE_SIZE)
                    .departmentIdType("open_department_id");
            if (FuncUtil.isNotEmpty(pageToken)) {
                builder.pageToken(pageToken);
            }
            ListDepartmentResp resp;
            try {
                resp = feishuClient.contact().v3().department().list(builder.build());
            } catch (Exception e) {
                throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "拉取飞书部门失败: " + e.getMessage());
            }
            if (!resp.success()) {
                log.error("拉取飞书部门失败, code: {}, msg: {}", resp.getCode(), resp.getMsg());
                throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "拉取飞书部门失败: " + resp.getMsg());
            }
            if (resp.getData() != null && FuncUtil.isNotEmpty(resp.getData().getItems())) {
                for (Department item : resp.getData().getItems()) {
                    result.add(item);
                }
            }
            hasMore = resp.getData() != null && Boolean.TRUE.equals(resp.getData().getHasMore());
            pageToken = resp.getData() == null ? null : resp.getData().getPageToken();
        }
        return result;
    }
}
