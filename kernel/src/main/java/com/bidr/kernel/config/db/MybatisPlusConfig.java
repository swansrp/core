package com.bidr.kernel.config.db;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.bidr.kernel.mybatis.inf.MybatisPlusTableInitializerInf;
import com.bidr.kernel.mybatis.log.MybatisLog;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.*;
import java.util.Map;

/**
 * Mybatis Plus 配置
 *
 * @author zong_b
 */
@Slf4j
@Configuration
public class MybatisPlusConfig {

    public static boolean SUPPORT_RECURSIVE;
    
    static {
        // 在类加载时就设置自定义日志实现，确保 MyBatis 使用我们的 Log
        LogFactory.useCustomLogging(MybatisLog.class);
    }
    @Resource
    private AppProperties appProperties;
    @Resource
    private ApplicationContext applicationContext;
    @Resource
    private DataSource dataSource;

    /**
     * mybatis-plus分页插件
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor innerInterceptor = new PaginationInnerInterceptor();
        //限制页大小
        innerInterceptor.setMaxLimit(appProperties.getMaxPageSize());
        interceptor.addInnerInterceptor(innerInterceptor);
        return interceptor;
    }

    @Bean
    public MPJConfig sqlInjector() {
        return new MPJConfig();
    }

    @PostConstruct
    public void initTables() {

        Map<String, MybatisPlusTableInitializerInf> beans =
                applicationContext.getBeansOfType(MybatisPlusTableInitializerInf.class);

        // 提前一次性确保 sys_table_version 表存在，避免每个表初始化时重复检查
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            DatabaseMetaData metaData = connection.getMetaData();
            ensureSysTableVersionTable(metaData, stmt);
            String version = metaData.getDatabaseProductVersion();
            SUPPORT_RECURSIVE = version.startsWith("8.");
        } catch (Exception e) {
            log.error("初始化 sys_table_version 表失败", e);
            return;
        }

        // 循环初始化各个业务表
        for (MybatisPlusTableInitializerInf initializer : beans.values()) {
            initializer.initTable(dataSource);
        }
    }

    private void ensureSysTableVersionTable(DatabaseMetaData metaData, Statement stmt) throws SQLException {
        String sysTableName = "sys_table_version";
        if (!tableExists(metaData, sysTableName)) {
            String createSysTableSql = "CREATE TABLE IF NOT EXISTS `sys_table_version` (\n" +
                    "              `table_name` varchar(255) NOT NULL COMMENT '表名',\n" +
                    "              `version` int NOT NULL COMMENT '版本',\n" +
                    "              `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                    "              PRIMARY KEY (`table_name`)\n" +
                    "            ) COMMENT='表版本控制';";
            stmt.executeUpdate(createSysTableSql);
        }
    }

    private boolean tableExists(DatabaseMetaData metaData, String tableName) throws SQLException {
        // 获取当前连接的数据库名(catalog),避免误判其他数据库中的同名表
        String catalog = metaData.getConnection().getCatalog();
        try (ResultSet rs = metaData.getTables(catalog, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}
