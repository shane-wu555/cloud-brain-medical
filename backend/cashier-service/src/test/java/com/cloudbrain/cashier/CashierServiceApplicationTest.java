package com.cloudbrain.cashier;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class CashierServiceApplicationTest {
    @Test
    void applicationClassCarriesSpringBootAnnotation() {
        assertThat(CashierServiceApplication.class.isAnnotationPresent(SpringBootApplication.class)).isTrue();
    }

    @Test
    void mainMethodExists() throws Exception {
        assertThat(CashierServiceApplication.class.getDeclaredMethod("main", String[].class)).isNotNull();
    }
}
