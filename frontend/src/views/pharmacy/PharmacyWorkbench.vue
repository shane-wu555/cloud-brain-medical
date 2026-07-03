<template>
  <div class="pharmacy-wks">
    <header class="pharmacy-nav">
      <div class="pharmacy-nav__brand">
        <span class="pharmacy-nav__logo">+</span>
        <span class="pharmacy-nav__title">药房工作台</span>
      </div>
      <div class="pharmacy-nav__right">
        <span>{{ auth.user?.name }} 药房人员</span>
        <span>{{ today }} {{ dayOfWeek }}</span>
        <el-button size="small" text class="nav-logout" @click="logout">退出</el-button>
      </div>
    </header>

    <div class="pharmacy-body">
      <aside class="pharmacy-sidebar">
        <div class="sidebar-hdr">
          <span>功能导航</span>
        </div>
        <button
          v-for="item in navItems"
          :key="item.key"
          :class="['nav-item', currentPage === item.key && 'nav-item--active']"
          @click="currentPage = item.key"
        >
          <span>{{ item.label }}</span>
          <em v-if="item.badge">{{ item.badge }}</em>
        </button>
        <div class="sidebar-footer">
          <span>待发药 {{ waitingPrescriptions.length }}</span>
          <span>已发药 {{ dispensedPrescriptions.length }}</span>
        </div>
      </aside>

      <main class="pharmacy-main">
        <section v-show="currentPage === 'dispense'" class="work-page">
          <div class="page-head">
            <div>
              <h1>处方发药</h1>
            </div>
            <el-button :loading="loadingPrescriptions" @click="loadPrescriptions">刷新</el-button>
          </div>

          <div class="query-bar">
            <el-input
              v-model="prescriptionSearch.patientName"
              clearable
              placeholder="患者姓名"
              @keyup.enter="applyPrescriptionSearch"
            />
            <el-input
              v-model="prescriptionSearch.prescriptionNo"
              clearable
              placeholder="处方号"
              @keyup.enter="applyPrescriptionSearch"
            />
            <el-button type="primary" @click="applyPrescriptionSearch">搜索</el-button>
            <el-button @click="resetPrescriptionSearch">重置</el-button>
          </div>

          <div class="dispense-layout">
            <div class="prescription-lists">
              <section class="work-card">
                <div class="card-head">
                  <div>
                    <h2>待发药处方</h2>
                    <p>共 {{ filteredWaitingPrescriptions.length }} 张</p>
                  </div>
                </div>
                <el-table
                  v-loading="loadingPrescriptions"
                  :data="filteredWaitingPrescriptions"
                  row-key="id"
                  highlight-current-row
                  empty-text="暂无待发药处方"
                >
                  <el-table-column prop="prescriptionNo" label="处方号" min-width="150" />
                  <el-table-column prop="patientName" label="患者" min-width="110" />
                  <el-table-column prop="diagnosis" label="诊断" min-width="180" show-overflow-tooltip />
                  <el-table-column label="开方时间" min-width="160">
                    <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
                  </el-table-column>
                  <el-table-column label="金额" width="100" align="right">
                    <template #default="{ row }">¥{{ amountText(row.totalAmount) }}</template>
                  </el-table-column>
                  <el-table-column label="操作" width="170" fixed="right">
                    <template #default="{ row }">
                      <el-button type="primary" link @click="showDetail(row)">处方明细</el-button>
                      <el-button type="success" link :loading="dispensingId === row.id" @click="dispense(row.id)">发药</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </section>

              <section class="work-card">
                <div class="card-head">
                  <div>
                    <h2>已发药处方</h2>
                    <p>共 {{ filteredDispensedPrescriptions.length }} 张</p>
                  </div>
                </div>
                <el-table
                  v-loading="loadingPrescriptions"
                  :data="filteredDispensedPrescriptions"
                  row-key="id"
                  highlight-current-row
                  empty-text="暂无已发药处方"
                >
                  <el-table-column prop="prescriptionNo" label="处方号" min-width="150" />
                  <el-table-column prop="patientName" label="患者" min-width="110" />
                  <el-table-column prop="diagnosis" label="诊断" min-width="180" show-overflow-tooltip />
                  <el-table-column label="发药时间" min-width="160">
                    <template #default="{ row }">{{ formatDateTime(row.dispensedAt) }}</template>
                  </el-table-column>
                  <el-table-column label="金额" width="100" align="right">
                    <template #default="{ row }">¥{{ amountText(row.totalAmount) }}</template>
                  </el-table-column>
                  <el-table-column label="操作" width="110" fixed="right">
                    <template #default="{ row }">
                      <el-button type="primary" link @click="showDetail(row)">处方明细</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </section>
            </div>

            <aside class="detail-panel">
              <div class="detail-panel__head">
                <h2>处方明细</h2>
                <el-tag v-if="selected" :type="prescriptionTagType(selected.status)" effect="plain">
                  {{ prescriptionStatusLabel(selected.status) }}
                </el-tag>
              </div>
              <el-empty v-if="!selected" description="点击处方明细查看药品" :image-size="86" />
              <template v-else>
                <div class="rx-summary">
                  <strong>{{ selected.patientName || '-' }}</strong>
                  <span>{{ selected.prescriptionNo }}</span>
                  <em>{{ selected.diagnosis || '未填写诊断' }}</em>
                </div>
                <div class="detail-meta">
                  <span>开方时间</span><strong>{{ formatDateTime(selected.createdAt) }}</strong>
                  <span>发药时间</span><strong>{{ formatDateTime(selected.dispensedAt) }}</strong>
                  <span>总金额</span><strong>¥{{ amountText(selected.totalAmount) }}</strong>
                </div>
                <el-table :data="selected.items" size="small" empty-text="暂无药品明细">
                  <el-table-column prop="drugName" label="药品" min-width="130" show-overflow-tooltip />
                  <el-table-column prop="quantity" label="数量" width="70" />
                  <el-table-column label="单价" width="90" align="right">
                    <template #default="{ row }">¥{{ amountText(row.unitPrice) }}</template>
                  </el-table-column>
                </el-table>
              </template>
            </aside>
          </div>
        </section>

        <section v-show="currentPage === 'returns'" class="work-page">
          <div class="page-head">
            <div>
              <h1>退药管理</h1>
            </div>
            <el-button :loading="loadingReturns" @click="loadReturns">刷新</el-button>
          </div>

          <div class="query-bar">
            <el-input
              v-model="returnSearch.patientName"
              clearable
              placeholder="患者姓名"
              @keyup.enter="applyReturnSearch"
            />
            <el-input
              v-model="returnSearch.prescriptionNo"
              clearable
              placeholder="处方号"
              @keyup.enter="applyReturnSearch"
            />
            <el-input
              v-model="returnSearch.returnNo"
              clearable
              placeholder="退药单号"
              @keyup.enter="applyReturnSearch"
            />
            <el-button type="primary" @click="applyReturnSearch">搜索</el-button>
            <el-button @click="resetReturnSearch">重置</el-button>
            <el-segmented v-model="returnStatus" :options="returnStatusOptions" @change="loadReturns" />
          </div>

          <div class="return-layout">
            <section class="work-card">
              <el-table v-loading="loadingReturns" :data="filteredDrugReturns" row-key="id" empty-text="暂无退药单">
                <el-table-column prop="returnNo" label="退药单号" min-width="150" />
                <el-table-column prop="prescriptionNo" label="处方号" min-width="150" />
                <el-table-column prop="patientName" label="患者" min-width="110" />
                <el-table-column label="金额" width="110" align="right">
                  <template #default="{ row }">¥{{ amountText(row.totalAmount) }}</template>
                </el-table-column>
                <el-table-column label="状态" width="130">
                  <template #default="{ row }">
                    <el-tag :type="returnStatusType(row.status)" effect="plain">{{ returnStatusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="180" fixed="right">
                  <template #default="{ row }">
                    <el-button type="primary" link @click="showReturnPrescriptionDetail(row)">处方明细</el-button>
                    <el-button type="warning" link @click="showReturnDetail(row)">退药明细</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </section>

            <aside class="detail-panel">
              <div class="detail-panel__head">
                <h2>{{ returnDetailTitle }}</h2>
                <el-tag v-if="selectedReturn" :type="returnStatusType(selectedReturn.status)" effect="plain">
                  {{ returnStatusLabel(selectedReturn.status) }}
                </el-tag>
              </div>
              <el-empty v-if="!selectedReturn" description="点击明细按钮查看详情" :image-size="86" />
              <template v-else-if="returnDetailMode === 'prescription'">
                <div class="rx-summary">
                  <strong>{{ selectedReturn.patientName || '-' }}</strong>
                  <span>{{ selectedReturn.prescriptionNo }}</span>
                  <em>退药单 {{ selectedReturn.returnNo }}</em>
                </div>
                <div class="detail-meta">
                  <span>退药状态</span><strong>{{ returnStatusLabel(selectedReturn.status) }}</strong>
                  <span>退药金额</span><strong>¥{{ amountText(selectedReturn.totalAmount) }}</strong>
                  <span>申请时间</span><strong>{{ formatDateTime(selectedReturn.createdAt) }}</strong>
                </div>
                <el-table :data="selectedReturn.items" size="small" empty-text="暂无处方药品明细">
                  <el-table-column prop="drugName" label="药品" min-width="130" show-overflow-tooltip />
                  <el-table-column prop="quantity" label="数量" width="70" />
                  <el-table-column label="单价" width="90" align="right">
                    <template #default="{ row }">¥{{ amountText(row.unitPrice) }}</template>
                  </el-table-column>
                  <el-table-column label="金额" width="90" align="right">
                    <template #default="{ row }">¥{{ amountText(row.amount) }}</template>
                  </el-table-column>
                </el-table>
              </template>
              <template v-else>
                <div class="rx-summary">
                  <strong>{{ selectedReturn.patientName || '-' }}</strong>
                  <span>{{ selectedReturn.returnNo }}</span>
                  <em>{{ selectedReturn.prescriptionNo }}</em>
                </div>
                <div class="detail-meta">
                  <span>意见类型</span><strong>{{ selectedReturn.opinionTemplate || '-' }}</strong>
                  <span>申请时间</span><strong>{{ formatDateTime(selectedReturn.createdAt) }}</strong>
                  <template v-if="selectedReturn.status === 'RETURN_REFUNDED'">
                    <span>退费收银员</span><strong>{{ selectedReturn.cashierId || '-' }}</strong>
                    <span>退费时间</span><strong>{{ formatDateTime(selectedReturn.completedAt) }}</strong>
                  </template>
                </div>
                <div class="opinion-block">
                  <span>医生退药意见</span>
                  <p>{{ selectedReturn.doctorOpinion || '-' }}</p>
                </div>
                <div v-if="selectedReturn.pharmacistOpinion" class="opinion-block">
                  <span>药房意见</span>
                  <p>{{ selectedReturn.pharmacistOpinion }}</p>
                </div>
              </template>
            </aside>
          </div>
        </section>

        <section v-show="currentPage === 'inventory'" class="work-page">
          <div class="page-head">
            <div>
              <h1>药品库存管理</h1>
            </div>
            <el-button :loading="loadingDrugs" @click="loadDrugs">刷新</el-button>
          </div>

          <div class="query-bar">
            <el-input v-model="drugKeyword" clearable placeholder="药品名称或编码" @keyup.enter="loadDrugs" @clear="loadDrugs" />
            <el-select v-model="drugStorageCondition" placeholder="存储条件" @change="loadDrugs">
              <el-option
                v-for="option in storageConditionOptions"
                :key="option.value || 'all'"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-button type="primary" :loading="loadingDrugs" @click="loadDrugs">搜索</el-button>
            <el-button @click="resetDrugSearch">重置</el-button>
          </div>

          <section class="work-card">
            <div v-loading="loadingDrugs" class="inventory-groups">
              <el-empty v-if="!inventoryGroups.length" description="暂无药品" :image-size="96" />
              <template v-else>
                <section
                  v-for="group in inventoryGroups"
                  :key="group.dosageForm"
                  :class="['inventory-group', group.lowStockCount > 0 && 'inventory-group--warning']"
                >
                  <button class="inventory-group__head" type="button" @click="toggleDosageGroup(group.dosageForm)">
                    <span class="inventory-group__toggle">{{ group.collapsed ? '+' : '-' }}</span>
                    <strong>{{ group.dosageForm }}</strong>
                    <span>{{ group.items.length }} 个药品</span>
                    <el-tag v-if="group.lowStockCount > 0" type="danger" effect="plain">
                      {{ group.lowStockCount }} 个库存预警
                    </el-tag>
                  </button>
                  <el-table
                    v-show="!group.collapsed"
                    :data="group.items"
                    row-key="id"
                    empty-text="暂无药品"
                    class="inventory-group__table"
                  >
                    <el-table-column prop="drugCode" label="编码" min-width="120" />
                    <el-table-column prop="drugName" label="药品" min-width="180" />
                    <el-table-column prop="specification" label="规格" min-width="140" />
                    <el-table-column prop="storageCondition" label="存储条件" width="120" />
                    <el-table-column prop="unit" label="单位" width="90" />
                    <el-table-column label="单价" width="100" align="right">
                      <template #default="{ row }">¥{{ amountText(row.unitPrice) }}</template>
                    </el-table-column>
                    <el-table-column prop="quantity" label="库存" width="90" />
                    <el-table-column prop="warningThreshold" label="预警阈值" width="100" />
                    <el-table-column label="预警" width="100">
                      <template #default="{ row }">
                        <el-tag :type="isLowStock(row) ? 'danger' : 'success'" effect="plain">
                          {{ isLowStock(row) ? '偏低' : '正常' }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="100" fixed="right">
                      <template #default="{ row }">
                        <el-button type="primary" link @click="openStockIn(row)">入库</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                </section>
              </template>
            </div>
          </section>
        </section>
      </main>
    </div>

    <el-dialog v-model="stockInForm.visible" title="新增库存登记" width="420px" destroy-on-close>
      <div v-if="stockInForm.drug" class="stock-in-summary">
        <strong>{{ stockInForm.drug.drugName }}</strong>
        <span>{{ stockInForm.drug.specification }} / {{ stockInForm.drug.dosageForm }}</span>
        <em>当前库存 {{ stockInForm.drug.quantity }} {{ stockInForm.drug.unit }}</em>
      </div>
      <el-form label-width="86px">
        <el-form-item label="入库数量">
          <el-input-number v-model="stockInForm.quantity" :min="1" :step="1" :precision="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="登记备注">
          <el-input v-model="stockInForm.reason" maxlength="128" show-word-limit placeholder="如采购入库、盘盈调整" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stockInForm.visible = false">取消</el-button>
        <el-button type="primary" :loading="stockInSubmitting" @click="submitStockIn">确认入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../../store/auth';
import {
  dispensePrescription,
  getDrugReturns,
  getDrugs,
  getPrescriptions,
  stockInDrug,
  type Drug,
  type DrugReturnOrder,
  type Prescription
} from '../../api/pharmacy';

type PageKey = 'dispense' | 'returns' | 'inventory';
type InventoryGroup = {
  dosageForm: string;
  items: Drug[];
  lowStockCount: number;
  collapsed: boolean;
};

const router = useRouter();
const auth = useAuthStore();

const currentPage = ref<PageKey>('dispense');
const waitingPrescriptions = ref<Prescription[]>([]);
const dispensedPrescriptions = ref<Prescription[]>([]);
const selected = ref<Prescription>();
const selectedReturn = ref<DrugReturnOrder>();
const returnDetailMode = ref<'prescription' | 'return'>('prescription');
const drugReturns = ref<DrugReturnOrder[]>([]);
const drugs = ref<Drug[]>([]);
const returnStatus = ref('RETURN_PENDING_REFUND');
const drugKeyword = ref('');
const drugStorageCondition = ref('');
const collapsedDosageGroups = ref<string[]>([]);
const loadingPrescriptions = ref(false);
const loadingReturns = ref(false);
const loadingDrugs = ref(false);
const stockInSubmitting = ref(false);
const dispensingId = ref('');
const authRedirecting = ref(false);

const prescriptionSearch = reactive({
  patientName: '',
  prescriptionNo: ''
});
const activePrescriptionSearch = reactive({
  patientName: '',
  prescriptionNo: ''
});
const returnSearch = reactive({
  patientName: '',
  prescriptionNo: '',
  returnNo: ''
});
const activeReturnSearch = reactive({
  patientName: '',
  prescriptionNo: '',
  returnNo: ''
});
const stockInForm = reactive({
  visible: false,
  drug: undefined as Drug | undefined,
  quantity: 1,
  reason: ''
});

const returnStatusOptions = [
  { label: '未缴费（已退药）', value: 'RETURNED' },
  { label: '未退费（已退药）', value: 'RETURN_PENDING_REFUND' },
  { label: '已退费（已退药）', value: 'RETURN_REFUNDED' }
];

const storageConditionOptions = [
  { label: '全部存储条件', value: '' },
  { label: '常温', value: '常温' },
  { label: '阴凉干燥', value: '阴凉干燥' },
  { label: '避光常温', value: '避光常温' },
  { label: '避光阴凉', value: '避光阴凉' },
  { label: '冷藏2-8℃', value: '冷藏2-8℃' }
];

const navItems = computed(() => [
  { key: 'dispense' as const, label: '处方发药', badge: waitingPrescriptions.value.length || '' },
  { key: 'returns' as const, label: '退药管理', badge: drugReturns.value.length || '' },
  { key: 'inventory' as const, label: '药品库存管理', badge: lowStockCount.value || '' }
]);

const lowStockCount = computed(() => drugs.value.filter(isLowStock).length);
const inventoryGroups = computed<InventoryGroup[]>(() => {
  const collapsed = new Set(collapsedDosageGroups.value);
  const byDosageForm = new Map<string, Drug[]>();
  for (const item of drugs.value) {
    const dosageForm = item.dosageForm || '未分类';
    const groupItems = byDosageForm.get(dosageForm) ?? [];
    groupItems.push(item);
    byDosageForm.set(dosageForm, groupItems);
  }

  return [...byDosageForm.entries()]
    .map(([dosageForm, items]) => {
      const sortedItems = [...items].sort(compareDrugsForInventory);
      return {
        dosageForm,
        items: sortedItems,
        lowStockCount: sortedItems.filter(isLowStock).length,
        collapsed: collapsed.has(dosageForm)
      };
    })
    .sort((left, right) => {
      const leftHasWarning = left.lowStockCount > 0;
      const rightHasWarning = right.lowStockCount > 0;
      if (leftHasWarning !== rightHasWarning) return leftHasWarning ? -1 : 1;
      if (left.lowStockCount !== right.lowStockCount) return right.lowStockCount - left.lowStockCount;
      return left.dosageForm.localeCompare(right.dosageForm, 'zh-CN');
    });
});

const filteredWaitingPrescriptions = computed(() => filterPrescriptions(waitingPrescriptions.value));
const filteredDispensedPrescriptions = computed(() => filterPrescriptions(dispensedPrescriptions.value));
const filteredDrugReturns = computed(() => filterReturns(drugReturns.value));
const returnDetailTitle = computed(() => (returnDetailMode.value === 'prescription' ? '处方明细' : '退药明细'));

const today = computed(() => new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }));
const dayOfWeek = computed(() => ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][new Date().getDay()]);

