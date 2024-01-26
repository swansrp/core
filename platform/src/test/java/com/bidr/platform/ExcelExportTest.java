package com.bidr.platform;

import com.alibaba.excel.EasyExcel;
import com.bidr.kernel.test.BaseTest;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.platform.dao.entity.SysConfig;
import com.bidr.platform.dao.entity.SysPortalColumn;
import com.bidr.platform.dao.repository.SysConfigService;
import com.bidr.platform.service.portal.PortalService;
import com.bidr.platform.vo.portal.PortalReq;
import com.bidr.platform.vo.portal.PortalWithColumnsRes;
import lombok.Data;
import org.jxls.common.Context;
import org.jxls.util.JxlsHelper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.testng.annotations.Test;

import javax.annotation.Resource;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Title: ExcelExportTest
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/25 11:04
 */
@Test
@SpringBootTest(classes = MiniApplication.class)
public class ExcelExportTest extends BaseTest {

    @Resource
    private PortalService portalService;
    @Resource
    private SysConfigService sysConfigService;

    public void test() throws ParseException, IOException {
        PortalReq req = new PortalReq();
        req.setName("sysConfig");
        PortalWithColumnsRes portal = portalService.getPortalWithColumnsConfig(req);
        ExcelExportVO vo = new ExcelExportVO();
        vo.setTitle(portal.getDisplayName());
        for (SysPortalColumn column : portal.getColumns()) {
            vo.getColumnTitles().add(column.getDisplayName());
        }
        List<SysConfig> sysConfigList = sysConfigService.list();
        for (SysConfig sysConfig : sysConfigList) {
            Map<String, Object> hashMap = ReflectionUtil.getHashMap(sysConfig);
            List<String> records = new ArrayList<>();
            for (SysPortalColumn column : portal.getColumns()) {
                records.add(StringUtil.parse(hashMap.get(column.getProperty())));
            }
            vo.getRecords().add(records);
        }
        export(vo);
    }

    private void export(Object data) throws IOException {
        try (InputStream is = new ClassPathResource("excel/portalExportTemplate.xlsx").getInputStream()) {
            try (OutputStream os = new FileOutputStream("target/formula_copy_output.xlsx")) {
                Context context = new Context();
                context.putVar("data", data);
                JxlsHelper jxlsHelper = JxlsHelper.getInstance();
                jxlsHelper.setUseFastFormulaProcessor(false);
                jxlsHelper.processTemplate(is, os, context);
            }
        }
    }

    private void exportByEasyExcel(Object data) throws IOException {
        try (InputStream is = new ClassPathResource("excel/portalExportTemplate.xlsx").getInputStream()) {
            try (OutputStream os = new FileOutputStream("target/formula_copy_output.xlsx")) {
                EasyExcel.write(os).withTemplate(is).sheet().doFill(data);
            }
        }
    }

    @Data
    public class ExcelExportVO {
        private String title;
        private List<String> columnTitles = new ArrayList<>();
        private List<List<String>> records = new ArrayList<>();
    }


}
