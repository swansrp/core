package com.bidr.insight.chatbi.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.insight.chatbi.dao.entity.ChatBiTableDesc;
import com.bidr.insight.chatbi.dao.mapper.ChatBiTableDescMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: ChatBiTableDescService
 * Description: 智能问数看板描述 Repository Service——portalName 与描述的映射维护
 *（table_code 列存 portalName，即 sys_portal.name）
 *
 * @author Sharp
 * @since 2026/8/15
 */
@Service
public class ChatBiTableDescService extends BaseSqlRepo<ChatBiTableDescMapper, ChatBiTableDesc> {

    /**
     * 全量 tableCode → 描述映射（看板数量有限，一次取回）
     */
    public Map<String, String> getDescriptionMap() {
        List<ChatBiTableDesc> descs = super.select(getQueryWrapper());
        Map<String, String> map = new HashMap<>();
        for (ChatBiTableDesc desc : descs) {
            if (StringUtils.hasText(desc.getDescription())) {
                map.put(desc.getTableCode(), desc.getDescription().trim());
            }
        }
        return map;
    }

    /**
     * 按描述覆盖保存（无则插入，有则更新）；描述为 null 即注销——删除该看板的注册行
     *（不能用 updateById 置 null：MyBatis-Plus 默认策略跳过 null 字段，描述清不掉）
     */
    public void saveDescription(String tableCode, String description) {
        LambdaQueryWrapper<ChatBiTableDesc> wrapper = getQueryWrapper();
        wrapper.eq(ChatBiTableDesc::getTableCode, tableCode);
        ChatBiTableDesc existing = super.selectOne(wrapper);
        if (description == null) {
            if (existing != null) {
                super.delete(wrapper);
            }
            return;
        }
        if (existing == null) {
            ChatBiTableDesc desc = new ChatBiTableDesc();
            desc.setTableCode(tableCode);
            desc.setDescription(description);
            super.insert(desc);
        } else {
            existing.setDescription(description);
            super.updateById(existing);
        }
    }
}