async function loadPrescriptions() {
  loadingPrescriptions.value = true;
  try {
    const all = await getPrescriptions();
    const waiting = all.filter((item) => item.status === 'WAITING_DISPENSE');
    const dispensed = all.filter((item) => item.status === 'DISPENSED' || Boolean(item.dispensedAt));
    waitingPrescriptions.value = [...waiting].sort((left, right) => timeValue(left.createdAt) - timeValue(right.createdAt));
    dispensedPrescriptions.value = [...dispensed].sort((left, right) => timeValue(right.dispensedAt || right.createdAt) - timeValue(left.dispensedAt || left.createdAt));
    if (selected.value) {
      selected.value = [...waitingPrescriptions.value, ...dispensedPrescriptions.value].find((item) => item.id === selected.value?.id);
    }
  } catch (error) {
    handleRequestFailure(error, '处方加载失败');
  } finally {
    loadingPrescriptions.value = false;
  }
}

async function loadReturns() {
  loadingReturns.value = true;
  try {
    drugReturns.value = await getDrugReturns({ status: returnStatus.value });
  } catch (error) {
    handleRequestFailure(error, '退药记录加载失败');
  } finally {
    loadingReturns.value = false;
  }
}

async function loadDrugs() {
  loadingDrugs.value = true;
  try {
    drugs.value = await getDrugs({
      keyword: drugKeyword.value.trim(),
      storageCondition: drugStorageCondition.value
    });
  } catch (error) {
    handleRequestFailure(error, '药品库存加载失败');
  } finally {
    loadingDrugs.value = false;
  }
}

