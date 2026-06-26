package com.cloudbrain.auth.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(MockSmsSender.class);

    @Override
    public void sendVerificationCode(String phone, String purpose, String code) {
        log.info("SMS provider=mock purpose={} phone={} code={}", purpose, phone, code);
    }

    @Override
    public boolean isLive() {
        return false;
    }
}
