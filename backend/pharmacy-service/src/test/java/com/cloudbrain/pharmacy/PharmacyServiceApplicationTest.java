package com.cloudbrain.pharmacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

class PharmacyServiceApplicationTest {
    @Test
    void applicationClassCarriesExpectedAnnotations() {
        assertThat(PharmacyServiceApplication.class.isAnnotationPresent(SpringBootApplication.class)).isTrue();
        assertThat(PharmacyServiceApplication.class.isAnnotationPresent(EnableScheduling.class)).isTrue();
    }

    @Test
    void mainMethodExists() throws Exception {
        assertThat(PharmacyServiceApplication.class.getDeclaredMethod("main", String[].class)).isNotNull();
    }

    @Test
    void mainDelegatesToSpringApplicationRun() {
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            String[] args = {"--spring.main.web-application-type=none"};

            PharmacyServiceApplication.main(args);

            springApplication.verify(() -> SpringApplication.run(PharmacyServiceApplication.class, args));
        }
    }
}