async function dispense(id: string) {
  dispensingId.value = id;
  try {
    selected.value = await dispensePrescription(id);
    ElMessage.success('发药完成，库存已扣减');
    await Promise.all([loadPrescriptions(), loadDrugs()]);
  } catch (error) {
    handleRequestFailure(error, '发药失败');
  } finally {
    dispensingId.value = '';
  }
}

function openStockIn(row: Drug) {
  stockInForm.drug = row;
  stockInForm.quantity = 1;
  stockInForm.reason = '';
  stockInForm.visible = true;
}

async function submitStockIn() {
  if (!stockInForm.drug) return;
  const quantity = Number(stockInForm.quantity);
  if (!Number.isFinite(quantity) || quantity <= 0) {
    ElMessage.error('入库数量必须大于 0');
    return;
  }
  stockInSubmitting.value = true;
  try {
    await stockInDrug(stockInForm.drug.id, {
      quantity,
      reason: stockInForm.reason.trim() || undefined
    });
    ElMessage.success('入库登记完成');
    stockInForm.visible = false;
    await loadDrugs();
  } catch (error) {
    handleRequestFailure(error, '入库登记失败');
  } finally {
    stockInSubmitting.value = false;
  }
}

function showDetail(row: Prescription) {
  selected.value = row;
}

