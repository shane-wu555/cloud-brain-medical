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
          @click="switchPage(item.key)"
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
        <section v-show="currentPage === 'dispense'" class="work-page dispense-page">
          <div class="page-head">
            <div>
              <h1>处方发药</h1>
            </div>
            <el-button :loading="loadingPrescriptions" @click="loadPrescriptions">刷新</el-button>
          </div>

          <div class="query-bar">
            <el-input
              v-model="prescriptionSearch.keyword"
              class="head-search"
              clearable
              placeholder="输入处方号、患者姓名或诊断"
              @keyup.enter="applyPrescriptionSearch"
              @clear="clearPrescriptionSearch"
            />
            <el-button type="primary" @click="applyPrescriptionSearch">搜索</el-button>
            <el-button @click="resetPrescriptionSearch">重置</el-button>
          </div>

          <section class="dispense-window">
            <div class="dispense-board-head">
              <div class="dispense-board-title">
                <span>处方发药</span>
                <strong>{{ activeDispenseTab === 'waiting' ? filteredWaitingPrescriptions.length : filteredDispensedPrescriptions.length }}</strong>
                <em>{{ activeDispenseTab === 'waiting' ? '待取药处方' : '已取药处方' }}</em>
              </div>

              <div class="dispense-board-tools">
                <div class="dispense-switch" role="tablist" aria-label="处方取药状态">
                  <button
                    type="button"
                    :class="['dispense-switch__tab', activeDispenseTab === 'waiting' && 'dispense-switch__tab--active']"
                    @click="activeDispenseTab = 'waiting'"
                  >
                    <span>待取药</span>
                    <em>{{ filteredWaitingPrescriptions.length }}</em>
                  </button>
                  <button
                    type="button"
                    :class="['dispense-switch__tab', activeDispenseTab === 'dispensed' && 'dispense-switch__tab--active']"
                    @click="activeDispenseTab = 'dispensed'"
                  >
                    <span>已取药</span>
                    <em>{{ filteredDispensedPrescriptions.length }}</em>
                  </button>
                  <i :class="['dispense-switch__thumb', activeDispenseTab === 'dispensed' && 'dispense-switch__thumb--right']"></i>
                </div>

                <el-input
                  v-model="prescriptionSearch.keyword"
                  class="dispense-search"
                  clearable
                  placeholder="处方号 / 患者 / 诊断"
                  @keyup.enter="applyPrescriptionSearch"
                  @clear="clearPrescriptionSearch"
                />
                <el-button type="primary" @click="applyPrescriptionSearch">搜索</el-button>
                <el-button @click="resetPrescriptionSearch">重置</el-button>
                <el-button :loading="loadingPrescriptions" @click="loadPrescriptions">刷新</el-button>
              </div>
            </div>

            <div v-loading="loadingPrescriptions" class="prescription-window">
              <el-empty v-if="!activePrescriptionRows.length" :description="activeDispenseTab === 'waiting' ? '暂无待取药处方' : '暂无已取药处方'" :image-size="96" />
              <article
                v-for="row in activePrescriptionRows"
                :key="row.id"
                :class="['rx-card', isPrescriptionExpanded(row.id) && 'rx-card--open']"
              >
                <button class="rx-card__main" type="button" @click="togglePrescriptionDetail(row.id)">
                  <span class="rx-card__identity">
                    <strong>{{ row.patientName || '-' }}</strong>
                    <em>{{ row.prescriptionNo }}</em>
                  </span>
                  <span class="rx-card__diagnosis">{{ row.diagnosis || '未填写诊断' }}</span>
                  <span class="rx-card__meta">
                    <small>{{ activeDispenseTab === 'waiting' ? '开方' : '取药' }}</small>
                    <b>{{ formatDateTime(activeDispenseTab === 'waiting' ? row.createdAt : row.dispensedAt) }}</b>
                  </span>
                  <span class="rx-card__amount">¥{{ amountText(row.totalAmount) }}</span>
                  <span class="rx-card__status">
                    <el-tag :type="prescriptionTagType(row.status)" effect="plain">
                      {{ prescriptionStatusLabel(row.status) }}
                    </el-tag>
                  </span>
                  <span class="rx-card__toggle">{{ isPrescriptionExpanded(row.id) ? '收起' : '展开' }}</span>
                </button>

                <div v-show="isPrescriptionExpanded(row.id)" class="rx-card__detail">
                  <div class="rx-detail-shell">
                    <aside class="rx-detail-summary">
                      <div class="rx-detail-summary__head">
                        <span>{{ row.patientName || '-' }}</span>
                        <el-tag :type="prescriptionTagType(row.status)" effect="plain">
                          {{ prescriptionStatusLabel(row.status) }}
                        </el-tag>
                      </div>
                      <div class="rx-detail-summary__no">{{ row.prescriptionNo }}</div>
                      <p>{{ row.diagnosis || '未填写诊断' }}</p>
                      <div class="rx-detail-stats">
                        <div>
                          <span>药品</span>
                          <strong>{{ row.items?.length || 0 }} 项</strong>
                        </div>
                        <div>
                          <span>金额</span>
                          <strong>¥{{ amountText(row.totalAmount) }}</strong>
                        </div>
                      </div>
                      <div class="rx-detail-time">
                        <span>开方</span><strong>{{ formatDateTime(row.createdAt) }}</strong>
                        <span>取药</span><strong>{{ formatDateTime(row.dispensedAt) }}</strong>
                      </div>
                    </aside>

                    <div class="rx-drug-checklist">
                      <div class="rx-drug-checklist__head">
                        <span>药品核对清单</span>
                        <em>逐项核对名称、数量和单价后确认发药</em>
                      </div>
                      <div v-if="row.items?.length" class="rx-drug-list">
                        <div v-for="(drug, index) in row.items" :key="`${row.id}-${drug.drugName}-${index}`" class="rx-drug-row">
                          <span class="rx-drug-row__index">{{ index + 1 }}</span>
                          <span class="rx-drug-row__name">{{ drug.drugName }}</span>
                          <span class="rx-drug-row__qty">× {{ drug.quantity }}</span>
                          <span class="rx-drug-row__price">¥{{ amountText(drug.unitPrice) }}</span>
                        </div>
                      </div>
                      <el-empty v-else description="暂无药品明细" :image-size="72" />

                      <div class="rx-card__actions">
                        <span>{{ activeDispenseTab === 'waiting' ? '确认后将扣减库存并流转到已取药' : '该处方已完成取药' }}</span>
                        <el-button
                          v-if="activeDispenseTab === 'waiting'"
                          type="success"
                          :loading="dispensingId === row.id"
                          @click.stop="dispense(row.id)"
                        >
                          确认取药
                        </el-button>
                      </div>
                    </div>
                  </div>
                </div>
              </article>
            </div>
          </section>

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
              v-model="returnSearch.keyword"
              class="head-search"
              clearable
              placeholder="输入退药单号、处方号或患者姓名"
              @keyup.enter="applyReturnSearch"
              @clear="clearReturnSearch"
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
            <el-button :loading="loadingDrugs" @click="searchDrugs">刷新</el-button>
          </div>

          <div class="query-bar">
            <el-input
              v-model="drugKeyword"
              clearable
              placeholder="药品名称或编码"
              @input="scheduleDrugSearch"
              @keydown.enter.prevent="searchDrugs"
              @clear="clearDrugKeyword"
            />
            <el-button type="primary" :loading="loadingDrugs" @click="searchDrugs">搜索</el-button>
            <el-button @click="resetDrugSearch">重置</el-button>
            <el-select v-model="drugStorageCondition" placeholder="存储条件" @change="searchDrugs">
              <el-option
                v-for="option in storageConditionOptions"
                :key="option.value || 'all'"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </div>

          <section class="work-card">
            <div class="inventory-search-summary">
              <span>{{ inventorySearchSummary }}</span>
              <span v-if="loadingDrugs">正在搜索...</span>
            </div>
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
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../../store/auth';
import { useQueuePolling } from '../../composables/useQueuePolling';
import { useUnreadBadgeTracker } from '../../composables/useUnreadBadgeTracker';
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

