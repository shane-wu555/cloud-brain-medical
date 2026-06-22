<template>
  <main class="page"><div class="workspace">
    <header class="topbar"><div><p class="eyebrow">窗口工作台</p><h1>建档、挂号与收费</h1><p class="muted">线上与窗口共用排班号源和支付状态。</p></div><el-button @click="logout">退出</el-button></header>
    <section class="grid">
      <el-card class="span-5" shadow="never"><template #header>患者查询 / 建档</template>
        <el-form label-position="top"><el-form-item label="手机号"><el-input v-model="patientForm.phone" /></el-form-item>
          <el-form-item label="姓名（新患者）"><el-input v-model="patientForm.name" /></el-form-item>
          <el-button @click="findPatient">查询</el-button><el-button type="primary" @click="buildPatient">线下建档</el-button></el-form>
        <el-alert v-if="patient" :title="`${patient.name} · ${patient.phone}`" :description="`患者编号：${patient.userId}`" type="success" :closable="false" />
      </el-card>
      <el-card class="span-7" shadow="never"><template #header>现场挂号收费</template>
        <el-form label-position="top"><el-form-item label="排班"><el-select v-model="scheduleId" class="full">
          <el-option v-for="item in schedules" :key="item.id" :disabled="item.available<=0" :label="`${item.doctorName} · ${item.workDate} ${item.period} · 剩余 ${item.available}`" :value="item.id" />
        </el-select></el-form-item><el-form-item label="测试挂号费"><el-input value="￥0.01" disabled /></el-form-item>
          <el-button type="primary" :disabled="!patient || !scheduleId" @click="register">挂号并收费</el-button></el-form>
        <el-result v-if="lastAppointment" icon="success" title="窗口挂号成功" :sub-title="`业务编号 ${lastAppointment.businessNo}，队列号 ${lastAppointment.queueNumber}`" />
      </el-card>
      <el-card class="span-12" shadow="never"><template #header>患者挂号与退费</template>
        <el-table :data="patientAppointments"><el-table-column prop="businessNo" label="业务编号" width="190" /><el-table-column prop="patientName" label="患者" width="110" /><el-table-column prop="doctorName" label="医生" width="110" /><el-table-column prop="visitDate" label="就诊日" width="130" /><el-table-column prop="status" label="状态" width="120" /><el-table-column prop="paymentStatus" label="支付" width="120" /><el-table-column label="操作"><template #default="{row}"><el-button v-if="!['CANCELLED','FINISHED','IN_VISIT'].includes(row.status)" type="danger" link @click="refund(row)">退号退费</el-button></template></el-table-column></el-table>
      </el-card>
    </section>
  </div></main>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../../store/auth';
import { getDepartments,getSchedules,type Department,type Schedule } from '../../api/doctor';
import { cancelAppointment,createOfflineAppointment,getAppointments,type Appointment } from '../../api/appointment';
import { createOfflinePatient,searchPatients,type PatientProfile } from '../../api/patient';
const router=useRouter();const auth=useAuthStore();const patientForm=reactive({phone:'13800000000',name:''});
const patient=ref<PatientProfile>();const schedules=ref<Schedule[]>([]);const departments=ref<Department[]>([]);const scheduleId=ref('');const lastAppointment=ref<Appointment>();
const patientAppointments=ref<Appointment[]>([]);
async function loadPatientAppointments(){patientAppointments.value=patient.value?await getAppointments({patientId:patient.value.userId}):[]}
async function findPatient(){patient.value=(await searchPatients(patientForm.phone))[0];if(!patient.value)ElMessage.warning('未找到患者，请线下建档');await loadPatientAppointments()}
async function buildPatient(){patient.value=await createOfflinePatient(patientForm.phone,patientForm.name);ElMessage.success('建档成功');await loadPatientAppointments()}
async function register(){const s=schedules.value.find(i=>i.id===scheduleId.value);if(!s||!patient.value)return;const d=departments.value.find(i=>i.id===s.departmentId);
  lastAppointment.value=await createOfflineAppointment({scheduleId:s.id,patientId:patient.value.userId,patientName:patient.value.name,doctorId:s.doctorId,doctorName:s.doctorName,departmentId:s.departmentId,departmentName:d?.name??'',visitDate:s.workDate,period:s.period,riskLevel:'LOW'});
  ElMessage.success('挂号及 0.01 元窗口收费成功');schedules.value=await getSchedules();await loadPatientAppointments()}
async function refund(row:Appointment){await cancelAppointment(row.id);ElMessage.success('退号成功，退款记录已生成');await loadPatientAppointments();schedules.value=await getSchedules()}
function logout(){auth.signOut();router.push('/login')}
onMounted(async()=>{departments.value=await getDepartments();schedules.value=await getSchedules();scheduleId.value=schedules.value.find(i=>i.available>0)?.id??''})
</script>