function showReturnPrescriptionDetail(row: DrugReturnOrder) {
  selectedReturn.value = row;
  returnDetailMode.value = 'prescription';
}

function showReturnDetail(row: DrugReturnOrder) {
  selectedReturn.value = row;
  returnDetailMode.value = 'return';
}

function applyPrescriptionSearch() {
  activePrescriptionSearch.patientName = prescriptionSearch.patientName.trim();
  activePrescriptionSearch.prescriptionNo = prescriptionSearch.prescriptionNo.trim();
}

function resetPrescriptionSearch() {
  prescriptionSearch.patientName = '';
  prescriptionSearch.prescriptionNo = '';
  activePrescriptionSearch.patientName = '';
  activePrescriptionSearch.prescriptionNo = '';
}

function applyReturnSearch() {
  activeReturnSearch.patientName = returnSearch.patientName.trim();
  activeReturnSearch.prescriptionNo = returnSearch.prescriptionNo.trim();
  activeReturnSearch.returnNo = returnSearch.returnNo.trim();
}

function resetReturnSearch() {
  returnSearch.patientName = '';
  returnSearch.prescriptionNo = '';
  returnSearch.returnNo = '';
  activeReturnSearch.patientName = '';
  activeReturnSearch.prescriptionNo = '';
  activeReturnSearch.returnNo = '';
}

