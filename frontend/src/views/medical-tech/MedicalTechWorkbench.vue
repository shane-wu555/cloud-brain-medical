<template>
  <main class="page">
    <div class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">医技工作台</p>
          <h1>{{ title }}</h1>
          <p class="muted">处理已缴费医技队列，生成草稿后由医技医生确认发布。</p>
        </div>
        <el-button @click="logout">退出</el-button>
      </header>

      <section class="grid">
        <el-card class="span-7" shadow="never">
          <template #header>待执行队列</template>
          <el-table :data="orders" highlight-current-row @current-change="select">
            <el-table-column prop="queueNumber" label="队列" width="70" />
            <el-table-column prop="urgency" label="优先级" width="90" />
            <el-table-column prop="patientName" label="患者" width="110" />
            <el-table-column prop="projectName" label="项目" />
            <el-table-column prop="executionLocation" label="地点" width="110" />
            <el-table-column prop="equipmentId" label="设备" width="90" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button v-if="row.status === 'WAITING'" link type="primary" @click.stop="start(row)">开始</el-button>
                <el-button v-if="row.status === 'WAITING'" link @click.stop="miss(row)">过号</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card class="span-5" shadow="never">
          <template #header>
            <div class="card-header">
              <span>结果与正式报告</span>
              <el-tag :type="aiFallback ? 'warning' : aiModel ? 'success' : 'info'" effect="plain">{{ aiModelLabel }}</el-tag>
            </div>
          </template>
          <el-form label-position="top">
            <el-form-item label="当前医嘱">
              <el-input :model-value="current?.projectName ?? '请选择队列医嘱'" disabled />
            </el-form-item>

            <template v-if="role === 'CHECK_DOCTOR'">
              <el-form-item label="CT/DICOM 附件"><input type="file" @change="chooseFile" /></el-form-item>
              <el-button :disabled="!file || !current" @click="uploadCt">上传并提交 CT AI</el-button>
              <el-button v-if="aiTaskId" @click="pollAi">刷新 CT AI 结果</el-button>
              <p class="muted">CT AI 任务：{{ aiStatus || '未提交' }}</p>
            </template>

            <template v-if="role === 'LAB_DOCTOR'">
              <el-form-item label="样本类型"><el-input v-model="lab.specimenType" /></el-form-item>
              <el-form-item label="条码"><el-input v-model="lab.barcode" /></el-form-item>
              <el-button :disabled="!current" @click="prepareSpecimen">登记并流转至分析</el-button>
              <el-form-item label="指标名称"><el-input v-model="lab.itemName" /></el-form-item>
              <el-form-item label="结果/单位">
                <el-input v-model="lab.value" />
                <el-input v-model="lab.unit" />
              </el-form-item>
              <el-button :disabled="!specimenId" @click="saveLab">保存检验指标</el-button>
            </template>

            <el-form-item label="检查所见/执行过程"><el-input v-model="report.findings" type="textarea" :rows="3" /></el-form-item>
            <el-form-item label="结论/结果"><el-input v-model="report.conclusion" type="textarea" :rows="3" /></el-form-item>
            <el-form-item label="后续建议"><el-input v-model="report.advice" type="textarea" :rows="2" /></el-form-item>
            <el-button :disabled="!current" @click="generateAiDraft">生成 AI 草稿</el-button>
            <el-button :disabled="!current" @click="draft">保存草稿</el-button>
            <el-button type="success" :disabled="!current" @click="confirm">确认发布</el-button>
          </el-form>
        </el-card>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../../store/auth';
import {
  confirmReport,
  createReportDraft as saveReportDraft,
  createSpecimen,
  getMedicalOrders,
  missMedicalOrder,
  refreshAiTask,
  saveLabResults,
  startMedicalOrder,
  submitCt,
  transitionSpecimen,
  uploadAttachment,
  type MedicalOrder
} from '../../api/medical-order';
import { createReportDraft as createAiReportDraft } from '../../api/ai';

