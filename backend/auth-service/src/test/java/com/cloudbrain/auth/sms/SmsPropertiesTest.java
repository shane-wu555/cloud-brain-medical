package com.cloudbrain.auth.sms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SmsPropertiesTest {
    @Test
    void propertiesExposeProviderAndAliyunSettings() {
        SmsProperties properties = new SmsProperties();
        SmsProperties.Aliyun aliyun = properties.getAliyun();

        properties.setProvider("aliyun");
        aliyun.setEndpoint("endpoint");
        aliyun.setRegionId("region");
        aliyun.setAccessKeyId("key-id");
        aliyun.setAccessKeySecret("key-secret");
        aliyun.setSignName("sign");
        aliyun.setTemplateCode("template");
        aliyun.setTemplateCodeLogin("template-login");
        aliyun.setTemplateCodeRegister("template-register");
        aliyun.setTemplateCodeResetPassword("template-reset");
        aliyun.setSchemeNameLogin("LOGIN_SCHEME");
        aliyun.setSchemeNameRegister("REGISTER_SCHEME");
        aliyun.setSchemeNameResetPassword("RESET_SCHEME");

        assertThat(properties.getProvider()).isEqualTo("aliyun");
        assertThat(aliyun.getEndpoint()).isEqualTo("endpoint");
        assertThat(aliyun.getRegionId()).isEqualTo("region");
        assertThat(aliyun.getAccessKeyId()).isEqualTo("key-id");
        assertThat(aliyun.getAccessKeySecret()).isEqualTo("key-secret");
        assertThat(aliyun.getSignName()).isEqualTo("sign");
        assertThat(aliyun.getTemplateCode()).isEqualTo("template");
        assertThat(aliyun.getTemplateCodeLogin()).isEqualTo("template-login");
        assertThat(aliyun.getTemplateCodeRegister()).isEqualTo("template-register");
        assertThat(aliyun.getTemplateCodeResetPassword()).isEqualTo("template-reset");
        assertThat(aliyun.getSchemeNameLogin()).isEqualTo("LOGIN_SCHEME");
        assertThat(aliyun.getSchemeNameRegister()).isEqualTo("REGISTER_SCHEME");
        assertThat(aliyun.getSchemeNameResetPassword()).isEqualTo("RESET_SCHEME");
    }
}
