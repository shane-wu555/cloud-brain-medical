package com.cloudbrain.patient;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

class PatientServiceApplicationTest {
    @Test
    void applicationClassCarriesExpectedAnnotations() {
        assertThat(PatientServiceApplication.class.isAnnotationPresent(SpringBootApplication.class)).isTrue();
        assertThat(PatientServiceApplication.class.isAnnotationPresent(EnableMethodSecurity.class)).isTrue();
    }

    @Test
    void mainMethodExists() throws Exception {
        assertThat(PatientServiceApplication.class.getDeclaredMethod("main", String[].class)).isNotNull();
    }
}
