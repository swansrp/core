package com.bidr.authorization.annotation.auth;

import com.bidr.authorization.constants.token.TokenItem;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: AuthLoginTenantIdsParseTest
 * Description: TENANT_IDS 解析三态离线单测（纯 JUnit4，不起 Spring）——
 * {@link AuthLogin#parseTenantIds(Map)} 是登录解析唯一收口，本测试钉死其口径：
 * 有值（Integer/Long/String 元素与 JSON 串形）→ 正确转 List&lt;Long&gt;；
 * 缺键/null map/dev 冒烟 map → null 旁路不抛；空列表 → 空列表不抛。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-17
 */
public class AuthLoginTenantIdsParseTest {

    private static Map<String, Object> mapWith(Object tenantIdsValue) {
        Map<String, Object> map = new HashMap<>();
        map.put(TokenItem.TENANT_IDS.name(), tenantIdsValue);
        return map;
    }

    /** 态 1（有值·Integer）：JSON 回读的 List 实形元素常为 Integer，须逐项转 Long */
    @Test
    public void parsesIntegerListToLongList() {
        List<Long> parsed = AuthLogin.parseTenantIds(mapWith(Arrays.asList(1, 2, 3)));
        Assert.assertEquals(Arrays.asList(1L, 2L, 3L), parsed);
    }

    /** 态 1b（有值·Long）：签发侧直接塞 List&lt;Long&gt; 的内存形态 */
    @Test
    public void parsesLongList() {
        List<Long> ids = Arrays.asList(7L, 8L);
        Assert.assertEquals(ids, AuthLogin.parseTenantIds(mapWith(ids)));
    }

    /** 态 1c（有值·String 元素）：数字字符串元素转 Long，脏元素跳过不抛 */
    @Test
    public void parsesStringElementsAndSkipsDirtyOnes() {
        List<Object> mixed = new ArrayList<>();
        mixed.add("9");
        mixed.add(" 10 ");
        mixed.add("not-a-number");
        mixed.add(null);
        Assert.assertEquals(Arrays.asList(9L, 10L), AuthLogin.parseTenantIds(mapWith(mixed)));
    }

    /** 态 1d（有值·JSON 串形）：redis hash 回读 List 可能是 "[4,5]" 字符串形态 */
    @Test
    public void parsesJsonStringForm() {
        Assert.assertEquals(Arrays.asList(4L, 5L), AuthLogin.parseTenantIds(mapWith("[4,5]")));
    }

    /** 态 2（缺键）：老项目/老 token/dev 冒烟 map 无此键 ⇒ null 旁路，不得抛 */
    @Test
    public void missingKeyYieldsNullBypass() {
        Assert.assertNull(AuthLogin.parseTenantIds(new HashMap<String, Object>()));
    }

    /** 态 2b（null map / null 值）：同样旁路不抛 */
    @Test
    public void nullMapAndNullValueYieldNull() {
        Assert.assertNull(AuthLogin.parseTenantIds(null));
        Assert.assertNull(AuthLogin.parseTenantIds(mapWith(null)));
    }

    /** 态 2c（坏串形）：非 JSON 的脏字符串按旁路处理，不得抛穿登录链 */
    @Test
    public void malformedStringYieldsNullWithoutThrowing() {
        Assert.assertNull(AuthLogin.parseTenantIds(mapWith("definitely-not-json")));
    }

    /** 态 3（空列表）：set 了空档 ⇒ 返回空列表；闸侧按用户终裁与 null 同旁路（全量可见），
     *  解析层仍如实保留两种形态，不在此处折叠 */
    @Test
    public void emptyListYieldsEmptyList() {
        List<Long> parsed = AuthLogin.parseTenantIds(mapWith(Collections.emptyList()));
        Assert.assertNotNull("解析层不得把空列表态吞成 null（形态保真，闸侧口径见 TenantContext）", parsed);
        Assert.assertTrue(parsed.isEmpty());
    }

    /** 负对照（针自证纪律的纯函数版）：若把键名写错（模拟漂移），解析必须拿不到值——
     *  证明本测试真的在读 TENANT_IDS 这个键，而非恒取到别处 */
    @Test
    public void wrongKeyDoesNotParseTenantData() {
        Map<String, Object> map = new HashMap<>();
        map.put("TENANT_IDS_DRIFTED", Arrays.asList(1, 2));
        Assert.assertNull(AuthLogin.parseTenantIds(map));
        Assert.assertNotNull(AuthLogin.parseTenantIds(mapWith(Arrays.asList(1, 2))));
    }
}
