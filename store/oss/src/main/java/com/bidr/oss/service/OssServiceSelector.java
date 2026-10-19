package com.bidr.oss.service;

import com.bidr.kernel.utils.DictEnumUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.oss.constant.dict.OssServiceTypeDict;
import com.bidr.oss.constant.param.OssParam;
import com.bidr.platform.dao.entity.SysConfig;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Title: OssServiceSelector
 * Description: 按运行参数选当前对象存储实现——系统参数 {@code OSS_SERVER_TYPE} → {@link BaseOssService}。
 * 把"读配置→查字典→取实现"的三步收成一处，业务侧按 key 直写/直读字节时一律经它取实现，
 * 不得再直连某一家 SDK 的模板件（否则换存储底座只改参数救不回直连的腿）。
 * 框架内其余需要选型的地方（{@code /web/oss} 上传、系统技能包字节通道）也一律走本类，
 * 取值链只有一份——两处各读各的，换现场时就会出现"一条腿按库、一条腿按 yml"的分叉。
 *
 * <p><b>取值链</b>：库值非空 → 用库值（运维在参数页表过态，以库为准，与历史行为一致）；
 * 库值为空或参数行不存在 → 用配置文件 {@code oss.server-type}；两头都没给 → 当场报错。
 * 空串是"未表态"而不是"选错"：种子默认值原本是 1（Minio），而 {@code @MetaParam} 只插行不改值，
 * 存量库行一旦带着种子值，配置文件那条腿在这台现场永远是死的；把种子改成空，
 * 新装现场只改 yml/环境变量就能定底座，运维真填了值仍旧压过 yml。</p>
 *
 * <p><b>不静默回落某一家</b>：两头都没给时报错而不是默认 Minio——底座猜错的代价是
 * 文件落到没人能读的地方且毫无痕迹，报错的代价只是一次配置补录。值非法（不在字典里）同样抛。</p>
 *
 * <p><b>每次现读</b>：选型是运行期可改的参数，缓存起来等于改了参数还要重启；
 * {@code SysConfigCacheService} 自带内存缓存与 TTL，这里不叠第二层缓存。</p>
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-15
 */
@Component
public class OssServiceSelector {

    @Resource
    private SysConfigCacheService sysConfigCacheService;

    /**
     * 配置文件兜底值。缺省空串＝这条腿也未表态，交给下面的报错；
     * 无容器的单测里字段为 null，判空一律按空白处理。
     */
    @Value("${oss.server-type:}")
    private String ymlServerType;

    /** 当前选型对应的实现 */
    public BaseOssService select() {
        return DictEnumUtil.getEnumByValue(resolveType(), OssServiceTypeDict.class).getService();
    }

    /**
     * 解析出生效的选型值：库值优先（运维在参数页表过态就以库为准），配置文件兜底。
     * 包内可见，便于单测直接判这条链而不必先装配四家实现。
     */
    String resolveType() {
        SysConfig row = sysConfigCacheService.getCache(OssParam.OSS_SERVER_TYPE.name());
        String fromDb = row == null ? null : row.getConfigValue();
        if (StringUtils.isNotBlank(fromDb)) {
            return fromDb.trim();
        }
        if (StringUtils.isNotBlank(ymlServerType)) {
            return ymlServerType.trim();
        }
        return Validator.assertException(ErrCodeSys.SYS_ERR_MSG,
                "未配置对象存储后端：系统参数「对象服务器类型」为空且配置文件未写 oss.server-type，"
                        + "二者任一给值即可（0=Local/1=Minio/2=Ali/3=火山引擎TOS）");
    }
}