const WORKBENCH_PAGE_SIZE = 100;

const router = useRouter();
const auth = useAuthStore();

const currentPage = ref<PageKey>('dispense');
const activeDispenseTab = ref<'waiting' | 'dispensed'>('waiting');
const expandedPrescriptionIds = ref<string[]>([]);
const waitingPrescriptions = ref<Prescription[]>([]);
const waitingPrescriptionAlerts = ref<Prescription[]>([]);
const dispensedPrescriptions = ref<Prescription[]>([]);
const selected = ref<Prescription>();
const selectedReturn = ref<DrugReturnOrder>();
const returnDetailMode = ref<'prescription' | 'return'>('prescription');
const drugReturns = ref<DrugReturnOrder[]>([]);
const returnAlertOrders = ref<DrugReturnOrder[]>([]);
const drugs = ref<Drug[]>([]);
const returnStatus = ref('');
const drugKeyword = ref('');
const drugStorageCondition = ref('');
const activeDrugKeyword = ref('');
const activeDrugStorageCondition = ref('');
const collapsedDosageGroups = ref<string[]>([]);
const loadingPrescriptions = ref(false);
const loadingReturns = ref(false);
const loadingDrugs = ref(false);
const stockInSubmitting = ref(false);
const dispensingId = ref('');
const authRedirecting = ref(false);
let drugSearchRequestId = 0;
let drugSearchTimer: number | undefined;
const unreadStoragePrefix = `pharmacy-workbench:${auth.user?.id ?? 'anonymous'}`;
const dispenseUnreadTracker = useUnreadBadgeTracker(`${unreadStoragePrefix}:dispense`);
const returnUnreadTracker = useUnreadBadgeTracker(`${unreadStoragePrefix}:returns`);
const loadedPages = reactive({
  dispense: false,
  returns: false,
  inventory: false
});

