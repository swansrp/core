package com.bidr.llm.team;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Title: TeamDefinitionStoreTest
 * Description: 团队定义 InMemory 存储用例（T4.2 团队编排门面）：存取全字段回读 /
 * teamKey 幂等覆盖 / 清单按 teamKey 升序稳定 / 删除幂等（不存在静默跳过）/ 空键拒绝。
 *
 * @author Sharp
 * @since 2026/11/1
 */
public class TeamDefinitionStoreTest {

    private final InMemoryTeamDefinitionStore store = new InMemoryTeamDefinitionStore();

    private TeamDefinition definition(String teamKey, String name, TeamMode mode, String... memberKeys) {
        TeamDefinition definition = new TeamDefinition();
        definition.setTeamKey(teamKey);
        definition.setName(name);
        definition.setMode(mode);
        List<TeamMember> members = new ArrayList<>();
        for (String key : memberKeys) {
            TeamMember member = new TeamMember();
            member.setKey(key);
            member.setName("成员-" + key);
            member.setPrompt("人设-" + key);
            members.add(member);
        }
        definition.setMembers(members);
        definition.setModerator("主持提示词");
        return definition;
    }

    /**
     * 存取回读：模式/成员/主持提示词全字段保真
     */
    @Test
    public void saveAndLoadRoundtripPreservesFields() {
        TeamDefinition definition = definition("review", "评审团", TeamMode.DEBATE, "a", "b");
        store.save(definition);

        TeamDefinition loaded = store.load("review");
        Assert.assertNotNull(loaded);
        Assert.assertEquals("review", loaded.getTeamKey());
        Assert.assertEquals("评审团", loaded.getName());
        Assert.assertEquals(TeamMode.DEBATE, loaded.getMode());
        Assert.assertEquals(2, loaded.getMembers().size());
        Assert.assertEquals("a", loaded.getMembers().get(0).getKey());
        Assert.assertEquals("人设-b", loaded.getMembers().get(1).getPrompt());
        Assert.assertEquals("主持提示词", loaded.getModerator());
        Assert.assertNull(store.load("absent"));
    }

    /**
     * 同 teamKey 再存 = 覆盖（幂等），不清单膨胀
     */
    @Test
    public void saveSameKeyOverwrites() {
        store.save(definition("t1", "一稿", TeamMode.PIPELINE, "a"));
        store.save(definition("t1", "二稿", TeamMode.ROUTER, "a", "b"));

        Assert.assertEquals(1, store.list().size());
        TeamDefinition loaded = store.load("t1");
        Assert.assertEquals("二稿", loaded.getName());
        Assert.assertEquals(TeamMode.ROUTER, loaded.getMode());
        Assert.assertEquals(2, loaded.getMembers().size());
    }

    /**
     * 清单按 teamKey 升序稳定（列表展示顺序可预期）
     */
    @Test
    public void listIsSortedByTeamKey() {
        store.save(definition("router-team", "乙", TeamMode.ROUTER, "a"));
        store.save(definition("alpha-team", "甲", TeamMode.PIPELINE, "a"));
        store.save(definition("zen-team", "丙", TeamMode.DEBATE, "a"));

        Assert.assertEquals(Arrays.asList("alpha-team", "router-team", "zen-team"),
                storeTeamKeys());
    }

    /**
     * 删除：删后读不到、清单收缩；不存在幂等跳过不抛
     */
    @Test
    public void deleteRemovesAndIsIdempotent() {
        store.save(definition("t1", "一", TeamMode.PIPELINE, "a"));
        store.save(definition("t2", "二", TeamMode.PIPELINE, "a"));

        store.delete("t1");
        Assert.assertNull(store.load("t1"));
        Assert.assertEquals(1, store.list().size());

        store.delete("absent");
        store.delete(null);
        Assert.assertEquals(1, store.list().size());
    }

    /**
     * 防线：空键/空对象拒绝保存
     */
    @Test
    public void rejectsBlankTeamKey() {
        try {
            store.save(definition("  ", "空白键", TeamMode.PIPELINE, "a"));
            Assert.fail("空白 teamKey 应拒绝保存");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("团队标识不能为空"));
        }
        try {
            store.save(null);
            Assert.fail("空对象应拒绝保存");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("团队标识不能为空"));
        }
    }

    private List<String> storeTeamKeys() {
        List<String> keys = new ArrayList<>();
        for (TeamDefinition definition : store.list()) {
            keys.add(definition.getTeamKey());
        }
        return keys;
    }
}