const auth = useAuthStore();
const router = useRouter();
const role = computed(() => auth.user?.role ?? '');
const titles: Record<string, string> = {
  CHECK_DOCTOR: '检查医生工作台',
  LAB_DOCTOR: '检验医生工作台',
  DISPOSAL_DOCTOR: '处置医生工作台'
};
const title = computed(() => titles[role.value] ?? '医技工作台');
const orders = ref<MedicalOrder[]>([]);
const current = ref<MedicalOrder>();
const file = ref<File>();
const aiTaskId = ref('');
const aiStatus = ref('');
const aiModel = ref('');
const aiFallback = ref(false);
const specimenId = ref('');
const report = reactive({ findings: '', conclusion: '', advice: '' });
const lab = reactive({ specimenType: '全血', barcode: `LAB-${Date.now()}`, itemName: '血红蛋白', value: '135', unit: 'g/L' });
const aiModelLabel = computed(() => !aiModel.value ? '未生成' : aiFallback.value ? `${aiModel.value} / Mock` : aiModel.value);

async function load() { orders.value = await getMedicalOrders(); }
function select(row?: MedicalOrder) {
  current.value = row;
  report.findings = '';
  report.conclusion = '';
  report.advice = '';
}
async function start(row: MedicalOrder) { await startMedicalOrder(row.id); ElMessage.success('已开始执行'); await load(); }
async function miss(row: MedicalOrder) { await missMedicalOrder(row.id); await load(); }
function chooseFile(event: Event) { file.value = (event.target as HTMLInputElement).files?.[0]; }
async function uploadCt() {
  if (!current.value || !file.value) return;
  const attachment = await uploadAttachment(current.value.id, file.value);
  const task = await submitCt(current.value.id, attachment.id);
  aiTaskId.value = task.externalTaskId;
  aiStatus.value = task.status;
  ElMessage.success('CT AI 任务已提交');
}
async function pollAi() {
  const task = await refreshAiTask(aiTaskId.value);
  aiStatus.value = task.status;
  if (task.status === 'COMPLETED') ElMessage.success('CT AI 报告草稿已生成，请刷新报告列表或继续确认');
}
async function generateAiDraft() {
  if (!current.value) return;
  const draft = await createAiReportDraft({
    orderId: current.value.id,
    reportType: current.value.orderType,
    projectName: current.value.projectName,
    findings: report.findings,
    conclusion: report.conclusion,
    context: current.value.purpose || current.value.bodyPart || ''
  });
  report.findings = draft.findings;
  report.conclusion = draft.conclusion;
  report.advice = draft.advice;
  aiModel.value = draft.model;
  aiFallback.value = draft.fallbackUsed;
  await saveReportDraft(current.value.id, report);
  ElMessage.success('AI 报告草稿已保存，确认后发布');
}
async function prepareSpecimen() {
  if (!current.value) return;
  const specimen = await createSpecimen(current.value.id, lab.specimenType, lab.barcode);
  specimenId.value = specimen.id;
  for (const status of ['COLLECTED', 'RECEIVED', 'ANALYZING']) await transitionSpecimen(specimen.id, status);
  ElMessage.success('样本已进入分析阶段');
}
async function saveLab() {
  if (!current.value) return;
  await saveLabResults(current.value.id, specimenId.value, [{ itemCode: 'HGB', itemName: lab.itemName, resultValue: lab.value, unit: lab.unit, referenceRange: '115-150', abnormalFlag: 'NORMAL', createdByType: 'HUMAN' }]);
  ElMessage.success('检验指标已保存');
}
async function draft() {
  if (!current.value) return;
  await saveReportDraft(current.value.id, report);
  ElMessage.success('报告草稿已保存');
}
async function confirm() {
  if (!current.value) return;
  await confirmReport(current.value.id, report);
  ElMessage.success('正式报告已发布');
  current.value = undefined;
  await load();
}
function logout() { auth.signOut(); router.push('/login'); }
onMounted(load);
</script>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
</style>
