package com.cloudbrain.auth.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sms")
public class SmsProperties {
    private String provider = "mock";
    private final Aliyun aliyun = new Aliyun();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Aliyun getAliyun() {
        return aliyun;
    }

    public static class Aliyun {
        private String endpoint = "dypnsapi.aliyuncs.com";
        private String regionId = "cn-hangzhou";
        private String accessKeyId = "";
        private String accessKeySecret = "";
        private String signName = "";
        private String templateCode = "";
        private String templateCodeLogin = "";
        private String templateCodeRegister = "";
        private String templateCodeResetPassword = "";
        private String schemeNameLogin = "LOGIN";
        private String schemeNameRegister = "REGISTER";
        private String schemeNameResetPassword = "RESET_PASSWORD";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegionId() {
            return regionId;
        }

        public void setRegionId(String regionId) {
            this.regionId = regionId;
        }

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }

        public String getSignName() {
            return signName;
        }

        public void setSignName(String signName) {
            this.signName = signName;
        }

        public String getTemplateCode() {
            return templateCode;
        }

        public void setTemplateCode(String templateCode) {
            this.templateCode = templateCode;
        }

        public String getTemplateCodeLogin() {
            return templateCodeLogin;
        }

        public void setTemplateCodeLogin(String templateCodeLogin) {
            this.templateCodeLogin = templateCodeLogin;
        }

        public String getTemplateCodeRegister() {
            return templateCodeRegister;
        }

        public void setTemplateCodeRegister(String templateCodeRegister) {
            this.templateCodeRegister = templateCodeRegister;
        }

        public String getTemplateCodeResetPassword() {
            return templateCodeResetPassword;
        }

        public void setTemplateCodeResetPassword(String templateCodeResetPassword) {
            this.templateCodeResetPassword = templateCodeResetPassword;
        }

        public String getSchemeNameLogin() {
            return schemeNameLogin;
        }

        public void setSchemeNameLogin(String schemeNameLogin) {
            this.schemeNameLogin = schemeNameLogin;
        }

        public String getSchemeNameRegister() {
            return schemeNameRegister;
        }

        public void setSchemeNameRegister(String schemeNameRegister) {
            this.schemeNameRegister = schemeNameRegister;
        }

        public String getSchemeNameResetPassword() {
            return schemeNameResetPassword;
        }

        public void setSchemeNameResetPassword(String schemeNameResetPassword) {
            this.schemeNameResetPassword = schemeNameResetPassword;
        }
    }
}
