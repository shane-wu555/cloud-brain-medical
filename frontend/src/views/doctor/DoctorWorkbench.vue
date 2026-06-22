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
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../../store/auth';
import { getAppointments, skipAppointment, updateAppointmentStatus, type Appointment } from '../../api/appointment';
import { getMedicalRecords, writeDoctorNote, type MedicalRecord } from '../../api/medical-record';

const router = useRouter();
const auth = useAuthStore();
const appointments = ref<Appointment[]>([]);
const records = ref<MedicalRecord[]>([]);
const current = ref<Appointment>();
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
    doctorRevisionNote: recordForm.doctorRevisionNote
  });
  await updateAppointmentStatus(current.value.id, 'FINISHED');
  ElMessage.success('病历已保存');
  current.value = undefined;
  await loadQueue();
  records.value = await getMedicalRecords({});
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
