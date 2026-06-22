package com.cloudbrain.medicalorder.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiCtClient {
    private final RestClient client;public AiCtClient(@Value("${services.ai.base-url}")String url){client=RestClient.builder().baseUrl(url).build();}
    @SuppressWarnings("unchecked") public Map<String,Object> submit(String orderId,String objectKey){return client.post().uri("/api/ai/ct-analysis").body(Map.of("orderId",orderId,"objectKey",objectKey,"modality","CT","bodyPart","HEAD")).retrieve().body(Map.class);}
    @SuppressWarnings("unchecked") public Map<String,Object> task(String taskId){return client.get().uri("/api/ai/tasks/{id}",taskId).retrieve().body(Map.class);}
}
