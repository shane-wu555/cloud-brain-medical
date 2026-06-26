package com.cloudbrain.auth.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AliyunSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(AliyunSmsSender.class);
    private static final DateTimeFormatter ALIYUN_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final SmsProperties.Aliyun properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AliyunSmsSender(SmsProperties.Aliyun properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        validateConfiguration();
    }

    @Override
    public void sendVerificationCode(String phone, String purpose, String code) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("AccessKeyId", properties.getAccessKeyId());
        params.put("Action", "SendSmsVerifyCode");
        params.put("AutoRetry", "1");
        params.put("CountryCode", "86");
        params.put("DuplicatePolicy", "1");
        params.put("Format", "JSON");
        params.put("Interval", "60");
        params.put("PhoneNumber", phone);
        params.put("RegionId", properties.getRegionId());
        params.put("SchemeName", resolveSchemeName(purpose));
        params.put("SignName", properties.getSignName());
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("SignatureVersion", "1.0");
        params.put("TemplateCode", resolveTemplateCode(purpose));
        params.put("TemplateParam", "{\"code\":\"" + code + "\",\"min\":\"5\"}");
        params.put("Timestamp", ALIYUN_TIMESTAMP_FORMATTER.format(Instant.now()));
        params.put("ValidTime", "300");
        params.put("Version", "2017-05-25");

        String requestBody = buildSignedRequestBody(params);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + properties.getEndpoint() + "/"))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode body = parseBody(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String providerError = buildProviderErrorMessage(response.statusCode(), body, response.body());
                log.warn("Aliyun SMS request rejected purpose={} phone={} error={}",
                        purpose, maskPhone(phone), providerError);
                throw new IllegalArgumentException(toUserFacingMessage(body, response.statusCode(), providerError));
            }
            String resultCode = body.path("Code").asText();
            if (!"OK".equals(resultCode)) {
                String providerError = buildProviderErrorMessage(response.statusCode(), body, response.body());
                log.warn("Aliyun SMS provider business failure purpose={} phone={} error={}",
                        purpose, maskPhone(phone), providerError);
                throw new IllegalArgumentException(toUserFacingMessage(body, response.statusCode(), providerError));
            }
            log.info("SMS sent via Aliyun purpose={} phone={} requestId={}",
                    purpose, maskPhone(phone), body.path("RequestId").asText());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to send SMS verification code", exception);
        }
    }

    @Override
    public boolean isLive() {
        return true;
    }

    private void validateConfiguration() {
        require("sms.aliyun.access-key-id", properties.getAccessKeyId());
        require("sms.aliyun.access-key-secret", properties.getAccessKeySecret());
        require("sms.aliyun.sign-name", properties.getSignName());
        require("sms.aliyun.endpoint", properties.getEndpoint());
        require("sms.aliyun.region-id", properties.getRegionId());
        resolveTemplateCode("LOGIN");
        resolveTemplateCode("REGISTER");
        resolveTemplateCode("RESET_PASSWORD");
        resolveSchemeName("LOGIN");
        resolveSchemeName("REGISTER");
        resolveSchemeName("RESET_PASSWORD");
    }

    private String buildSignedRequestBody(Map<String, String> params) {
        String canonicalizedQuery = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> percentEncode(entry.getKey()) + "=" + percentEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
        String stringToSign = "POST&%2F&" + percentEncode(canonicalizedQuery);
        String signature = sign(stringToSign, properties.getAccessKeySecret() + "&");
        return canonicalizedQuery + "&Signature=" + percentEncode(signature);
    }

    private String sign(String content, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to sign SMS request", exception);
        }
    }

    private String percentEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private void require(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(field + " must be configured when sms.provider=aliyun");
        }
    }

    private String resolveTemplateCode(String purpose) {
        String code = switch (purpose) {
            case "LOGIN" -> firstNonBlank(properties.getTemplateCodeLogin(), properties.getTemplateCode());
            case "REGISTER" -> firstNonBlank(properties.getTemplateCodeRegister(), properties.getTemplateCode());
            case "RESET_PASSWORD" -> firstNonBlank(properties.getTemplateCodeResetPassword(), properties.getTemplateCode());
            default -> throw new IllegalArgumentException("Unsupported SMS purpose: " + purpose);
        };
        if (code == null || code.isBlank()) {
            throw new IllegalStateException("A template code must be configured for sms purpose " + purpose);
        }
        return code;
    }

    private String resolveSchemeName(String purpose) {
        String schemeName = switch (purpose) {
            case "LOGIN" -> properties.getSchemeNameLogin();
            case "REGISTER" -> properties.getSchemeNameRegister();
            case "RESET_PASSWORD" -> properties.getSchemeNameResetPassword();
            default -> throw new IllegalArgumentException("Unsupported SMS purpose: " + purpose);
        };
        if (schemeName == null || schemeName.isBlank()) {
            throw new IllegalStateException("A scheme name must be configured for sms purpose " + purpose);
        }
        return schemeName;
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private JsonNode parseBody(String body) throws IOException {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }

    private String buildProviderErrorMessage(int httpStatus, JsonNode body, String rawBody) {
        String code = body.path("Code").asText("");
        String message = body.path("Message").asText("");
        String detail = body.path("AccessDeniedDetail").asText("");
        if (!code.isBlank() || !message.isBlank() || !detail.isBlank()) {
            StringBuilder builder = new StringBuilder("SMS provider request failed");
            if (!code.isBlank()) {
                builder.append(": ").append(code);
            }
            if (!message.isBlank()) {
                builder.append(" - ").append(message);
            }
            if (!detail.isBlank()) {
                builder.append(" (").append(detail).append(")");
            }
            builder.append(" [HTTP ").append(httpStatus).append("]");
            return builder.toString();
        }
        return "SMS provider request failed with HTTP " + httpStatus + ": " + rawBody;
    }

    private String toUserFacingMessage(JsonNode body, int httpStatus, String fallback) {
        String code = body.path("Code").asText("").trim();
        String message = body.path("Message").asText("").trim();
        if ("biz.FREQUENCY".equalsIgnoreCase(code)) {
            return "获取验证码太频繁，请稍后再试";
        }
        if ("UNKNOWN".equalsIgnoreCase(code) && "UNKNOWN".equalsIgnoreCase(message)) {
            return "该手机号暂时无法接收短信，请稍后重试或更换手机号";
        }
        if ("Forbidden.NoPermission".equalsIgnoreCase(code)) {
            return "短信服务尚未授权，请联系管理员检查阿里云 RAM 权限";
        }
        if (httpStatus >= 500) {
            return "短信服务暂时不可用，请稍后再试";
        }
        return fallback;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
