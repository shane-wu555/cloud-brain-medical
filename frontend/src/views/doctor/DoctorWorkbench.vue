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
          <el-input v-model="queueKeyword" clearable placeholder="按患者姓名或业务编号搜索" style="margin-bottom:12px" />
          <el-table :data="filteredAppointments" highlight-current-row @current-change="selectAppointment">
            <el-table-column prop="queueNumber" label="序号" width="80" />
            <el-table-column prop="patientName" label="患者" width="110" />
            <el-table-column prop="source" label="来源" width="100" />
            <el-table-column prop="departmentName" label="科室" width="120" />
            <el-table-column prop="visitDate" label="日期" width="130" />
            <el-table-column prop="period" label="时段" width="100" />
            <el-table-column prop="missedCount" label="过号" width="80" />
            <el-table-column prop="triageSummary" label="AI 问诊摘要" />
            <el-table-column label="操作" width="210">
              <template #default="{ row }">
                <el-button v-if="row.status === 'WAITING'" size="small" type="primary" link @click.stop="call(row)">叫号</el-button>
                <el-button v-if="row.status === 'WAITING' || row.status === 'CALLED'" size="small" type="success" link @click.stop="start(row)">接诊</el-button>
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
            <el-form-item label="既往史"><el-input v-model="recordForm.pastHistory" type="textarea" :rows="2" /></el-form-item>
            <el-form-item label="过敏史"><el-input v-model="recordForm.allergyHistory" type="textarea" :rows="2" /></el-form-item>
            <el-form-item label="体格检查"><el-input v-model="recordForm.physicalExamination" type="textarea" :rows="2" /></el-form-item>
            <el-form-item label="初步诊断">
              <el-input v-model="recordForm.diagnosis" />
            </el-form-item>
            <el-form-item label="处理方案">
              <el-input v-model="recordForm.treatmentPlan" type="textarea" :rows="4" />
            </el-form-item>
            <el-form-item label="AI内容修订说明">
              <el-input v-model="recordForm.doctorRevisionNote" />
            </el-form-item>
            <el-button type="primary" :disabled="!current" @click="saveRecord">保存病历</el-button>
            <el-button type="success" :disabled="!current" @click="finishVisit">结束接诊</el-button>
          </el-form>
        </el-card>

        <el-card class="span-12" shadow="never">
          <template #header>医技项目开单</template>
          <div style="display:flex;gap:12px;margin-bottom:12px">
            <el-input v-model="itemKeyword" clearable placeholder="搜索项目名称或编码" />
            <el-select v-model="itemCategory" clearable placeholder="项目分类" style="width:180px">
              <el-option label="检查" value="CHECK" /><el-option label="检验" value="LAB" /><el-option label="处置" value="DISPOSAL" />
            </el-select>
            <el-select v-model="orderUrgency" style="width:140px"><el-option label="常规" value="ROUTINE" /><el-option label="急诊" value="EMERGENCY" /></el-select>
            <el-checkbox v-model="favoritesOnly">只看常用</el-checkbox>
            <el-button type="primary" :disabled="!current || !selectedItems.length" @click="submitOrders">批量开单（{{ selectedItems.length }}）</el-button>
          </div>
          <el-table :data="filteredItems" @selection-change="selectedItems=$event">
            <el-table-column type="selection" width="50" /><el-table-column prop="code" label="编码" width="130" />
            <el-table-column prop="name" label="项目" /><el-table-column prop="category" label="分类" width="100" />
            <el-table-column prop="price" label="价格（元）" width="120" />
            <el-table-column label="常用" width="80"><template #default="{row}"><el-button link @click.stop="toggleFavorite(row.code)">{{ favoriteCodes.includes(row.code)?'★':'☆' }}</el-button></template></el-table-column>
          </el-table>
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
        <el-card class="span-12" shadow="never"><template #header>相关历史病历（访问将审计）</template>
          <div style="display:flex;gap:12px;margin-bottom:12px"><el-input v-model="historyReason" placeholder="访问原因" /><el-button :disabled="!current" @click="loadHistory">查看历史</el-button></div>
          <el-table :data="historyRecords"><el-table-column prop="visitDate" label="日期" width="120" /><el-table-column prop="departmentName" label="科室" width="120" /><el-table-column prop="chiefComplaint" label="主诉" /><el-table-column prop="preliminaryDiagnosis" label="诊断" /></el-table>
        </el-card>
        <el-card class="span-12" shadow="never"><template #header>已确认医技报告</template>
          <el-table :data="formalReports"><el-table-column prop="reportType" label="类型" width="100" /><el-table-column prop="findings" label="所见/过程" /><el-table-column prop="conclusion" label="结论" /><el-table-column prop="advice" label="建议" /></el-table>
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
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../../store/auth';
import { callAppointment, getTodayQueue, skipAppointment, startAppointment, updateAppointmentStatus, type Appointment } from '../../api/appointment';
import { getMedicalRecords, getPatientHistory, writeDoctorNote, type MedicalRecord } from '../../api/medical-record';
import { getClinicalAssistance } from '../../api/ai';
import { createMedicalOrder, getMedicalItems, getReports, type MedicalItem, type MedicalReport } from '../../api/medical-order';

