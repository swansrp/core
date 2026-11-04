package com.bidr.kernel.constant.param;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: ParamSync
 * Description: 同步事件参数（T3.3a）。
 * <p>
 * 保留天数走参数体系而不是常量：每个宿主对端的最长停机时间不同，对端停摆超过保留期就会丢增量，
 * 这个数只能由运维按现场放大，不能替它钉死在代码里。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Getter
@AllArgsConstructor
@MetaParam("同步事件")
public enum ParamSync implements Param {
    /**
     * 同步事件保留天数
     */
    OUTBOX_RETENTION_DAYS("同步事件保留天数", "7",
            "【按需调整】sync_outbox 事件保留多少天，默认 7 天；外部系统停摆超过这个天数会丢增量，请按对端最长停机时间放大");

    private final String title;
    private final String defaultValue;
    private final String remark;
}