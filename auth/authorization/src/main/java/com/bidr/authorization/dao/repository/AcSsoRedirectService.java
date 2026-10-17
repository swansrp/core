package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcSsoRedirect;
import com.bidr.authorization.dao.mapper.AcSsoRedirectDao;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Title: AcSsoRedirectService
 * Description: Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 * <p>🔴 类名硬约束：框架 {@code BaseAdminController.getRepo()} 按
 * {@code Introspector.decapitalize(实体简名)+"Service"} 从容器取 bean，本类必须叫此名。</p>
 *
 * @author Sharp
 * @since 2026-10-14
 */
@Service
public class AcSsoRedirectService extends BaseSqlRepo<AcSsoRedirectDao, AcSsoRedirect> {

    /**
     * 取某 partner 名下全部有效登记行（valid='1'）——供出票口白名单闸做 origin 等值比较用
     * （M-11：解析与比较放 Java 侧，mapper SQL 不动；单 partner 登记行个位数，无性能面）。
     * <p>🔴 登记面查重（{@link #existsValid} / {@link #existsValidExcludingId}）仍按全串等值走 SQL，
     * 不改用本方法。</p>
     *
     * @param partnerId ac_partner 口径 id（null ⇒ 空列表，不抛）
     * @return 该 partner 名下全部 valid='1' 登记行
     */
    public List<AcSsoRedirect> listValid(Long partnerId) {
        if (partnerId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<AcSsoRedirect> wrapper = super.getQueryWrapper();
        wrapper.eq(AcSsoRedirect::getPartnerId, partnerId);
        wrapper.eq(AcSsoRedirect::getValid, CommonConst.YES);
        return super.select(wrapper);
    }

    /**
     * 白名单命中判定：(partner_id, redirect_uri) 组合全串等值 ∧ valid='1'。
     * <p>🔴 禁止退化成"只按 redirect_uri 全局查"——出票口与登记面查重共用本方法，判据只此一处。</p>
     *
     * @param partnerId   ac_partner 口径 id
     * @param redirectUri 回调地址原串（未做任何截断）
     * @return 命中有效登记行 ⇒ true
     */
    public boolean existsValid(Long partnerId, String redirectUri) {
        if (partnerId == null || redirectUri == null) {
            return false;
        }
        return super.count(validWrapper(partnerId, redirectUri)) > 0;
    }

    /**
     * 同 {@link #existsValid} 的三条件，但排除指定 id 的那一行——供登记面**编辑口改地址时查重**用：
     * 不排除自身会把"保存但没动 redirect_uri"判成重复。
     * <p>🔴 出票口不得改用本方法：出票判据要保持 {@link #existsValid} 这一个真相。</p>
     *
     * @param excludeId 本次编辑命中的行 id（null ⇒ 调用方契约不满足，按"未命中"返回 false，不放行）
     */
    public boolean existsValidExcludingId(Long partnerId, String redirectUri, Long excludeId) {
        if (partnerId == null || redirectUri == null || excludeId == null) {
            return false;
        }
        return super.count(validWrapperExcludingId(partnerId, redirectUri, excludeId)) > 0;
    }

    /**
     * 三条件＋排除自身 id 的共享构造：{@link #existsValidExcludingId} 的判据只此一处。
     * 🔴 测试必须直接取本方法断言，而不是在测试里重搭一遍 {@code .ne(...)}——后者删掉生产那一行
     * 测试仍全绿，等于给"编辑口查重漏杀自身"留一条无针的路径（10-14 review R2）。
     * <p>包可见性同 {@link #validWrapper}：不为测试拓宽公开 API。</p>
     */
    LambdaQueryWrapper<AcSsoRedirect> validWrapperExcludingId(Long partnerId, String redirectUri, Long excludeId) {
        return validWrapper(partnerId, redirectUri).ne(AcSsoRedirect::getId, excludeId);
    }

    /**
     * 三条件的共享构造（{@code partner_id} ∧ {@code redirect_uri} ∧ {@code valid='1'}）：
     * {@link #existsValid} 与 {@link #existsValidExcludingId} 共用，防两条判据各写一遍后漂移。
     * <p>🔴 {@code valid} 那一项是 M-22 红线的一半（撤销＝软删后置 '0'，不过滤就会把已撤的行当有效登记），
     * 删除它 {@code AcSsoRedirectServiceTest} 的真针必翻红。</p>
     * <p>包内可见＝给同包的离线测试直接断言 wrapper 形态，不为此拓宽公开 API。</p>
     */
    LambdaQueryWrapper<AcSsoRedirect> validWrapper(Long partnerId, String redirectUri) {
        LambdaQueryWrapper<AcSsoRedirect> wrapper = super.getQueryWrapper();
        wrapper.eq(AcSsoRedirect::getPartnerId, partnerId);
        wrapper.eq(AcSsoRedirect::getRedirectUri, redirectUri);
        wrapper.eq(AcSsoRedirect::getValid, CommonConst.YES);
        return wrapper;
    }
}