const router = useRouter();
const auth = useAuthStore();
const appointments = ref<Appointment[]>([]);
const queueKeyword=ref('');
const filteredAppointments=computed(()=>{const keyword=queueKeyword.value.trim().toLowerCase();return keyword?appointments.value.filter(item=>item.patientName.toLowerCase().includes(keyword)||item.businessNo.toLowerCase().includes(keyword)):appointments.value});
const records = ref<MedicalRecord[]>([]);
const historyRecords=ref<MedicalRecord[]>([]);const historyReason=ref('复诊关联病史查阅');
const medicalItems=ref<MedicalItem[]>([]);const selectedItems=ref<MedicalItem[]>([]);const itemKeyword=ref('');const itemCategory=ref('');const orderUrgency=ref('ROUTINE');const favoritesOnly=ref(false);const favoriteCodes=ref<string[]>(JSON.parse(localStorage.getItem('favorite-medical-items')||'[]'));
const formalReports=ref<MedicalReport[]>([]);
const filteredItems=computed(()=>medicalItems.value.filter(item=>(!favoritesOnly.value||favoriteCodes.value.includes(item.code))&&(!itemCategory.value||item.category===itemCategory.value)&&(!itemKeyword.value||`${item.code}${item.name}`.toLowerCase().includes(itemKeyword.value.toLowerCase()))));
const recordVersion=ref<number>();const dirty=ref(false);let loadingRecord=false;
const current = ref<Appointment>();
const aiPrompt = ref('结合当前病历给出鉴别诊断方向和进一步检查建议');
const aiMessages = ref<Array<{ id: string; label: string; content: string; kind: 'diagnosis' | 'advice' }>>([]);
const diagnosisSource = ref<'HUMAN' | 'AI'>('HUMAN');
const diagnosisAiRecordId = ref<string>();
const recordForm = reactive({
  chiefComplaint: '',
  presentIllness: '',
  pastHistory: '',
  allergyHistory: '',
  physicalExamination: '',
  diagnosis: '',
  treatmentPlan: '',
  doctorRevisionNote: ''
});

