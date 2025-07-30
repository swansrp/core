package com.bidr.mcp.constant.dict;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Title: McpTypeDict
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/7/30 21:30
 */

@RequiredArgsConstructor
@Getter
@MetaDict(value = "MCP_TYPE_DICT", remark = "MCP工具类型字典")
public enum McpTypeDict implements Dict {
    /**
     * MCP工具类型字典
     */
    TOOL("0", "工具"), RESOURCE("1", "资源"), PROMPT("2", "提示词"),
    ;

    private final String value;
    private final String label;
}