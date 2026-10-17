package com.bidr.admin.service.common;

import com.bidr.admin.service.excel.handler.PortalExcelHandlerInf;
import com.bidr.admin.service.excel.progress.PortalExcelUploadProgressInf;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.mybatis.service.TableSyncService;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.platform.constant.upload.UploadProgressStep;
import com.bidr.platform.service.excel.EasyExcelHandler;
import com.bidr.platform.service.excel.ModelDataListener;
import com.bidr.platform.service.excel.ShadowTableModelDataListener;
import com.bidr.platform.utils.excel.EasyExcelUtil;
import com.bidr.platform.vo.upload.PortalUploadProgressRes;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Excel解析服务基类
 * <p>
 * 支持影子表自动切换功能：子类覆写 {@link #getShadowTableNames()} 返回需要同步的表名列表，
 * 使用 {@link #buildShadowListener(EasyExcelHandler, Map)} 创建 Listener 即可自动处理影子表切换。
 *
 * @param <ENTITY> 实体类型
 * @param <VO>     Excel VO类型
 */
public abstract class BaseExcelParseService<ENTITY, VO> implements PortalExcelUploadProgressInf, PortalExcelHandlerInf {
    @Resource
    protected TokenService tokenService;
    @Resource
    protected PlatformTransactionManager transactionManager;
    @Resource
    protected TableSyncService tableSyncService;

    protected Class<VO> getEntityClass() {
        return (Class<VO>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 0);
    }

    protected Class<VO> getVoClass() {
        return (Class<VO>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 1);
    }

    protected abstract ModelDataListener<ENTITY, VO> getExcelListener(Map<String, Object> arg);

    /**
     * 获取需要同步的表名列表
     * <p>
     * 子类覆写此方法返回需要影子表切换的表名列表
     *
     * @return 表名列表，默认返回空列表
     */
    protected List<String> getShadowTableNames() {
        return Collections.emptyList();
    }

    /**
     * 构建支持影子表切换的 Listener（默认不开启事务）
     * <p>
     * 使用方式：
     * <pre>
     * &#64;Override
     * protected ModelDataListener&lt;Entity, VO&gt; getExcelListener(Map&lt;String, Object&gt; arg) {
     *     return buildShadowListener(new EasyExcelHandler&lt;&gt;() {
     *         // 只需实现业务方法：parse、save、validate 等
     *     }, arg);
     * }
     * </pre>
     *
     * @param handler 业务 handler
     * @param arg     参数
     * @return ShadowTableModelDataListener
     */
    protected ShadowTableModelDataListener<ENTITY, VO> buildShadowListener(
            EasyExcelHandler<ENTITY, VO> handler, Map<String, Object> arg) {
        return ModelDataListener.withShadowTable(handler, arg, tableSyncService, getShadowTableNames());
    }

    /**
     * 构建支持影子表切换的 Listener
     *
     * @param handler           业务 handler
     * @param arg               参数
     * @param enableTransaction 是否开启事务
     * @return ShadowTableModelDataListener
     */
    protected ShadowTableModelDataListener<ENTITY, VO> buildShadowListener(
            EasyExcelHandler<ENTITY, VO> handler, Map<String, Object> arg, boolean enableTransaction) {
        return ModelDataListener.withShadowTable(handler, arg, transactionManager, enableTransaction, tableSyncService, getShadowTableNames());
    }

    /**
     * 构建普通 Listener（默认不开启事务，不支持影子表切换）
     *
     * @param handler 业务 handler
     * @param arg     参数
     * @return ModelDataListener
     */
    protected ModelDataListener<ENTITY, VO> buildListener(EasyExcelHandler<ENTITY, VO> handler, Map<String, Object> arg) {
        return new ModelDataListener<>(handler, arg);
    }

    /**
     * 构建普通 Listener（不支持影子表切换）
     *
     * @param handler           业务 handler
     * @param arg               参数
     * @param enableTransaction 是否开启事务
     * @return ModelDataListener
     */
    protected ModelDataListener<ENTITY, VO> buildListener(
            EasyExcelHandler<ENTITY, VO> handler, Map<String, Object> arg, boolean enableTransaction) {
        return new ModelDataListener<>(handler, arg, transactionManager, enableTransaction);
    }

    @Override
    public TokenService getTokenService() {
        return tokenService;
    }

    @Async
    public void parseFile(File file, Map<String, Object> arg) {
        startUploadProgress(100);
        EasyExcelUtil.read(file, getExcelListener(arg), getVoClass());
    }

    public byte[] templateExport() {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        templateExcel(os, getVoClass());
        return os.toByteArray();
    }

    protected void setUploadProgress(UploadProgressStep step, Integer total, Integer loaded, String comment) {
        if (FuncUtil.isEmpty(loaded) && FuncUtil.isNotEmpty(total)) {
            startValidateRecord(total);
        } else if (FuncUtil.isNotEmpty(loaded) && FuncUtil.isEmpty(comment)) {
            addUploadProgress(loaded);
        } else if (FuncUtil.isNotEmpty(comment)) {
            uploadProgressException(comment);
        } else {
            updateUploadProgress(step, total, loaded, comment);
        }
    }

    private void updateUploadProgress(UploadProgressStep step, Integer total, Integer loaded, String comment) {
        PortalUploadProgressRes item = getUploadProgress();
        item.setStep(step);
        if (FuncUtil.isNotEmpty(total)) {
            item.setTotal(total);
        }
        if (FuncUtil.isNotEmpty(loaded)) {
            item.setLoaded(loaded);
        }
        if (FuncUtil.isNotEmpty(comment)) {
            item.getComments().add(comment);
        }
        setUploadProgress(item);
    }

    protected TransactionStatus getTransactionStatus() {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionManager.getTransaction(def);
    }
}
