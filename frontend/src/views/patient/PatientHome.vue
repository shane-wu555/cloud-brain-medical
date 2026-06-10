<template>
  <main class="page">
    <div class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">患者端</p>
          <h1>智能问诊与线上挂号</h1>
          <p class="muted">{{ profile?.name }}，{{ profile?.age }} 岁，{{ profile?.tags.join(' / ') }}</p>
        </div>
        <el-button @click="logout">退出</el-button>
      </header>

      <section class="grid">
        <el-card class="span-5" shadow="never">
          <template #header>AI 智能问诊</template>
          <el-checkbox-group v-model="symptomTags" class="tag-row">
            <el-checkbox-button label="头痛" />
            <el-checkbox-button label="眩晕" />
            <el-checkbox-button label="影像复查" />
            <el-checkbox-button label="剧烈疼痛" />
          </el-checkbox-group>
          <el-input
            v-model="symptomDescription"
            type="textarea"
            :rows="5"
            placeholder="描述症状、持续时间、既往病史，系统会带入挂号摘要。"
          />
          <el-button type="primary" class="full consult-button" :loading="consulting" @click="runConsultation">
            生成问诊建议
          </el-button>
          <el-alert
            v-if="consultation"
            :title="`${consultation.recommendedDepartmentName} / 风险等级：${consultation.riskLevel}`"
            :description="consultation.recordDraft"
            show-icon
            :closable="false"
          />
        </el-card>

        <el-card class="span-7" shadow="never">
          <template #header>科室与医生</template>
          <el-tabs v-model="selectedDepartmentId" @tab-change="loadDoctorsAndSchedules">
            <el-tab-pane
              v-for="department in departments"
              :key="department.id"
              :label="department.name"
              :name="department.id"
            />
          </el-tabs>
          <el-table :data="doctors" height="238">
            <el-table-column prop="name" label="医生" width="110" />
            <el-table-column prop="title" label="职称" width="120" />
            <el-table-column prop="specialty" label="擅长" />
          </el-table>
        </el-card>

        <el-card class="span-12" shadow="never">
          <template #header>可预约排班</template>
          <el-table :data="schedules">
            <el-table-column prop="doctorName" label="医生" width="120" />
            <el-table-column prop="workDate" label="日期" width="140" />
            <el-table-column prop="period" label="时段" width="120" />
            <el-table-column label="号源" width="140">
              <template #default="{ row }">{{ row.booked }} / {{ row.capacity }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button type="primary" size="small" @click="book(row)">锁号并支付</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card class="span-12" shadow="never">
          <template #header>我的挂号</template>
          <el-table :data="appointments">
            <el-table-column prop="id" label="挂号单" width="120" />
            <el-table-column prop="departmentName" label="科室" width="120" />
            <el-table-column prop="doctorName" label="医生" width="120" />
            <el-table-column prop="visitDate" label="日期" width="140" />
            <el-table-column prop="period" label="时段" width="100" />
            <el-table-column prop="status" label="状态" width="120" />
            <el-table-column prop="paymentStatus" label="支付" width="100" />
            <el-table-column prop="triageSummary" label="问诊摘要" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button size="small" :disabled="row.status === 'CANCELLED' || row.status === 'FINISHED'" @click="cancel(row)">
                  取消
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../../store/auth';
import { getPatientProfile, type PatientProfile } from '../../api/patient';
import {
  cancelAppointment,
  createAiConsultation,
  createAppointment,
  getAppointments,
  getDepartments,
  getDoctors,
  getSchedules,
  payAppointment,
  type Appointment,
  type ConsultationResult,
  type Department,
  type Doctor,
  type Schedule
} from '../../api/medical';

const router = useRouter();
const auth = useAuthStore();
const profile = ref<PatientProfile>();
const departments = ref<Department[]>([]);
const doctors = ref<Doctor[]>([]);
const schedules = ref<Schedule[]>([]);
const appointments = ref<Appointment[]>([]);
const selectedDepartmentId = ref('');
const symptomTags = ref<string[]>(['头痛']);
const symptomDescription = ref('反复头痛 3 天，伴轻度眩晕，希望预约神经内科进一步检查。');
const consultation = ref<ConsultationResult>();
const consulting = ref(false);

async function loadDoctorsAndSchedules() {
  doctors.value = await getDoctors(selectedDepartmentId.value);
  schedules.value = await getSchedules({ departmentId: selectedDepartmentId.value });
}

async function loadAppointments() {
  appointments.value = await getAppointments({ patientId: profile.value?.id ?? 'patient-001' });
}

async function runConsultation() {
  consulting.value = true;
  try {
    consultation.value = await createAiConsultation({
      patientId: profile.value?.id ?? 'patient-001',
      description: symptomDescription.value,
      symptomTags: symptomTags.value
    });
    selectedDepartmentId.value = consultation.value.recommendedDepartmentId;
    await loadDoctorsAndSchedules();
    ElMessage.success('已生成问诊建议');
  } finally {
    consulting.value = false;
  }
}

async function book(schedule: Schedule) {
  const doctor = doctors.value.find((item) => item.id === schedule.doctorId);
  const department = departments.value.find((item) => item.id === schedule.departmentId);
  const locked = await createAppointment({
    scheduleId: schedule.id,
    patientId: profile.value?.id ?? 'patient-001',
    patientName: profile.value?.name ?? auth.user?.name ?? '患者',
    doctorId: schedule.doctorId,
    doctorName: schedule.doctorName,
    departmentId: schedule.departmentId,
    departmentName: doctor?.departmentName ?? department?.name ?? '未分配科室',
    visitDate: schedule.workDate,
    period: schedule.period,
    triageSummary: consultation.value?.summary ?? symptomDescription.value,
    riskLevel: consultation.value?.riskLevel ?? 'LOW',
    recommendedDepartmentId: consultation.value?.recommendedDepartmentId
  });
  await payAppointment(locked.id, 'WECHAT');
  ElMessage.success('缴费成功，挂号已确认并生成本次电子病历');
  await loadAppointments();
}

async function cancel(appointment: Appointment) {
  try {
    await cancelAppointment(appointment.id);
    ElMessage.success('已取消挂号并发起退费');
    await loadAppointments();
  } catch (error) {
    ElMessage.error('就诊当天不可取消或退费');
  }
}

function logout() {
  auth.signOut();
  router.push('/login');
}

onMounted(async () => {
  profile.value = await getPatientProfile();
  departments.value = await getDepartments();
  selectedDepartmentId.value = departments.value[0]?.id ?? '';
  await runConsultation();
  await loadDoctorsAndSchedules();
  await loadAppointments();
});
</script>

<style scoped>
.tag-row {
  margin-bottom: 12px;
}

.consult-button {
  margin: 12px 0;
}
</style>
