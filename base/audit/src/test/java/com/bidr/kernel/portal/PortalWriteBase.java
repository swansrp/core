package com.bidr.kernel.portal;

/**
 * Title: PortalWriteBase
 * Description: execution 腿测试夹具——声明在<b>非业务包</b>的框架基类形态（模拟
 * kernel {@code BaseAdminController}：方法声明在框架、宿主子类只继承）。
 * within 腿按方法声明类求值，本类不会被业务包 within 命中；只有
 * {@code execution(* ...PortalWriteBase+.*(..))} 腿能拦到继承写面。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author sharp (audit 框架增强 review：execution 腿零行为测试补齐 2026-10-25)
 */
public class PortalWriteBase {

    public String updatePortal(String req) {
        return "ok";
    }
}
