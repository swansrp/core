package com.bidr.td;

import com.bidr.td.sync.TagSyncInterceptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TagSyncInterceptor 测试
 */
public class TagSyncInterceptorTest {

    @Test
    public void testInterceptorCreation() {
        TagSyncInterceptor interceptor = new TagSyncInterceptor();
        assertNotNull(interceptor);
    }
}
