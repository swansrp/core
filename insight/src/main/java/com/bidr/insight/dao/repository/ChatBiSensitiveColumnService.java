package com.bidr.insight.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.insight.dao.entity.ChatBiSensitiveColumn;
import com.bidr.insight.dao.mapper.ChatBiSensitiveColumnMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Title: ChatBiSensitiveColumnService
 * Description: 智能问数敏感列配置 Repository Service——按看板的敏感列行维护（整板覆盖保存）
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Service
public class ChatBiSensitiveColumnService extends BaseSqlRepo<ChatBiSensitiveColumnMapper, ChatBiSensitiveColumn> {

    /**
     * 按看板取全部敏感列配置行
     */
    public List<ChatBiSensitiveColumn> listByTableCode(String tableCode) {
        LambdaQueryWrapper<ChatBiSensitiveColumn> wrapper = getQueryWrapper();
        wrapper.eq(ChatBiSensitiveColumn::getTableCode, tableCode);
        return super.select(wrapper);
    }

    /**
     * 整板覆盖保存：先清该看板全部行再批量插入（columns 为空即清空该板敏感配置）
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceAll(String tableCode, List<ChatBiSensitiveColumn> columns) {
        LambdaQueryWrapper<ChatBiSensitiveColumn> wrapper = getQueryWrapper();
        wrapper.eq(ChatBiSensitiveColumn::getTableCode, tableCode);
        super.delete(wrapper);
        if (columns != null) {
            for (ChatBiSensitiveColumn column : columns) {
                super.insert(column);
            }
        }
    }
}
