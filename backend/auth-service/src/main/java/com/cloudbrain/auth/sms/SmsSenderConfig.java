package com.cloudbrain.auth.sms;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsSenderConfig {
    @Bean
    public SmsSender smsSender(SmsProperties properties, ObjectMapper objectMapper) {
        if ("aliyun".equalsIgnoreCase(properties.getProvider())) {
            return new AliyunSmsSender(properties.getAliyun(), objectMapper);
        }
        return new MockSmsSender();
    }
}