function resetDrugSearch() {
  drugKeyword.value = '';
  drugStorageCondition.value = '';
  loadDrugs();
}

function toggleDosageGroup(dosageForm: string) {
  collapsedDosageGroups.value = collapsedDosageGroups.value.includes(dosageForm)
    ? collapsedDosageGroups.value.filter((item) => item !== dosageForm)
    : [...collapsedDosageGroups.value, dosageForm];
}

function isLowStock(item: Drug) {
  return item.quantity <= item.warningThreshold;
}

function compareDrugsForInventory(left: Drug, right: Drug) {
  if (isLowStock(left) !== isLowStock(right)) return isLowStock(left) ? -1 : 1;
  const nameCompare = left.drugName.localeCompare(right.drugName, 'zh-CN');
  if (nameCompare !== 0) return nameCompare;
  return left.drugCode.localeCompare(right.drugCode);
}

function filterPrescriptions(items: Prescription[]) {
  const patientName = activePrescriptionSearch.patientName.toLowerCase();
  const prescriptionNo = activePrescriptionSearch.prescriptionNo.toLowerCase();
  return items.filter((item) => {
    const matchesPatient = !patientName || (item.patientName ?? '').toLowerCase().includes(patientName);
    const matchesNo = !prescriptionNo || item.prescriptionNo.toLowerCase().includes(prescriptionNo);
    return matchesPatient && matchesNo;
  });
}

