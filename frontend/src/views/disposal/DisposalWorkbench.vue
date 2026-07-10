<template>
  <div class="disposal">
    <header class="nav">
      <div class="nav__brand">
        <span class="nav__logo">+</span>
        <span>处置医生工作台</span>
      </div>
      <div class="nav__right">
        <span>{{ auth.user?.name }}</span>
        <span>{{ today }} {{ dayOfWeek }}</span>
        <button :class="['my-entry', showMySchedule && 'my-entry--active']" type="button" @click="showMySchedule = !showMySchedule">我的</button>
        <el-button size="small" text class="nav__logout" @click="logout">退出</el-button>
      </div>
    </header>

    <div :class="['body', showMySchedule && 'body--personal']">
      <aside v-if="!showMySchedule" class="sidebar">
        <div class="side-head">
          <span>处置队列</span>
          <el-button :loading="refreshing" size="small" text @click="refreshOrders">刷新</el-button>
        </div>

        <el-input v-model="keyword" clearable size="small" placeholder="搜索患者/项目/队列号" />

        <div class="tabs">
          <button :class="['tab', tab === 'active' && 'tab--active']" @click="tab = 'active'">
            待处理 {{ activeCount }}
          </button>
          <button :class="['tab', tab === 'done' && 'tab--active']" @click="tab = 'done'">
            已完成 {{ doneCount }}
          </button>
          <button :class="['tab', tab === 'all' && 'tab--active']" @click="tab = 'all'">
            全部 {{ orders.length }}
          </button>
        </div>

        <div class="queue-list">
          <button
            v-for="order in filteredOrders"
            :key="order.id"
            :class="['queue-card', current?.id === order.id && 'queue-card--active']"
            @click="selectOrder(order)"
          >
            <div class="queue-card__top">
              <span class="queue-card__num">{{ formatQueueNo(order.queueNumber) }}</span>
              <strong>{{ order.patientName }}</strong>
              <el-tag :type="statusTagType(order.status)" size="small" effect="plain">{{ statusLabel(order.status) }}</el-tag>
            </div>
            <div class="queue-card__item">{{ order.itemName }}</div>
            <div class="queue-card__sub">
              <span>{{ order.roomName || '处置室' }}</span>
              <span v-if="order.missedCount">过号 {{ order.missedCount }} 次</span>
            </div>
            <div class="queue-card__actions" @click.stop>
              <el-button v-if="order.status === 'WAITING'" size="small" type="primary" link @click="call(order)">叫号</el-button>
              <el-button v-if="['WAITING','CALLED'].includes(order.status)" size="small" type="success" link @click="start(order)">开始处置</el-button>
              <el-button v-if="['WAITING','CALLED'].includes(order.status)" size="small" link @click="miss(order)">过号</el-button>
            </div>
          </button>
          <el-empty v-if="!filteredOrders.length" description="暂无处置医嘱" :image-size="72" />
        </div>
      </aside>

      <main v-if="showMySchedule" class="main main--schedule">
        <DoctorPersonalSchedule />
      </main>

      <main v-else class="main">
        <el-empty v-if="!current" description="请选择左侧处置患者" :image-size="100" />

        <template v-else>
          <section class="patient-bar">
            <div class="avatar">{{ current.patientName.slice(-1) }}</div>
            <div class="patient-info">
              <div class="patient-title">
                <strong>{{ current.patientName }}</strong>
                <el-tag v-if="current.urgency === 'EMERGENCY'" type="danger" size="small">急诊</el-tag>
                <el-tag :type="statusTagType(current.status)" size="small" effect="plain">{{ statusLabel(current.status) }}</el-tag>
              </div>
              <div class="patient-meta">
                <span>项目：{{ current.itemName }}</span>
                <span>部位：{{ current.bodyPart || '-' }}</span>
                <span>房间：{{ current.roomName || '-' }}</span>
                <span>开单医生：{{ current.orderingDoctorId }}</span>
              </div>
            </div>
            <div class="patient-actions">
              <el-button v-if="current.status === 'WAITING'" type="primary" @click="call(current)">叫号</el-button>
              <el-button v-if="['WAITING','CALLED'].includes(current.status)" type="success" @click="start(current)">开始处置</el-button>
              <el-button v-if="['WAITING','CALLED'].includes(current.status)" @click="miss(current)">过号</el-button>
            </div>
          </section>

          <section class="sheet-panel">
            <div class="sheet-head no-print">
              <div>
                <h1>处置记录单</h1>
              </div>
            </div>

            <article class="record-sheet">
              <header class="record-title">
                <h2>智慧云脑诊疗平台</h2>
                <h3>处置记录单</h3>
              </header>

              <div class="info-grid">
                <label>
                  <em>姓名</em>
                  <span>{{ current.patientName }}</span>
                </label>
                <label>
                  <em>处置队列号</em>
                  <span>{{ formatQueueNo(current.queueNumber) }}</span>
                </label>
                <label>
                  <em>处置项目</em>
                  <span>{{ current.itemName }}</span>
                </label>
                <label>
                  <em>处置房间</em>
                  <span>{{ current.roomName || '-' }}</span>
                </label>
                <label>
                  <em>执行人</em>
                  <span>{{ auth.user?.name || current.executingStaffId || '-' }}</span>
                </label>
                <label>
                  <em>确认时间</em>
                  <span>{{ confirmedAt || '-' }}</span>
                </label>
                <label>
                  <em>开始时间</em>
                  <span>{{ formatDateTime(current.startedAt) }}</span>
                </label>
                <label>
                  <em>完成时间</em>
                  <span>{{ formatDateTime(current.completedAt) }}</span>
                </label>
              </div>

              <div class="sheet-section">
                <strong>患者情况</strong>
                <p>{{ current.purpose || '未填写' }}</p>
              </div>

              <label class="sheet-section sheet-section--editable sheet-section--record">
                <strong>处置记录</strong>
                <textarea
                  v-model="form.record"
                  :disabled="current.status === 'COMPLETED'"
                />
              </label>

              <footer class="record-footer">
                <span>执行签名：{{ auth.user?.name || '-' }}</span>
                <span>日期：{{ confirmedAt || today }}</span>
              </footer>
            </article>

            <div class="sheet-actions no-print">
              <el-button :disabled="!published" @click="printRecord">打印记录单</el-button>
              <el-button
                type="primary"
                :disabled="current.status !== 'IN_PROGRESS'"
                :loading="submitting"
                @click="finishDisposal"
              >
                完成处置并确认记录单
              </el-button>
            </div>
          </section>
        </template>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import DoctorPersonalSchedule from '../../components/DoctorPersonalSchedule.vue';
