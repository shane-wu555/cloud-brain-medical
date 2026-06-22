<template>
  <main class="page">
    <div class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">管理端</p>
          <h1>基础运营看板</h1>
          <p class="muted">科室、医生、号源和 AI 问诊量的第一阶段管理视图。</p>
        </div>
        <el-button @click="logout">退出</el-button>
      </header>

      <section class="grid">
        <el-card class="span-3 stat" shadow="never">
          <span>今日挂号</span>
          <strong>{{ overview?.todayAppointments ?? 0 }}</strong>
        </el-card>
        <el-card class="span-3 stat" shadow="never">
          <span>待接诊</span>
          <strong>{{ overview?.waitingVisits ?? 0 }}</strong>
        </el-card>
        <el-card class="span-3 stat" shadow="never">
          <span>出诊医生</span>
          <strong>{{ overview?.activeDoctors ?? 0 }}</strong>
        </el-card>
        <el-card class="span-3 stat" shadow="never">
          <span>AI 问诊</span>
          <strong>{{ overview?.aiTriageCount ?? 0 }}</strong>
        </el-card>

        <el-card class="span-6" shadow="never">
          <template #header>科室负载</template>
          <el-table :data="overview?.departmentLoads ?? []">
            <el-table-column prop="name" label="科室" />
            <el-table-column prop="value" label="挂号量" width="120" />
          </el-table>
        </el-card>

        <el-card class="span-6" shadow="never">
          <template #header>新增排班</template>
          <el-form label-position="top">
            <el-form-item label="医生">
              <el-select v-model="scheduleForm.doctorId" class="full" @change="syncDoctor">
                <el-option v-for="doctor in doctors" :key="doctor.id" :label="`${doctor.name} / ${doctor.departmentName}`" :value="doctor.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="日期">
              <el-date-picker v-model="scheduleForm.workDate" class="full" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item label="时段">
              <el-segmented v-model="scheduleForm.period" :options="['上午', '下午', '全天']" />
            </el-form-item>
            <el-form-item label="号源数">
              <el-input-number v-model="scheduleForm.capacity" :min="1" :max="100" />
            </el-form-item>
            <el-button type="primary" @click="submitSchedule">保存排班</el-button>
          </el-form>
        </el-card>

        <el-card class="span-12" shadow="never">
          <template #header>当前排班</template>
          <el-table :data="schedules">
            <el-table-column prop="doctorName" label="医生" width="120" />
            <el-table-column prop="workDate" label="日期" width="140" />
            <el-table-column prop="period" label="时段" width="120" />
            <el-table-column prop="capacity" label="号源" width="100" />
            <el-table-column prop="booked" label="已约" width="100" />
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
import { createSchedule, getDoctors, getSchedules, type Doctor, type Schedule } from '../../api/doctor';
import { getDashboardOverview } from '../../api/dashboard';

const router = useRouter();
const auth = useAuthStore();
const overview = ref<Awaited<ReturnType<typeof getDashboardOverview>>>();
const doctors = ref<Doctor[]>([]);
const schedules = ref<Schedule[]>([]);
const scheduleForm = reactive({
  doctorId: '',
  doctorName: '',
  departmentId: '',
  workDate: '',
  period: '上午',
  capacity: 20
});

function syncDoctor() {
  const doctor = doctors.value.find((item) => item.id === scheduleForm.doctorId);
  scheduleForm.doctorName = doctor?.name ?? '';
  scheduleForm.departmentId = doctor?.departmentId ?? '';
}

async function submitSchedule() {
  syncDoctor();
  await createSchedule({
    doctorId: scheduleForm.doctorId,
    departmentId: scheduleForm.departmentId,
    workDate: scheduleForm.workDate,
    period: scheduleForm.period,
    capacity: scheduleForm.capacity
  });
  ElMessage.success('排班已保存');
  schedules.value = await getSchedules();
}

function logout() {
  auth.signOut();
  router.push('/login');
}

onMounted(async () => {
  overview.value = await getDashboardOverview();
  doctors.value = await getDoctors();
  scheduleForm.doctorId = doctors.value[0]?.id ?? '';
  syncDoctor();
  schedules.value = await getSchedules();
});
</script>