function filterReturns(items: DrugReturnOrder[]) {
  const patientName = activeReturnSearch.patientName.toLowerCase();
  const prescriptionNo = activeReturnSearch.prescriptionNo.toLowerCase();
  const returnNo = activeReturnSearch.returnNo.toLowerCase();
  return items.filter((item) => {
    const matchesPatient = !patientName || item.patientName.toLowerCase().includes(patientName);
    const matchesPrescriptionNo = !prescriptionNo || item.prescriptionNo.toLowerCase().includes(prescriptionNo);
    const matchesReturnNo = !returnNo || item.returnNo.toLowerCase().includes(returnNo);
    return matchesPatient && matchesPrescriptionNo && matchesReturnNo;
  });
}

function prescriptionStatusLabel(value: string) {
  return {
    WAITING_DISPENSE: '待发药',
    DISPENSED: '已发药',
    RETURNED: '未缴费（已退药）',
    RETURN_PENDING_REFUND: '未退费（已退药）',
    RETURN_REFUNDED: '已退费（已退药）'
  }[value] ?? value;
}

function prescriptionTagType(value: string): '' | 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  if (value === 'WAITING_DISPENSE') return 'warning';
  if (value === 'DISPENSED') return 'success';
  if (value === 'RETURNED' || value === 'RETURN_REFUNDED') return 'info';
  return '';
}

