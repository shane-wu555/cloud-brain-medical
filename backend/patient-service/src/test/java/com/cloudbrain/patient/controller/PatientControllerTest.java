package com.cloudbrain.patient.controller;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class PatientControllerTest {
    @Test
    void validatesChineseResidentIdentityChecksum() {
        assertThat(PatientController.validIdCard("11010519491231002X")).isTrue();
        assertThat(PatientController.validIdCard("110105194912310021")).isFalse();
        assertThat(PatientController.validIdCard("123")).isFalse();
    }
}