const prescriptionSearch = reactive({
  keyword: ''
});
const returnSearch = reactive({
  keyword: ''
});
const stockInForm = reactive({
  visible: false,
  drug: undefined as Drug | undefined,
  quantity: 1,
  reason: ''
});

const returnStatusOptions = [
  { label: '\u5168\u90e8\u7c7b\u578b', value: '' },
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
  { key: 'dispense' as const, label: '处方发药', badge: dispenseUnreadTracker.unreadCount.value || '' },
  { key: 'returns' as const, label: '退药管理', badge: returnUnreadTracker.unreadCount.value || '' },
  { key: 'inventory' as const, label: '药品库存管理', badge: '' }
]);

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

const inventorySearchSummary = computed(() => {
  const filters = [];
  if (activeDrugKeyword.value) {
    filters.push(`关键词“${activeDrugKeyword.value}”`);
  }
  if (activeDrugStorageCondition.value) {
    filters.push(`存储条件“${activeDrugStorageCondition.value}”`);
  }
  const prefix = filters.length ? `当前筛选：${filters.join('，')}` : '当前显示：全部药品';
  return `${prefix}，共 ${drugs.value.length} 个药品`;
});

const filteredWaitingPrescriptions = computed(() => {
  const keyword = prescriptionSearch.keyword.trim().toLowerCase();
  return waitingPrescriptions.value.filter((item) => {
    if (!keyword) return true;
    return matchesKeywordSearch(keyword, [item.prescriptionNo, item.patientName, item.diagnosis]);
  });
});
const filteredDispensedPrescriptions = computed(() => {
  const keyword = prescriptionSearch.keyword.trim().toLowerCase();
  return dispensedPrescriptions.value.filter((item) => {
    if (!keyword) return true;
    return matchesKeywordSearch(keyword, [item.prescriptionNo, item.patientName, item.diagnosis]);
  });
});
const activePrescriptionRows = computed(() => (
  activeDispenseTab.value === 'waiting'
    ? filteredWaitingPrescriptions.value
    : filteredDispensedPrescriptions.value
));
const filteredDrugReturns = computed(() => {
  const keyword = returnSearch.keyword.trim().toLowerCase();
  return drugReturns.value.filter((item) => {
    if (!keyword) return true;
    return matchesKeywordSearch(keyword, [
      item.returnNo,
      item.prescriptionNo,
      item.patientName,
      item.doctorOpinion,
      item.pharmacistOpinion
    ]);
  });
});
const returnDetailTitle = computed(() => (returnDetailMode.value === 'prescription' ? '处方明细' : '退药明细'));