import { useAuthStore } from '../../store/auth';
import { useQueuePolling } from '../../composables/useQueuePolling';
import {
  callMedicalOrder,
  completeMedicalOrder,
  confirmReport,
  createReportDraft,
  getWorkspace,
  missMedicalOrder,
  startMedicalOrder,
  type MedicalOrder
} from '../../api/medical-order';

const auth = useAuthStore();
const router = useRouter();

const weekDays = ['日', '一', '二', '三', '四', '五', '六'];
const today = new Date().toLocaleDateString('zh-CN');
const dayOfWeek = `星期${weekDays[new Date().getDay()]}`;

const orders = ref<MedicalOrder[]>([]);
const current = ref<MedicalOrder>();
const keyword = ref('');
const tab = ref<'active' | 'done' | 'all'>('active');
const refreshing = ref(false);
const submitting = ref(false);
const published = ref(false);
const confirmedAt = ref('');
const showMySchedule = ref(false);

// 用户正在填写处置记录或未发布时跳过队列轮询，避免覆盖正在输入的内容
const isEditing = computed(() => !!current.value && !published.value);

// 定时轮询队列：缴费后自动刷新处置列表
useQueuePolling(isEditing, loadOrders);

const form = reactive({
  record: ''
});

const activeStatuses = ['WAITING', 'CALLED', 'IN_PROGRESS'];
const activeCount = computed(() => orders.value.filter(o => activeStatuses.includes(o.status)).length);
const doneCount = computed(() => orders.value.filter(o => o.status === 'COMPLETED').length);

