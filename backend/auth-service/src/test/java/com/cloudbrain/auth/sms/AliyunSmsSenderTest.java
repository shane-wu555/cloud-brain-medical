package com.cloudbrain.auth.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class AliyunSmsSenderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void constructorRejectsMissingConfiguration() {
        SmsProperties.Aliyun aliyun = new SmsProperties.Aliyun();

        assertThatThrownBy(() -> new AliyunSmsSender(aliyun, objectMapper))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void senderReportsLiveProvider() {
        assertThat(new AliyunSmsSender(configuredAliyun(), objectMapper).isLive()).isTrue();
    }

    @Test
    void sendVerificationCodeSucceedsWhenProviderReturnsOk() throws Exception {
        AliyunSmsSender sender = new AliyunSmsSender(configuredAliyun(), objectMapper);
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        ReflectionTestUtils.setField(sender, "httpClient", httpClient);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"Code\":\"OK\",\"RequestId\":\"req-1\"}");
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        sender.sendVerificationCode("13900000000", "LOGIN", "123456");
    }

    @Test
    void sendVerificationCodeMapsProviderBusinessErrorToIllegalArgumentException() throws Exception {
        AliyunSmsSender sender = new AliyunSmsSender(configuredAliyun(), objectMapper);
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        ReflectionTestUtils.setField(sender, "httpClient", httpClient);
        when(response.statusCode()).thenReturn(429);
        when(response.body()).thenReturn("{\"Code\":\"biz.FREQUENCY\",\"Message\":\"too often\"}");
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThatThrownBy(() -> sender.sendVerificationCode("13900000000", "LOGIN", "123456"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendVerificationCodeWrapsIoFailure() throws Exception {
        AliyunSmsSender sender = new AliyunSmsSender(configuredAliyun(), objectMapper);
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        ReflectionTestUtils.setField(sender, "httpClient", httpClient);
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("offline"));

        assertThatThrownBy(() -> sender.sendVerificationCode("13900000000", "REGISTER", "123456"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sendVerificationCodeMapsAdditionalProviderFailures() throws Exception {
        AliyunSmsSender sender = new AliyunSmsSender(configuredAliyun(), objectMapper);
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        ReflectionTestUtils.setField(sender, "httpClient", httpClient);
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"Code\":\"Forbidden.NoPermission\",\"Message\":\"denied\"}");
        assertThatThrownBy(() -> sender.sendVerificationCode("13900000000", "RESET_PASSWORD", "123456"))
                .isInstanceOf(IllegalArgumentException.class);

        when(response.statusCode()).thenReturn(503);
        when(response.body()).thenReturn("");
        assertThatThrownBy(() -> sender.sendVerificationCode("13900000000", "LOGIN", "123456"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void helperMethodsCoverFallbackAndValidationBranches() throws Exception {
        AliyunSmsSender sender = new AliyunSmsSender(configuredAliyun(), objectMapper);
        ObjectNode frequency = objectMapper.createObjectNode().put("Code", "biz.FREQUENCY").put("Message", "too often");
        ObjectNode unknown = objectMapper.createObjectNode().put("Code", "UNKNOWN").put("Message", "UNKNOWN");
        ObjectNode forbidden = objectMapper.createObjectNode().put("Code", "Forbidden.NoPermission").put("Message", "denied");
        ObjectNode detailed = objectMapper
                .createObjectNode()
                .put("Code", "ERR")
                .put("Message", "bad")
                .put("AccessDeniedDetail", "detail");

        assertThat(invoke(sender, "toUserFacingMessage", new Class<?>[] {JsonNode.class, int.class, String.class}, frequency, 429, "fallback"))
                .isEqualTo("获取验证码太频繁，请稍后再试");
        assertThat(invoke(sender, "toUserFacingMessage", new Class<?>[] {JsonNode.class, int.class, String.class}, unknown, 400, "fallback"))
                .isEqualTo("该手机号暂时无法接收短信，请稍后重试或更换手机号");
        assertThat(invoke(sender, "toUserFacingMessage", new Class<?>[] {JsonNode.class, int.class, String.class}, forbidden, 403, "fallback"))
                .isEqualTo("短信服务尚未授权，请联系管理员检查阿里云 RAM 权限");
        assertThat(invoke(sender, "toUserFacingMessage", new Class<?>[] {JsonNode.class, int.class, String.class}, objectMapper.createObjectNode(), 503, "fallback"))
                .isEqualTo("短信服务暂时不可用，请稍后再试");
        assertThat(invoke(sender, "toUserFacingMessage", new Class<?>[] {JsonNode.class, int.class, String.class}, objectMapper.createObjectNode(), 400, "fallback"))
                .isEqualTo("fallback");

        assertThat(invoke(sender, "buildProviderErrorMessage", new Class<?>[] {int.class, JsonNode.class, String.class}, 403, detailed, "raw"))
                .isEqualTo("SMS provider request failed: ERR - bad (detail) [HTTP 403]");
        assertThat(invoke(sender, "buildProviderErrorMessage", new Class<?>[] {int.class, JsonNode.class, String.class}, 500, objectMapper.createObjectNode(), "raw-body"))
                .isEqualTo("SMS provider request failed with HTTP 500: raw-body");

        assertThat(invoke(sender, "percentEncode", new Class<?>[] {String.class}, "a+b*c~d"))
                .isEqualTo("a%2Bb%2Ac~d");
        assertThat(invoke(sender, "maskPhone", new Class<?>[] {String.class}, "13900000000"))
                .isEqualTo("139****0000");
        assertThat(invoke(sender, "maskPhone", new Class<?>[] {String.class}, "12345"))
                .isEqualTo("12345");
    }

    @Test
    void constructorAcceptsFallbackTemplateCodesAndRejectsMissingSchemeOrUnsupportedPurpose() throws Exception {
        SmsProperties.Aliyun fallback = configuredAliyun();
        fallback.setTemplateCodeLogin(" ");
        fallback.setTemplateCodeRegister(null);
        fallback.setTemplateCodeResetPassword("");
        AliyunSmsSender sender = new AliyunSmsSender(fallback, objectMapper);

        assertThat(invoke(sender, "resolveTemplateCode", new Class<?>[] {String.class}, "LOGIN"))
                .isEqualTo("template-common");
        assertThat(invoke(sender, "resolveTemplateCode", new Class<?>[] {String.class}, "REGISTER"))
                .isEqualTo("template-common");
        assertThat(invoke(sender, "resolveTemplateCode", new Class<?>[] {String.class}, "RESET_PASSWORD"))
                .isEqualTo("template-common");

        assertThatThrownBy(() -> invoke(sender, "resolveTemplateCode", new Class<?>[] {String.class}, "OTHER"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> invoke(sender, "resolveSchemeName", new Class<?>[] {String.class}, "OTHER"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);

        SmsProperties.Aliyun invalidScheme = configuredAliyun();
        invalidScheme.setSchemeNameLogin(" ");
        assertThatThrownBy(() -> new AliyunSmsSender(invalidScheme, objectMapper))
                .isInstanceOf(IllegalStateException.class);
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private SmsProperties.Aliyun configuredAliyun() {
        SmsProperties.Aliyun aliyun = new SmsProperties.Aliyun();
        aliyun.setAccessKeyId("key-id");
        aliyun.setAccessKeySecret("key-secret");
        aliyun.setSignName("sign");
        aliyun.setEndpoint("dypnsapi.aliyuncs.com");
        aliyun.setRegionId("cn-hangzhou");
        aliyun.setTemplateCode("template-common");
        aliyun.setTemplateCodeLogin("template-login");
        aliyun.setTemplateCodeRegister("template-register");
        aliyun.setTemplateCodeResetPassword("template-reset");
        aliyun.setSchemeNameLogin("LOGIN");
        aliyun.setSchemeNameRegister("REGISTER");
        aliyun.setSchemeNameResetPassword("RESET_PASSWORD");
        return aliyun;
    }
}
