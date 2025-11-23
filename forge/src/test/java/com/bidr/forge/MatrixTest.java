package com.bidr.forge;

import com.bidr.forge.bo.MatrixColumns;
import com.bidr.forge.dao.repository.SysMatrixService;
import com.bidr.kernel.test.BaseTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.testng.annotations.Test;

import javax.annotation.Resource;

/**
 * Title: MatrixTest
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/11/22 20:30
 */
@Test
@SpringBootTest(classes = ForgeApplication.class)
public class MatrixTest extends BaseTest {
    @Resource
    private SysMatrixService sysMatrixService;

    public void testGetMatrixColumns() {
        MatrixColumns bizUserInfo = sysMatrixService.getMatrixColumns("biz_user_info");
        log(bizUserInfo);
    }
}