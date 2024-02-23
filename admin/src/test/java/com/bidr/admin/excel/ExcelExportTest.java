package com.bidr.admin.excel;

import com.alibaba.excel.EasyExcel;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.service.PortalService;
import com.bidr.admin.vo.PortalReq;
import com.bidr.admin.vo.PortalWithColumnsRes;
import com.bidr.kernel.test.BaseTest;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.platform.MiniApplication;
import com.bidr.platform.bo.excel.ExcelExportBO;
import com.bidr.platform.config.excel.jxls.JxlsGroupRowCommand;
import com.bidr.platform.dao.entity.SysConfig;
import com.bidr.platform.dao.repository.SysConfigService;
import org.jxls.area.Area;
import org.jxls.builder.AreaBuilder;
import org.jxls.builder.xls.XlsCommentAreaBuilder;
import org.jxls.common.CellRef;
import org.jxls.common.Context;
import org.jxls.transform.Transformer;
import org.jxls.util.JxlsHelper;
import org.jxls.util.TransformerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.testng.annotations.Test;

import javax.annotation.Resource;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
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

    public static void main(String[] args) throws IOException {
        List<ClassRoom> data = buildData();
        try (InputStream is = new ClassPathResource("excel/excelGroupTestTemplate.xlsx").getInputStream()) {
            try (OutputStream os = Files.newOutputStream(Paths.get("d:/excelGroupTest.xlsx"))) {
                Transformer transformer = TransformerFactory.createTransformer(is, os);
                AreaBuilder areaBuilder = new XlsCommentAreaBuilder(transformer);
                XlsCommentAreaBuilder.addCommandMapping("groupRow", JxlsGroupRowCommand.class);
                List<Area> xlsAreaList = areaBuilder.build();
                Area xlsArea = xlsAreaList.get(0);
                Context context = new Context();
                context.putVar("data", data);
                xlsArea.applyAt(new CellRef("Sheet1!A1"), context);
                transformer.write();
            }
        }
    }

    private static List<ClassRoom> buildData() {
        List<ClassRoom> rooms = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ClassRoom room = new ClassRoom("C " + (i + 1));
            for (int j = 0; j < 3; j++) {
                Teacher teacher = new Teacher("C " + (i + 1) + "-T-" + (j + 1));
                for (int k = 0; k < 3; k++) {
                    Student student = new Student("C " + (i + 1) + "-T-" + (j + 1) + "-S-" + (k + 1));
                    teacher.getStudentList().add(student);
                }
                room.getTeacherList().add(teacher);
            }
            rooms.add(room);
        }
        return rooms;
    }

    public void test() throws ParseException, IOException {
        PortalReq req = new PortalReq();
        req.setName("sysConfig");
        PortalWithColumnsRes portal = portalService.getPortalWithColumnsConfig(req);
        ExcelExportBO vo = new ExcelExportBO();
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


}