const today = computed(() => new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }));
const dayOfWeek = computed(() => ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][new Date().getDay()]);

async function loadPrescriptions(silent = false) {
  if (!silent) loadingPrescriptions.value = true;
  try {
    const query = {
      page: 0,
      size: WORKBENCH_PAGE_SIZE
    };
    const [waiting, dispensed] = await Promise.all([
      getPrescriptions({ ...query, status: 'WAITING_DISPENSE' }),
      getPrescriptions({ ...query, status: 'DISPENSED' })
    ]);
    waitingPrescriptions.value = [...waiting].sort((left, right) => timeValue(left.createdAt) - timeValue(right.createdAt));
    dispensedPrescriptions.value = [...dispensed].sort((left, right) => timeValue(right.dispensedAt || right.createdAt) - timeValue(left.dispensedAt || left.createdAt));
    if (selected.value) {
      selected.value = [...waitingPrescriptions.value, ...dispensedPrescriptions.value].find((item) => item.id === selected.value?.id);
    }
    loadedPages.dispense = true;
  } catch (error) {
    handleRequestFailure(error, '处方加载失败');
  } finally {
    if (!silent) loadingPrescriptions.value = false;
  }
}

async function loadReturns(silent = false) {
  if (!silent) loadingReturns.value = true;
  try {
    drugReturns.value = await getDrugReturns({
      status: returnStatus.value || undefined,
      page: 0,
      size: WORKBENCH_PAGE_SIZE
    });
    loadedPages.returns = true;
  } catch (error) {
    handleRequestFailure(error, '退药记录加载失败');
  } finally {
    if (!silent) loadingReturns.value = false;
  }
}

async function refreshWorkbenchAlerts() {
  const [waitingResult, returnResult] = await Promise.allSettled([
    getPrescriptions({ status: 'WAITING_DISPENSE', page: 0, size: WORKBENCH_PAGE_SIZE }),
    getDrugReturns({ page: 0, size: WORKBENCH_PAGE_SIZE })
  ]);

  if (waitingResult.status === 'fulfilled') {
    waitingPrescriptionAlerts.value = [...waitingResult.value].sort(
      (left, right) => timeValue(left.createdAt) - timeValue(right.createdAt)
    );
  } else if (isUnauthorized(waitingResult.reason)) {
    redirectToLogin();
    return;
  }

  if (returnResult.status === 'fulfilled') {
    returnAlertOrders.value = [...returnResult.value].sort(
      (left, right) => timeValue(right.createdAt) - timeValue(left.createdAt)
    );
  } else if (isUnauthorized(returnResult.reason)) {
    redirectToLogin();
  }
}

async function loadDrugs(criteria?: { keyword?: string; storageCondition?: string }) {
  const keyword = criteria?.keyword ?? drugKeyword.value.trim();
  const storageCondition = criteria?.storageCondition ?? drugStorageCondition.value;
  const requestId = ++drugSearchRequestId;
  loadingDrugs.value = true;
  try {
    const result = await getDrugs({
      keyword: keyword || undefined,
      storageCondition: storageCondition || undefined
    });
    if (requestId !== drugSearchRequestId) return;
    drugs.value = result;
    activeDrugKeyword.value = keyword;
    activeDrugStorageCondition.value = storageCondition;
    collapsedDosageGroups.value = [];
    loadedPages.inventory = true;
  } catch (error) {
    if (requestId !== drugSearchRequestId) return;
    handleRequestFailure(error, '药品库存加载失败');
  } finally {
    if (requestId === drugSearchRequestId) {
      loadingDrugs.value = false;
    }
  }
}

