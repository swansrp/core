package com.bidr.agent.console.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Title: SystemSkillInitializer
 * Description: 系统级出厂技能包启动对账入口：jar 内 {@code agent-skills/} 随发版更新，重启即生效，不需要人工上传。
 *
 * <p>失败策略与框架侧其余启动初始化同口径：只记 ERROR、不阻断启动——对账本身幂等，
 * 存储或表暂时不可用时下次启动自然重试；把启动搞挂的代价大于晚一个周期对齐账目。
 * 这条路径不是假设：存储选型走 sys_config 的 {@code OSS_SERVER_TYPE}，而参数行缺失时现读是抛异常
 * 而非回默认值（专有化形态没种过这一行就是这种情形），必须靠这里吞掉。</p>
 *
 * <p>开关 {@code agent.console.skill-seed.enabled}：缺省即开（开箱可用，接入方不写一行配置就能拿到出厂包），
 * 但留一条关闭路径——对账器会写 {@code sys_skill_asset} 与对象存储，
 * 专有化部署若要求"框架不发内容"或本地跑测不希望每次启动都写库，都得能整体摘掉，
 * 而不是靠删 jar 里的资源目录。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.console.skill-seed.enabled", havingValue = "true", matchIfMissing = true)
public class SystemSkillInitializer implements ApplicationRunner {

    private final SystemSkillSeedService seedService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            SystemSkillSeedService.Outcome outcome = seedService.seed();
            if (!outcome.changed()) {
                log.info("系统出厂技能包对账: {}", outcome);
            }
        } catch (Exception e) {
            log.error("系统出厂技能包对账失败（不阻断启动，下次启动重试）: {}", e.getMessage(), e);
        }
    }
}
