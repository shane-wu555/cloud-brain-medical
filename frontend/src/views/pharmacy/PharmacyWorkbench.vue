<template>
  <main class="page">
    <div class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">药房医生工作台</p>
          <h1>处方发药与库存</h1>
          <p class="muted">仅已缴费处方进入待发药队列，发药和退药都会生成库存流水。</p>
        </div>
        <el-button @click="logout">退出</el-button>
      </header>

      <section class="grid">
        <el-card class="span-8" shadow="never">
          <template #header>
            <div class="card-header">
              <span>待处理处方</span>
              <el-segmented v-model="status" :options="statusOptions" @change="loadPrescriptions" />
            </div>
          </template>
          <el-table :data="prescriptions" highlight-current-row @current-change="selected = $event">
            <el-table-column prop="prescriptionNo" label="处方号" width="150" />
            <el-table-column prop="patientName" label="患者" width="100" />
            <el-table-column prop="diagnosis" label="诊断" />
            <el-table-column prop="totalAmount" label="金额" width="90" />
            <el-table-column prop="status" label="状态" width="130" />
            <el-table-column label="操作" width="170">
              <template #default="{ row }">
                <el-button v-if="row.status === 'WAITING_DISPENSE'" type="primary" link @click.stop="dispense(row.id)">发药</el-button>
                <el-button v-if="row.status === 'DISPENSED'" type="warning" link @click.stop="openReturn(row)">退药</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card class="span-4" shadow="never">
          <template #header>处方明细</template>
          <el-empty v-if="!selected" description="请选择一张处方" :image-size="72" />
          <div v-else class="detail">
            <h3>{{ selected.patientName }} · {{ selected.prescriptionNo }}</h3>
            <p class="muted">{{ selected.diagnosis }}</p>
            <el-table :data="selected.items">
              <el-table-column prop="drugName" label="药品" />
              <el-table-column prop="quantity" label="数量" width="70" />
              <el-table-column prop="usage" label="用法" width="90" />
              <el-table-column prop="frequency" label="频次" width="100" />
            </el-table>
          </div>
        </el-card>

        <el-card class="span-12" shadow="never">
          <template #header>
            <div class="card-header">
              <span>药品库存</span>
              <el-input v-model="drugKeyword" clearable placeholder="搜索药品" style="width:260px" @change="loadDrugs" />
            </div>
          </template>
          <el-table :data="drugs">
            <el-table-column prop="drugCode" label="编码" width="120" />
            <el-table-column prop="drugName" label="药品" />
            <el-table-column prop="specification" label="规格" width="140" />
            <el-table-column prop="unitPrice" label="单价" width="90" />
            <el-table-column prop="quantity" label="库存" width="90" />
            <el-table-column label="预警" width="90">
              <template #default="{ row }">
                <el-tag :type="row.quantity <= row.warningThreshold ? 'danger' : 'success'" effect="plain">
                  {{ row.quantity <= row.warningThreshold ? '偏低' : '正常' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </section>
    </div>

    <el-dialog v-model="returnVisible" title="退药" width="420px">
      <el-input v-model="returnReason" type="textarea" :rows="3" placeholder="退药原因" />
      <template #footer>
        <el-button @click="returnVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReturn">确认退药</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../../store/auth';
import { dispensePrescription, getDrugs, getPrescriptions, returnPrescription, type Drug, type Prescription } from '../../api/pharmacy';

const router = useRouter();
const auth = useAuthStore();
const statusOptions = [
  { label: '待发药', value: 'WAITING_DISPENSE' },
  { label: '已发药', value: 'DISPENSED' },
  { label: '已退药', value: 'RETURNED' }
];
const status = ref('WAITING_DISPENSE');
const prescriptions = ref<Prescription[]>([]);
const selected = ref<Prescription>();
const drugs = ref<Drug[]>([]);
const drugKeyword = ref('');
const returnVisible = ref(false);
const returnReason = ref('患者退药');
const returning = ref<Prescription>();

async function loadPrescriptions() {
  prescriptions.value = await getPrescriptions({ status: status.value });
}

async function loadDrugs() {
  drugs.value = await getDrugs(drugKeyword.value);
}

async function dispense(id: string) {
  selected.value = await dispensePrescription(id);
  ElMessage.success('发药完成，库存已扣减');
  await Promise.all([loadPrescriptions(), loadDrugs()]);
}

function openReturn(row: Prescription) {
  returning.value = row;
  returnReason.value = '患者退药';
  returnVisible.value = true;
}

async function submitReturn() {
  if (!returning.value) return;
  selected.value = await returnPrescription(returning.value.id, returnReason.value);
  returnVisible.value = false;
  ElMessage.success('退药完成，库存已回补');
  await Promise.all([loadPrescriptions(), loadDrugs()]);
}

function logout() {
  auth.signOut();
  router.push('/login');
}

onMounted(async () => {
  await Promise.all([loadPrescriptions(), loadDrugs()]);
});
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail h3 {
  margin: 0 0 6px;
  font-size: 16px;
}
</style>