async function dispense(id: string) {
  dispensingId.value = id;
  try {
    await dispensePrescription(id);
    selected.value = undefined;
    ElMessage.success('发药完成，库存已扣减');
    const reloads = [loadPrescriptions()];
    if (loadedPages.inventory) {
      reloads.push(loadDrugs());
    }
    await Promise.all(reloads);
    activeDispenseTab.value = 'dispensed';
    expandedPrescriptionIds.value = [id];
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

function isPrescriptionExpanded(id: string) {
  return expandedPrescriptionIds.value.includes(id);
}

function togglePrescriptionDetail(id: string) {
  expandedPrescriptionIds.value = isPrescriptionExpanded(id)
    ? expandedPrescriptionIds.value.filter((item) => item !== id)
    : [id];
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
  prescriptionSearch.keyword = prescriptionSearch.keyword.trim();
}

function clearPrescriptionSearch() {
  prescriptionSearch.keyword = '';
}

function resetPrescriptionSearch() {
  clearPrescriptionSearch();
}

function applyReturnSearch() {
  returnSearch.keyword = returnSearch.keyword.trim();
}

function clearReturnSearch() {
  returnSearch.keyword = '';
}

function resetReturnSearch() {
  clearReturnSearch();
}

function resetDrugSearch() {
  clearDrugSearchTimer();
  drugKeyword.value = '';
  drugStorageCondition.value = '';
  void loadDrugs({
    keyword: '',
    storageCondition: ''
  });
}

function searchDrugs() {
  clearDrugSearchTimer();
  void searchDrugsAfterInputSettled();
}

function clearDrugSearchTimer() {
  if (drugSearchTimer) {
    window.clearTimeout(drugSearchTimer);
    drugSearchTimer = undefined;
  }
}

function scheduleDrugSearch() {
  clearDrugSearchTimer();
  drugSearchTimer = window.setTimeout(() => {
    void searchDrugsAfterInputSettled();
  }, 300);
}

async function searchDrugsAfterInputSettled() {
  await nextTick();
  await loadDrugs({
    keyword: drugKeyword.value.trim(),
    storageCondition: drugStorageCondition.value
  });
}

function clearDrugKeyword() {
  clearDrugSearchTimer();
  drugKeyword.value = '';
  void searchDrugsAfterInputSettled();
}

function switchPage(page: PageKey) {
  const changed = currentPage.value !== page;
  if (page !== 'dispense') {
    selected.value = undefined;
  }
  if (page !== 'returns') {
    selectedReturn.value = undefined;
    returnDetailMode.value = 'prescription';
  }
  currentPage.value = page;
  markPageAsRead(page);
  if (!changed) {
    void loadCurrentPage();
  }
}

function markPageAsRead(page: PageKey) {
  if (page === 'dispense') {
    dispenseUnreadTracker.markRead(waitingPrescriptionAlerts.value.map((item) => item.id));
  }
  if (page === 'returns') {
    returnUnreadTracker.markRead(returnAlertOrders.value.map((item) => item.id));
  }
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

function matchesKeywordSearch(keyword: string, values: Array<string | number | null | undefined>) {
  return values
    .filter((value): value is string | number => value !== null && value !== undefined && value !== '')
    .some((value) => String(value).toLowerCase().includes(keyword));
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

async function loadCurrentPage() {
  if (currentPage.value === 'dispense' && !loadedPages.dispense) {
    await loadPrescriptions();
  }
  if (currentPage.value === 'returns' && !loadedPages.returns) {
    await loadReturns();
  }
  if (currentPage.value === 'inventory' && !loadedPages.inventory) {
    await loadDrugs();
  }
}

watch(currentPage, () => {
  loadCurrentPage();
});

watch(activeDispenseTab, () => {
  expandedPrescriptionIds.value = [];
});

watch(
  () => prescriptionSearch.keyword,
  () => {
    expandedPrescriptionIds.value = [];
  }
);

watch(
  () => waitingPrescriptionAlerts.value.map((item) => item.id),
  (ids) => {
    dispenseUnreadTracker.sync(ids);
  }
);

watch(
  () => returnAlertOrders.value.map((item) => item.id),
  (ids) => {
    returnUnreadTracker.sync(ids);
  }
);

// 定时轮询处方列表：缴费后自动刷新待发药列表
// 查看处方明细时跳过轮询，避免刷新导致选中状态丢失
const isEditing = computed(() => !!selected.value || !!selectedReturn.value || expandedPrescriptionIds.value.length > 0);
useQueuePolling(isEditing, async () => {
  await Promise.all([loadPrescriptions(true), loadReturns(true), refreshWorkbenchAlerts()]);
});

onBeforeUnmount(() => {
  clearDrugSearchTimer();
});

onMounted(async () => {
  await Promise.all([loadCurrentPage(), refreshWorkbenchAlerts()]);
  dispenseUnreadTracker.sync(waitingPrescriptionAlerts.value.map((item) => item.id));
  returnUnreadTracker.sync(returnAlertOrders.value.map((item) => item.id));
  markPageAsRead(currentPage.value);
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
  height: 56px;
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
  font-family: inherit;
  line-height: 1;
}

.pharmacy-nav__right > span,
.pharmacy-nav__right :deep(.el-button) {
  height: 32px;
  display: inline-flex;
  align-items: center;
  font: inherit;
  line-height: 1;
}

.nav-logout {
  color: rgb(255 255 255 / 88%);
}

.pharmacy-body {
  height: calc(100vh - 56px);
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

.query-bar .head-search {
  width: 280px;
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

.inventory-search-summary {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  color: #64748b;
  font-size: 13px;
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

.dispense-window {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.05);
}

.dispense-page > .page-head,
.dispense-page > .query-bar {
  display: none;
}

.dispense-board-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid #e5e7eb;
}

.dispense-board-title {
  min-width: 180px;
  display: grid;
  grid-template-columns: auto auto;
  align-items: baseline;
  gap: 2px 10px;
}

.dispense-board-title span {
  grid-column: 1 / -1;
  color: #111827;
  font-size: 20px;
  font-weight: 800;
}

.dispense-board-title strong {
  color: #0899a5;
  font-size: 28px;
  line-height: 1;
}

.dispense-board-title em {
  color: #64748b;
  font-size: 13px;
  font-style: normal;
}

.dispense-board-tools {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.dispense-switch {
  position: relative;
  width: 260px;
  height: 36px;
  padding: 4px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  border: 1px solid #b9edf0;
  border-radius: 8px;
  background: #f0f9fa;
}

.dispense-switch__thumb {
  position: absolute;
  left: 4px;
  top: 4px;
  width: calc(50% - 4px);
  height: calc(100% - 8px);
  border-radius: 6px;
  background: #0899a5;
  box-shadow: 0 6px 18px rgba(8, 153, 165, 0.2);
  transition: transform 0.2s ease;
}

.dispense-switch__thumb--right {
  transform: translateX(100%);
}

.dispense-switch__tab {
  position: relative;
  z-index: 1;
  border: none;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: transparent;
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.dispense-switch__tab--active {
  color: #fff;
}

.dispense-switch__tab em {
  min-width: 22px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.24);
  font-size: 12px;
  font-style: normal;
}

.dispense-switch__tab:not(.dispense-switch__tab--active) em {
  background: #dff7f8;
  color: #0899a5;
}

.dispense-search {
  width: 260px;
}

.prescription-window {
  min-height: 360px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rx-card {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  background: linear-gradient(180deg, #ffffff 0%, #fbfdfe 100%);
  transition: border-color 0.16s, box-shadow 0.16s;
}

.rx-card:hover,
.rx-card--open {
  border-color: #9de3e7;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.06);
}

.rx-card__main {
  width: 100%;
  min-height: 76px;
  border: none;
  padding: 14px 16px;
  display: grid;
  grid-template-columns: minmax(150px, 1.1fr) minmax(180px, 1.4fr) minmax(160px, 1fr) 100px 108px 62px;
  align-items: center;
  gap: 12px;
  background: #fff;
  color: #1f2937;
  cursor: pointer;
  text-align: left;
}

.rx-card__main:hover {
  background: #f8fcfd;
}

.rx-card__identity,
.rx-card__meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.rx-card__identity strong {
  color: #111827;
  font-size: 17px;
}

.rx-card__identity em,
.rx-card__meta small {
  color: #64748b;
  font-size: 12px;
  font-style: normal;
}

.rx-card__diagnosis,
.rx-card__meta b {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rx-card__amount {
  color: #0899a5;
  font-size: 18px;
  font-weight: 800;
  text-align: right;
}

.rx-card__status {
  display: flex;
  justify-content: center;
}

.rx-card__toggle {
  height: 28px;
  border: 1px solid #b9edf0;
  border-radius: 5px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #0899a5;
  background: #f0f9fa;
  font-weight: 700;
  text-align: center;
}

.rx-card__detail {
  padding: 14px 16px 16px;
  border-top: 1px solid #e5e7eb;
  background:
    linear-gradient(135deg, rgba(8, 153, 165, 0.07) 0%, rgba(255, 255, 255, 0) 36%),
    #fbfdfe;
}

.rx-detail-shell {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 14px;
  align-items: stretch;
}

.rx-detail-summary,
.rx-drug-checklist {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}

.rx-detail-summary {
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rx-detail-summary__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.rx-detail-summary__head span {
  color: #111827;
  font-size: 18px;
  font-weight: 800;
}

.rx-detail-summary__no {
  color: #0899a5;
  font-size: 13px;
  font-weight: 800;
  overflow-wrap: anywhere;
}

.rx-detail-summary p {
  min-height: 48px;
  margin: 0;
  padding: 10px;
  border-radius: 6px;
  background: #f8fafc;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.rx-detail-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.rx-detail-stats div {
  padding: 10px;
  border-radius: 6px;
  background: #f0f9fa;
}

.rx-detail-stats span,
.rx-detail-time span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.rx-detail-stats strong {
  display: block;
  margin-top: 4px;
  color: #111827;
  font-size: 17px;
}

.rx-detail-time {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 7px 8px;
  font-size: 13px;
}

.rx-detail-time strong {
  min-width: 0;
  color: #111827;
  font-weight: 600;
}

.rx-drug-checklist {
  min-width: 0;
  padding: 14px;
}

.rx-drug-checklist__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.rx-drug-checklist__head span {
  color: #111827;
  font-size: 15px;
  font-weight: 800;
}

.rx-drug-checklist__head em {
  color: #94a3b8;
  font-size: 12px;
  font-style: normal;
}

.rx-drug-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rx-drug-row {
  min-height: 46px;
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) 72px 96px;
  align-items: center;
  gap: 10px;
  background: #fff;
}

.rx-drug-row__index {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: #e6f9fa;
  color: #0899a5;
  font-size: 12px;
  font-weight: 800;
}

.rx-drug-row__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #1f2937;
  font-weight: 700;
}

.rx-drug-row__qty {
  color: #475569;
  font-weight: 700;
  text-align: right;
}

.rx-drug-row__price {
  color: #0899a5;
  font-weight: 800;
  text-align: right;
}

.rx-card__actions {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #dbe3ef;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.rx-card__actions span {
  color: #64748b;
  font-size: 13px;
}

.dispense-layout {
  display: none;
}

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
  .return-layout {
    grid-template-columns: 1fr;
  }

  .dispense-board-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .dispense-board-tools {
    width: 100%;
    justify-content: flex-start;
  }

  .rx-card__main {
    grid-template-columns: minmax(150px, 1fr) minmax(180px, 1.2fr) 120px 88px;
  }

  .rx-card__meta {
    display: none;
  }

  .rx-detail-shell {
    grid-template-columns: 1fr;
  }

  .detail-panel {
    position: static;
  }
}

@media (max-width: 900px) {
  .pharmacy-body {
    height: auto;
    min-height: calc(100vh - 56px);
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

  .dispense-switch {
    width: 100%;
  }

  .dispense-board-tools {
    flex-direction: column;
    align-items: stretch;
  }

  .dispense-search {
    width: 100%;
  }

  .rx-card__main {
    grid-template-columns: 1fr auto;
    gap: 8px;
  }

  .rx-card__diagnosis,
  .rx-card__status {
    grid-column: 1 / -1;
  }

  .rx-card__amount {
    text-align: left;
  }

  .rx-drug-checklist__head,
  .rx-card__actions {
    align-items: stretch;
    flex-direction: column;
  }

  .rx-drug-row {
    grid-template-columns: 28px minmax(0, 1fr) auto;
  }

  .rx-drug-row__price {
    grid-column: 2 / -1;
    text-align: left;
  }
}
</style>
