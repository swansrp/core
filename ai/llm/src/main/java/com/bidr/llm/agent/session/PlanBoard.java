package com.bidr.llm.agent.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: PlanBoard
 * Description: 计划待办板（llm 框架通用状态机，业务零绑定）——submit_plan 提交清单、
 * start_plan_item 标记执行中（新开自动回退旧执行中）、done_plan_item 逐条挑勾、
 * settle 终态收口（成功补挑勾 / 失败置 stopped）。载体无关：会话链挂
 * {@link AgentSessionContext}（状态快照 list 引用 + persistStages 回调），票据链等
 * 短生命周期编排挂各自持有 list 即可——状态真源是外部 list 引用（本类只 clear+add
 * 不换引用），终态收口直改 item 仍生效。含待办数组容错解析（LLM 偏离格式传
 * [{id,text}] 对象时取 text 字段，防 JSON 原文直接下发前端勾选清单）
 *
 * @author Sharp
 * @since 2026/8/23
 */
public class PlanBoard {

    /** 待办条数上限（防 LLM 拆分过细刷屏；各链工具描述中的 3-8/3-10 为建议区间） */
    public static final int PLAN_MAX_ITEMS = 12;

    private static final ObjectMapper OM = new ObjectMapper();

    /** 待办清单（外部持有的引用：会话状态快照或编排票据；所有操作 synchronized 于它） */
    private final List<AgentPlanItem> items;
    /** 状态变更回调（可空；锁外调用——会话侧挂快照持久化等重操作） */
    private final Runnable onChange;

    public PlanBoard(List<AgentPlanItem> items, Runnable onChange) {
        this.items = items;
        this.onChange = onChange;
    }

    /** 提交计划清单（覆盖旧清单，id 自 1 重排；在原 list 上 clear+add 保持引用不变） */
    public void submit(List<String> texts) {
        synchronized (items) {
            items.clear();
            int id = 1;
            for (String text : texts) {
                if (text != null && !text.trim().isEmpty()) {
                    items.add(new AgentPlanItem(id++, text.trim(), AgentPlanItem.PENDING, null));
                }
            }
        }
        fireChange();
    }

    /** 标记执行中（同一时刻至多一条：新开自动回退旧执行中为 pending；未命中返回 false） */
    public boolean start(int id) {
        boolean hit;
        synchronized (items) {
            hit = false;
            for (AgentPlanItem item : items) {
                if (item.getId() == id) {
                    item.setStatus(AgentPlanItem.RUNNING);
                    hit = true;
                } else if (AgentPlanItem.RUNNING.equals(item.getStatus())) {
                    item.setStatus(AgentPlanItem.PENDING);
                }
            }
        }
        if (hit) {
            fireChange();
        }
        return hit;
    }

    /** 待办挑勾（完成备注可空；未命中返回 false 不变更） */
    public boolean done(int id, String note) {
        synchronized (items) {
            for (AgentPlanItem item : items) {
                if (item.getId() == id) {
                    item.setStatus(AgentPlanItem.DONE);
                    item.setNote(note == null || note.trim().isEmpty() ? null : note.trim());
                    fireChange();
                    return true;
                }
            }
        }
        return false;
    }

    /** 终态收口：running 条目按结局置位（成功→done 补挑勾 / 失败→stopped），
     *  pending 保持原样如实反映未执行部分——清单不再永久卡在转圈态 */
    public void settle(boolean ok) {
        synchronized (items) {
            for (AgentPlanItem item : items) {
                if (AgentPlanItem.RUNNING.equals(item.getStatus())) {
                    item.setStatus(ok ? AgentPlanItem.DONE : AgentPlanItem.STOPPED);
                    if (ok && (item.getNote() == null || item.getNote().trim().isEmpty())) {
                        item.setNote("收口自动挑勾");
                    }
                }
            }
        }
        fireChange();
    }

    /** 清单快照（线程安全副本，轮询下发/序列化用） */
    public List<AgentPlanItem> items() {
        synchronized (items) {
            return new ArrayList<>(items);
        }
    }

    public boolean isEmpty() {
        synchronized (items) {
            return items.isEmpty();
        }
    }

    /** 计划快照文本（工具回显供 LLM 掌握编号与进度）：每行「id. [x]/[>]/[ ] 文本（备注）」 */
    public String planText() {
        synchronized (items) {
            if (items.isEmpty()) {
                return "（尚未提交计划）";
            }
            StringBuilder sb = new StringBuilder();
            for (AgentPlanItem item : items) {
                sb.append(item.getId()).append(". ")
                        .append(AgentPlanItem.DONE.equals(item.getStatus()) ? "[x] "
                                : AgentPlanItem.RUNNING.equals(item.getStatus()) ? "[>] " : "[ ] ")
                        .append(item.getText());
                if (item.getNote() != null && !item.getNote().isEmpty()) {
                    sb.append("（").append(item.getNote()).append("）");
                }
                sb.append('\n');
            }
            return sb.toString();
        }
    }

    /** 计划进度单行摘要（挑勾回显用，避免每轮回显全量清单刷上下文）：
     *  如「计划进度：3/9 完成，执行中 #4」；待办编号 LLM 自己刚提交过，无需回灌全文 */
    public String planBrief() {
        int done = 0;
        Integer running = null;
        int total;
        synchronized (items) {
            total = items.size();
            if (total == 0) {
                return "（尚未提交计划）";
            }
            for (AgentPlanItem item : items) {
                if (AgentPlanItem.DONE.equals(item.getStatus())) {
                    done++;
                } else if (AgentPlanItem.RUNNING.equals(item.getStatus())) {
                    running = item.getId();
                }
            }
        }
        StringBuilder sb = new StringBuilder("计划进度：").append(done).append('/').append(total).append(" 完成");
        if (running != null) {
            sb.append("，执行中 #").append(running);
        }
        return sb.toString();
    }

    /** 待办数组解析（submit_plan 工具入参）：合法数组返回条目清单，非法返回 null。
     *  对象元素容错：LLM 偏离格式传 [{id,text}] 时取 text/title/name 字段（值节点直取文本） */
    public static List<String> parseItems(String json) {
        List<String> texts = new ArrayList<>();
        try {
            JsonNode arr = OM.readTree(json == null || json.trim().isEmpty() ? "[]" : json);
            if (!arr.isArray()) {
                return null;
            }
            for (JsonNode n : arr) {
                String t;
                if (n.isTextual()) {
                    t = n.asText();
                } else if (n.isObject()) {
                    JsonNode tn = n.has("text") ? n.get("text") : n.has("title") ? n.get("title") : n.get("name");
                    t = tn == null || !tn.isTextual() ? null : tn.asText();
                } else if (n.isValueNode()) {
                    t = n.asText(null);
                } else {
                    t = null;
                }
                if (t != null && !t.trim().isEmpty()) {
                    texts.add(t.trim());
                }
            }
            return texts;
        } catch (Exception e) {
            return null;
        }
    }

    private void fireChange() {
        if (onChange != null) {
            onChange.run();
        }
    }
}
