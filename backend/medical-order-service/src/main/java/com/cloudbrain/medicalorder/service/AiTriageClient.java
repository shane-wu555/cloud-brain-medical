package com.cloudbrain.medicalorder.service;

import com.cloudbrain.medicalorder.domain.MedicalOrder;
import com.cloudbrain.medicalorder.repository.MedicalOrderRepository.ExecutorCandidate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiTriageClient {
    private final RestClient client;
    public AiTriageClient(@Value("${services.ai.base-url}") String url){client=RestClient.builder().baseUrl(url).build();}
    @SuppressWarnings("unchecked")
    public TriageResult triage(MedicalOrder order,List<ExecutorCandidate> candidates){
        List<Map<String,Object>> payloadCandidates=candidates.stream().map(c->Map.<String,Object>of(
                "doctorId",c.id(),"doctorName",c.name(),"specialties",Arrays.asList(c.specialties().split(",")),
                "currentLoad",c.currentLoad(),"capacity",c.capacity(),"available",true,"location",c.location(),
                "equipmentIds",c.equipmentIds()==null?List.of():Arrays.asList(c.equipmentIds().split(",")))).toList();
        try{
            Map<String,Object> result=client.post().uri("/api/ai/triage").body(Map.of(
                    "orderId",order.id(),"projectType",order.projectName(),"bodyPart",order.bodyPart()==null?"":order.bodyPart(),
                    "requiredSpecialty",order.projectName(),"urgency",order.urgency(),"candidates",payloadCandidates)).retrieve().body(Map.class);
            return new TriageResult((String)result.get("doctorId"),(String)result.get("doctorName"),(String)result.get("location"),
                    (String)result.get("equipmentId"),"AI",String.join("；",(List<String>)result.getOrDefault("reasons",List.of())));
        }catch(Exception ignored){
            ExecutorCandidate selected=candidates.stream().min(Comparator.comparingInt(ExecutorCandidate::currentLoad)).orElseThrow(()->new IllegalStateException("没有可用执行医生"));
            String equipment=selected.equipmentIds()==null?null:selected.equipmentIds().split(",")[0];
            return new TriageResult(selected.id(),selected.name(),selected.location(),equipment,"RULE","AI 不可用，按最低负载规则分配");
        }
    }
    public record TriageResult(String executorId,String executorName,String location,String equipmentId,String source,String reasons){}
}
