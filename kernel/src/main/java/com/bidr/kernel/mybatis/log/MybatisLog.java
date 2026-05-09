package com.bidr.kernel.mybatis.log;


import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bidr.kernel.config.log.LogSuppressor;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.github.yulichang.base.JoinService;
import org.apache.ibatis.logging.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis SQL 日志处理器
 * 拦截 MyBatis 日志并格式化为完整的可执行 SQL 和 Markdown 表格
 * 
 * @author Sharp
 * @since 2025/12/2 10:45
 */
public class MybatisLog implements Log {

    // MyBatis 日志消息前缀常量
    private static final String PREFIX_PREPARING = "==>  Preparing:";
    private static final String PREFIX_PARAMETERS = "==> Parameters:";
    private static final String PREFIX_COLUMNS = "<==    Columns:";
    private static final String PREFIX_ROW = "<==        Row:";
    private static final String PREFIX_TOTAL = "<==      Total:";
    private static final String PREFIX_UPDATES = "<==    Updates:";
    
    private static final String KEYWORD_PREPARING = "Preparing:";
    private static final String KEYWORD_PARAMETERS = "Parameters:";
    private static final String KEYWORD_COLUMNS = "Columns:";
    private static final String KEYWORD_ROW = "Row:";

    private static final ThreadLocal<List<String>> COLS = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<List<String>>> ROWS = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<String> PREPARING_SQL = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> PARAMETERS_LIST = ThreadLocal.withInitial(ArrayList::new);

    private final Logger log;

    public MybatisLog(String clazz) {
        this.log = LoggerFactory.getLogger(clazz);
    }

    @Override
    public void debug(String msg) {
        // 直接输出 SQL 语句和参数
        if (log.isDebugEnabled()) {
            process(msg);
            log.debug(msg);
        }

    }

    @Override
    public void trace(String msg) {
        if (log.isTraceEnabled()) {
            // log.trace(msg);
            process(msg);
        }
    }

    @Override
    public void warn(String msg) {
        log.warn(msg);
    }

    @Override
    public void error(String msg) {
        log.error(msg);
    }

    @Override
    public void error(String msg, Throwable t) {
        log.error(msg, t);
    }

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public boolean isTraceEnabled() {
        return true;
    }

    private void process(String msg) {
        if (msg.startsWith(PREFIX_PREPARING)) {
            // 如果有之前未输出的批量操作，先输出
            if (!PARAMETERS_LIST.get().isEmpty()) {
                printResult();
                clearThreadLocals();
            }
            String sql = msg.substring(msg.indexOf(KEYWORD_PREPARING) + KEYWORD_PREPARING.length()).trim();
            PREPARING_SQL.set(sql);
        } else if (msg.startsWith(PREFIX_PARAMETERS)) {
            String params = msg.substring(msg.indexOf(KEYWORD_PARAMETERS) + KEYWORD_PARAMETERS.length()).trim();
            PARAMETERS_LIST.get().add(params);
        } else if (msg.startsWith(PREFIX_COLUMNS)) {
            String part = msg.substring(msg.indexOf(KEYWORD_COLUMNS) + KEYWORD_COLUMNS.length()).trim();

            List<String> cols = new ArrayList<>();
            for (String c : part.split(",")) {
                cols.add(c.trim());
            }

            COLS.set(cols);
        } else if (msg.startsWith(PREFIX_ROW)) {
            String part = msg.substring(msg.indexOf(KEYWORD_ROW) + KEYWORD_ROW.length()).trim();

            List<String> rowValues = new ArrayList<>();
            for (String r : part.split(",")) {
                rowValues.add(r.trim());
            }

            ROWS.get().add(rowValues);
        } else if (msg.startsWith(PREFIX_TOTAL) || msg.startsWith(PREFIX_UPDATES)) {
            // 使用 try-finally 确保即使发生异常也能清理 ThreadLocal
            try {
                printResult();
            } finally {
                clearThreadLocals();
            }
        }
    }

    private void printResult() {
        List<String> cols = COLS.get();
        List<List<String>> rows = ROWS.get();
        List<String> parametersList = PARAMETERS_LIST.get();
        StringBuilder resultOutput = new StringBuilder();

        // 输出完整 SQL，使用 Markdown 代码块格式
        if (FuncUtil.isNotEmpty(PREPARING_SQL.get()) && !parametersList.isEmpty()) {
            // 获取调用者信息（调用 BaseSqlRepo 的位置）
            String callerInfo = getCallerInfo();
            if (FuncUtil.isNotEmpty(callerInfo)) {
                resultOutput.append(" `").append(callerInfo).append("`");
            }

            // 如果是批量操作（多组参数）
            if (parametersList.size() > 1) {
                resultOutput.append("\n### \ud83d\udd39 Complete SQL (Batch: ").append(parametersList.size()).append(" statements)");
                resultOutput.append("\n```sql");
                for (String params : parametersList) {
                    String completeSql = MybatisLogFormatter.buildSql(PREPARING_SQL.get(), params);
                    resultOutput.append("\n").append(completeSql).append(";");
                }
                resultOutput.append("\n```");
            } else {
                // 单条 SQL
                String completeSql = MybatisLogFormatter.buildSql(PREPARING_SQL.get(), parametersList.get(0));
                resultOutput.append("\n### \ud83d\udd39 Complete SQL");
                resultOutput.append("\n```sql\n");
                resultOutput.append(completeSql);
                resultOutput.append("\n```");
            }
        }

        // 输出结果集，使用 Markdown 表格格式
        if (!cols.isEmpty()) {
            String tableOutput = MybatisLogFormatter.formatMarkdown(cols, rows);
            resultOutput.append("\n### \ud83d\udccb Query Result (").append(rows.size()).append(" row").append(rows.size() > 1 ? "s" : "").append(")\n");
            resultOutput.append(tableOutput);
        }
        if (!LogSuppressor.isLoggingSuppressed()) {
            log.trace(resultOutput.toString());
        }
    }

    /**
     * 获取调用 BaseSqlRepo 方法的文件和行号
     */
    private String getCallerInfo() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        boolean findBaseSqlRepo = false;
        
        // 需要跳过的框架类
        String baseSqlRepoClassName = BaseSqlRepo.class.getName();
        String joinServiceClassName = JoinService.class.getName();
        String iServiceClassName = IService.class.getName();
        String serviceImplClassName = ServiceImpl.class.getName();
        
        // 遍历堆栈，找到真正的业务调用者
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            String methodName = element.getMethodName();

            if (findBaseSqlRepo) {
                // 跳过框架内部类和代理类
                if (className.equals(baseSqlRepoClassName) || 
                    className.equals(serviceImplClassName) ||
                    className.contains("$")) {
                    continue;
                }
                
                // 只保留 com.bidr 包下的业务类
                if (!className.startsWith("com.bidr")) {
                    continue;
                }
                
                String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
                int lineNumber = element.getLineNumber();
                return simpleClassName + ".java:" + lineNumber + " " + methodName;
            }
            
            // 检查是否进入了 MyBatis-Plus 的服务层
            if (className.equals(baseSqlRepoClassName) || 
                className.equals(joinServiceClassName) || 
                className.equals(iServiceClassName)) {
                findBaseSqlRepo = true;
            }
        }
        return null;
    }

    /**
     * 清理 ThreadLocal 资源
     */
    private void clearThreadLocals() {
        COLS.get().clear();
        ROWS.get().clear();
        PARAMETERS_LIST.get().clear();
        COLS.remove();
        ROWS.remove();
        PREPARING_SQL.remove();
        PARAMETERS_LIST.remove();
    }
}