const filteredOrders = computed(() => {
  let list = orders.value;
  if (tab.value === 'active') list = list.filter(o => activeStatuses.includes(o.status));
  if (tab.value === 'done') list = list.filter(o => o.status === 'COMPLETED');
  const q = keyword.value.trim().toLowerCase();
  if (!q) return list;
  return list.filter(o => `${o.queueNumber ?? ''}${formatQueueNo(o.queueNumber)}${o.patientName}${o.itemName}${o.roomName ?? ''}`.toLowerCase().includes(q));
});

function formatQueueNo(value?: number) {
  if (value === undefined || value === null) return '-';
  return `CZ${String(value).padStart(3, '0')}`;
}

function statusLabel(status: string) {
  return ({
    WAITING: '待叫号',
    CALLED: '已叫号',
    IN_PROGRESS: '处置中',
    COMPLETED: '已完成',
    PENDING_PAYMENT: '待缴费',
    WAITING_TRIAGE: '待分诊'
  } as Record<string, string>)[status] ?? status;
}

function statusTagType(status: string): '' | 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  if (status === 'WAITING') return 'warning';
  if (status === 'CALLED' || status === 'IN_PROGRESS') return 'primary';
  if (status === 'COMPLETED') return 'success';
  return 'info';
}

function formatDateTime(value?: string) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('zh-CN', { hour12: false });
}

function formatDate(value?: string) {
  if (!value) return today;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return today;
  return date.toLocaleDateString('zh-CN');
}

function errorMessage(error: unknown, fallback: string) {
  const data = (error as { response?: { data?: unknown } })?.response?.data;
  if (typeof data === 'string' && data.trim()) return data;
  if (data && typeof data === 'object') {
    const record = data as Record<string, unknown>;
    for (const key of ['message', 'error', 'detail']) {
      if (typeof record[key] === 'string' && String(record[key]).trim()) return String(record[key]);
    }
  }
  return (error as Error)?.message || fallback;
}

function buildSummary() {
  return `处置记录：${form.record.trim()}`;
}

async function loadOrders() {
  const workspace = await getWorkspace();
  orders.value = workspace.orders;
  if (current.value) {
    const latest = orders.value.find(item => item.id === current.value?.id);
    if (latest) current.value = latest;
  }
}

async function refreshOrders() {
  refreshing.value = true;
  try {
    await loadOrders();
    // 刷新后保持当前选中状态和已填写内容，不重置
    if (current.value) {
      await selectOrder(current.value, true);
    }
  } finally {
    refreshing.value = false;
  }
}

async function selectOrder(order: MedicalOrder, isReselect = false) {
  // ── Synchronous reset (Vue batches into one render) ──
  current.value = order;
  // 仅第一次选择时清空表单，刷新时保留已填内容
  if (!isReselect) {
    form.record = '';
    published.value = false;
    confirmedAt.value = '';
  }

  // ── Collect async data: workspace API gives us queue + report in one call ──
  const workspace = await getWorkspace(order.id);
  const report = workspace.detail?.report ?? null;
  published.value = report?.status === 'CONFIRMED';
  confirmedAt.value = report?.confirmedAt ? formatDate(report.confirmedAt) : '';
  if (!isReselect || !form.record.trim()) {
    form.record = report?.findings || report?.conclusion || order.resultSummary || '';
  }
}

async function call(order: MedicalOrder) {
  try {
    await callMedicalOrder(order.id);
    ElMessage.success('已叫号');
    await loadOrders(); // loadOrders 自动同步 current.value
  } catch (error) {
    ElMessage.error(errorMessage(error, '叫号失败'));
  }
}

async function start(order: MedicalOrder) {
  try {
    await startMedicalOrder(order.id);
    ElMessage.success('已开始处置');
    await loadOrders(); // loadOrders 自动同步 current.value
  } catch (error) {
    ElMessage.error(errorMessage(error, '开始处置失败'));
  }
}