async function selectAppointment(row?: Appointment) {
  loadingRecord=true;
  current.value = row;
  recordForm.chiefComplaint = row?.triageSummary ?? '';
  recordForm.presentIllness = '';
  recordForm.pastHistory='';recordForm.allergyHistory='';recordForm.physicalExamination='';
  recordForm.diagnosis = '';
  recordForm.treatmentPlan = '';
  recordForm.doctorRevisionNote = '';
  aiMessages.value = [];
  diagnosisSource.value = 'HUMAN';
  diagnosisAiRecordId.value = undefined;
  recordVersion.value=undefined;historyRecords.value=[];
  if (row) {
    const currentRecords = await getMedicalRecords({ appointmentId: row.id });
    const currentRecord = currentRecords[0];
    if (currentRecord) {
      recordForm.chiefComplaint = currentRecord.chiefComplaint || currentRecord.aiTriageSummary;
      recordForm.presentIllness = currentRecord.presentIllness ?? '';
      recordForm.pastHistory=currentRecord.pastHistory??'';recordForm.allergyHistory=currentRecord.allergyHistory??'';
      recordForm.physicalExamination=currentRecord.physicalExamination??'';
      recordForm.diagnosis = currentRecord.diagnosis ?? '';
      recordForm.treatmentPlan = currentRecord.treatmentPlan ?? '';
      recordForm.doctorRevisionNote = currentRecord.doctorRevisionNote ?? '';
      recordVersion.value=currentRecord.version;
    }
  }
  loadingRecord=false;dirty.value=false;
}

async function loadQueue() {
  appointments.value = await getTodayQueue();
}

async function call(appointment:Appointment){await callAppointment(appointment.id);ElMessage.success('已叫号');await loadQueue()}
async function start(appointment:Appointment){current.value=await startAppointment(appointment.id);await selectAppointment(current.value);ElMessage.success('已开始接诊');await loadQueue()}

async function saveRecord() {
  if (!current.value) return;
  if(recordVersion.value===undefined){ElMessage.warning('电子病历尚未创建，请稍后刷新');return}
  const saved=await writeDoctorNote({
    appointmentId: current.value.id,
    version:recordVersion.value,
    chiefComplaint: recordForm.chiefComplaint,
    presentIllness: recordForm.presentIllness,
    pastHistory:recordForm.pastHistory,allergyHistory:recordForm.allergyHistory,
    physicalExamination:recordForm.physicalExamination,preliminaryDiagnosis: recordForm.diagnosis,
    treatmentPlan: recordForm.treatmentPlan,
    doctorRevisionNote: recordForm.doctorRevisionNote,
    diagnosisCreatedByType: diagnosisSource.value,
    diagnosisAiRecordId: diagnosisAiRecordId.value
  });
  recordVersion.value=saved.version;dirty.value=false;ElMessage.success('病历已保存，可继续编辑');
  records.value = await getMedicalRecords({});
}

async function finishVisit(){if(!current.value)return;if(dirty.value||recordVersion.value===undefined){ElMessage.warning('请先保存当前病历');return}await updateAppointmentStatus(current.value.id,'FINISHED');ElMessage.success('接诊已结束');current.value=undefined;await loadQueue()}
async function loadHistory(){if(!current.value)return;historyRecords.value=await getPatientHistory(current.value.patientId,current.value.id,historyReason.value)}
async function submitOrders(){if(!current.value)return;await Promise.all(selectedItems.value.map(item=>createMedicalOrder({appointmentId:current.value!.id,patientId:current.value!.patientId,patientName:current.value!.patientName,orderType:item.category,projectCode:item.code,projectName:item.name,purpose:recordForm.diagnosis||recordForm.chiefComplaint,bodyPart:item.category==='CHECK'?'头部':'',amount:item.price,urgency:orderUrgency.value})));ElMessage.success(`已生成 ${selectedItems.value.length} 条待缴费医技申请`);selectedItems.value=[]}
function toggleFavorite(code:string){favoriteCodes.value=favoriteCodes.value.includes(code)?favoriteCodes.value.filter(item=>item!==code):[...favoriteCodes.value,code];localStorage.setItem('favorite-medical-items',JSON.stringify(favoriteCodes.value))}

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
  medicalItems.value=(await getMedicalItems()).filter(item=>item.category!=='DRUG');
  formalReports.value=await getReports();
});
watch(recordForm,()=>{if(!loadingRecord)dirty.value=true},{deep:true});
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
