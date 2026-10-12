package com.bidr.agent.console;

import com.bidr.agent.console.dao.entity.SkillAsset;
import com.bidr.agent.console.skill.SystemSkillAdminService;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.exception.NoticeException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Title: SystemSkillAdminController
 * Description: 系统级技能包写面（M14 棒 4-E）：上传 / 发布 / 下线 / 注销墓碑 ＋ 台账列表。
 * 读侧（哪些空间能读到）不在这只端点上——那是棒 4-F 的 {@code SkillCatalogPort} 并集，
 * 本端点只管"这个包在系统层面是什么状态"。
 * <p>
 * <b>鉴权口径</b>：闸门在 {@link SystemSkillAdminService} 里（平台管理员，反射取 permitService、
 * bean 取不到即拒绝），与 {@link AgentManifestController} 展开原文那道闸同一口径；
 * 拦截链仍走平台 {@code /web/api} 的登录校验。之所以不把闸写在控制器上：写面一旦有第二个入口
 * （后续 CLI／导入），控制器层的闸会漏，服务层的闸漏不掉。
 * <p>
 * <b>为什么"上线"这一档不叫 delete</b>：业务侧 {@code /web/api/skill/{id}/delete} 是软删语义，
 * 而系统半的"撤销出厂包"必须是 {@code TOMBSTONE}——只置 valid=0 的行会被下次启动的对账器就地复活
 * （棒 4-D 已用单测钉住），端点名直接把落库的状态写出来，免得调用方以为删了就没了。
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Api(tags = "系统管理 - 系统级技能包（上传/发布/下线/注销，仅平台管理员）")
@RestController
@RequestMapping("/web/api/agent/skill")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent-console.api", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SystemSkillAdminController {

    private final SystemSkillAdminService adminService;

    @ApiOperation(value = "系统级技能包台账（含未发布/下线/墓碑全档，仅平台管理员可读）",
            notes = "不做 origin/status 筛选：管理页要看全。valid=0 的软删历史行不在列（撤销的正确表达是 status=TOMBSTONE）")
    @GetMapping("/assets")
    public List<SkillAsset> assets() {
        return adminService.listAssets();
    }

    /**
     * 上传只落未发布档，可见性由发布动作决定——把"传上去"和"对所有空间生效"分成两拍，
     * 是为了让误传一个包不至于立刻进所有人的提示词。
     */
    @ApiOperation(value = "上传系统级技能包 zip（上传 ≠ 可见：落 UNPUBLISHED，须再调 publish）",
            notes = "包契约＝单根文件夹＋根下 SKILL.md＋frontmatter.name 等于根名＋description 非空；"
                    + "同名撞出厂包（origin=BUILTIN）直接拒绝（出厂包名保留）；"
                    + "同名重传＝版本递增的新行，旧行不动；入库字节是确定性重打包产物，"
                    + "sha 按归一化后的字节算（与对账器同一把尺）")
    @PostMapping("/upload")
    public SkillAsset upload(@ApiParam(value = "skill 包 zip 文件（单根文件夹结构）", required = true)
                             @RequestPart("file") MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new NoticeException("skill 包读取失败: " + e.getMessage());
        }
        return adminService.upload(bytes);
    }

    @ApiOperation(value = "发布（进入系统半可读集）",
            notes = "UNPUBLISHED／OFFLINE → PUBLISHED；已是 PUBLISHED＝幂等成功且不写库；"
                    + "TOMBSTONE＝拒绝（注销不可逆，要恢复请重新上传）")
    @PostMapping("/{id}/publish")
    public void publish(@ApiParam(value = "台账行 id", required = true) @PathVariable("id") Long id) {
        adminService.publish(id);
        Resp.notice("系统技能包已发布，对所有空间免绑定可读");
    }

    @ApiOperation(value = "下线（读侧立即不再下发，行与包对象保留、可重新发布）",
            notes = "PUBLISHED → OFFLINE；已是 OFFLINE＝幂等成功且不写库；"
                    + "UNPUBLISHED＝拒绝（尚未发布，无需下线）；TOMBSTONE＝拒绝")
    @PostMapping("/{id}/offline")
    public void offline(@ApiParam(value = "台账行 id", required = true) @PathVariable("id") Long id) {
        adminService.offline(id);
        Resp.notice("系统技能包已下线");
    }

    @ApiOperation(value = "注销（落 TOMBSTONE 墓碑；撤销出厂包的正确表达，重启不复活）",
            notes = "任何状态 → TOMBSTONE；已是 TOMBSTONE＝幂等成功且不写库。"
                    + "只写 status 一列：不置 valid=0（那样会被启动对账器就地复活）、不删对象（留档可回滚）。"
                    + "注销后该名字仍是出厂包的保留名，不可再发布")
    @PostMapping("/{id}/tombstone")
    public void tombstone(@ApiParam(value = "台账行 id", required = true) @PathVariable("id") Long id) {
        adminService.tombstone(id);
        Resp.notice("系统技能包已注销（墓碑），重启不再装回");
    }
}