async function miss(order: MedicalOrder) {
  try {
    await missMedicalOrder(order.id);
    ElMessage.success('已标记过号');
    await loadOrders(); // loadOrders 自动同步 current.value
  } catch (error) {
    ElMessage.error(errorMessage(error, '过号失败'));
  }
}

async function finishDisposal() {
  if (!current.value) return;
  if (!form.record.trim()) {
    ElMessage.warning('请填写处置记录');
    return;
  }

  submitting.value = true;
  try {
    const completed = await completeMedicalOrder(current.value.id, {
      summary: buildSummary(),
      createdByType: 'HUMAN'
    });
    await createReportDraft(current.value.id, {
      findings: form.record.trim(),
      conclusion: form.record.trim(),
      advice: ''
    });
    const report = await confirmReport(current.value.id, {
      findings: form.record.trim(),
      conclusion: form.record.trim(),
      advice: ''
    });
    published.value = true;
    confirmedAt.value = report.confirmedAt ? formatDate(report.confirmedAt) : today;
    ElMessage.success('处置已完成，记录单已确认');
    await loadOrders(); // loadOrders 自动同步 current.value
  } catch (error) {
    ElMessage.error(errorMessage(error, '完成处置失败'));
  } finally {
    submitting.value = false;
  }
}

function printRecord() {
  nextTick(() => window.print());
}

function logout() {
  auth.signOut();
  router.push('/login');
}

onMounted(loadOrders);
</script>

<style scoped>
.disposal {
  min-height: 100vh;
  background: #edf4fb;
  color: #0f2742;
}

.nav {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: linear-gradient(135deg, #0cbdcc 0%, #0899a5 100%);
  color: #fff;
  box-shadow: 0 2px 10px rgba(15, 39, 66, 0.16);
}

.nav__brand,
.nav__right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.nav__brand {
  font-size: 18px;
  font-weight: 700;
}

.nav__logo {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.16);
}

.nav__right {
  font-size: 13px;
  font-family: inherit;
  line-height: 1;
}

.nav__right > span,
.nav__right :deep(.el-button),
.my-entry {
  height: 32px;
  display: inline-flex;
  align-items: center;
  font: inherit;
  line-height: 1;
}

.nav__logout {
  color: rgba(255, 255, 255, 0.9);
}

.body {
  height: calc(100vh - 56px);
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  min-height: 0;
}

.body--personal {
  grid-template-columns: minmax(0, 1fr);
}

.sidebar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  border-right: 1px solid #d6e4ef;
  background: #f8fbfe;
  min-height: 0;
}

.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 700;
}

.tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px;
  padding: 3px;
  border-radius: 6px;
  background: #e4eef7;
}

.tab {
  height: 30px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #64748b;
  font-size: 12px;
  cursor: pointer;
}

.tab--active {
  background: #fff;
  color: #0b6f95;
  font-weight: 700;
  box-shadow: 0 1px 3px rgba(15, 39, 66, 0.08);
}

.queue-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: auto;
  min-height: 0;
}

.queue-card {
  width: 100%;
  padding: 10px;
  border: 1px solid #dbe7f0;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.queue-card:hover,
.queue-card--active {
  border-color: #0b86b5;
  box-shadow: 0 5px 16px rgba(11, 134, 181, 0.14);
}

.queue-card__top,
.queue-card__sub,
.queue-card__actions,
.patient-title,
.patient-meta,
.patient-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.queue-card__top strong {
  flex: 1;
}

.queue-card__num {
  width: 34px;
  height: 26px;
  display: grid;
  place-items: center;
  border-radius: 5px;
  background: #e0f2fe;
  color: #0369a1;
  font-weight: 700;
  flex-shrink: 0;
}

.queue-card__item {
  margin-top: 8px;
  color: #334155;
  font-size: 13px;
}

.queue-card__sub {
  justify-content: space-between;
  margin-top: 7px;
  color: #8aa0b5;
  font-size: 12px;
}

.queue-card__actions {
  justify-content: flex-end;
  margin-top: 6px;
}

.main {
  padding: 16px;
  overflow: auto;
  min-width: 0;
}

.main--schedule {
  overflow: hidden;
}

.patient-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border: 1px solid #dbe7f0;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 39, 66, 0.06);
}

