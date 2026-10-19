package com.bidr.oss.constant.param;

import com.bidr.kernel.constant.param.MetaParam;
import com.bidr.kernel.constant.param.Param;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: ObjectStorageConstant
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2019/10/16 21:18
 */
@Getter
@MetaParam("对象存储")
@AllArgsConstructor
public enum OssParam implements Param {
    /**
     *
     */
    OSS_SERVER_TYPE("对象服务器类型", "", "【装机定，勿轻改】存储后端选型（0=Local/1=Minio/2=Ali/3=火山引擎TOS），必须与实际部署的对象存储一致；⚠️ 只改本值不迁底座，既有文件会读不到、新文件落错地方；改后需重启或等配置缓存刷新。"
            + "取值链：本值非空＝以本值为准（运维表态优先）→ 本值为空＝回落配置文件 oss.server-type（新装现场不改库也能定底座）→ 两头都空＝上传当场报错，绝不静默假定某一家；"
            + "想让配置文件接管本现场，把本值清空即可（清空＝未表态，不是选错）"),

    OSS_PREVIEW_URL("在线预览服务地址", "0", "【按需调整·需部署 kkFileView】office/图纸等在线预览的跳转地址，支持本机反代相对路径（如 /kkfileview/onlinePreview?url=）或完整外链；0 或留空＝前端不出现预览入口；改后刷新页面即生效");


    private final String title;
    private final String defaultValue;
    private final String remark;
}
