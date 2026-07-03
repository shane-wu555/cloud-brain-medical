package com.cloudbrain.cashier.controller;

import com.cloudbrain.cashier.repository.CashierRepository;
import com.cloudbrain.cashier.service.AppointmentPaymentClient;
import com.cloudbrain.cashier.service.MedicalOrderPaymentClient;
import com.cloudbrain.cashier.service.PatientAccessClient;
import com.cloudbrain.cashier.service.PrescriptionPaymentClient;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
@RequestMapping("/api")
public class CashierController {
    private static final MediaType SVG_MEDIA_TYPE = MediaType.parseMediaType("image/svg+xml");

    private final CashierRepository repository;
    private final boolean testModeEnabled;
    private final AppointmentPaymentClient appointmentClient;
    private final MedicalOrderPaymentClient medicalOrderClient;
    private final PrescriptionPaymentClient prescriptionClient;
    private final PatientAccessClient patientAccessClient;

    public CashierController(CashierRepository repository,
            AppointmentPaymentClient appointmentClient,
            MedicalOrderPaymentClient medicalOrderClient,
            PrescriptionPaymentClient prescriptionClient,
            PatientAccessClient patientAccessClient,
            @Value("${payment.test-mode-enabled:false}") boolean testModeEnabled) {
        this.repository = repository;
        this.appointmentClient = appointmentClient;
        this.medicalOrderClient = medicalOrderClient;
        this.prescriptionClient = prescriptionClient;
        this.patientAccessClient = patientAccessClient;
        this.testModeEnabled = testModeEnabled;
    }

    @PostMapping("/payments/orders")
    @PreAuthorize("hasAnyRole('PATIENT','CASHIER')")
    public CashierRepository.Payment createOrder(@RequestBody CreatePaymentRequest request, JwtAuthenticationToken auth) {
        String patientId = restrict(request.patientId(), auth);
        BigDecimal amount = request.amount() == null ? new BigDecimal("0.01") : request.amount();
        if (amount.signum() < 0) throw new IllegalArgumentException("支付金额不能为负数");
        return repository.createPaymentOrder(
                request.businessType(),
                request.businessId(),
                patientId,
                amount,
                request.paymentMethod() == null ? "WECHAT" : request.paymentMethod(),
                auth.getToken().getSubject());
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAnyRole('PATIENT','CASHIER','ADMIN')")
    public List<CashierRepository.Payment> payments(
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "businessId", required = false) String businessId,
            @RequestParam(name = "businessType", required = false) String businessType,
            @RequestParam(name = "status", required = false) String status,
            JwtAuthenticationToken auth) {
        return repository.payments(restrict(patientId, auth), businessId, businessType, status);
    }

