package com.bidr.authorization.controller.group;

import com.bidr.authorization.dao.entity.AcGroupBind;
import com.bidr.authorization.dao.repository.AcGroupBindService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户组通用绑定关系控制器
 * <p>
 * 通过 bindType（query 参数）区分不同绑定场景，一套接口覆盖所有
 * 「用户组 + 字典项/业务id + 可选属性」的绑定需求。
 * <p>
 * 与字典完全解耦：后端只存取 groupId + bindType + attachValue + extraData(JSON)，
 * 字典项 label 的翻译由前端用本地字典补全。
 * <p>
 * Controller 仅负责输入校验、调用 Service、输出格式化；事务与业务逻辑下沉到
 * {@link AcGroupBindService}（因 Resp.notice 本质是抛异常返回，Controller 上的
 * @Transactional 无法保证事务正常提交回滚）。
 *
 * @author sharp
 * @since 2026/07/18
 */
@Api(tags = "用户组 - 通用绑定管理")
@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/web/group/bind")
public class GroupBindController {

    private final AcGroupBindService acGroupBindService;

    /**
     * 获取已绑定列表
     * <p>
     * 返回值为扁平结构 [{value, ...extraData 展开字段}]，
     * 前端可据此直接访问 readOnly 等属性，label 由前端用本地字典匹配补全。
     *
     * @param entityId 用户组id
     * @param bindType 绑定类型
     */
    @ApiOperation(value = "获取已绑定(列表)")
    @RequestMapping(value = "/bind/list", method = RequestMethod.GET)
    public List<Map<String, Object>> getBindList(
            @RequestParam @NotNull(message = "未提供实体id") Object entityId,
            @RequestParam @NotBlank(message = "未提供绑定类型") String bindType) {
        List<AcGroupBind> bindList = acGroupBindService.listBind(entityId, bindType);
        if (FuncUtil.isEmpty(bindList)) {
            return new ArrayList<>();
        }
        return bindList.stream()
                .map(this::flatten)
                .collect(Collectors.toList());
    }

    /**
     * 查看单条绑定信息
     *
     * @param entityId 用户组id
     * @param attachId 绑定目标值
     * @param bindType 绑定类型
     */
    @ApiOperation(value = "查看绑定信息")
    @RequestMapping(value = "/bind/info", method = RequestMethod.GET)
    public Map<String, Object> getBindInfo(
            @RequestParam @NotNull(message = "未提供实体id") Object entityId,
            @RequestParam @NotNull(message = "未提供绑定目标") Object attachId,
            @RequestParam @NotBlank(message = "未提供绑定类型") String bindType) {
        AcGroupBind bind = acGroupBindService.getOneBind(entityId, attachId, bindType);
        if (bind == null) {
            return null;
        }
        return flatten(bind);
    }

    /**
     * 替换绑定（按 groupId + bindType 维度做全量替换）
     * <p>
     * 以 (groupId, bindType) 为作用域：新列表中存在但旧库没有的 → 新增；
     * 旧库存在但新列表没有的 → 删除；交集保持不变（extraData 不变）。
     *
     * @param entityId     用户组id
     * @param bindType     绑定类型
     * @param attachValues 绑定目标值列表
     */
    @ApiOperation(value = "替换绑定")
    @RequestMapping(value = "/replace", method = RequestMethod.POST)
    public void replace(
            @RequestParam @NotNull(message = "未提供实体id") Object entityId,
            @RequestParam @NotBlank(message = "未提供绑定类型") String bindType,
            @RequestBody(required = false) List<Object> attachValues) {
        acGroupBindService.replace(entityId, bindType, attachValues);
        Resp.notice("替换成功");
    }

    /**
     * 修改绑定信息（更新 extraData）
     * <p>
     * 若绑定关系不存在，会先创建（upsert 语义）。
     * data 中的字段会整体覆盖 extra_data（非合并），以避免历史脏字段残留。
     *
     * @param entityId 用户组id
     * @param attachId 绑定目标值
     * @param bindType 绑定类型
     * @param data     绑定属性对象（将序列化为 JSON 存入 extra_data）
     */
    @ApiOperation(value = "修改绑定信息")
    @RequestMapping(value = "/bind/info", method = RequestMethod.POST)
    public void updateBindInfo(
            @RequestParam @NotNull(message = "未提供实体id") Object entityId,
            @RequestParam @NotNull(message = "未提供绑定目标") Object attachId,
            @RequestParam @NotBlank(message = "未提供绑定类型") String bindType,
            @RequestBody(required = false) Object data) {
        String extraJson = data == null ? null : JsonUtil.toJson(data);
        acGroupBindService.upsertBindInfo(entityId, attachId, bindType, extraJson);
        Resp.notice("修改信息成功");
    }

    /**
     * 查询当前登录用户在指定 groupType + bindType 下的绑定 attachValue 列表
     * <p>
     * 按用户的数据权限范围（dataScope）递归展开：
     * <ul>
     *   <li>ALL：该 groupType 下所有组的绑定</li>
     *   <li>SUBORDINATE：本组 + 递归子组</li>
     *   <li>其他：仅本组</li>
     * </ul>
     * 结果去重。用户取当前登录用户（AccountContext），不允许查别人。
     *
     * @param groupType 用户组类型（ac_group.type）
     * @param bindType  绑定类型（ac_group_bind.bind_type）
     * @return 去重后的 attachValue 列表
     */
    @ApiOperation(value = "查询当前用户权限范围内的绑定值")
    @RequestMapping(value = "/my", method = RequestMethod.GET)
    public List<String> getMyBindValues(
            @RequestParam @NotBlank(message = "未提供用户组类型") String groupType,
            @RequestParam @NotBlank(message = "未提供绑定类型") String bindType) {
        Long userId = AccountContext.getUserId();
        return acGroupBindService.listAttachValuesByDataScope(userId, groupType, bindType);
    }

// ==================== 内部方法（仅输出格式化，不涉及数据访问） ====================

    /**
     * 将绑定记录扁平化为前端友好的结构：
     * {value: attachValue, ...extraData 解析后的字段}
     * <p>
     * extraData 解析失败时只返回 value，不抛异常。
     */
    private Map<String, Object> flatten(AcGroupBind bind) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("value", bind.getAttachValue());
        if (FuncUtil.isNotEmpty(bind.getExtraData())) {
            try {
                Map<String, Object> extra = JsonUtil.readJson(bind.getExtraData(), Map.class, String.class, Object.class);
                if (extra != null) {
                    result.putAll(extra);
                }
            } catch (Exception ignored) {
                // extraData 解析失败，仅返回 value
            }
        }
        return result;
    }
}