.avatar {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #0b86b5;
  color: #fff;
  font-size: 20px;
  font-weight: 700;
}

.patient-info {
  flex: 1;
  min-width: 0;
}

.patient-title strong {
  font-size: 18px;
}

.patient-meta {
  flex-wrap: wrap;
  margin-top: 8px;
  color: #64748b;
  font-size: 13px;
}

.sheet-panel {
  margin-top: 16px;
  padding: 16px;
  border: 1px solid #dbe7f0;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 39, 66, 0.06);
}

.sheet-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.sheet-head h1 {
  margin: 0;
  font-size: 20px;
}

.sheet-head p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.record-sheet {
  max-width: 1160px;
  min-height: 620px;
  margin: 0 auto;
  padding: 30px 40px;
  border: 1px solid #cbd7e2;
  background: #fff;
  color: #0f172a;
  font-family: "SimSun", "Microsoft YaHei", sans-serif;
}

.record-title {
  text-align: center;
  padding-bottom: 16px;
  border-bottom: 3px double #334155;
}

.record-title h2,
.record-title h3 {
  margin: 0;
}

.record-title h2 {
  font-size: 22px;
  letter-spacing: 7px;
}

.record-title h3 {
  margin-top: 10px;
  font-size: 18px;
  letter-spacing: 8px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 28px;
  margin: 22px 0;
}

.info-grid label {
  display: flex;
  gap: 8px;
  align-items: baseline;
  min-width: 0;
  border-bottom: 1px solid #cbd5e1;
  padding: 4px 0;
}

.info-grid em {
  color: #475569;
  font-style: normal;
  white-space: nowrap;
}

.info-grid span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.sheet-section {
  display: block;
  margin-top: 18px;
}

.sheet-section strong {
  display: block;
  margin-bottom: 8px;
  font-size: 16px;
}

.sheet-section p,
.sheet-section textarea {
  width: 100%;
  min-height: 72px;
  box-sizing: border-box;
  margin: 0;
  padding: 10px 2px;
  border: 0;
  border-bottom: 1px solid #cbd5e1;
  outline: none;
  background: transparent;
  color: #0f172a;
  font-family: inherit;
  font-size: 15px;
  line-height: 1.9;
  white-space: pre-wrap;
}

.sheet-section textarea {
  resize: vertical;
}

.sheet-section--record textarea {
  min-height: 190px;
}

.sheet-section textarea:focus {
  border-bottom-color: #0b86b5;
}

.sheet-section textarea:disabled {
  color: #0f172a;
  cursor: default;
}

.record-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 30px;
  font-size: 15px;
}

.sheet-actions {
  max-width: 1160px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin: 14px auto 0;
}

@media (max-width: 1120px) {
  .body {
    grid-template-columns: 280px minmax(0, 1fr);
  }

  .record-sheet {
    padding: 24px;
  }
}

@media print {
  .no-print,
  .nav,
  .sidebar,
  .patient-bar {
    display: none !important;
  }

  .disposal,
  .body,
  .main,
  .sheet-panel {
    display: block;
    height: auto;
    min-height: 0;
    padding: 0;
    border: 0;
    background: #fff;
    box-shadow: none;
    overflow: visible;
  }

  .record-sheet {
    max-width: none;
    min-height: 0;
    margin: 0;
    border: 0;
  }

  .sheet-section textarea {
    resize: none;
  }
}
.my-entry {
  height: 32px;
  padding: 0 14px;
  border: 1px solid rgba(255, 255, 255, 0.38);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease, border-color 0.2s ease;
}
.my-entry:hover,
.my-entry--active {
  border-color: #fff;
  background: #fff;
  color: #0899a5;
}
</style>
