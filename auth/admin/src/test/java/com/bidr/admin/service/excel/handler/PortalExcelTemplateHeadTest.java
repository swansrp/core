package com.bidr.admin.service.excel.handler;

import com.alibaba.excel.EasyExcel;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.vo.PortalWithColumnsRes;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Title: PortalExcelTemplateHeadTest
 * Description: portal模版导出表头回归测试
 * <p>debug背景：2026-09-04 用户反馈模版导出的excel没有表头无法使用。
 * 根因：buildExcelHead只收录vo上带{@code @ExcelProperty}注解的字段，而portal链路的vo均无该注解，
 * 导致表头恒为空列表；同时导入新增按vo字段声明顺序映射列，与模版列序不一致会错位。
 * 修复：表头以sys_portal_column.display_name配置为准，导入新增改为按配置列序解析。</p>
 *
 * @author Sharp
 * @since 2026/09/04 16:40
 */
@Test
public class PortalExcelTemplateHeadTest {

    /**
     * 表头应与portal配置列的显示名一致（修复前vo无@ExcelProperty注解时表头为空）
     */
    public void buildHeadFromPortalConfig() {
        PortalWithColumnsRes portal = buildPortal(column("userName", "用户账号"), column("name", "用户姓名"),
                column("deptName", "部门名称"));
        List<List<String>> head = new PortalExcelHandlerInf() {
        }.buildExcelHead(portal, Object.class);
        assertEquals(head.size(), 3, "表头列数应与portal配置列数一致");
        assertEquals(head.get(0), Arrays.asList("用户账号"));
        assertEquals(head.get(1), Arrays.asList("用户姓名"));
        assertEquals(head.get(2), Arrays.asList("部门名称"));
    }

    /**
     * 配置显示名为空时应兜底用属性名，保证该列仍有表头
     */
    public void buildHeadFallbackToProperty() {
        PortalWithColumnsRes portal = buildPortal(column("remark", null), column("name", "用户姓名"));
        List<List<String>> head = new PortalExcelHandlerInf() {
        }.buildExcelHead(portal, Object.class);
        assertEquals(head.get(0), Arrays.asList("remark"));
        assertEquals(head.get(1), Arrays.asList("用户姓名"));
    }

    /**
     * 端到端：templateExcel写出后读回，第一行必须是配置的表头（修复前导出的excel没有表头）
     */
    public void templateExcelWriteHeadRow() {
        PortalWithColumnsRes portal = buildPortal(column("userName", "用户账号"), column("name", "用户姓名"));
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        new PortalExcelHandlerInf() {
        }.templateExcel(os, portal, Object.class);
        List<Map<Integer, String>> rows = EasyExcel.read(new ByteArrayInputStream(os.toByteArray())).sheet()
                .headRowNumber(0).doReadSync();
        assertTrue(rows.size() >= 1, "模版应至少有表头一行");
        assertEquals(rows.get(0).get(0), "用户账号", "excel首行首列应为配置的表头");
        assertEquals(rows.get(0).get(1), "用户姓名", "excel首行次列应为配置的表头");
    }

    private PortalWithColumnsRes buildPortal(SysPortalColumn... columns) {
        PortalWithColumnsRes portal = new PortalWithColumnsRes();
        portal.setDisplayName("测试模版");
        portal.setColumns(Arrays.asList(columns));
        return portal;
    }

    private SysPortalColumn column(String property, String displayName) {
        SysPortalColumn column = new SysPortalColumn();
        column.setProperty(property);
        column.setDisplayName(displayName);
        return column;
    }
}
