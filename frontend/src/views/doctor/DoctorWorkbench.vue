<template>
  <main class="page">
    <div class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">医生端</p>
          <h1>今日接诊队列</h1>
          <p class="muted">查看待接诊患者，保存门诊病历，完成第一阶段诊疗闭环。</p>
        </div>
        <el-button @click="logout">退出</el-button>
      </header>

      <section class="doctor-split">
        <div class="business-pane">
          <section class="grid">
        <el-card class="span-7" shadow="never">
          <template #header>待接诊队列</template>
          <el-table :data="appointments" highlight-current-row @current-change="selectAppointment">
            <el-table-column prop="queueNumber" label="序号" width="80" />
            <el-table-column prop="patientName" label="患者" width="110" />
            <el-table-column prop="source" label="来源" width="100" />
            <el-table-column prop="departmentName" label="科室" width="120" />
            <el-table-column prop="visitDate" label="日期" width="130" />
            <el-table-column prop="period" label="时段" width="100" />
            <el-table-column prop="missedCount" label="过号" width="80" />
            <el-table-column prop="triageSummary" label="AI 问诊摘要" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button size="small" @click.stop="skip(row)">过号</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card class="span-5" shadow="never">
          <template #header>病历编辑</template>
          <el-form label-position="top">
            <el-form-item label="当前患者">
              <el-input :model-value="current?.patientName ?? '请选择队列患者'" disabled />
            </el-form-item>
            <el-form-item label="主诉">
              <el-input v-model="recordForm.chiefComplaint" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="现病史">
              <el-input v-model="recordForm.presentIllness" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="诊断">
              <el-input v-model="recordForm.diagnosis" />
            </el-form-item>
            <el-form-item label="处理方案">
              <el-input v-model="recordForm.treatmentPlan" type="textarea" :rows="4" />
            </el-form-item>
            <el-form-item label="AI内容修订说明">
              <el-input v-model="recordForm.doctorRevisionNote" />
            </el-form-item>
            <el-button type="primary" class="full" :disabled="!current" @click="saveRecord">保存病历并完成接诊</el-button>
          </el-form>
        </el-card>

        <el-card class="span-12" shadow="never">
          <template #header>已保存病历</template>
          <el-table :data="records">
            <el-table-column prop="id" label="病历号" width="120" />
            <el-table-column prop="patientName" label="患者" width="120" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column prop="chiefComplaint" label="主诉" />
            <el-table-column prop="diagnosis" label="诊断" />
            <el-table-column prop="treatmentPlan" label="方案" />
          </el-table>
        </el-card>
          </section>
        </div>

        <aside class="ai-pane">
          <el-card shadow="never" class="ai-card">
            <template #header>
              <div class="ai-header">
                <span>AI 助理医生</span>
                <el-tag type="warning" effect="plain">辅助建议</el-tag>
              </div>
            </template>
            <el-alert
              title="AI 结果不能直接形成诊断或处方"
              type="warning"
              :closable="false"
              show-icon
            />
            <div class="context-block">
              <strong>当前上下文</strong>
              <p>{{ current ? `${current.patientName} · ${current.departmentName}` : '请先在左侧选择患者' }}</p>
              <p class="muted">仅使用本次就诊与已授权的相关病历。</p>
            </div>
            <div class="ai-messages">
              <div v-for="message in aiMessages" :key="message.id" class="ai-message">
                <span>{{ message.label }}</span>
                <p>{{ message.content }}</p>
                <el-button v-if="message.kind === 'diagnosis'" size="small" @click="applyDiagnosis(message)">
                  填入左侧诊断
                </el-button>
              </div>
              <el-empty v-if="!aiMessages.length" description="生成建议后在此显示，左侧业务操作不会被遮挡" :image-size="72" />
            </div>
            <el-input
              v-model="aiPrompt"
              type="textarea"
              :rows="3"
              placeholder="例如：结合主诉和现病史给出鉴别诊断方向"
            />
            <el-button type="primary" class="full ai-action" :disabled="!current" @click="generateAssistance">
              生成辅助建议
            </el-button>
          </el-card>
        </aside>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../../store/auth';
import { getAppointments, skipAppointment, updateAppointmentStatus, type Appointment } from '../../api/appointment';
import { getMedicalRecords, writeDoctorNote, type MedicalRecord } from '../../api/medical-record';
import { getClinicalAssistance } from '../../api/ai';

