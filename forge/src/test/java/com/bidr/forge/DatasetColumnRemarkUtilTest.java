package com.bidr.forge;

import com.bidr.forge.utils.DatasetColumnRemarkUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

public class DatasetColumnRemarkUtilTest {

    @Test
    public void testParseLineCommentByAlias() {
        String sql = "SELECT  \n" +
                "  bti.id AS id, -- 主键id\n" +
                "  bti.user_name AS userName,-- 姓名\n" +
                "  bti.age AS age, -- 年龄\n" +
                "  bti1.user_no AS userNo -- 工号\n" +
                "FROM biz_test_info bti LEFT JOIN biz_test_info1 bti1 ON bti.id = bti1.people_test_id";

        Map<String, String> m = DatasetColumnRemarkUtil.parseSelectColumnRemarks(sql);
        System.out.println(m);
    }

    @Test
    public void testParseCommaThenCommentSameLine() {
        // 你反馈的写法：注释都在逗号后、并且后续字段可能继续在同一行
        String sql = "SELECT " +
                " bti.id AS id, --id " +
                " bti.user_name AS userName, --姓名 " +
                " bti.age AS age, --年龄 " +
                " bti1.user_no AS userNo --工号 " +
                "FROM biz_test_info bti LEFT JOIN biz_test_info1 bti1 ON bti.id = bti1.people_test_id";

        Map<String, String> m = DatasetColumnRemarkUtil.parseSelectColumnRemarks(sql);
        System.out.println(m);
    }

    @Test
    public void testLastColumnInlineCommentBindsToItself() {
        String sql = "SELECT " +
                " bti.id AS id, " +
                " bti.user_name AS userName, " +
                " bti.age AS age, " +
                // 你反馈的写法：最后一个字段后面直接跟 -- 注释
                " bti1.user_no AS userNo--年龄 " +
                "FROM biz_test_info bti LEFT JOIN biz_test_info1 bti1 ON bti.id = bti1.people_test_id";

        Map<String, String> m = DatasetColumnRemarkUtil.parseSelectColumnRemarks(sql);
        System.out.println(m);
    }
}

