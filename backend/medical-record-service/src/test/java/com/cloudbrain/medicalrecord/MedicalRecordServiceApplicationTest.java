package com.cloudbrain.medicalrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class MedicalRecordServiceApplicationTest {
    @Test
    void applicationClassCarriesSpringBootAnnotation() {
        assertThat(MedicalRecordServiceApplication.class.isAnnotationPresent(SpringBootApplication.class)).isTrue();
    }

    @Test
    void mainDelegatesToSpringApplicationRun() {
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            String[] args = {"--spring.main.web-application-type=servlet"};

            MedicalRecordServiceApplication.main(args);

            springApplication.verify(() -> SpringApplication.run(MedicalRecordServiceApplication.class, args));
        }
    }
}
