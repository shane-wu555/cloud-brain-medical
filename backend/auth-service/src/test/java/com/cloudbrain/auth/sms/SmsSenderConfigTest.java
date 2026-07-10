package com.cloudbrain.auth.sms;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SmsSenderConfigTest {
    private final SmsSenderConfig config = new SmsSenderConfig();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsMockSenderWhenProviderIsNotAliyun() {
        SmsProperties properties = new SmsProperties();
        properties.setProvider("mock");

        SmsSender sender = config.smsSender(properties, objectMapper);

        assertThat(sender).isInstanceOf(MockSmsSender.class);
    }

    @Test
    void returnsAliyunSenderWhenProviderIsAliyun() {
        SmsProperties properties = configuredAliyunProperties();

        SmsSender sender = config.smsSender(properties, objectMapper);

        assertThat(sender).isInstanceOf(AliyunSmsSender.class);
    }

    private SmsProperties configuredAliyunProperties() {
        SmsProperties properties = new SmsProperties();
        properties.setProvider("aliyun");
        SmsProperties.Aliyun aliyun = properties.getAliyun();
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
        return properties;
    }
}