function returnStatusLabel(value: string) {
  return {
    RETURNED: '未缴费（已退药）',
    RETURN_PENDING_REFUND: '未退费（已退药）',
    RETURN_REFUNDED: '已退费（已退药）'
  }[value] ?? value;
}

function returnStatusType(value: string): '' | 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  if (value === 'RETURN_PENDING_REFUND') return 'warning';
  if (value === 'RETURN_REFUNDED') return 'success';
  if (value === 'RETURNED') return 'info';
  return '';
}

function amountText(value: number) {
  return Number(value ?? 0).toFixed(2);
}

function formatDateTime(value?: string) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-';
}

function timeValue(value?: string) {
  if (!value) return 0;
  const time = new Date(value).getTime();
  return Number.isFinite(time) ? time : 0;
}

function errorMessage(error: unknown, fallback: string) {
  const candidate = error as {
    response?: { data?: { message?: string; error?: string } | string };
    message?: string;
  };
  const data = candidate.response?.data;
  if (typeof data === 'string' && data) return data;
  if (typeof data === 'object' && data?.message) return data.message;
  if (typeof data === 'object' && data?.error) return data.error;
  return candidate.message || fallback;
}

function isUnauthorized(error: unknown) {
  return (error as { response?: { status?: number } })?.response?.status === 401;
}

function handleRequestFailure(error: unknown, fallback: string) {
  if (isUnauthorized(error)) {
    redirectToLogin();
    return;
  }
  ElMessage.error(errorMessage(error, fallback));
}

function redirectToLogin() {
  if (authRedirecting.value) return;
  authRedirecting.value = true;
  auth.signOut();
  ElMessage.error('登录已过期，请重新登录');
  router.replace('/login');
}

function logout() {
  auth.signOut();
  router.push('/login');
}

onMounted(async () => {
  await Promise.all([loadPrescriptions(), loadReturns(), loadDrugs()]);
});
</script>

<style scoped>
.pharmacy-wks {
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
  font-family: Inter, "Microsoft YaHei", system-ui, sans-serif;
}

