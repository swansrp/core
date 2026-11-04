package com.bidr.authorization.client.oauth2;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.net.URLEncoder;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Title: OAuth2ClientTest
 * Description: OAuth2Client 离线单测（T3.1 棒0）：授权 URL 拼接 ＋ code 换票的信封两形态／
 * 400 错误码解析。全程 {@link MockRestServiceServer} 绑内部 RestTemplate，不发真实 HTTP。
 *
 * <p>🔴 针自证腿：正向换票断言 access_token／token_type／expires_in 三字段都取到值——
 * 只断言"不抛异常"挡不住信封解析退化成空对象。</p>
 *
 * @author coder (棒0, T3.1 客户端件下沉 2026-11-03)
 * @since 2026-11-03
 */
public class OAuth2ClientTest {

    private static final String BASE_URL = "http://idp.local/inkhub";
    private static final String REDIRECT_URI = "http://127.0.0.1:18081/oauth2/callback";

    private OAuth2Client client;
    private MockRestServiceServer server;

    @Before
    public void setUp() {
        // base 故意带尾部斜杠、secret 故意带空白：验证两个规整口径（裁尾斜杠／trim）
        client = new OAuth2Client(BASE_URL + "/", "epc-ai", "  s3cret  ", REDIRECT_URI);
        server = MockRestServiceServer.createServer(client.restTemplate());
    }

    @Test
    public void buildAuthorizeUrlCarriesCodeGrantAndEncodesValues() throws Exception {
        String url = client.buildAuthorizeUrl("st-1");

        Assert.assertTrue("授权 URL 必须以 authorize 端点开头（尾部斜杠已裁）: " + url,
                url.startsWith(BASE_URL + "/web/oauth2/authorize?"));
        Assert.assertTrue("缺 response_type=code: " + url, url.contains("response_type=code"));
        Assert.assertTrue("缺 client_id: " + url, url.contains("client_id=epc-ai"));
        Assert.assertTrue("redirect_uri 必须 URL 编码: " + url,
                url.contains("redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8")));
        Assert.assertTrue("state 必须原样透传（编码后）: " + url, url.contains("&state=st-1"));
        Assert.assertFalse("base 尾部斜杠未裁会拼出双斜杠: " + url, url.contains("inkhub//web"));
    }

    @Test
    public void buildAuthorizeUrlOmitsBlankState() {
        String url = client.buildAuthorizeUrl(null);

        Assert.assertFalse("state 为空时不得带 state 参数: " + url, url.contains("state="));
        Assert.assertEquals("configuredRedirectUri 与 URL 里编码值必须同源", REDIRECT_URI,
                client.configuredRedirectUri());
    }

    @Test
    public void exchangeCodeParsesWrappedEnvelope() {
        server.expect(requestTo(BASE_URL + "/web/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(Matchers.containsString("grant_type=authorization_code")))
                .andExpect(content().string(Matchers.containsString("code=the-code")))
                .andExpect(content().string(Matchers.containsString("client_secret=s3cret")))
                .andRespond(withSuccess("{\"status\":{\"code\":0},\"payload\":"
                        + "{\"access_token\":\"tok-wrapped\",\"token_type\":\"Bearer\",\"expires_in\":3600}}",
                        MediaType.APPLICATION_JSON));

        OAuth2TokenRes res = client.exchangeCode("the-code", REDIRECT_URI);

        server.verify();
        Assert.assertEquals("tok-wrapped", res.getAccessToken());
        Assert.assertEquals("Bearer", res.getTokenType());
        Assert.assertEquals(Long.valueOf(3600L), res.getExpiresIn());
    }

    @Test
    public void exchangeCodeParsesBareEnvelopeAndDefaultsTokenType() {
        server.expect(requestTo(BASE_URL + "/web/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"tok-bare\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));

        OAuth2TokenRes res = client.exchangeCode("the-code", REDIRECT_URI);

        server.verify();
        Assert.assertEquals("tok-bare", res.getAccessToken());
        Assert.assertEquals("缺 token_type 时按 Bearer 兜底", "Bearer", res.getTokenType());
        Assert.assertEquals(Long.valueOf(7200L), res.getExpiresIn());
    }

    @Test
    public void exchangeCodeSurfacesRfc6749ErrorBody() {
        server.expect(requestTo(BASE_URL + "/web/oauth2/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\",\"error_description\":\"授权码已失效\"}"));

        try {
            client.exchangeCode("bad-code", REDIRECT_URI);
            Assert.fail("400 必须抛 OAuth2ExchangeException");
        } catch (OAuth2ExchangeException e) {
            Assert.assertTrue("错误消息须含 error 码: " + e.getMessage(),
                    e.getMessage().contains("invalid_grant"));
            Assert.assertTrue("错误消息须含 description（且 UTF-8 未乱码）: " + e.getMessage(),
                    e.getMessage().contains("授权码已失效"));
        }
    }

    @Test
    public void exchangeCodeSurfacesEnvelopeErrorCode() {
        server.expect(requestTo(BASE_URL + "/web/oauth2/token"))
                .andRespond(withSuccess("{\"status\":{\"code\":40301},\"payload\":{}}",
                        MediaType.APPLICATION_JSON));

        try {
            client.exchangeCode("the-code", REDIRECT_URI);
            Assert.fail("信封 status.code != 0 必须抛 OAuth2ExchangeException");
        } catch (OAuth2ExchangeException e) {
            Assert.assertTrue("错误消息须含信封错误码: " + e.getMessage(), e.getMessage().contains("40301"));
        }
    }

    @Test
    public void exchangeCodeRejectsEnvelopeWithoutAccessToken() {
        server.expect(requestTo(BASE_URL + "/web/oauth2/token"))
                .andRespond(withSuccess("{\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));

        try {
            client.exchangeCode("the-code", REDIRECT_URI);
            Assert.fail("缺 access_token 必须抛 OAuth2ExchangeException");
        } catch (OAuth2ExchangeException e) {
            Assert.assertTrue("错误消息须点名缺 access_token: " + e.getMessage(),
                    e.getMessage().contains("access_token"));
        }
    }
}
