package com.bidr.platform.config.log;

import ch.qos.logback.classic.spi.CallerData;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.db.DBAppenderBase;
import com.bidr.kernel.utils.DateUtil;
import com.bidr.kernel.utils.FuncUtil;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import static com.bidr.platform.config.log.LogMdcConstant.LOG_SILENT;

/**
 * Title: DbLogbackAppender
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/3/26 11:53
 */
@Data
public class DbLogbackAppender extends DBAppenderBase<ILoggingEvent> {

    private static final Method GET_GENERATED_KEYS_METHOD;
    // 对应于数据库字段的插入数据序号
    private static final int ID_INDEX = 0;
    private static final int PROJECT_ID_INDEX = 1;
    private static final int MODULE_ID_INDEX = 2;
    private static final int ENV_TYPE_INDEX = 3;
    private static final int CREATE_TIME_INDEX = 4;
    private static final int LOG_SEQ_INDEX = 5;
    private static final int LOG_LEVEL_INDEX = 6;
    private static final int REQUEST_ID_INDEX = 7;
    private static final int TRACE_ID_INDEX = 8;
    private static final int REQUEST_IP_INDEX = 9;
    private static final int USER_IP_INDEX = 10;
    private static final int SERVER_IP_INDEX = 11;
    private static final int THREAD_NAME_INDEX = 12;
    private static final int CLASS_NAME_INDEX = 13;
    private static final int METHOD_NAME_INDEX = 14;
    private static final int CONTENT_INDEX = 15;
    private static final StackTraceElement EMPTY_CALLER_DATA = CallerData.naInstance();

    // 处理主键的自动生成，这里我们使用手工生成，因此下面代码可忽略
    static {
        // PreparedStatement.getGeneratedKeys() method was added in JDK 1.4
        Method getGeneratedKeysMethod;
        try {
            // the
            getGeneratedKeysMethod = PreparedStatement.class.getMethod("getGeneratedKeys", (Class[]) null);
        } catch (Exception ex) {
            getGeneratedKeysMethod = null;
        }
        GET_GENERATED_KEYS_METHOD = getGeneratedKeysMethod;
    }

    private final AtomicLong LOG_SEQ_BUILDER = new AtomicLong();
    private String projectId;
    private String moduleId;
    private String envType;
    private String insertSQL;

    private static String buildInsertSQL() {
        return "INSERT INTO sys_log " +
                "(project_id, module_id, env_type, create_time, log_seq, log_level, request_id, trace_id, request_ip, user_ip, server_ip, thread_name, class_name, method_name, content) " +
                "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    public void start() {
        insertSQL = buildInsertSQL();
        cnxSupportsBatchUpdates = connectionSource.supportsBatchUpdates();
        super.start();
        super.started = true;
    }

    private void bindLoggingEventWithInsertStatement(PreparedStatement stmt, ILoggingEvent event) throws SQLException {
        // TODO 手工处理ID的生成
        stmt.setString(PROJECT_ID_INDEX, projectId);
        stmt.setString(MODULE_ID_INDEX, moduleId);
        stmt.setString(ENV_TYPE_INDEX, envType);
        stmt.setLong(LOG_SEQ_INDEX, LOG_SEQ_BUILDER.getAndIncrement());
        stmt.setString(CREATE_TIME_INDEX,
                DateUtil.formatDate(new Date(event.getTimeStamp()), "yyyy-MM-dd HH:mm:ss.SSS"));
        stmt.setString(CONTENT_INDEX, buildLogContent(event));
        stmt.setString(LOG_LEVEL_INDEX, event.getLevel().toString());
        stmt.setString(CLASS_NAME_INDEX, event.getLoggerName());
        stmt.setString(THREAD_NAME_INDEX, event.getThreadName());
        stmt.setString(REQUEST_ID_INDEX, event.getMDCPropertyMap().getOrDefault("REQUEST_ID", ""));
        stmt.setString(TRACE_ID_INDEX, event.getMDCPropertyMap().getOrDefault("logToken", ""));
        stmt.setString(REQUEST_IP_INDEX, event.getMDCPropertyMap().getOrDefault("IP", ""));
        stmt.setString(USER_IP_INDEX, event.getMDCPropertyMap().getOrDefault("USER_IP", ""));
        stmt.setString(SERVER_IP_INDEX, event.getMDCPropertyMap().getOrDefault("SERVER_IP", ""));
    }

    private String buildLogContent(ILoggingEvent event) {
        StringBuilder content = new StringBuilder(event.getFormattedMessage());
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (FuncUtil.isNotEmpty(throwableProxy)) {
            if (StringUtils.isNotBlank(event.getFormattedMessage())) {
                content.append("\n");
            }
            content.append(throwableProxy.getClassName());
            content.append(throwableProxy.getMessage());
            if (FuncUtil.isNotEmpty(throwableProxy.getStackTraceElementProxyArray())) {
                int stackSize = throwableProxy.getStackTraceElementProxyArray().length;
                int stackLine = Math.min(stackSize, 15);
                for (int i = 0; i < stackLine; i++) {
                    content.append("\n").append(throwableProxy.getStackTraceElementProxyArray()[i].getSTEAsString());
                }
            }
        }
        return content.toString();
    }

    private void bindCallerDataWithPreparedStatement(PreparedStatement stmt,
                                                     StackTraceElement[] callerDataArray) throws SQLException {
        StackTraceElement caller = extractFirstCaller(callerDataArray);
        stmt.setString(METHOD_NAME_INDEX,
                caller.getMethodName() + "(" + caller.getFileName() + ":" + caller.getLineNumber() + ")");
    }

    @Override
    protected void subAppend(ILoggingEvent event, Connection connection,
                             PreparedStatement insertStatement) throws Throwable {
        if (event.getMDCPropertyMap().get(LOG_SILENT) != null) {
            throw new Exception();
        }
        bindLoggingEventWithInsertStatement(insertStatement, event);
        // This is expensive... should we do it every time?
        bindCallerDataWithPreparedStatement(insertStatement, event.getCallerData());
        int updateCount = insertStatement.executeUpdate();
        if (updateCount != 1) {
            addWarn("Failed to insert loggingEvent");
        }
    }

    private StackTraceElement extractFirstCaller(StackTraceElement[] callerDataArray) {
        StackTraceElement caller = EMPTY_CALLER_DATA;
        if (hasAtLeastOneNonNullElement(callerDataArray)) {
            caller = callerDataArray[0];
        }
        return caller;
    }

    private boolean hasAtLeastOneNonNullElement(StackTraceElement[] callerDataArray) {
        return callerDataArray != null && callerDataArray.length > 0 && callerDataArray[0] != null;
    }

    @Override
    protected Method getGeneratedKeysMethod() {
        return GET_GENERATED_KEYS_METHOD;
    }

    @Override
    protected String getInsertSQL() {
        return insertSQL;
    }

    @Override
    protected void secondarySubAppend(ILoggingEvent event, Connection connection, long eventId) throws Throwable {
    }

    @Override
    protected long selectEventId(PreparedStatement insertStatement,
                                 Connection connection) throws SQLException, InvocationTargetException {
        return 0;
    }
}
