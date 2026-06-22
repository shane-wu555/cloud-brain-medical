from uuid import uuid4

from .models import ClinicalAssistanceRequest, ClinicalAssistanceResponse, ClinicalSuggestion


def assist(request: ClinicalAssistanceRequest) -> ClinicalAssistanceResponse:
    complaint = request.chief_complaint.strip() or "当前症状"
    history = request.present_illness.strip()
    context = f"；现病史：{history}" if history else ""
    return ClinicalAssistanceResponse(
        aiRecordId=f"ai-assist-{uuid4()}",
        suggestions=[
            ClinicalSuggestion(
                kind="diagnosis",
                label="AI 鉴别诊断草稿",
                content=f"基于主诉“{complaint}”{context}，建议结合神经系统查体、生命体征及正式检查报告形成鉴别诊断。",
            ),
            ClinicalSuggestion(
                kind="advice",
                label="进一步检查建议",
                content=(request.prompt.strip() or "结合本次病历评估进一步检查")
                + "；若存在意识障碍、突发剧烈头痛或局灶神经体征，应优先按急诊规则处置。",
            ),
        ],
    )
