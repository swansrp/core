package com.bidr.authorization.controller.openapi;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthPartner;
import com.bidr.authorization.vo.sync.SyncEventRes;
import com.bidr.kernel.mybatis.dao.entity.SyncOutbox;
import com.bidr.kernel.mybatis.dao.repository.SyncOutboxService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: SyncEventController
 * Description: 变更事件拉取端点（T3.3a）——{@code GET /open-api/v1/sync/events}，
 * {@code @Auth(AuthPartner)} 机器凭证鉴权（x-api-key: Base64(appKey:appSecret)）。
 * <p>
 * 类级挂 {@code AuthPartner}：这是系统级对接口，调用方是外部系统而不是某个登录用户。
 * 该身份本来就不带租户，所以端点<b>不做租户过滤</b>——每条事件自带 {@code tenantId}，
 * 由消费方按自己的授权面解释归属；在这里按租户裁剪反而会让系统级对端只看到一片。
 * </p>
 * <p>
 * 分页是 id 游标而非 offset：发件箱只追加，offset 在清理任务删掉老行之后会整体错位（漏事件），
 * 而 {@code id > cursor} 天然免疫已有行被删。{@code limit} 有上限是因为对端一次拉十万行
 * 会把本端连接和内存一起拖住；下限与缺省都收敛到默认值，让"没填/填错"都退化成可用行为。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Api(tags = "T3.3a open-api - 变更事件拉取")
@RestController
@RequestMapping(path = "/open-api/v1/sync")
@RequiredArgsConstructor
@Auth(AuthPartner.class)
public class SyncEventController {

    /**
     * 缺省与非法 limit 的收敛值（一次常见批量的折中）
     */
    private static final int DEFAULT_LIMIT = 500;

    /**
     * limit 上限：再大就把单次响应变成不可控的传输量
     */
    private static final int MAX_LIMIT = 1000;

    private final SyncOutboxService syncOutboxService;

    /**
     * 按 id 游标增量拉取变更事件（升序）。
     *
     * @param cursor 游标＝上次拉到的最后一个 id；不传/非正＝从头拉
     * @param limit  条数，默认 500、上限 1000、非正数收敛到默认值
     */
    @ApiOperation(value = "拉取变更事件", notes = "机器凭证鉴权（x-api-key: Base64(appKey:appSecret)）；"
            + "id 升序，把本次最后一条的 id 作为下次的 cursor 回传即可续拉。不按租户过滤，"
            + "每条的 tenantId 由消费方自行解释（空＝全局事件）")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "cursor", value = "游标＝上次拉到的最后一个 id，不传＝从头拉",
                    paramType = "query", dataType = "long"),
            @ApiImplicitParam(name = "limit", value = "本次最多返回条数：默认 500、上限 1000",
                    paramType = "query", dataType = "int")
    })
    @GetMapping(value = "/events")
    public List<SyncEventRes> events(@RequestParam(required = false) Long cursor,
                                     @RequestParam(required = false) Integer limit) {
        List<SyncOutbox> events = syncOutboxService.listAfter(cursor, resolveLimit(limit));
        List<SyncEventRes> res = new ArrayList<>(events.size());
        for (SyncOutbox event : events) {
            res.add(convert(event));
        }
        return res;
    }

    /**
     * limit 归一：没填与非法值一律退化成缺省，超过上限按上限截——上限必须是硬收敛，
     * 否则对端一个手误就能拉走全表。
     */
    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * 手写映射而非 BeanUtil：两边字段集刻意不同（{@code createAt} 不外发），
     * 抄写工具会把"不外发"这件事悄悄变成"外发一切同名属性"。
     */
    private SyncEventRes convert(SyncOutbox event) {
        SyncEventRes res = new SyncEventRes();
        res.setId(event.getId());
        res.setTenantId(event.getTenantId());
        res.setAggType(event.getAggType());
        res.setAggId(event.getAggId());
        res.setEventType(event.getEventType());
        res.setPayload(event.getPayload());
        res.setOccurredAt(event.getOccurredAt());
        return res;
    }
}