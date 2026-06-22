from .models import ConsultationRequest, ConsultationResponse, DoctorRecommendation


def consult(request: ConsultationRequest) -> ConsultationResponse:
    text = request.description.lower()
    tags = set(request.symptom_tags)
    neurological = bool(tags & {"头痛", "眩晕"}) or any(word in text for word in ("头痛", "眩晕"))
    urgent = bool(tags & {"剧烈疼痛"}) or any(word in text for word in ("昏迷", "抽搐", "意识不清"))
    department_id = "dept-neuro" if neurological or urgent else "dept-general"
    department_name = "神经内科" if department_id == "dept-neuro" else "全科医学"
    doctors = [
        DoctorRecommendation(doctorId="doctor-001", doctorName="张医生", reason="擅长头痛、眩晕与脑血管疾病")
        if department_id == "dept-neuro"
        else DoctorRecommendation(doctorId="doctor-003", doctorName="陈医生", reason="适合常见病和慢病初诊")
    ]
    risk = "HIGH" if urgent else "MEDIUM" if neurological else "LOW"
    return ConsultationResponse(
        summary=f"症状摘要：{request.description}",
        riskLevel=risk,
        recommendedDepartmentId=department_id,
        recommendedDepartmentName=department_name,
        recommendedDoctors=doctors,
        suggestOfflineUrgent=urgent,
        recordDraft=f"AI 问诊初稿，需由门诊医生复核：{request.description}",
    )
