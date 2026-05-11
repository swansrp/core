package com.bidr.td;

import com.bidr.td.annotation.TdColumn;
import com.bidr.td.annotation.TdStable;
import com.bidr.td.annotation.TdTag;
import com.bidr.td.annotation.TdTimestamp;
import com.bidr.td.constant.TdDataType;
import com.bidr.td.vo.TdRangeReq;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BaseTdRepo 查询操作测试
 */
public class BaseTdRepoQueryTest {

    @Test
    public void testQueryRangeReqStructure() {
        TdRangeReq req = new TdRangeReq();
        req.setFrom(1000L);
        req.setTo(2000L);
        assertEquals(1000L, req.getFrom());
        assertEquals(2000L, req.getTo());
        assertNull(req.getAdvanced());
    }
}
