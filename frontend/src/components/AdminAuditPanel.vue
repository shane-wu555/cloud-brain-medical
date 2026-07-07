<template>
  <div class="audit-page">
    <div class="page-head">
      <div>
        <h1>审计日志</h1>
      </div>
      <el-button type="primary" :loading="loading" @click="loadLogs">刷新</el-button>
    </div>

    <div class="query-panel">
      <el-input
        v-model.trim="filters.keyword"
        clearable
        class="keyword-input"
        placeholder="关键词搜索"
        @keyup.enter="loadLogs"
      />
      <el-button type="primary" :loading="loading" @click="loadLogs">搜索</el-button>
      <el-button @click="resetFilters">重置</el-button>
      <el-select v-model="filters.service" clearable filterable placeholder="服务">
        <el-option v-for="item in serviceOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="filters.action" clearable filterable placeholder="操作类型">
        <el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="filters.resourceType" clearable filterable placeholder="资源类型">
        <el-option v-for="item in resourceOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-date-picker
        v-model="timeRange"
        class="time-range"
        type="datetimerange"
        value-format="YYYY-MM-DDTHH:mm:ss"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
      />
    </div>

    <section class="audit-card">
      <div class="audit-toolbar">
        <span>当前结果 {{ logs.length }} 条</span>
        <span v-if="lastLoadedAt">更新时间 {{ lastLoadedAt }}</span>
      </div>

      <el-table :data="logs" v-loading="loading" empty-text="暂无审计记录" stripe table-layout="auto">
        <el-table-column label="时间" width="172">
          <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
        </el-table-column>

        <el-table-column label="操作记录" min-width="420" class-name="summary-column">
          <template #default="{ row }">
            <div class="summary-cell">
              <div class="summary-top">
                <strong>{{ operationSummary(row) }}</strong>
                <el-tag :type="actionTagType(row.action)" effect="plain" size="small">
                  {{ actionKindLabel(row.action) }}
                </el-tag>
              </div>
              <span class="summary-meta">{{ detailSummary(row) }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作人" width="190">
          <template #default="{ row }">
            <div class="stack-cell">
              <strong>{{ actorLabel(row) }}</strong>
              <span>{{ actorSubLabel(row) }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="来源服务" width="140">
          <template #default="{ row }">
            <span :class="['service-badge', serviceBadgeClass(row.service)]">{{ serviceLabel(row.service) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="查看" width="76" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="detailVisible" title="审计详情" width="860px">
      <template v-if="selectedLog">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="操作说明" :span="2">{{ operationSummary(selectedLog) }}</el-descriptions-item>
          <el-descriptions-item label="发生时间">{{ formatDateTime(selectedLog.occurredAt) }}</el-descriptions-item>
          <el-descriptions-item label="来源服务">
            <span :class="['service-badge', serviceBadgeClass(selectedLog.service)]">{{ serviceLabel(selectedLog.service) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="操作类型">{{ actionLabel(selectedLog.action) }}</el-descriptions-item>
          <el-descriptions-item label="资源类型">{{ resourceLabel(selectedLog.resourceType) }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ actorLabel(selectedLog) }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ roleLabel(selectedLog.role) }}</el-descriptions-item>
          <el-descriptions-item label="资源编号">{{ selectedLog.resourceId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="请求 IP">{{ selectedLog.requestIp || '-' }}</el-descriptions-item>
          <el-descriptions-item label="患者编号">{{ selectedLog.patientId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="业务编号">{{ selectedLog.businessId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="事件编号" :span="2">{{ selectedLog.eventId }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-block">
          <h3>补充信息</h3>
          <el-table :data="detailRows(selectedLog)" size="small" border empty-text="无补充信息">
            <el-table-column prop="label" label="项目" width="180" />
            <el-table-column prop="value" label="内容" />
          </el-table>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { getAuditLogs, type AuditLogEntry } from '../api/audit';

const props = defineProps<{
  active: boolean;
}>();

const emit = defineEmits<{
  loaded: [count: number];
}>();

const loading = ref(false);
const loadedOnce = ref(false);
const logs = ref<AuditLogEntry[]>([]);
const selectedLog = ref<AuditLogEntry | null>(null);
const detailVisible = ref(false);
const lastLoadedAt = ref('');
const timeRange = ref<[string, string] | []>([]);

const filters = reactive({
  service: '',
  action: '',
  resourceType: '',
  keyword: ''
});

const serviceLabelMap: Record<string, string> = {
  'auth-service': '认证服务',
  'medical-record-service': '病历服务',
  'medical-order-service': '医技医嘱服务',
  'pharmacy-service': '药房服务',
  'cashier-service': '收费服务',
  'audit-service': '审计服务'
};

const actionLabelMap: Record<string, string> = {
  REGISTER: '账号注册',
  LOGIN: '账号登录',
  SMS_LOGIN: '短信登录',
  SEND_SMS_CODE: '发送验证码',
  RESET_PASSWORD: '重置密码',
  MEDICAL_RECORD_LIST_VIEW: '查看病历列表',
  MEDICAL_RECORD_DETAIL_VIEW: '查看病历详情',
  MEDICAL_RECORD_HISTORY_VIEW: '查看历史病历',
  MEDICAL_ORDER_LIST_VIEW: '查看医技医嘱列表',
  MEDICAL_REPORT_LIST_VIEW: '查看检查/检验报告列表',
  PRESCRIPTION_LIST_VIEW: '查看处方列表',
  PRESCRIPTION_DETAIL_VIEW: '查看处方详情',
  DRUG_RETURN_LIST_VIEW: '查看退药单列表',
  MEDICAL_RECORD_VIEW: '查看病历',
  MEDICAL_RECORD_UPDATE: '修改病历',
  MEDICAL_RECORD_ARCHIVE: '归档病历',
  AI_RESULT_CONFIRMED: '确认 AI 结果',
  MEDICAL_REPORT_CONFIRM: '确认检查报告',
  MEDICAL_REPORT_REJECT: '驳回检查报告',
  MEDICAL_REPORT_VIEW: '查看检查报告',
  MEDICAL_ATTACHMENT_DOWNLOAD: '下载检查附件',
  PRESCRIPTION_CREATE: '创建处方',
  PRESCRIPTION_VIEW: '查看处方',
  PRESCRIPTION_DISPENSE: '处方发药',
  PRESCRIPTION_RETURN: '处方退药',
  DRUG_RETURN_CREATE: '创建退药单',
  DRUG_RETURN_REFUND_COMPLETE: '完成退药退款',
  DRUG_STOCK_IN: '药品入库',
  PAYMENT_ORDER_CREATE: '创建支付单',
  PAYMENT_CONFIRMED: '确认支付',
  PAYMENT_FAILED: '支付失败',
  PAYMENT_REFUND: '退款'
};

const actionSentenceMap: Record<string, string> = {
  REGISTER: '注册了账号',
  LOGIN: '登录了系统',
  SMS_LOGIN: '通过短信验证码登录了系统',
  SEND_SMS_CODE: '申请发送短信验证码',
  RESET_PASSWORD: '重置了登录密码',
  MEDICAL_RECORD_LIST_VIEW: '查看了病历列表',
  MEDICAL_RECORD_DETAIL_VIEW: '查看了病历详情',
  MEDICAL_RECORD_HISTORY_VIEW: '查看了历史病历',
  MEDICAL_ORDER_LIST_VIEW: '查看了医技医嘱列表',
  MEDICAL_REPORT_LIST_VIEW: '查看了检查/检验报告列表',
  PRESCRIPTION_LIST_VIEW: '查看了处方列表',
  PRESCRIPTION_DETAIL_VIEW: '查看了处方详情',
  DRUG_RETURN_LIST_VIEW: '查看了退药单列表',
  MEDICAL_RECORD_VIEW: '查看了病历',
  MEDICAL_RECORD_UPDATE: '修改了病历',
  MEDICAL_RECORD_ARCHIVE: '归档了病历',
  AI_RESULT_CONFIRMED: '确认了 AI 结果',
  MEDICAL_REPORT_CONFIRM: '确认了检查报告',
  MEDICAL_REPORT_REJECT: '驳回了检查报告',
  MEDICAL_REPORT_VIEW: '查看了检查报告',
  MEDICAL_ATTACHMENT_DOWNLOAD: '下载了检查附件',
  PRESCRIPTION_CREATE: '创建了处方',
  PRESCRIPTION_VIEW: '查看了处方',
  PRESCRIPTION_DISPENSE: '完成了处方发药',
  PRESCRIPTION_RETURN: '完成了处方退药',
  DRUG_RETURN_CREATE: '创建了退药单',
  DRUG_RETURN_REFUND_COMPLETE: '完成了退药退款',
  DRUG_STOCK_IN: '完成了药品入库',
  PAYMENT_ORDER_CREATE: '创建了支付单',
  PAYMENT_CONFIRMED: '确认了支付',
  PAYMENT_FAILED: '记录了支付失败',
  PAYMENT_REFUND: '完成了退款'
};

const resourceLabelMap: Record<string, string> = {
  AUTH_ACCOUNT: '认证账号',
  SMS_CODE: '短信验证码',
  MEDICAL_RECORD: '病历',
  MEDICAL_RECORD_DIAGNOSIS: '病历诊断',
  MEDICAL_REPORT: '检查报告',
  MEDICAL_ATTACHMENT: '检查附件',
  PRESCRIPTION: '处方',
  DRUG_RETURN: '退药单',
  DRUG: '药品',
  PAYMENT_ORDER: '支付单',
  REFUND: '退款单'
};

const roleLabelMap: Record<string, string> = {
  ADMIN: '管理员',
  OUTPATIENT_DOCTOR: '门诊医生',
  CHECK_DOCTOR: '检查医生',
  LAB_DOCTOR: '检验医生',
  DISPOSAL_DOCTOR: '处置医生',
  PHARMACY_STAFF: '药房人员',
  CASHIER: '收费员',
  PATIENT: '患者',
  SYSTEM: '系统'
};

const valueLabelMap: Record<string, string> = {
  true: '成功',
  false: '失败',
  SUCCESS: '成功',
  FAILED: '失败',
  INVALID_CREDENTIALS: '账号或密码错误',
  ACCOUNT_DISABLED: '账号已停用',
  DRAFT: '草稿',
  ACTIVE: '有效',
  ARCHIVED: '已归档',
  CONFIRMED: '已确认',
  PENDING_PAYMENT: '待支付',
  PAID: '已支付',
  WAITING_DISPENSE: '待发药',
  DISPENSED: '已发药',
  RETURNED: '已退药',
  RETURN_PENDING_REFUND: '退药待退款',
  RETURN_REFUNDED: '已完成退药退款',
  CANCELLED: '已取消',
  PENDING: '待处理',
  COMPLETED: '已完成',
  REJECTED: '已驳回',
  WAITING_TRIAGE: '待分诊',
  WAITING: '等待中',
  CALLED: '已叫号',
  IN_PROGRESS: '进行中',
  DISCARDED: '已废弃',
  ADOPTED: '直接采用',
  MODIFIED: '修改后采用',
  LIST: '列表查询',
  DETAIL: '明细查看',
  APPOINTMENT: '按预约查看',
  HISTORY: '历史病历查看',
  WECHAT: '微信支付',
  WECHAT_TEST: '微信测试支付',
  ALIPAY: '支付宝',
  CASH: '现金',
  PRESCRIPTION: '处方',
  MEDICAL_ORDER: '医技医嘱',
  CHECK: '检查',
  LAB: '检验',
  DISPOSAL: '处置',
  APPOINTMENT_FEE: '挂号费',
  CT_ANALYSIS: 'CT 分析',
  OUTPATIENT_PAYMENT: '门诊缴费项目',
  PAYMENT_RECORD: '缴费退费记录',
  DISPENSE_ARRANGEMENT: '待取药安排',
  DISPENSE_RECORD: '取药退药记录',
  DISPOSAL_ARRANGEMENT: '待处置安排',
  DISPOSAL_RECORD: '处置记录',
  ALL: '全部',
  REGISTER: '注册',
  LOGIN: '登录',
  RESET_PASSWORD: '重置密码'
};

const detailKeyMap: Record<string, string> = {
  success: '处理结果',
  failureReason: '失败原因',
  account: '登录账号',
  username: '登录账号',
  userAgent: '浏览器信息',
  purpose: '验证码用途',
  auditSummary: '备注',
  view: '查看场景',
  typeFilter: '类型筛选',
  statusFilter: '状态筛选',
  resultCount: '结果数量',
  relatedPrescriptionCount: '关联处方数量',
  relatedDisposalCount: '关联处置数量',
  status: '业务状态',
  accessScope: '访问范围',
  reason: '原因',
  version: '病历版本',
  adoptionStatus: 'AI 结果采用方式',
  aiAssistanceId: 'AI 辅助记录编号',
  aiRecordId: 'AI 病历编号',
  aiReportId: 'AI 报告编号',
  prescriptionId: '处方编号',
  refundOrderId: '退款单编号',
  returnId: '退药单编号',
  paymentOrderId: '支付单编号',
  businessType: '业务类型',
  paymentMethod: '支付方式',
  amount: '金额',
  totalAmount: '总金额',
  quantity: '数量',
  drugName: '药品名称',
  tradeNo: '交易流水号',
  fileName: '文件名'
};

const serviceOptions = Object.entries(serviceLabelMap).map(([value, label]) => ({ value, label }));
const actionOptions = Object.entries(actionLabelMap).map(([value, label]) => ({ value, label }));
const resourceOptions = Object.entries(resourceLabelMap).map(([value, label]) => ({ value, label }));

watch(
  () => props.active,
  (active) => {
    if (active && !loadedOnce.value) {
      void loadLogs();
    }
  },
  { immediate: true }
);

async function loadLogs() {
  loading.value = true;
  try {
    const [from, to] = timeRange.value.length === 2 ? timeRange.value : ['', ''];
    logs.value = await getAuditLogs({
      service: filters.service || undefined,
      action: filters.action || undefined,
      resourceType: filters.resourceType || undefined,
      keyword: filters.keyword || undefined,
      from: from || undefined,
      to: to || undefined
    });
    loadedOnce.value = true;
    lastLoadedAt.value = formatDateTime(new Date().toISOString());
    emit('loaded', logs.value.length);
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    loading.value = false;
  }
}

function resetFilters() {
  filters.service = '';
  filters.action = '';
  filters.resourceType = '';
  filters.keyword = '';
  timeRange.value = [];
  void loadLogs();
}

function openDetail(log: AuditLogEntry) {
  selectedLog.value = log;
  detailVisible.value = true;
}

function operationSummary(log: AuditLogEntry): string {
  const subject = isAuthLog(log) ? authSubject(log) : actorLabel(log);
  const auditSummary = detailValue(log, 'auditSummary');
  if (auditSummary) {
    return `${subject}${auditSummary}`;
  }
  if (log.action === 'SEND_SMS_CODE') {
    return `${subject}${sendCodeSentence(log)}`;
  }
  if (log.action === 'LOGIN' && detailValue(log, 'success') === '失败') {
    return `${subject}登录失败`;
  }
  const sentence = actionSentenceMap[log.action] ?? `执行了${actionLabel(log.action)}`;
  return `${subject}${sentence}`;
}

function detailSummary(log: AuditLogEntry): string {
  const parts: string[] = [];
  const account = accountValue(log);

  if (isAuthLog(log)) {
    if (account) {
      parts.push(`登录账号：${account}`);
    }
  } else if (log.resourceId) {
    parts.push(`${resourceLabel(log.resourceType)}编号：${shortId(log.resourceId, 18)}`);
  }

  for (const key of ['status', 'typeFilter', 'statusFilter', 'accessScope', 'resultCount', 'relatedPrescriptionCount', 'relatedDisposalCount', 'success', 'failureReason', 'adoptionStatus', 'amount', 'purpose']) {
    const value = detailValue(log, key);
    if (value) {
      parts.push(`${detailKeyLabel(key)}：${value}`);
    }
  }

  if (!parts.length) {
    return `${serviceLabel(log.service)} / ${resourceLabel(log.resourceType)}`;
  }
  return parts.join('；');
}

function detailRows(log: AuditLogEntry): Array<{ label: string; value: string }> {
  return Object.entries((log.details ?? {}) as Record<string, unknown>).map(([key, value]) => ({
    label: detailKeyLabel(key),
    value: formatDetailValue(key, value)
  }));
}

function actorLabel(log: AuditLogEntry): string {
  const name = normalizedActorName(log);
  if (name) {
    return name;
  }
  const account = accountValue(log);
  if (account) {
    return `账号 ${account}`;
  }
  if (log.userId) {
    return log.userId;
  }
  return '未知操作人';
}

function actorSubLabel(log: AuditLogEntry): string {
  const parts: string[] = [];
  if (log.role) {
    parts.push(roleLabel(log.role));
  }
  const account = accountValue(log);
  if (account && normalizedActorName(log)) {
    parts.push(`登录账号：${account}`);
  } else if (!account && log.userId && normalizedActorName(log) && log.userId !== normalizedActorName(log)) {
    parts.push(`用户 ID：${log.userId}`);
  }
  return parts.join(' · ') || '-';
}

function authSubject(log: AuditLogEntry): string {
  const name = normalizedActorName(log);
  if (name) {
    return name;
  }
  const account = accountValue(log);
  if (account) {
    return `账号 ${account}`;
  }
  return '未知账号';
}

function normalizedActorName(log: AuditLogEntry): string {
  const candidate = (log.actorName ?? '').trim();
  if (!candidate) {
    return '';
  }
  const account = accountValue(log);
  if (candidate === log.userId || candidate === account) {
    return '';
  }
  return candidate;
}

function accountValue(log: AuditLogEntry): string {
  const details = (log.details ?? {}) as Record<string, unknown>;
  return asString(details['account']) || asString(details['username']) || '';
}

function sendCodeSentence(log: AuditLogEntry): string {
  const purpose = asString(((log.details ?? {}) as Record<string, unknown>)['purpose']);
  if (!purpose) {
    return '申请发送短信验证码';
  }
  return `申请发送${purposeLabel(purpose)}验证码`;
}

function purposeLabel(purpose: string): string {
  return valueLabelMap[purpose] ?? purpose;
}

function detailKeyLabel(key: string): string {
  return detailKeyMap[key] ?? key;
}

function detailValue(log: AuditLogEntry, key: string): string {
  const details = (log.details ?? {}) as Record<string, unknown>;
  const rawValue = details[key];
  if (rawValue === null || rawValue === undefined || rawValue === '') {
    return '';
  }
  return formatDetailValue(key, rawValue);
}

function formatDetailValue(key: string, value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  if (typeof value === 'boolean') {
    return value ? '成功' : '失败';
  }
  if (typeof value === 'number') {
    return String(value);
  }
  if (Array.isArray(value)) {
    return value.map((item) => formatDetailValue(key, item)).join('、');
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  const text = String(value);
  return valueLabelMap[text] ?? text;
}

function serviceLabel(service: string): string {
  return serviceLabelMap[service] ?? service;
}

function serviceBadgeClass(service: string): string {
  return {
    'auth-service': 'service-badge--auth',
    'medical-record-service': 'service-badge--record',
    'medical-order-service': 'service-badge--order',
    'pharmacy-service': 'service-badge--pharmacy',
    'cashier-service': 'service-badge--cashier',
    'audit-service': 'service-badge--audit'
  }[service] ?? 'service-badge--default';
}

function actionLabel(action: string): string {
  return actionLabelMap[action] ?? action;
}

function resourceLabel(resourceType: string): string {
  return resourceLabelMap[resourceType] ?? resourceType;
}

function roleLabel(role?: string): string {
  if (!role) {
    return '-';
  }
  return roleLabelMap[role] ?? role;
}

function isAuthLog(log: AuditLogEntry): boolean {
  return log.service === 'auth-service';
}

function actionKindLabel(action: string): string {
  if (action.includes('VIEW') || action.includes('DOWNLOAD')) return '查看';
  if (action.includes('CREATE') || action === 'REGISTER') return '新增';
  if (action.includes('UPDATE') || action.includes('CONFIRM') || action.includes('DISPENSE') || action.includes('RETURN') || action.includes('REFUND') || action.includes('ARCHIVE') || action === 'RESET_PASSWORD') return '变更';
  if (action.includes('FAILED') || action.includes('REJECT')) return '异常';
  if (action.includes('LOGIN') || action.includes('SMS')) return '账号';
  return '操作';
}

function actionTagType(action: string): 'success' | 'warning' | 'info' | 'danger' {
  if (action.includes('FAILED') || action.includes('REJECT')) return 'danger';
  if (action.includes('VIEW') || action.includes('DOWNLOAD')) return 'info';
  if (action.includes('LOGIN') || action.includes('SMS')) return 'warning';
  return 'success';
}

function shortId(value: string, maxLength = 12): string {
  if (value.length <= maxLength) {
    return value;
  }
  return `${value.slice(0, maxLength)}...`;
}

function asString(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function formatDateTime(value: string): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value.replace('T', ' ').replace('Z', '').slice(0, 19);
  }
  return date.toLocaleString('zh-CN', { hour12: false });
}

function errorMessage(error: unknown): string {
  const responseMessage = (error as { response?: { data?: { message?: string } } })?.response?.data?.message;
  if (responseMessage) return responseMessage;
  return error instanceof Error ? error.message : '加载审计日志失败';
}
</script>

<style scoped>
.audit-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.page-head h1 {
  margin: 0 0 6px;
  color: #111827;
  font-size: 22px;
  font-weight: 700;
}

.page-head p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.query-panel {
  padding: 14px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.query-panel :deep(.el-select),
.query-panel :deep(.el-input) {
  width: 170px;
}

.keyword-input {
  width: 280px;
}

.time-range {
  width: 360px;
}

.audit-card {
  min-width: 0;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.audit-card :deep(.summary-column .cell) {
  white-space: normal;
  overflow: visible;
  text-overflow: clip;
}

.audit-toolbar {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #64748b;
  font-size: 12px;
}

.summary-cell,
.stack-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.summary-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.summary-top strong,
.stack-cell strong {
  color: #111827;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
}

.summary-top strong {
  flex: 1;
  min-width: 0;
  white-space: normal;
  word-break: break-word;
}

.summary-meta,
.stack-cell span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
  word-break: break-all;
}

.service-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 84px;
  padding: 4px 10px;
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
  white-space: nowrap;
}

.service-badge--auth {
  color: #1d4ed8;
  background: #eff6ff;
  border-color: #bfdbfe;
}

.service-badge--record {
  color: #0f766e;
  background: #ecfeff;
  border-color: #99f6e4;
}

.service-badge--order {
  color: #b45309;
  background: #fffbeb;
  border-color: #fde68a;
}

.service-badge--pharmacy {
  color: #166534;
  background: #f0fdf4;
  border-color: #bbf7d0;
}

.service-badge--cashier {
  color: #7c3aed;
  background: #f5f3ff;
  border-color: #ddd6fe;
}

.service-badge--audit {
  color: #b91c1c;
  background: #fef2f2;
  border-color: #fecaca;
}

.service-badge--default {
  color: #475569;
  background: #f8fafc;
  border-color: #cbd5e1;
}

.detail-block {
  margin-top: 16px;
}

.detail-block h3 {
  margin: 0 0 10px;
  color: #111827;
  font-size: 14px;
  font-weight: 700;
}

@media (max-width: 900px) {
  .page-head {
    flex-direction: column;
  }

  .query-panel :deep(.el-select),
  .query-panel :deep(.el-input),
  .keyword-input,
  .time-range {
    width: 100%;
  }

  .audit-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
