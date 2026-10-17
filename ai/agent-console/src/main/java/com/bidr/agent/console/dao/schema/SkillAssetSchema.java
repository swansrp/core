package com.bidr.agent.console.dao.schema;

import com.bidr.agent.console.dao.entity.SkillAsset;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: SkillAssetSchema
 * Description: 系统级技能资产台账表结构定义（DDL 正源，建表即冻结，变更只走 setUpgradeDDL 追加）。
 *
 * <p>框架平台表按惯例带 {@code sys_} 前缀、表名不带 schema 前缀（同 {@code sys_agent_manifest}）；
 * 不带前缀是因为 core 是被所有项目共用的框架，把某个库名写进建表语句就把框架钉死在一张库上了。</p>
 *
 * <p>🔴 <b>全表没有唯一键，包括 {@code skill_name}</b>：这张表要表达"撤销后再加回"的三态——
 * 同名行会同时存在（软删的旧行 + 新加回的行），把软删位 {@code valid} 和唯一键组合起来是已知陷阱
 * （MySQL 不看 {@code valid} 的值，第二次加回直接撞键）。按名唯一改由 {@code SkillAssetService}
 * 的写腿在服务层保证；业务侧的 {@code kb_skill} 同样没有唯一键，口径一致。</p>
 *
 * <p>不建 {@code update_by}：本表的重活由对账线程做（无登录上下文），落了也只能是空列，
 * 谁改的看 {@code sys_agent_manifest} 那类证据更准。</p>
 *
 * <p>建表这条腿不在离线单测里验：本仓的 MyBatis 离线测试要手搓 TableInfo 缓存，
 * 代价高于收益；建表与列型由应用启动时的 schema 机制实跑覆盖，代码侧只留本文件为唯一正源。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Service
public class SkillAssetSchema extends BaseMybatisSchema<SkillAsset> {

    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_skill_asset`\n" +
                "(\n" +
                "    `id`             bigint(20)   NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "    `skill_name`     varchar(64)  NOT NULL COMMENT '技能包名（提示词与工具里出现的那个名字，按名唯一在服务层保证，见类注释）',\n" +
                "    `title`          varchar(128) DEFAULT NULL COMMENT '展示名（NULL＝没提供，展示层回落 skill_name）',\n" +
                "    `description`    varchar(512) DEFAULT NULL COMMENT '技能描述（提示词渐进式披露用）',\n" +
                "    `category`       varchar(64)  DEFAULT NULL COMMENT '分类（NULL＝未归类）',\n" +
                "    `version`        int(11)      DEFAULT NULL COMMENT '当前版本（重递++；NULL＝没取到——填 0 会被读成真实的第一版，是假数据）',\n" +
                "    `status`         varchar(16)  NOT NULL COMMENT '状态：UNPUBLISHED 未发布/PUBLISHED 已发布/OFFLINE 已下线/TOMBSTONE 注销墓碑',\n" +
                "    `origin`         varchar(16)  NOT NULL COMMENT '来源：BUILTIN 随 jar 发版（对账器按内容指纹维护）/UPLOADED 页面上传的数据资产（对账永不触碰）',\n" +
                "    `package_key`    varchar(256) NOT NULL COMMENT '当前版本包在对象存储里的 key（只存定位串，本表不含存储客户端语义）',\n" +
                "    `package_sha256` varchar(64)  DEFAULT NULL COMMENT '确定性重打包后的内容指纹（NULL＝还没算过，与\"算过\"可区分）',\n" +
                "    `file_count`     int(11)      DEFAULT NULL COMMENT '包内文件数（不含目录项；NULL＝没数过）',\n" +
                "    `create_by`      varchar(50)  DEFAULT NULL COMMENT '创建人（对账线程无登录上下文时为空，属正常）',\n" +
                "    `create_at`      datetime(3)  DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "    `update_at`      datetime(3)  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（重递版本/改状态才动）',\n" +
                "    `valid`          char(1)      DEFAULT '1' COMMENT '有效性（撤销＝软删，行留着；故同名可多行并存，见类注释）',\n" +
                "    PRIMARY KEY (`id`),\n" +
                "    KEY `idx_skill_name` (`skill_name`),\n" +
                "    KEY `idx_status_origin` (`status`, `origin`)\n" +
                ") COMMENT='系统级技能资产台账（一行一个技能包档：状态/来源/包位置/版本）——没有空间上下文的会话也能读到的那一半；空间维度的使用关系在业务侧 kb_skill。本表不参与空间行级隔离，也不反向引用空间/用户/对象存储模板'");
    }
}
