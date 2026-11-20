package com.bidr.kernel.config.response;

import com.bidr.kernel.event.ServiceExceptionEvent;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.tomcat.util.http.fileupload.impl.IOFileUploadException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.io.EOFException;
import java.io.IOException;

/**
 * Title: ResponseExceptionHandlerClientAbortTest
 * Description: 客户端传输中途断开不得进服务端故障告警通道的契约单测（非连库，直接调 handler）。
 * <p>
 * 背景：分片上传点「停止上传」（前端 abort）后，在传分片被客户端从半截掐断，Tomcat 抛
 * {@code MultipartException → IOFileUploadException → EOFException}，掉进 {@code errorHandler(Exception)}
 * 兜底腿 ⇒ log.error＋发布 ServiceExceptionEvent ⇒ 邮箱/站内信把正常用户操作当服务端故障报警。
 * <p>
 * 钉住三点：abort 形态静默（返回 null＋不发事件）；报警触发点（publishExceptionEvent）不被调用的负向腿；
 * 非 abort 形态（超限/业务 EOF/普通故障）行为不变照常告警的对照腿。
 *
 * @author Sharp
 * @since 2026/11/18
 */
public class ResponseExceptionHandlerClientAbortTest {

    private ResponseExceptionHandler handler;
    private ApplicationContext applicationContext;

    @Before
    public void setUp() throws Exception {
        handler = new ResponseExceptionHandler();
        applicationContext = Mockito.mock(ApplicationContext.class);
        ReflectionTestUtils.setField(handler, "applicationContext", applicationContext);
    }

    /** 生产实况形态：Spring MultipartException 包 Tomcat IOFileUploadException 包 EOFException */
    private static MultipartException multipartEof() {
        return new MultipartException("Failed to parse multipart servlet request",
                new IOFileUploadException("Processing of multipart/form-data request failed. java.io.EOFException",
                        new EOFException()));
    }

    private void assertNotAlerted(MultipartException ex) {
        Assert.assertNull("abort 形态应静默返回 null（客户端已断开，无响应体可写）", handler.errorHandler(ex));
        Mockito.verify(applicationContext, Mockito.never()).publishEvent(Mockito.any(ServiceExceptionEvent.class));
    }

    private void assertStillAlerted(Exception ex) {
        ResponseEntity<?> entity = handler.errorHandler(ex);
        Assert.assertNotNull("非 abort 形态必须维持原兜底行为（错误信封＋告警事件）", entity);
        Mockito.verify(applicationContext, Mockito.times(1)).publishEvent(Mockito.any(ServiceExceptionEvent.class));
    }

    @Test
    public void multipartEofIsSilenced() {
        assertNotAlerted(multipartEof());
    }

    @Test
    public void multipartConnectionResetMessageIsSilenced() {
        assertNotAlerted(new MultipartException("Failed to parse multipart servlet request",
                new IOException("connection reset by peer")));
    }

    @Test
    public void multipartClientAbortCauseIsSilenced() {
        assertNotAlerted(new MultipartException("Failed to parse multipart servlet request",
                new ClientAbortException(new EOFException())));
    }

    @Test
    public void maxUploadSizeIsNotSilenced() {
        // 负向腿：超限不是 abort（根因是 FileSizeLimitExceededException 而非 EOF），维持原行为
        assertStillAlerted(new MaxUploadSizeExceededException(1024L, new IOException("request exceeds limit")));
    }

    @Test
    public void businessEofIsNotSilenced() {
        // 负向腿：判定仅限 multipart 请求解析阶段，业务代码抛的 EOF（磁盘文件损坏等）仍是服务端故障
        assertStillAlerted(new RuntimeException("read file failed", new EOFException()));
    }

    @Test
    public void plainRuntimeExceptionIsNotSilenced() {
        // 对照腿：普通故障行为不变
        assertStillAlerted(new RuntimeException("boom"));
    }
}
