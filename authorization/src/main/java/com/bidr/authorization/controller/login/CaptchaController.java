package com.bidr.authorization.controller.login;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthNone;
import com.bidr.authorization.service.captcha.CaptchaService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Title: CaptchaController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/26 17:39
 */
@Slf4j
@Auth(AuthNone.class)
@Api(tags = "系统基础 - 图形验证码")
@RestController("CaptchaController")
@RequestMapping(value = "/web")
public class CaptchaController {

    @Resource
    private CaptchaService captchaService;

    @ApiOperation(value = "获取图形验证", notes = "利用token获取图形验证码")
    @RequestMapping(value = "/captcha.jpg", method = RequestMethod.GET)
    @ApiImplicitParams({@ApiImplicitParam(paramType = "query", dataType = "String", name = "token", value = "token",
            required = true), @ApiImplicitParam(paramType = "query", dataType = "String", name = "captchaType",
            required = true)})
    public void getCaptcha(HttpServletRequest request, HttpServletResponse response,
                           @RequestParam(value = "token") String token,
                           @RequestParam(value = "captchaType") String captchaType) throws IOException {

        ServletOutputStream stream = response.getOutputStream();
        try {
            response.setContentType("image/png");
            BufferedImage image;
            image = captchaService.generateCaptcha(token, captchaType);
            ImageIO.write(image, "png", stream);
        } catch (Exception e) {
            log.error("获取图形验证码", e);
        } finally {
            if (stream != null) {
                stream.flush();
                stream.close();
            }
        }
    }
}
