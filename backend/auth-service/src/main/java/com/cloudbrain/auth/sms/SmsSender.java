package com.cloudbrain.auth.sms;

public interface SmsSender {
    void sendVerificationCode(String phone, String purpose, String code);

    boolean isLive();
}
