package com.bidr.platform.redis.controller;

import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.redis.cache.RedisCacheConfig;
import com.bidr.platform.redis.vo.RedisKeyInfoVO;
import com.bidr.platform.redis.vo.RedisKeyTreeNodeVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Title: RedisAdminController
 * Description: Redis 管理后台接口
 *
 * @author Sharp
 * @since 2026/07/14
 */
@Slf4j
@Api(tags = "系统管理 - Redis缓存管理")
@RestController("RedisAdminController")
@RequestMapping(value = "/web/admin/redis")
@RequiredArgsConstructor
public class RedisAdminController {

    private static final long SCAN_COUNT = 1000L;
    private static final int MAX_KEY_LIMIT = 10000;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取项目前缀
     */
    private String getPrefix() {
        return RedisCacheConfig.getKey("");
    }

    /**
     * 查询 Redis Key 列表（带TTL、类型、大小）
     *
     * @param pattern    匹配模式（为空则查全部项目key）
     * @param currentPage 当前页
     * @param pageSize    每页大小
     * @return 分页 Key 信息列表
     */
    @ApiOperation("查询Redis Key列表")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public Map<String, Object> list(
            @ApiParam("匹配模式") @RequestParam(required = false, defaultValue = "") String pattern,
            @ApiParam("精确前缀") @RequestParam(required = false) String keyPrefix,
            @ApiParam("当前页") @RequestParam(required = false, defaultValue = "1") long currentPage,
            @ApiParam("每页大小") @RequestParam(required = false, defaultValue = "50") long pageSize) {

        String prefix = getPrefix();
        String matchPattern;
        if (StringUtils.isNotBlank(keyPrefix)) {
            // 树节点点击：精确前缀匹配
            String fullPrefix = keyPrefix.startsWith(prefix) ? keyPrefix : prefix + keyPrefix;
            matchPattern = fullPrefix.endsWith(":") ? fullPrefix + "*" : fullPrefix + ":*";
        } else if (StringUtils.isNotBlank(pattern)) {
            matchPattern = prefix + "*" + pattern + "*";
        } else {
            matchPattern = prefix + "*";
        }

        ScanOptions scanOptions = ScanOptions.scanOptions().match(matchPattern).count(SCAN_COUNT).build();
        List<String> allKeys = new ArrayList<>();
        Cursor<String> cursor = redisTemplate.scan(scanOptions);
        while (cursor.hasNext()) {
            String key = cursor.next();
            allKeys.add(key);
            if (allKeys.size() >= MAX_KEY_LIMIT) {
                break;
            }
        }
        cursor.close();

        // 排序
        allKeys.sort(String::compareTo);

        long total = allKeys.size();
        long fromIndex = (currentPage - 1) * pageSize;
        long toIndex = Math.min(fromIndex + pageSize, total);

        List<String> pageKeys = fromIndex < total
                ? allKeys.subList((int) fromIndex, (int) toIndex)
                : Collections.emptyList();

        List<RedisKeyInfoVO> records = pageKeys.stream()
                .map(this::buildKeyInfo)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("current", currentPage);
        result.put("size", pageSize);
        return result;
    }

    /**
     * 获取单个 Key 的详细信息（含Value）
     */
    @ApiOperation("获取Key详情（含Value）")
    @RequestMapping(value = "/detail", method = RequestMethod.GET)
    public Map<String, Object> detail(@ApiParam("Key名称") @RequestParam String key) {
        Validator.assertNotBlank(key, ErrCodeSys.PA_PARAM_NULL, "key");
        // 安全检查：只能查看项目前缀下的key
        String prefix = getPrefix();
        String fullKey = key.startsWith(prefix) ? key : prefix + key;

        Map<String, Object> result = new LinkedHashMap<>();
        RedisKeyInfoVO info = buildKeyInfo(fullKey);
        result.put("key", info.getKey());
        result.put("type", info.getType());
        result.put("ttl", info.getTtl());
        result.put("size", info.getSize());

        DataType dataType = redisTemplate.type(fullKey);
        Object value = null;
        switch (dataType) {
            case STRING:
                value = redisTemplate.opsForValue().get(fullKey);
                break;
            case HASH:
                value = redisTemplate.opsForHash().entries(fullKey);
                break;
            case LIST:
                value = redisTemplate.opsForList().range(fullKey, 0, -1);
                break;
            case SET:
                value = redisTemplate.opsForSet().members(fullKey);
                break;
            case ZSET:
                value = redisTemplate.opsForZSet().rangeWithScores(fullKey, 0, -1);
                break;
            default:
                value = "不支持查看的数据类型";
                break;
        }
        result.put("value", value);
        return result;
    }

    /**
     * 删除单个 Key
     */
    @ApiOperation("删除单个Key")
    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public void delete(@RequestBody Map<String, String> body) {
        String key = body.get("key");
        Validator.assertNotBlank(key, ErrCodeSys.PA_PARAM_NULL, "key");
        String prefix = getPrefix();
        String fullKey = key.startsWith(prefix) ? key : prefix + key;
        redisTemplate.delete(fullKey);
        Resp.notice("删除成功");
    }

    /**
     * 批量删除 Key
     */
    @ApiOperation("批量删除Key")
    @RequestMapping(value = "/delete/list", method = RequestMethod.POST)
    public void deleteList(@RequestBody List<String> keys) {
        Validator.assertNotEmpty(keys, ErrCodeSys.PA_PARAM_NULL, "keys");
        String prefix = getPrefix();
        List<String> fullKeys = keys.stream()
                .map(k -> k.startsWith(prefix) ? k : prefix + k)
                .collect(Collectors.toList());
        Long count = redisTemplate.delete(fullKeys);
        Resp.notice("成功删除" + (count != null ? count : 0) + "个Key");
    }

