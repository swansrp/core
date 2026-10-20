package com.bidr.blob.dao.schema;

import com.bidr.blob.dao.entity.KbBlob;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 内容块（跨空间去重，不带空间维度）表结构定义（DDL 正源，建表即冻结，变更只走 setUpgradeDDL 追加；
 * M16 棒 1 自 inkhub-file 下沉 core/store/blob，createDDL 一字未动）
 *
 * @author sharp
 */
@Service
public class KbBlobSchema extends BaseMybatisSchema<KbBlob> {

    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `kb_blob`\n" +
                "(\n" +
                "    `id`          bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "    `sha256`      char(64)     NOT NULL COMMENT '内容哈希',\n" +
                "    `storage_key` varchar(512) NOT NULL COMMENT '对象存储key（raw/{哈希路径}）',\n" +
                "    `file_size`   bigint(20)   NOT NULL COMMENT '字节数',\n" +
                "    `mime_type`   varchar(100) DEFAULT NULL COMMENT 'MIME类型',\n" +
                "    `ref_count`   int(11)      NOT NULL DEFAULT '0' COMMENT '引用计数（kb_file_version 数）',\n" +
                "    `create_by`   varchar(50)  DEFAULT NULL COMMENT '创建者',\n" +
                "    `create_at`   datetime(3)  DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "    `update_by`   varchar(50)  DEFAULT NULL COMMENT '更新者',\n" +
                "    `update_at`   datetime(3)  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "    `valid`       char(1)      DEFAULT '1' COMMENT '有效性',\n" +
                "    PRIMARY KEY (`id`),\n" +
                "    UNIQUE KEY `uk_sha256` (`sha256`)\n" +
                ") COMMENT='内容块（跨空间去重，不带 space_id）';");

        // M16 棒 1（D7 甲口径）：追加 kind（内容种类，谁有权下令删字节的归属标记）与
        // storage_type（存储后端快照）。存量派生品行不回填（kind NULL 仍按引用源计数回收）。
        setUpgradeDDL(1, "ALTER TABLE `kb_blob` ADD COLUMN `kind` varchar(32) DEFAULT NULL COMMENT '内容种类（D7 甲口径：original=原件锚/render=可交付件/image=裸图无引用源；存量派生品行不回填）', "
                + "ADD COLUMN `storage_type` varchar(32) DEFAULT NULL COMMENT '存储后端快照（选型时点值：LOCAL/MINIO/ALI/VOLCANO，取值链同 BlobStore.currentStorageType）'");
    }
}
