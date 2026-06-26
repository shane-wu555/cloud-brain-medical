package com.cloudbrain.appointment.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PatientVerificationClient {
    private final RestClient client; private final String internalApiKey;
    public PatientVerificationClient(@Value("${services.patient.base-url}") String url,@Value("${internal.api-key}") String key) {
        client=RestClient.builder().baseUrl(url).build(); internalApiKey=key;
    }
    public boolean isVerified(String patientId) {
        Map<String,Boolean> result=client.get().uri("/api/internal/patients/{id}/verification",patientId)
                .header("X-Internal-Api-Key",internalApiKey).retrieve()
                .body(new ParameterizedTypeReference<Map<String,Boolean>>(){});
        return result!=null && Boolean.TRUE.equals(result.get("realNameVerified"));
    }

    public boolean owns(String accountId,String patientId) {
        Map<String,Boolean> result=client.get().uri(uriBuilder -> uriBuilder
                        .path("/api/internal/patients/{id}/ownership")
                        .queryParam("accountId",accountId)
                        .build(patientId))
                .header("X-Internal-Api-Key",internalApiKey).retrieve()
                .body(new ParameterizedTypeReference<Map<String,Boolean>>(){});
        return result!=null && Boolean.TRUE.equals(result.get("owned"));
    }

    public boolean hasBoundPatient(String accountId) {
        Map<String,Object> result=client.get().uri("/api/internal/patients/accounts/{accountId}/binding",accountId)
                .header("X-Internal-Api-Key",internalApiKey).retrieve()
                .body(new ParameterizedTypeReference<Map<String,Object>>(){});
        return result!=null && Boolean.TRUE.equals(result.get("hasBoundPatient"));
    }

    public String boundPatientId(String accountId) {
        Map<String,Object> result=client.get().uri("/api/internal/patients/accounts/{accountId}/binding",accountId)
                .header("X-Internal-Api-Key",internalApiKey).retrieve()
                .body(new ParameterizedTypeReference<Map<String,Object>>(){});
        if(result==null || !Boolean.TRUE.equals(result.get("hasBoundPatient"))) return null;
        Object id=result.get("boundPatientId");
        return id==null?null:id.toString();
    }
}