    /**
     * 修改 Key 的过期时间
     */
    @ApiOperation("修改Key过期时间")
    @RequestMapping(value = "/expire", method = RequestMethod.POST)
    public void expire(@RequestBody Map<String, Object> body) {
        String key = (String) body.get("key");
        Validator.assertNotBlank(key, ErrCodeSys.PA_PARAM_NULL, "key");
        Object ttlObj = body.get("ttl");
        Validator.assertNotNull(ttlObj, ErrCodeSys.PA_PARAM_NULL, "ttl");
        long ttl = Long.parseLong(ttlObj.toString());

        String prefix = getPrefix();
        String fullKey = key.startsWith(prefix) ? key : prefix + key;

        if (ttl < 0) {
            // 永不过期
            redisTemplate.persist(fullKey);
            Resp.notice("已设置为永不过期");
        } else {
            redisTemplate.expire(fullKey, ttl, java.util.concurrent.TimeUnit.SECONDS);
            Resp.notice("过期时间已更新");
        }
    }

    /**
     * 获取 Key 树形结构（按 ":" 分隔）
     *
     * @return 树节点列表
     */
    @ApiOperation("获取Key树形结构")
    @RequestMapping(value = "/tree", method = RequestMethod.GET)
    public List<RedisKeyTreeNodeVO> tree() {
        String prefix = getPrefix();
        String matchPattern = prefix + "*";

        ScanOptions scanOptions = ScanOptions.scanOptions().match(matchPattern).count(SCAN_COUNT).build();
        List<String> allKeys = new ArrayList<>();
        Cursor<String> cursor = redisTemplate.scan(scanOptions);
        while (cursor.hasNext()) {
            allKeys.add(cursor.next());
            if (allKeys.size() >= MAX_KEY_LIMIT) {
                break;
            }
        }
        cursor.close();

        allKeys.sort(String::compareTo);
        return buildKeyTree(allKeys);
    }

    /**
     * 将扁平 Key 列表构建为树形结构
     */
    private List<RedisKeyTreeNodeVO> buildKeyTree(List<String> keys) {
        Map<String, TreeBuildNode> rootMap = new LinkedHashMap<>();

        for (String key : keys) {
            String[] parts = key.split(":");
            // 只构建文件夹层级，跳过最后一段（实际的Key名）
            int folderDepth = parts.length - 1;
            if (folderDepth <= 0) {
                continue;
            }
            Map<String, TreeBuildNode> currentMap = rootMap;
            StringBuilder path = new StringBuilder();

            for (int i = 0; i < folderDepth; i++) {
                String part = parts[i];
                if (path.length() > 0) {
                    path.append(":");
                }
                path.append(part);

                TreeBuildNode node = currentMap.computeIfAbsent(part,
                        k -> new TreeBuildNode(part, path.toString()));
                node.keyCount++;
                currentMap = node.children;
            }
        }

        return rootMap.values().stream()
                .map(this::convertTreeNode)
                .collect(Collectors.toList());
    }

    /**
     * 递归转换树构建节点为 VO
     */
    private RedisKeyTreeNodeVO convertTreeNode(TreeBuildNode build) {
        RedisKeyTreeNodeVO vo = new RedisKeyTreeNodeVO();
        vo.setTitle(build.label);
        vo.setKey(build.fullPath);
        vo.setKeyCount(build.keyCount);
        vo.setIsKey(build.isKey);
        if (!build.children.isEmpty()) {
            vo.setChildren(build.children.values().stream()
                    .map(this::convertTreeNode)
                    .collect(Collectors.toList()));
        }
        return vo;
    }

    /**
     * 树构建辅助内部类
     */
    private static class TreeBuildNode {
        final String label;
        final String fullPath;
        long keyCount = 0;
        boolean isKey = false;
        Map<String, TreeBuildNode> children = new LinkedHashMap<>();

        TreeBuildNode(String label, String fullPath) {
            this.label = label;
            this.fullPath = fullPath;
        }
    }

    /**
     * 构建Key信息
     */
    private RedisKeyInfoVO buildKeyInfo(String fullKey) {
        RedisKeyInfoVO vo = new RedisKeyInfoVO();
        vo.setKey(fullKey);
        DataType dataType = redisTemplate.type(fullKey);
        vo.setType(dataType.code());
        vo.setTtl(redisTemplate.getExpire(fullKey));
        vo.setSize(getKeySize(fullKey, dataType));
        return vo;
    }

    /**
     * 获取Key的大小
     */
    private Long getKeySize(String key, DataType dataType) {
        try {
            switch (dataType) {
                case STRING:
                    Object val = redisTemplate.opsForValue().get(key);
                    return val != null ? (long) val.toString().length() : 0L;
                case HASH:
                    return (long) redisTemplate.opsForHash().size(key);
                case LIST:
                    return redisTemplate.opsForList().size(key);
                case SET:
                    return redisTemplate.opsForSet().size(key);
                case ZSET:
                    return redisTemplate.opsForZSet().size(key);
                default:
                    return 0L;
            }
        } catch (Exception e) {
            log.warn("获取Key大小失败: {}", key, e);
            return 0L;
        }
    }
}
