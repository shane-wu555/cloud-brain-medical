package com.cloudbrain.auth.sms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MockSmsSenderTest {
    @Test
    void mockSenderIsNotLiveAndAcceptsSendRequest() {
        MockSmsSender sender = new MockSmsSender();

        sender.sendVerificationCode("13900000000", "LOGIN", "123456");

        assertThat(sender.isLive()).isFalse();
    }
}
