package com.bidr.agent.console.skill;

import com.bidr.kernel.utils.DictEnumUtil;
import com.bidr.oss.constant.dict.OssServiceTypeDict;
import com.bidr.oss.constant.param.OssParam;
import com.bidr.oss.service.ObjectStorageService;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Title: SystemSkillStore
 * Description: 系统级技能包的字节通道：key 规则 {@code agent-skill/{skill_name}/v{n}.zip}。
 *
 * <p><b>走框架通用门面、不认厂商</b>：底座可能是 MinIO、本机盘、阿里云或火山，选型正源＝sys_config 的
 * {@code OSS_SERVER_TYPE}（与框架 {@code /web/oss} 上传同一个值），本类只调
 * {@link ObjectStorageService} 的按对象名直写/直读 SPI。<b>每次现读</b>而不是启动时缓存：
 * 选型是运行期可改的系统参数，缓存就等于改了参数还得重启才生效。</p>
 *
 * <p><b>本类不含空间语义</b>：它只管"这串 key 的字节进出去"，不认识空间、不认识租户、
 * 也不认识某个技能该不该被谁读到——那些是消费方的判定。核心动机是框架侧的会话可能没有空间上下文，
 * 通道层一旦掺进空间参数，无空间的那条腿就接不上。</p>
 *
 * <p><b>key 前缀与业务 {@code skill/} 分开的原因</b>：两侧同桶不同主。同一个桶里
 * {@code skill/…} 归业务侧出厂器与上传链路维护、{@code agent-skill/…} 归框架侧对账器维护，
 * 前缀分开才能保证两侧的写入永远碰不到对方的对象——否则一个同名技能会互相覆盖，
 * 而对账器对此毫无感知（它只认自己算出的 sha）。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSkillStore {

    private final SysConfigCacheService sysConfigCacheService;

    /** 当前版本包的 key（历史版本同规则递增留档，可回滚） */
    public static String packageKey(String skillName, int version) {
        return "agent-skill/" + skillName + "/v" + version + ".zip";
    }

    public void put(String key, byte[] bytes) {
        storage().putBytes(key, bytes, "application/zip");
        log.info("系统技能包已写入: key={} bytes={}", key, bytes.length);
    }

    public byte[] get(String key) {
        return storage().readBytes(key);
    }

    /**
     * 包的可访问地址（现签，有效期与签名语义由存储实现决定）——
     * 中文保存名走框架统一的 content-disposition 编码
     */
    public String readUrl(String key, String fileName) {
        ObjectStorageService storage = storage();
        return storage.getReadUrl(storage.buildAccessUrl(key), fileName);
    }

    private ObjectStorageService storage() {
        String type = sysConfigCacheService.getParamValueAvail(OssParam.OSS_SERVER_TYPE);
        return DictEnumUtil.getEnumByValue(type, OssServiceTypeDict.class).getService();
    }
}
