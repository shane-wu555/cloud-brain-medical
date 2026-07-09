package com.cloudbrain.cashier.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.cloudbrain.cashier.repository.CashierRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class InternalCashierControllerTest {
    @Mock
    CashierRepository repository;

    @Test
    void paymentDelegatesToRepositoryAndDefaultsNullAmountToZero() {
        InternalCashierController controller = new InternalCashierController(repository, "internal-key");

        controller.payment(
                "internal-key",
                new InternalCashierController.PaymentCommand("APPOINTMENT", "biz-1", "patient-1", null, "WECHAT", "operator-1"));

        verify(repository).recordPayment("APPOINTMENT", "biz-1", "patient-1", BigDecimal.ZERO, "WECHAT", "operator-1");
    }

    @Test
    void refundDelegatesToRepository() {
        InternalCashierController controller = new InternalCashierController(repository, "internal-key");

        controller.refund(
                "internal-key",
                new InternalCashierController.RefundCommand("PRESCRIPTION", "biz-2", "patient-2", BigDecimal.TEN, "reason", "operator-2"));

        verify(repository).recordRefund("PRESCRIPTION", "biz-2", "patient-2", BigDecimal.TEN, "reason", "operator-2");
    }

    @Test
    void rejectsWhenInternalApiKeyDoesNotMatch() {
        InternalCashierController controller = new InternalCashierController(repository, "internal-key");

        assertThatThrownBy(() -> controller.payment(
                "wrong-key",
                new InternalCashierController.PaymentCommand("APPOINTMENT", "biz-1", "patient-1", BigDecimal.ONE, "WECHAT", "operator-1")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(401));
    }
}
