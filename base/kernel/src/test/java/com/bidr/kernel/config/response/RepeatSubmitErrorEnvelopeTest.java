package com.bidr.kernel.config.response;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.RepeatSubmitException;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Title: RepeatSubmitErrorEnvelopeTest
 * Description: {@code @RepeatSubmit} 拒绝时<b>必须回错误信封</b>的契约单测（非连库，直接调 handler）。
 * <p>
 * 背景（缺陷 F-1）：该 handler 早期实现返回 {@code void}，Spring 视作"已处理完、无响应体"⇒ HTTP 200＋空 body；
 * 而框架前端 {@code request()} 判失败要同时满足"status.code≠0 且 status.msg 非空"，空 body 两条都不成立 ⇒
 * 既不弹错也不 reject，业务页把 resolve 当成功（实测＝S0 重复提交显示"提交完成"而查询码空白，用户从此查不到进度）。
 * <p>
 * 因此这里钉住信封四要素：body 非空、code≠成功码、msg 非空（前端 reject 的开关）、原文文案落 details 与 payload.errMsg（前端弹的文案）。
 * 另钉一条"不发异常事件"的取舍：重复提交属客户端行为，不应进服务端故障告警通道。
 *
 * @author Sharp
 * @since 2026/10/09
 */
public class RepeatSubmitErrorEnvelopeTest {

    /** RepeatSubmitAspect 抛的就是注解默认文案（{@code RepeatSubmit.message()}） */
    private static final String MSG = "请求处理中，请勿重复提交";

    private Response<?> handle() {
        ResponseEntity<Response<String>> entity =
                new ResponseExceptionHandler().errorHandler(new RepeatSubmitException(MSG));
        Assert.assertEquals("拒绝类失败应与 NoticeException/ServiceException 同族走 HTTP 200＋错误信封",
                HttpStatus.OK, entity.getStatusCode());
        return entity.getBody();
    }

    @Test
    public void rejectMustCarryNonEmptyBody() {
        Assert.assertNotNull("body 为 null 就是旧 void 实现造成假成功的来源", handle());
    }

    @Test
    public void statusCodeMustNotBeSuccessCode() {
        Response<?> body = handle();
        Assert.assertNotNull(body.getStatus());
        Assert.assertNotEquals("code 等于成功码 ⇒ 前端判成功",
                ErrCodeSys.SUCCESS.getErrCode(), body.getStatus().getCode());
        Assert.assertEquals(ErrCodeSys.SYS_ERR_MSG.getErrCode(), body.getStatus().getCode());
    }

    @Test
    public void statusMsgMustBePresentForFrontendReject() {
        Assert.assertEquals("msg 为空则框架 request() 不 reject（就是 F-1 的第二段）",
                "SYS_ERR_MSG", handle().getStatus().getMsg());
    }

    @Test
    public void originalMessageReachesDetailsAndPayloadErrMsg() {
        Response<?> body = handle();
        Assert.assertEquals(MSG, body.getStatus().getDetails());
        // Response(ServiceException) 内部把 ExceptionResponse 强转进泛型 T，静态类型是 String，故经 Object 取值
        Object payload = body.getPayload();
        Assert.assertTrue("payload 应为 ExceptionResponse，前端按 payload.errMsg 弹文案",
                payload instanceof ExceptionResponse);
        ExceptionResponse ex = (ExceptionResponse) payload;
        Assert.assertEquals(MSG, ex.getErrMsg());
        Assert.assertEquals("WARN 级＝前端 errTypeMapList[3] 走 message.warning", "3", ex.getErrLevel());
    }
}