    @GetMapping("/refunds")
    @PreAuthorize("hasAnyRole('PATIENT','CASHIER','ADMIN')")
    public List<CashierRepository.Refund> refunds(
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "businessId", required = false) String businessId,
            JwtAuthenticationToken auth) {
        return repository.refunds(restrict(patientId, auth), businessId);
    }

    @PostMapping("/refunds/drug-return")
    @PreAuthorize("hasRole('CASHIER')")
    @Transactional
    public Object refundDrugReturn(@RequestBody DrugReturnRefundRequest request, JwtAuthenticationToken auth) {
        if (request.returnId() == null || request.returnId().isBlank()) {
            throw new IllegalArgumentException("returnId 不能为空");
        }
        if (request.prescriptionId() == null || request.prescriptionId().isBlank()) {
            throw new IllegalArgumentException("prescriptionId 不能为空");
        }
        String patientId = restrict(request.patientId(), auth);
        var payment = repository.findOptionalByBusiness("PRESCRIPTION", request.prescriptionId());
        if (payment.isEmpty() || (!"PAID".equals(payment.get().status()) && !"REFUNDED".equals(payment.get().status()))) {
            throw new IllegalStateException("处方没有可退费的已支付记录");
        }
        CashierRepository.Refund refund = repository.recordRefund(
                "PRESCRIPTION",
                request.prescriptionId(),
                patientId,
                request.amount(),
                request.reason() == null || request.reason().isBlank() ? "退药退费" : request.reason(),
                auth.getToken().getSubject());
        prescriptionClient.completeDrugReturn(request.returnId(), auth.getToken().getSubject(), refund.id());
        return refund;
    }

    @PostMapping("/payments/test-callback")
    @PreAuthorize("hasAnyRole('PATIENT','CASHIER','ADMIN')")
    @Transactional
    public CashierRepository.Payment testCallback(@RequestBody TestPaymentRequest request, JwtAuthenticationToken auth) {
        ensureTestModeEnabled();
        String patientId = restrict(request.patientId(), auth);
        if (request.businessType() == null || request.businessType().isBlank()
                || request.businessId() == null || request.businessId().isBlank()) {
            throw new IllegalArgumentException("businessType 和 businessId 不能为空");
        }
        String channel = normalizeChannel(request.channel(), true);
        String tradeNo = request.channelTradeNo() == null || request.channelTradeNo().isBlank()
                ? "test-" + UUID.randomUUID()
                : request.channelTradeNo();
        CashierRepository.Payment payment = repository.recordTestPayment(
                request.businessType(),
                request.businessId(),
                patientId,
                channel,
                auth.getToken().getSubject(),
                tradeNo);
        confirmBusinessPayment(payment);
        return payment;
    }

    @PostMapping("/payments/test-failure")
    @PreAuthorize("hasAnyRole('PATIENT','CASHIER','ADMIN')")
    public CashierRepository.Payment testFailure(@RequestBody TestPaymentRequest request, JwtAuthenticationToken auth) {
        ensureTestModeEnabled();
        String patientId = restrict(request.patientId(), auth);
        CashierRepository.Payment payment = repository.recordTestFailure(
                request.businessType(),
                request.businessId(),
                patientId,
                auth.getToken().getSubject(),
                "SIMULATED_FAILURE");
        if ("APPOINTMENT".equals(request.businessType())) {
            appointmentClient.fail(request.businessId(), patientId, payment.id());
        }
        return payment;
    }

    @GetMapping(value = "/payments/scan-entry", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional
    public ResponseEntity<String> scanEntry(
            @RequestParam("paymentId") String paymentId,
            @RequestParam("channel") String channel) {
        try {
            ensureTestModeEnabled();
            String normalizedChannel = normalizeChannel(channel, false);
            CashierRepository.Payment before = repository.findById(paymentId);
            CashierRepository.Payment payment = repository.recordTestPaymentById(
                    paymentId,
                    normalizedChannel,
                    "PUBLIC_SCAN",
                    buildTradeNo(normalizedChannel, before.businessType(), before.businessId()));
            boolean newlyPaid = "PENDING".equals(before.status()) && "PAID".equals(payment.status());
            if (newlyPaid) {
                confirmBusinessPayment(payment);
            }
            String message = newlyPaid
                    ? "二维码已识别，测试支付状态已同步，收银台页面会自动显示缴费成功。"
                    : "该支付单此前已完成测试支付，无需重复扫码。";
            return htmlResponse(scanResultPage(true, channelLabel(normalizedChannel), message));
        } catch (Exception error) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_HTML)
                    .cacheControl(CacheControl.noStore())
                    .body(scanResultPage(false, channelLabel(channel), errorMessage(error, "扫码处理失败，请返回收银台刷新二维码后重试。")));
        }
    }

    @GetMapping(value = "/payments/qr-code", produces = "image/svg+xml")
    public ResponseEntity<String> qrCode(@RequestParam("target") String target) {
        ensureTestModeEnabled();
        String content = target == null ? "" : target.trim();
        if (content.isBlank()) throw new IllegalArgumentException("target 不能为空");
        if (content.length() > 2048) throw new IllegalArgumentException("二维码内容过长");
        return ResponseEntity.ok()
                .contentType(SVG_MEDIA_TYPE)
                .cacheControl(CacheControl.noStore())
                .body(renderQrCodeSvg(content));
    }

    private void confirmBusinessPayment(CashierRepository.Payment payment) {
        if ("APPOINTMENT".equals(payment.businessType())) {
            appointmentClient.confirm(payment.businessId(), payment.patientId(), payment.paymentMethod(), payment.id());
        }
        if ("MEDICAL_ORDER".equals(payment.businessType())) {
            medicalOrderClient.confirm(payment.businessId(), payment.patientId(), payment.id());
        }
        if ("PRESCRIPTION".equals(payment.businessType())) {
            prescriptionClient.confirm(payment.businessId(), payment.patientId(), payment.id());
        }
    }

    private void ensureTestModeEnabled() {
        if (!testModeEnabled) throw new IllegalStateException("测试支付未启用");
    }

    private String normalizeChannel(String channel, boolean allowSimulated) {
        String normalized = channel == null || channel.isBlank() ? "WECHAT" : channel.toUpperCase();
        List<String> supported = allowSimulated
                ? List.of("WECHAT", "ALIPAY", "MEDICAL_INSURANCE", "SIMULATED")
                : List.of("WECHAT", "ALIPAY", "MEDICAL_INSURANCE");
        if (!supported.contains(normalized)) {
            throw new IllegalArgumentException("不支持的测试支付渠道");
        }
        return normalized;
    }

    private String buildTradeNo(String channel, String businessType, String businessId) {
        return "%s-scan-%s-%s-%d".formatted(
                channel.toLowerCase(),
                businessType.toLowerCase(),
                businessId,
                System.currentTimeMillis());
    }

    private ResponseEntity<String> htmlResponse(String html) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noStore())
                .body(html);
    }

    private String renderQrCodeSvg(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    320,
                    320,
                    Map.of(
                            EncodeHintType.MARGIN, 1,
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            return toSvg(matrix);
        } catch (WriterException error) {
            throw new IllegalStateException("二维码生成失败", error);
        }
    }

    private String toSvg(BitMatrix matrix) {
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < matrix.getHeight(); y += 1) {
            for (int x = 0; x < matrix.getWidth(); x += 1) {
                if (matrix.get(x, y)) {
                    path.append('M').append(x).append(' ').append(y).append("h1v1H").append(x).append('z');
                }
            }
        }
        return """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" shape-rendering="crispEdges" role="img" aria-label="支付二维码">
                  <rect width="100%%" height="100%%" fill="#ffffff"/>
                  <path d="%s" fill="#111827"/>
                </svg>
                """.formatted(matrix.getWidth(), matrix.getHeight(), path);
    }

    private String scanResultPage(boolean success, String channelLabel, String message) {
        String title = success ? "扫码成功" : "扫码失败";
        String badge = success ? "已同步收银台" : "请返回窗口重试";
        String tone = success ? "#16a34a" : "#dc2626";
        String safeChannel = HtmlUtils.htmlEscape(channelLabel);
        String safeMessage = HtmlUtils.htmlEscape(message);
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>%s</title>
                  <style>
                    :root { color-scheme: light; }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      min-height: 100vh;
                      display: grid;
                      place-items: center;
                      padding: 24px;
                      background:
                        radial-gradient(circle at top, rgba(14, 165, 233, 0.16), transparent 38%%),
                        linear-gradient(180deg, #f8fbff 0%%, #eef6ff 100%%);
                      font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
                      color: #0f172a;
                    }
                    .scan-card {
                      width: min(100%%, 420px);
                      padding: 28px 24px;
                      border-radius: 24px;
                      background: rgba(255, 255, 255, 0.96);
                      border: 1px solid rgba(148, 163, 184, 0.18);
                      box-shadow: 0 18px 48px rgba(15, 23, 42, 0.12);
                    }
                    .scan-chip {
                      display: inline-flex;
                      align-items: center;
                      padding: 6px 12px;
                      border-radius: 999px;
                      background: rgba(255, 255, 255, 0.9);
                      color: %s;
                      border: 1px solid rgba(15, 23, 42, 0.08);
                      font-size: 13px;
                      font-weight: 700;
                    }
                    h1 {
                      margin: 18px 0 8px;
                      font-size: 28px;
                      line-height: 1.15;
                    }
                    p {
                      margin: 0;
                      color: #475569;
                      line-height: 1.7;
                      font-size: 15px;
                    }
                    .channel {
                      margin-top: 16px;
                      padding-top: 16px;
                      border-top: 1px solid #e2e8f0;
                      color: #1e293b;
                      font-weight: 600;
                    }
                  </style>
                </head>
                <body>
                  <section class="scan-card">
                    <span class="scan-chip">%s</span>
                    <h1>%s</h1>
                    <p>%s</p>
                    <div class="channel">支付方式：%s</div>
                  </section>
                </body>
                </html>
                """.formatted(title, tone, badge, title, safeMessage, safeChannel);
    }

    private String channelLabel(String channel) {
        if (channel == null || channel.isBlank()) return "扫码支付";
        return switch (channel.toUpperCase()) {
            case "WECHAT" -> "微信支付";
            case "ALIPAY" -> "支付宝支付";
            case "MEDICAL_INSURANCE" -> "医保卡支付";
            case "SIMULATED" -> "模拟支付";
            default -> channel;
        };
    }

    private String errorMessage(Exception error, String fallback) {
        if (error.getMessage() != null && !error.getMessage().isBlank()) return error.getMessage();
        return fallback;
    }

    private String restrict(String patientId, JwtAuthenticationToken auth) {
        if (!"PATIENT".equals(auth.getToken().getClaimAsString("role"))) return patientId;
        String accountId = auth.getToken().getSubject();
        String scopedPatientId = patientId;
        if (scopedPatientId == null || scopedPatientId.isBlank()) {
            scopedPatientId = patientAccessClient.boundPatientId(accountId);
        }
        if (scopedPatientId == null || scopedPatientId.isBlank()) {
            throw new AccessDeniedException("请先添加并绑定就诊人");
        }
        if (!patientAccessClient.owns(accountId, scopedPatientId)) {
            throw new AccessDeniedException("患者只能查看自己账户名下就诊人的费用记录");
        }
        return scopedPatientId;
    }

    public record TestPaymentRequest(String businessType, String businessId, String patientId,
            String channel, String channelTradeNo) {
    }

    public record CreatePaymentRequest(String businessType, String businessId, String patientId,
            BigDecimal amount, String paymentMethod) {
    }

    public record DrugReturnRefundRequest(String returnId, String prescriptionId, String patientId,
            BigDecimal amount, String reason) {
    }
}