.pharmacy-nav {
  height: 52px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  background: linear-gradient(135deg, #0cbdcc 0%, #0899a5 100%);
  color: #fff;
  box-shadow: 0 2px 8px rgb(0 0 0 / 18%);
  z-index: 10;
}

.pharmacy-nav__brand,
.pharmacy-nav__right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pharmacy-nav__logo {
  width: 30px;
  height: 30px;
  border-radius: 7px;
  display: grid;
  place-items: center;
  background: #fff;
  color: #0899a5;
  font-size: 20px;
  font-weight: 900;
}

.pharmacy-nav__title {
  font-size: 16px;
  font-weight: 700;
}

.pharmacy-nav__right {
  gap: 20px;
  font-size: 13px;
}

.nav-logout {
  color: rgb(255 255 255 / 88%);
}

.pharmacy-body {
  height: calc(100vh - 52px);
  display: flex;
  overflow: hidden;
}

.pharmacy-main {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding: 16px;
}

.pharmacy-sidebar {
  width: 190px;
  flex-shrink: 0;
  padding: 12px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-right: 1px solid #e5e7eb;
}

.sidebar-hdr {
  height: 34px;
  padding: 0 4px 8px;
  display: flex;
  align-items: center;
  color: #1f2937;
  font-size: 14px;
  font-weight: 700;
}

.nav-item {
  width: 100%;
  height: 40px;
  border: none;
  border-radius: 6px;
  margin-bottom: 6px;
  padding: 0 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: transparent;
  color: #374151;
  cursor: pointer;
  font-size: 14px;
  text-align: left;
}

.nav-item:hover {
  background: #f8fafc;
}

.nav-item--active {
  background: #e6f9fa;
  color: #0899a5;
  font-weight: 700;
}

.nav-item em {
  min-width: 22px;
  height: 20px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: #0cbdcc;
  color: #fff;
  font-size: 12px;
  font-style: normal;
}

.sidebar-footer {
  margin-top: auto;
  padding: 10px 4px 0;
  border-top: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #9ca3af;
  font-size: 12px;
}

.work-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.page-head,
.query-bar,
.card-head,
.detail-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.page-head h1 {
  margin: 0 0 4px;
  color: #111827;
  font-size: 22px;
  letter-spacing: 0;
}

.page-head p,
.card-head p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.query-bar {
  justify-content: flex-start;
  flex-wrap: wrap;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.query-bar .el-input {
  width: 240px;
}

.query-bar .el-select {
  width: 180px;
}

.inventory-groups {
  min-height: 160px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.inventory-group {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.inventory-group--warning {
  border-color: #f3b8b8;
}

.inventory-group__head {
  width: 100%;
  min-height: 44px;
  border: none;
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  background: #f8fafc;
  color: #374151;
  cursor: pointer;
  text-align: left;
}

.inventory-group--warning .inventory-group__head {
  background: #fff5f5;
}

.inventory-group__head:hover {
  background: #edf8fa;
}

.inventory-group__head strong {
  color: #111827;
  font-size: 15px;
}

.inventory-group__head span {
  color: #64748b;
  font-size: 13px;
}

.inventory-group__toggle {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  display: grid;
  place-items: center;
  background: #e6f9fa;
  color: #0899a5;
  font-size: 16px;
  font-weight: 800;
  line-height: 1;
}

.inventory-group__table {
  width: 100%;
}

.dispense-layout,
.return-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  align-items: start;
}

.prescription-lists {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.work-card,
.detail-panel {
  min-width: 0;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.card-head {
  align-items: flex-start;
  margin-bottom: 14px;
}

.card-head h2,
.detail-panel h2 {
  margin: 0 0 4px;
  color: #111827;
  font-size: 16px;
  letter-spacing: 0;
}

.detail-panel {
  position: sticky;
  top: 0;
}

.rx-summary {
  margin: 12px 0;
  padding: 12px;
  border: 1px solid #a8e8ec;
  border-radius: 8px;
  background: #f0f9fa;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.rx-summary strong {
  color: #111827;
  font-size: 18px;
}

.rx-summary span {
  color: #0899a5;
  font-weight: 700;
}

.rx-summary em {
  color: #64748b;
  font-size: 13px;
  font-style: normal;
}

.detail-meta {
  margin-bottom: 12px;
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 8px 10px;
  font-size: 13px;
}

.detail-meta span {
  color: #64748b;
}

.detail-meta strong {
  min-width: 0;
  color: #1f2937;
  font-weight: 600;
}

.opinion-block {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.opinion-block span {
  display: block;
  margin-bottom: 8px;
  color: #64748b;
  font-size: 14px;
  font-weight: 700;
}

.opinion-block p {
  margin: 0;
  color: #1f2937;
  font-size: 16px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.stock-in-summary {
  margin-bottom: 16px;
  padding: 12px;
  border: 1px solid #d1f2f4;
  border-radius: 8px;
  background: #f0f9fa;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stock-in-summary strong {
  color: #111827;
  font-size: 16px;
}

.stock-in-summary span,
.stock-in-summary em {
  color: #64748b;
  font-size: 13px;
  font-style: normal;
}

@media (max-width: 1200px) {
  .dispense-layout,
  .return-layout {
    grid-template-columns: 1fr;
  }

  .detail-panel {
    position: static;
  }
}

@media (max-width: 900px) {
  .pharmacy-body {
    height: auto;
    min-height: calc(100vh - 52px);
    flex-direction: column;
    overflow: visible;
  }

  .pharmacy-main {
    overflow: visible;
  }

  .pharmacy-sidebar {
    width: 100%;
    flex-direction: row;
    overflow-x: auto;
    border-right: none;
    border-bottom: 1px solid #e5e7eb;
  }

  .sidebar-hdr,
  .sidebar-footer {
    display: none;
  }

  .nav-item {
    width: auto;
    min-width: 126px;
    margin-right: 6px;
    margin-bottom: 0;
  }
}

@media (max-width: 760px) {
  .pharmacy-nav {
    height: auto;
    min-height: 52px;
    align-items: flex-start;
    flex-direction: column;
    padding: 10px 14px;
  }

  .page-head,
  .query-bar,
  .card-head {
    align-items: stretch;
    flex-direction: column;
  }

  .query-bar .el-input {
    width: 100%;
  }

  .query-bar .el-select {
    width: 100%;
  }
}
</style>