const router = useRouter();
const auth = useAuthStore();
const appointments = ref<Appointment[]>([]);
const records = ref<MedicalRecord[]>([]);
const current = ref<Appointment>();
const aiPrompt = ref('结合当前病历给出鉴别诊断方向和进一步检查建议');
const aiMessages = ref<Array<{ id: string; label: string; content: string; kind: 'diagnosis' | 'advice' }>>([]);
const diagnosisSource = ref<'HUMAN' | 'AI'>('HUMAN');
const diagnosisAiRecordId = ref<string>();
const recordForm = reactive({
  chiefComplaint: '',
  presentIllness: '',
  diagnosis: '',
  treatmentPlan: '',
  doctorRevisionNote: ''
});

async function selectAppointment(row?: Appointment) {
  current.value = row;
  recordForm.chiefComplaint = row?.triageSummary ?? '';
  recordForm.presentIllness = '';
  recordForm.diagnosis = '';
  recordForm.treatmentPlan = '';
  recordForm.doctorRevisionNote = '';
  aiMessages.value = [];
  diagnosisSource.value = 'HUMAN';
  diagnosisAiRecordId.value = undefined;
  if (row) {
    const currentRecords = await getMedicalRecords({ appointmentId: row.id });
    const currentRecord = currentRecords[0];
    if (currentRecord) {
      recordForm.chiefComplaint = currentRecord.chiefComplaint || currentRecord.aiTriageSummary;
      recordForm.presentIllness = currentRecord.presentIllness ?? '';
      recordForm.diagnosis = currentRecord.diagnosis ?? '';
      recordForm.treatmentPlan = currentRecord.treatmentPlan ?? '';
      recordForm.doctorRevisionNote = currentRecord.doctorRevisionNote ?? '';
    }
  }
}

async function loadQueue() {
  appointments.value = await getAppointments({ doctorId: 'doctor-001', status: 'WAITING' });
}

async function saveRecord() {
  if (!current.value) return;
  await writeDoctorNote({
    appointmentId: current.value.id,
    chiefComplaint: recordForm.chiefComplaint,
    presentIllness: recordForm.presentIllness,
    diagnosis: recordForm.diagnosis,
    treatmentPlan: recordForm.treatmentPlan,
    doctorRevisionNote: recordForm.doctorRevisionNote,
    diagnosisCreatedByType: diagnosisSource.value,
    diagnosisAiRecordId: diagnosisAiRecordId.value
  });
  await updateAppointmentStatus(current.value.id, 'FINISHED');
  ElMessage.success('病历已保存');
  current.value = undefined;
  await loadQueue();
  records.value = await getMedicalRecords({});
}

async function generateAssistance() {
  if (!current.value) return;
  const result = await getClinicalAssistance({
    appointmentId: current.value.id,
    patientId: current.value.patientId,
    chiefComplaint: recordForm.chiefComplaint,
    presentIllness: recordForm.presentIllness,
    prompt: aiPrompt.value
  });
  aiMessages.value = result.suggestions.map((suggestion, index) => ({
    id: index === 0 ? result.aiRecordId : `${result.aiRecordId}-${index}`,
    ...suggestion
  }));
}

function applyDiagnosis(message: { id: string; content: string }) {
  recordForm.diagnosis = message.content;
  diagnosisSource.value = 'AI';
  diagnosisAiRecordId.value = message.id;
  recordForm.doctorRevisionNote = '已由门诊医生复核 AI 诊断草稿';
  ElMessage.warning('已填入 AI 草稿，请医生复核、修改后再保存');
}

async function skip(appointment: Appointment) {
  await skipAppointment(appointment.id);
  ElMessage.success('已顺延 3 个号');
  await loadQueue();
}

function logout() {
  auth.signOut();
  router.push('/login');
}

onMounted(async () => {
  await loadQueue();
  records.value = await getMedicalRecords({});
});
</script>

<style scoped>
.doctor-split {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(320px, 1fr);
  gap: 16px;
  align-items: start;
}

.business-pane {
  min-width: 0;
}

.ai-pane {
  position: sticky;
  top: 20px;
}

.ai-card {
  border-color: #c7d2fe;
}

.ai-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.context-block {
  margin: 16px 0;
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
}

.context-block p,
.ai-message p {
  margin: 6px 0 0;
}

.ai-messages {
  min-height: 260px;
  max-height: 420px;
  overflow: auto;
  margin-bottom: 12px;
}

.ai-message {
  margin-bottom: 12px;
  padding: 12px;
  border-left: 3px solid #6366f1;
  background: #eef2ff;
}

.ai-message span {
  font-weight: 700;
}

.ai-action {
  margin-top: 10px;
}

@media (max-width: 1100px) {
  .doctor-split {
    grid-template-columns: 1fr;
  }

  .ai-pane {
    position: static;
  }
}
</style>
