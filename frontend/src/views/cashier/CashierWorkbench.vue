<template>
  <div class="cashier">
    <header class="cashier-nav">
      <div class="cashier-nav__brand">
        <span class="cashier-nav__logo">￥</span>
        <span class="cashier-nav__title">挂号缴费窗口工作台</span>
      </div>
      <div class="cashier-nav__right">
        <span>{{ auth.user?.name }} 收银员</span>
        <span>{{ today }} {{ dayOfWeek }}</span>
        <el-button size="small" text class="nav-logout" @click="logout">退出</el-button>
      </div>
    </header>

    <div class="cashier-body">
      <aside class="cashier-sidebar">
        <button
          v-for="item in navItems"
          :key="item.key"
          :class="['nav-item', currentPage === item.key && 'nav-item--active']"
          @click="switchPage(item.key)"
        >
          <span>{{ item.label }}</span>
          <em v-if="item.badge">{{ item.badge }}</em>
        </button>
      </aside>

      <main class="cashier-main">
        <section v-show="currentPage === 'payments'" class="work-page">
          <div class="page-head">
            <div>
              <h1>待缴费</h1>
            </div>
          </div>

          <div class="query-bar">
            <el-input
              v-model="paymentSearch.keyword"
              class="head-search"
              clearable
              placeholder="输入身份证号或姓名"
              @keyup.enter="applyPaymentSearch"
              @clear="clearPaymentSearch"
            />
            <el-button type="primary" :loading="searchingPayment" @click="applyPaymentSearch">搜索</el-button>
            <el-button @click="resetPaymentSearch">重置</el-button>
            <el-segmented v-model="paymentSearch.feeType" :options="feeFilterOptions" />
          </div>

          <div class="stat-strip">
            <div v-for="item in categorySummaries" :key="item.key" class="stat-box">
              <span>{{ item.label }}</span>
              <strong>￥{{ amountText(item.amount) }}</strong>
              <em>{{ item.count }} 项</em>
            </div>
          </div>

          <el-table v-loading="loadingAll" :data="filteredPendingItems" row-key="businessKey" empty-text="暂无待缴费用">
            <el-table-column label="类别" width="100">
              <template #default="{ row }">
                <el-tag :type="feeTagType(row.feeType)" effect="plain">{{ feeTypeLabel(row.feeType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="patientName" label="患者" width="110" />
            <el-table-column prop="title" label="项目" min-width="180" show-overflow-tooltip />
            <el-table-column prop="description" label="详情" min-width="260" show-overflow-tooltip />
            <el-table-column label="金额" width="120" align="right">
              <template #default="{ row }">
                <strong class="amount">￥{{ amountText(row.amount) }}</strong>
              </template>
            </el-table-column>
            <el-table-column label="医保参考" width="130" align="right">
              <template #default="{ row }">
                <span class="insurance-amount">￥{{ amountText(row.insuranceAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link :loading="qrPreparingKey === row.businessKey" @click="openQr(row)">
                  缴费
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section v-show="currentPage === 'registration'" class="work-page">
          <div class="page-head">
            <div>
              <h1>线下挂号</h1>
            </div>
            <el-button :loading="loadingSchedules" @click="refreshSchedules">刷新号源</el-button>
          </div>

          <div class="registration-layout">
            <el-card shadow="never">
              <template #header>就诊人信息</template>
              <el-form label-position="top" class="patient-form">
                <div class="form-grid">
                  <el-form-item label="证件类型">
                    <el-select v-model="patientForm.idType" class="full" @change="onIdTypeChange">
                      <el-option v-for="item in idTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="证件号">
                    <el-input
                      v-model="patientForm.idNumber"
                      :maxlength="patientForm.idType === 'ID_CARD' ? 18 : 64"
                      clearable
                      @input="onCertificateInput"
                      @blur="() => searchPatientWhenIdCard(false)"
                    />
                  </el-form-item>
                  <el-form-item label="姓名">
                    <el-input v-model="patientForm.name" clearable />
                  </el-form-item>
                  <el-form-item label="手机号">
                    <el-input v-model="patientForm.phone" clearable placeholder="选填" />
                  </el-form-item>
                  <el-form-item label="性别">
                    <el-select v-model="patientForm.gender" class="full">
                      <el-option v-for="item in genderOptions" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="出生日期">
                    <el-date-picker
                      v-model="patientForm.birthDate"
                      class="full"
                      type="date"
                      value-format="YYYY-MM-DD"
                      :disabled="patientForm.idType === 'ID_CARD' && isValidIdCard(patientForm.idNumber)"
                    />
                  </el-form-item>
                </div>
                <div class="form-actions">
                  <el-button :loading="searchingPatient" @click="searchPatient">查询档案</el-button>
                  <el-button type="primary" :loading="savingPatient" :disabled="!canConfirmPatient" @click="confirmPatient">
                    确认就诊人
                  </el-button>
                </div>
              </el-form>

              <div v-if="patient" class="patient-card">
                <div class="patient-card__avatar">{{ patient.name.slice(-1) }}</div>
                <div class="patient-card__info">
                  <strong>{{ patient.name }}</strong>
                  <span>{{ idTypeLabel(patient.idType) }} {{ patient.idNumber || '-' }}</span>
                  <span>{{ genderLabel(patient.gender) }} · {{ patient.birthDate || '出生日期未填' }}</span>
                  <span>患者编号 {{ currentPatientId }}</span>
                </div>
              </div>
            </el-card>

            <el-card shadow="never">
              <template #header>挂号信息</template>
              <div class="form-grid">
                <el-select v-model="selectedDepartmentId" clearable filterable placeholder="科室" class="full">
                  <el-option v-for="item in registrationDepartments" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
                <el-select v-model="selectedDoctorId" clearable filterable placeholder="医生" class="full">
                  <el-option v-for="item in doctorOptions" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
                <el-select v-model="selectedSlotId" filterable placeholder="选择号源" class="full form-span-2">
                  <el-option
                    v-for="item in scheduleOptions"
                    :key="item.slot.id"
                    :disabled="item.slot.available <= 0"
                    :label="scheduleLabel(item)"
                    :value="item.slot.id"
                  />
                </el-select>
              </div>
              <div class="registration-footer">
                <div>
                  <span>挂号费</span>
                  <strong>￥{{ selectedRegistrationFeeText }}</strong>
                  <em>医保参考 ￥{{ selectedRegistrationInsuranceFeeText }}</em>
                </div>
                <el-button type="primary" :loading="registering" :disabled="!canRegister" @click="register">
                  挂号并收费
                </el-button>
              </div>

              <el-result
                v-if="lastAppointment"
                class="register-result"
                icon="success"
                title="挂号成功"
                :sub-title="`业务编号 ${lastAppointment.businessNo}，队列号 ${lastAppointment.queueNumber}`"
              >
                <template #extra>
                  <el-button type="primary" @click="printRegistrationSlip(lastAppointment)">打印挂号单</el-button>
                </template>
              </el-result>
            </el-card>
          </div>
        </section>

        <section v-show="currentPage === 'appointmentRecords'" class="work-page">
          <div class="page-head">
            <div>
              <h1>挂号记录</h1>
            </div>
          </div>

          <div class="query-bar">
            <el-input
              v-model="appointmentRecordSearch.keyword"
              class="head-search"
              clearable
              placeholder="输入身份证号或姓名"
              @keyup.enter="applyAppointmentRecordSearch"
              @clear="clearAppointmentRecordSearch"
            />
            <el-button type="primary" :loading="searchingAppointmentRecords" @click="applyAppointmentRecordSearch">搜索</el-button>
            <el-button @click="resetAppointmentRecordSearch">重置</el-button>
            <el-select v-model="appointmentRecordSearch.status" clearable placeholder="状态" style="width: 150px">
              <el-option label="待支付" value="PENDING_PAYMENT" />
              <el-option label="已挂号" value="REGISTERED" />
              <el-option label="已取消" value="CANCELLED" />
              <el-option label="已完成" value="FINISHED" />
            </el-select>
          </div>

          <el-table v-loading="loadingAll" :data="filteredAppointmentRecords" table-layout="auto" empty-text="暂无挂号记录">
            <el-table-column prop="patientName" label="患者" min-width="110" />
            <el-table-column label="身份证号" min-width="180">
              <template #default="{ row }">{{ appointmentPatientIdNumber(row) }}</template>
            </el-table-column>
            <el-table-column prop="departmentName" label="科室" min-width="140" />
            <el-table-column prop="doctorName" label="医生" min-width="120" />
            <el-table-column label="就诊诊室" min-width="140">
              <template #default="{ row }">{{ appointmentRoomName(row) }}</template>
            </el-table-column>
            <el-table-column label="就诊时间" min-width="170">
              <template #default="{ row }">{{ row.visitDate }} {{ normalizeStartTime(row.startTime) || row.period }}</template>
            </el-table-column>
            <el-table-column label="状态" min-width="110">
              <template #default="{ row }">
                <el-tag :type="appointmentTagType(row)" effect="plain" size="small">{{ appointmentStatusLabel(row) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="printRegistrationSlip(row)">打印</el-button>
                <el-button v-if="canRefundAppointment(row)" type="danger" link @click="refund(row)">退号</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section v-show="currentPage === 'paymentRecords'" class="work-page">
          <div class="page-head">
            <div>
              <h1>缴费退费记录</h1>
            </div>
          </div>

          <div class="query-bar">
            <el-input
              v-model="paymentRecordSearch.keyword"
              class="head-search"
              clearable
              placeholder="输入身份证号或姓名"
              @keyup.enter="applyPaymentRecordSearch"
              @clear="clearPaymentRecordSearch"
            />
            <el-button type="primary" :loading="searchingPaymentRecords" @click="applyPaymentRecordSearch">搜索</el-button>
            <el-button @click="resetPaymentRecordSearch">重置</el-button>
            <el-select v-model="paymentRecordSearch.businessType" clearable placeholder="费用类型" style="width: 150px">
              <el-option label="挂号费" value="APPOINTMENT" />
              <el-option label="医技费用" value="MEDICAL_ORDER" />
              <el-option label="药费" value="PRESCRIPTION" />
            </el-select>
            <el-select v-model="paymentRecordSearch.status" clearable placeholder="支付状态" style="width: 140px">
              <el-option label="待支付" value="PENDING" />
              <el-option label="已支付" value="PAID" />
              <el-option label="支付失败" value="FAILED" />
            </el-select>
          </div>

          <el-table v-loading="loadingAll" :data="filteredPaymentRecords" empty-text="暂无缴费记录">
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ businessTypeLabel(row.businessType) }}</template>
            </el-table-column>
            <el-table-column label="患者" width="110">
              <template #default="{ row }">{{ paymentRecordPatientName(row) }}</template>
            </el-table-column>
            <el-table-column label="身份证号" min-width="180">
              <template #default="{ row }">{{ paymentRecordIdNumber(row) }}</template>
            </el-table-column>
            <el-table-column label="项目" min-width="190" show-overflow-tooltip>
              <template #default="{ row }">{{ paymentRecordTitle(row) }}</template>
            </el-table-column>
            <el-table-column label="金额" width="110" align="right">
              <template #default="{ row }">￥{{ amountText(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="方式" width="130">
              <template #default="{ row }">{{ paymentMethodLabel(row.paymentMethod) }}</template>
            </el-table-column>
            <el-table-column label="医保" width="90">
              <template #default="{ row }">
                <el-tag v-if="isInsurancePayment(row)" type="success" effect="plain" size="small">是</el-tag>
                <span v-else class="muted-cell">否</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="paymentTagType(row.status)" effect="plain" size="small">{{ paymentOrderStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="分配诊室" min-width="160">
              <template #default="{ row }">
                <template v-if="row.businessType === 'MEDICAL_ORDER'">
                  <span v-if="medicalOrderExecutor(row.businessId)">{{ medicalOrderExecutor(row.businessId) }}</span>
                  <span v-else class="muted-cell">—</span>
                </template>
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.paidAt || row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </section>

        <section v-show="currentPage === 'drugReturnRefunds'" class="work-page">
          <div class="page-head">
            <div>
              <h1>退药待退费</h1>
            </div>
          </div>

          <div class="query-bar">
            <el-input
              v-model="drugReturnSearch.keyword"
              class="head-search"
              clearable
              placeholder="输入退药单号、处方号或患者姓名"
              @keyup.enter="applyDrugReturnSearch"
              @clear="clearDrugReturnSearch"
            />
            <el-button type="primary" :loading="searchingDrugReturns" @click="applyDrugReturnSearch">搜索</el-button>
            <el-button @click="resetDrugReturnSearch">重置</el-button>
          </div>

          <el-table v-loading="loadingAll" :data="filteredDrugReturns" table-layout="auto" empty-text="暂无待退费退药单">
            <el-table-column prop="returnNo" label="退药单号" width="150" />
            <el-table-column prop="prescriptionNo" label="处方号" width="150" />
            <el-table-column prop="patientName" label="患者" width="110" />
            <el-table-column prop="doctorOpinion" label="医生意见" min-width="220" show-overflow-tooltip />
            <el-table-column label="金额" width="120" align="right">
              <template #default="{ row }">
                <strong class="amount">¥{{ amountText(row.totalAmount) }}</strong>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link :loading="refundingReturnId === row.id" @click="refundDrug(row)">退费完成</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </main>
    </div>

    <el-dialog v-model="qrDialog.visible" title="扫码缴费" width="460px" @closed="resetQrDialog">
      <div v-if="qrDialog.item" class="qr-dialog">
        <div class="qr-meta">
          <strong>{{ qrDialog.item.patientName }} · {{ feeTypeLabel(qrDialog.item.feeType) }}</strong>
          <span>{{ qrDialog.item.title }}</span>
          <em>￥{{ amountText(currentQrAmount) }}</em>
          <span v-if="qrDialog.channel === 'MEDICAL_INSURANCE'" class="qr-meta__insurance">医保支付参考价</span>
        </div>
        <div class="qr-channel-picker">
          <button
            v-for="option in paymentChannelOptions"
            :key="option.value"
            type="button"
            :class="['qr-channel-button', qrDialog.channel === option.value && 'qr-channel-button--active']"
            :disabled="qrDialog.status === 'PAID'"
            @click="changeQrChannel(option.value)"
          >
            <strong>{{ option.action }}</strong>
            <span>{{ option.label }}</span>
          </button>
        </div>
        <div class="qr-card" :data-channel="qrDialog.channel">
          <div class="qr-card__header">
            <span>{{ currentQrChannel.label }}</span>
            <em>{{ qrDialog.status === 'PAID' ? '已完成' : '等待扫码' }}</em>
          </div>
          <div v-if="qrDialog.qrLoading" class="payment-qr payment-qr--loading">二维码生成中...</div>
          <div v-else-if="qrDialog.qrSvg" class="payment-qr" v-html="qrDialog.qrSvg" />
          <el-alert
            v-else
            :title="qrDialog.qrError || '二维码生成失败，请关闭后重新打开缴费弹窗'"
            type="error"
            :closable="false"
          />
        </div>
        <el-alert
          v-if="qrDialog.status === 'PAID'"
          title="二维码已被识别，缴费成功"
          type="success"
          :closable="false"
        />
        <p v-else class="qr-hint">
          {{ currentQrChannel.hint }}。
        </p>
        <p v-if="scanBaseUrlNotice" class="qr-hint qr-hint--warn">
          当前未配置扫码基地址，二维码将使用本页地址。手机扫码测试时，请在 frontend/.env.local 配置
          VITE_PAYMENT_SCAN_BASE_URL 为当前电脑的局域网访问地址。
        </p>
      </div>
      <template #footer>
        <span class="qr-footer-status">
          {{ qrDialog.status === 'PAID' ? '缴费成功' : '等待扫码完成' }}
        </span>
        <el-button @click="qrDialog.visible = false">关闭</el-button>
      </template>
    </el-dialog>

    <section class="print-area">
      <div v-if="printAppointment" class="print-slip">
        <h2>智慧云脑诊疗中心挂号单</h2>
        <div class="print-rule" />
        <div class="print-grid">
          <span>业务编号</span><strong>{{ printAppointment.businessNo }}</strong>
          <span>队列号</span><strong>{{ printAppointment.queueNumber }}</strong>
          <span>患者姓名</span><strong>{{ printAppointment.patientName }}</strong>
          <span>科室</span><strong>{{ printAppointment.departmentName }}</strong>
          <span>医生</span><strong>{{ printAppointment.doctorName }}</strong>
          <span>就诊时间</span><strong>{{ printAppointment.visitDate }} {{ normalizeStartTime(printAppointment.startTime) || printAppointment.period }}</strong>
          <span>支付状态</span><strong>{{ paymentStatusLabel(printAppointment.paymentStatus) }}</strong>
          <span>打印时间</span><strong>{{ printTime }}</strong>
        </div>
        <div class="print-note">请按队列号候诊，过号后由诊室重新安排。</div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../../store/auth';
import { cancelAppointment, createOfflineAppointment, getAppointments, type Appointment } from '../../api/appointment';
import { getDepartments, getDoctors, getSchedules, type Department, type Doctor, type Schedule } from '../../api/doctor';
import { getMedicalOrders, type MedicalOrder } from '../../api/medical-order';
import { getDrugReturns, getPrescriptions, type DrugReturnOrder, type Prescription } from '../../api/pharmacy';
import {
  createPaymentOrder,
  getPaymentQrCode,
  getPayments,
  refundDrugReturn,
  type BusinessType,
  type PaymentChannel,
  type PaymentOrder
} from '../../api/cashier';
import {
  createOfflinePatient,
  getPatientsByIds,
  patientProfileId,
  searchPatientByIdNumber,
  type Gender,
  type IdType,
  type PatientProfile
} from '../../api/patient';
import { appointmentStatusLabel, paymentStatusLabel } from '../../utils/status';

type PageKey = 'payments' | 'registration' | 'appointmentRecords' | 'paymentRecords' | 'drugReturnRefunds';
type FeeType = 'REGISTRATION' | 'DRUG' | 'CHECK' | 'LAB' | 'DISPOSAL';
type FeeFilter = 'ALL' | FeeType;

interface PendingFeeItem {
  businessKey: string;
  businessType: BusinessType;
  businessId: string;
  patientId: string;
  patientName: string;
  feeType: FeeType;
  title: string;
  description: string;
  amount: number;
  insuranceAmount: number;
  sortTime: string;
}

interface ScheduleOption {
  schedule: Schedule;
  slot: NonNullable<Schedule['timeSlots']>[number];
}

const router = useRouter();
const auth = useAuthStore();

const currentPage = ref<PageKey>('payments');
const authRedirecting = ref(false);
const loadingAll = ref(false);
const loadingSchedules = ref(false);
const searchingPatient = ref(false);
const savingPatient = ref(false);
const registering = ref(false);
const qrPreparingKey = ref('');
const searchingPayment = ref(false);
const searchingAppointmentRecords = ref(false);
const searchingPaymentRecords = ref(false);
const searchingDrugReturns = ref(false);

const departments = ref<Department[]>([]);
const doctors = ref<Doctor[]>([]);
const schedules = ref<Schedule[]>([]);
const appointments = ref<Appointment[]>([]);
const medicalOrders = ref<MedicalOrder[]>([]);
const prescriptions = ref<Prescription[]>([]);
const drugReturns = ref<DrugReturnOrder[]>([]);
const refundingReturnId = ref('');
const paymentRecords = ref<PaymentOrder[]>([]);
const patientProfiles = ref<PatientProfile[]>([]);

const selectedDepartmentId = ref('');
const selectedDoctorId = ref('');
const selectedSlotId = ref('');
const lastAppointment = ref<Appointment>();
const printAppointment = ref<Appointment>();
const printTime = ref('');

const paymentSearch = reactive({ keyword: '', feeType: 'ALL' as FeeFilter, patientIds: null as string[] | null });
const appointmentRecordSearch = reactive({ keyword: '', status: '', patientIds: null as string[] | null });
const paymentRecordSearch = reactive({
  keyword: '',
  businessType: '' as BusinessType | '',
  status: '',
  patientIds: null as string[] | null
});
const drugReturnSearch = reactive({
  keyword: ''
});

const qrDialog = reactive({
  visible: false,
  checking: false,
  status: '' as '' | 'PENDING' | 'PAID',
  paymentId: '',
  channel: 'WECHAT' as PaymentChannel,
  qrSvg: '',
  qrLoading: false,
  qrError: '',
  item: undefined as PendingFeeItem | undefined
});
let qrStatusTimer: number | undefined;

const idTypeOptions: Array<{ label: string; value: IdType }> = [
  { label: '居民身份证', value: 'ID_CARD' },
  { label: '护照', value: 'PASSPORT' },
  { label: '港澳台证件', value: 'HK_MACAO_TAIWAN' },
  { label: '其他证件', value: 'OTHER' }
];
const genderOptions: Array<{ label: string; value: Gender }> = [
  { label: '男', value: 'MALE' },
  { label: '女', value: 'FEMALE' },
  { label: '未知', value: 'UNKNOWN' }
];

const patientForm = reactive({
  idType: 'ID_CARD' as IdType,
  idNumber: '',
  name: '',
  gender: 'UNKNOWN' as Gender,
  birthDate: '',
  phone: ''
});
const patient = ref<PatientProfile>();
let autoSearchTimer: number | undefined;
const lastAutoSearchNumber = ref('');

const weekDays = ['日', '一', '二', '三', '四', '五', '六'];
const today = new Date().toLocaleDateString('zh-CN');
const dayOfWeek = `星期${weekDays[new Date().getDay()]}`;
const EXCLUDED_REGISTRATION_DEPARTMENT_KEYWORDS = ['处置科', '检查科', '检验科', '药房', '收费处', '系统管理'];
const nowTimestamp = ref(Date.now());
let nowTimer: number | undefined;

const paymentChannelOptions: Array<{ value: PaymentChannel; action: string; label: string; hint: string }> = [
  { value: 'WECHAT', action: '按微信支付', label: '微信支付', hint: '请使用微信扫一扫当前二维码' },
  { value: 'ALIPAY', action: '按支付宝支付', label: '支付宝支付', hint: '请使用支付宝扫一扫当前二维码' },
  { value: 'MEDICAL_INSURANCE', action: '使用医保卡支付', label: '医保卡支付', hint: '请使用医保终端或扫码设备识别当前二维码' }
];

const navItems = computed(() => [
  { key: 'payments' as const, label: '待缴费', badge: pendingItems.value.length || '' },
  { key: 'drugReturnRefunds' as const, label: '退药待退费', badge: drugReturns.value.length || '' },
  { key: 'registration' as const, label: '线下挂号', badge: '' },
  { key: 'appointmentRecords' as const, label: '挂号记录', badge: '' },
  { key: 'paymentRecords' as const, label: '缴费退费记录', badge: '' }
]);

const currentPatientId = computed(() => patient.value ? patientProfileId(patient.value) : '');
const canConfirmPatient = computed(() => {
  if (!patientForm.idNumber.trim() || !patientForm.name.trim() || !patientForm.birthDate) return false;
  return patientForm.idType !== 'ID_CARD' || isValidIdCard(patientForm.idNumber);
});
const registrationDepartments = computed(() => departments.value.filter(department => isRegistrationDepartment(department.id)));

const doctorOptions = computed(() => {
  const map = new Map<string, { id: string; name: string }>();
  schedules.value
    .filter(item => isRegistrationDepartment(item.departmentId))
    .filter(item => !selectedDepartmentId.value || item.departmentId === selectedDepartmentId.value)
    .filter(item => hasFutureScheduleSlot(item))
    .forEach(item => map.set(item.doctorId, { id: item.doctorId, name: item.doctorName }));
  return [...map.values()];
});

const scheduleOptions = computed<ScheduleOption[]>(() => {
  return schedules.value
    .filter(item => isRegistrationDepartment(item.departmentId))
    .filter(item => !selectedDepartmentId.value || item.departmentId === selectedDepartmentId.value)
    .filter(item => !selectedDoctorId.value || item.doctorId === selectedDoctorId.value)
    .flatMap(schedule => (schedule.timeSlots ?? []).map(slot => ({ schedule, slot })))
    .filter(item => isFutureScheduleSlot(item))
    .sort((left, right) => `${left.schedule.workDate} ${left.slot.startTime}`.localeCompare(`${right.schedule.workDate} ${right.slot.startTime}`));
});
const selectedScheduleOption = computed(() => scheduleOptions.value.find(item => item.slot.id === selectedSlotId.value));
const doctorMap = computed(() => new Map(doctors.value.map(item => [item.id, item])));
const patientProfileMap = computed(() => new Map(patientProfiles.value.map(item => [patientProfileId(item), item])));
const slotRoomNameMap = computed(() => {
  const map = new Map<string, string>();
  schedules.value.forEach(schedule => {
    const roomName = schedule.roomName || doctorMap.value.get(schedule.doctorId)?.roomName || '';
    if (!roomName) return;
    map.set(schedule.id, roomName);
    (schedule.timeSlots ?? []).forEach(slot => {
      map.set(slot.id, roomName);
    });
  });
  return map;
});
const selectedRegistrationFee = computed(() => selectedScheduleOption.value ? registrationFee(selectedScheduleOption.value.schedule.doctorId) : 15);
const selectedRegistrationFeeText = computed(() => amountText(selectedRegistrationFee.value));
const selectedRegistrationInsuranceFeeText = computed(() => amountText(insuranceAmount('REGISTRATION', selectedRegistrationFee.value)));
const canRegister = computed(() => Boolean(canConfirmPatient.value && selectedScheduleOption.value && selectedScheduleOption.value.slot.available > 0));

const appointmentMap = computed(() => new Map(appointments.value.map(item => [item.id, item])));
const medicalOrderMap = computed(() => new Map(medicalOrders.value.map(item => [item.id, item])));
const prescriptionMap = computed(() => new Map(prescriptions.value.map(item => [item.id, item])));
const pendingPaymentMap = computed(() => {
  const map = new Map<string, PaymentOrder>();
  paymentRecords.value
    .filter(item => item.status === 'PENDING')
    .forEach(item => map.set(`${item.businessType}:${item.businessId}`, item));
  return map;
});

const pendingItems = computed<PendingFeeItem[]>(() => {
  const registrationItems = appointments.value
    .filter(item => item.status === 'PENDING_PAYMENT' && item.paymentStatus === 'UNPAID')
    .map(item => ({
      businessKey: `APPOINTMENT:${item.id}`,
      businessType: 'APPOINTMENT' as const,
      businessId: item.id,
      patientId: item.patientId,
      patientName: item.patientName,
      feeType: 'REGISTRATION' as const,
      title: `${item.departmentName} · ${item.doctorName}`,
      description: `${item.visitDate} ${normalizeStartTime(item.startTime) || item.period} · ${item.businessNo}`,
      amount: Number(pendingPaymentMap.value.get(`APPOINTMENT:${item.id}`)?.amount ?? registrationFee(item.doctorId)),
      insuranceAmount: insuranceAmount('REGISTRATION', Number(pendingPaymentMap.value.get(`APPOINTMENT:${item.id}`)?.amount ?? registrationFee(item.doctorId))),
      sortTime: `${item.visitDate} ${normalizeStartTime(item.startTime) || '00:00'}`
    }));

  const medicalItems = medicalOrders.value
    .filter(item => item.paymentStatus === 'UNPAID' || item.status === 'PENDING_PAYMENT')
    .map(item => ({
      businessKey: `MEDICAL_ORDER:${item.id}`,
      businessType: 'MEDICAL_ORDER' as const,
      businessId: item.id,
      patientId: item.patientId,
      patientName: item.patientName,
      feeType: item.orderType as FeeType,
      title: item.itemName,
      description: `${feeTypeLabel(item.orderType as FeeType)} · ${urgencyLabel(item.urgency)}${item.bodyPart ? ` · ${item.bodyPart}` : ''}`,
      amount: Number(item.amount ?? 0),
      insuranceAmount: insuranceAmount(item.orderType as FeeType, Number(item.amount ?? 0)),
      sortTime: item.id
    }));

  const drugItems = prescriptions.value
    .filter(item => item.status === 'PENDING_PAYMENT' || item.status === 'CONFIRMED')
    .map(item => ({
      businessKey: `PRESCRIPTION:${item.id}`,
      businessType: 'PRESCRIPTION' as const,
      businessId: item.id,
      patientId: item.patientId,
      patientName: item.patientName || '患者',
      feeType: 'DRUG' as const,
      title: item.prescriptionNo || '处方药费',
      description: prescriptionDescription(item),
      amount: Number(item.totalAmount ?? 0),
      insuranceAmount: insuranceAmount('DRUG', Number(item.totalAmount ?? 0)),
      sortTime: item.id
    }));

  return [...registrationItems, ...medicalItems, ...drugItems].sort((left, right) => right.sortTime.localeCompare(left.sortTime));
});

const filteredPendingItems = computed(() => {
  return pendingItems.value
    .filter(item => paymentSearch.feeType === 'ALL' || item.feeType === paymentSearch.feeType)
    .filter(item => matchesPatientSearch(item.patientId, item.patientName, `${item.title} ${item.description}`, paymentSearch));
});

const categorySummaries = computed(() => [
  summarize('REGISTRATION', '挂号费'),
  summarize('DRUG', '药费'),
  summarize('CHECK', '检查费'),
  summarize('LAB', '检验费'),
  summarize('DISPOSAL', '处置费')
]);

const feeFilterOptions = computed(() => [
  { label: `全部 ${pendingItems.value.length}`, value: 'ALL' },
  ...categorySummaries.value.map(item => ({ label: `${item.label} ${item.count}`, value: item.key }))
]);

const filteredAppointmentRecords = computed(() => {
  return appointments.value
    .filter(item => matchesPatientSearch(item.patientId, item.patientName, `${item.businessNo} ${item.departmentName} ${item.doctorName}`, appointmentRecordSearch))
    .filter(item => {
      if (!appointmentRecordSearch.status) return true;
      if (appointmentRecordSearch.status === 'REGISTERED') return ['WAITING', 'CALLED', 'IN_VISIT', 'REVISIT_WAITING'].includes(item.status);
      return item.status === appointmentRecordSearch.status;
    })
    .sort((left, right) => `${right.visitDate} ${normalizeStartTime(right.startTime)}`.localeCompare(`${left.visitDate} ${normalizeStartTime(left.startTime)}`));
});

const sortedPaymentRecords = computed(() =>
  [...paymentRecords.value].sort((left, right) => (right.paidAt || right.createdAt || '').localeCompare(left.paidAt || left.createdAt || ''))
);

const filteredPaymentRecords = computed(() => {
  return sortedPaymentRecords.value
    .filter(item => !paymentRecordSearch.businessType || item.businessType === paymentRecordSearch.businessType)
    .filter(item => !paymentRecordSearch.status || item.status === paymentRecordSearch.status)
    .filter(item => matchesPatientSearch(item.patientId, paymentRecordPatientName(item), paymentRecordTitle(item), paymentRecordSearch));
});

const filteredDrugReturns = computed(() => {
  const keyword = drugReturnSearch.keyword.trim().toLowerCase();
  return [...drugReturns.value]
    .filter(item => {
      if (!keyword) return true;
      return `${item.returnNo} ${item.prescriptionNo} ${item.patientName} ${item.doctorOpinion}`.toLowerCase().includes(keyword);
    })
    .sort((left, right) => (right.createdAt || '').localeCompare(left.createdAt || ''));
});

const currentQrChannel = computed(
  () => paymentChannelOptions.find(item => item.value === qrDialog.channel) ?? paymentChannelOptions[0]
);
const currentQrAmount = computed(() => {
  if (!qrDialog.item) return 0;
  return qrDialog.channel === 'MEDICAL_INSURANCE' ? qrDialog.item.insuranceAmount : qrDialog.item.amount;
});

const configuredScanBaseUrl = (import.meta.env.VITE_PAYMENT_SCAN_BASE_URL ?? '').trim().replace(/\/+$/, '');

const paymentScanBaseUrl = computed(() => {
  if (configuredScanBaseUrl) return configuredScanBaseUrl;
  if (typeof window === 'undefined') return '';
  return window.location.origin;
});

const qrScanUrl = computed(() => {
  if (!qrDialog.paymentId || !paymentScanBaseUrl.value) return '';
  const url = new URL('/api/payments/scan-entry', paymentScanBaseUrl.value);
  url.searchParams.set('paymentId', qrDialog.paymentId);
  url.searchParams.set('channel', qrDialog.channel);
  return url.toString();
});

const scanBaseUrlNotice = computed(() => {
  if (configuredScanBaseUrl) return false;
  if (typeof window === 'undefined') return false;
  return ['localhost', '127.0.0.1'].includes(window.location.hostname);
});

async function loadQrSvg() {
  if (!qrDialog.visible || !qrScanUrl.value) return;
  const target = qrScanUrl.value;
  qrDialog.qrLoading = true;
  qrDialog.qrError = '';
  qrDialog.qrSvg = '';
  try {
    const svg = await getPaymentQrCode(target);
    if (target === qrScanUrl.value) {
      if (!svg.trim().startsWith('<svg')) {
        throw new Error('二维码接口未返回有效 SVG，请确认 cashier-service 和 gateway-service 已重启');
      }
      qrDialog.qrSvg = svg;
    }
  } catch (error) {
    if (target === qrScanUrl.value) {
      qrDialog.qrError = errorMessage(error, '二维码生成失败，请确认后端和网关服务已重启');
    }
  } finally {
    if (target === qrScanUrl.value) {
      qrDialog.qrLoading = false;
    }
  }
}

function switchPage(page: PageKey) {
  currentPage.value = page;
  if (page === 'registration') {
    loadRegistrationData();
  } else {
    void loadCurrentPageData();
  }
}

async function loadCurrentPageData() {
  if (currentPage.value === 'payments') {
    await loadPaymentsPage();
    return;
  }
  if (currentPage.value === 'appointmentRecords') {
    await loadAppointmentRecordsPage();
    return;
  }
  if (currentPage.value === 'paymentRecords') {
    await loadPaymentRecordsPage();
    return;
  }
  if (currentPage.value === 'drugReturnRefunds') {
    await loadDrugReturnRefundPage();
  }
}

async function loadPaymentsPage() {
  loadingAll.value = true;
  try {
    const [appointmentsResult, prescriptionsResult, medicalOrdersResult] = await Promise.allSettled([
      getAppointments(),
      getPrescriptions(),
      getMedicalOrders()
    ]);
    appointments.value = unwrap(appointmentsResult, [], '挂号记录');
    prescriptions.value = unwrap(prescriptionsResult, [], '处方');
    medicalOrders.value = unwrap(medicalOrdersResult, [], '检查检验处置医嘱');
  } finally {
    loadingAll.value = false;
  }
}

async function loadAppointmentRecordsPage() {
  loadingAll.value = true;
  try {
    const [appointmentsResult, doctorsResult, schedulesResult] = await Promise.allSettled([
      getAppointments(),
      getDoctors(),
      getSchedules()
    ]);
    appointments.value = unwrap(appointmentsResult, [], '挂号记录');
    doctors.value = unwrap(doctorsResult, doctors.value, '医生列表');
    schedules.value = unwrap(schedulesResult, schedules.value, '排班列表');
    await syncPatientProfiles();
  } finally {
    loadingAll.value = false;
  }
}

async function loadPaymentRecordsPage() {
  loadingAll.value = true;
  try {
    const [appointmentsResult, prescriptionsResult, medicalOrdersResult, paymentsResult] = await Promise.allSettled([
      getAppointments(),
      getPrescriptions(),
      getMedicalOrders(),
      getPayments()
    ]);
    appointments.value = unwrap(appointmentsResult, [], '挂号记录');
    prescriptions.value = unwrap(prescriptionsResult, [], '处方');
    medicalOrders.value = unwrap(medicalOrdersResult, [], '检查检验处置医嘱');
    paymentRecords.value = unwrap(paymentsResult, [], '缴费记录');
    await syncPatientProfiles();
  } finally {
    loadingAll.value = false;
  }
}

async function loadDrugReturnRefundPage() {
  loadingAll.value = true;
  try {
    drugReturns.value = await getDrugReturns({ status: 'RETURN_PENDING_REFUND' });
  } catch (error) {
    handleRequestFailure(error, '退药单加载失败');
  } finally {
    loadingAll.value = false;
  }
}

async function syncPatientProfiles() {
  const patientIds = [
    ...new Set([
      ...appointments.value.map(item => item.patientId),
      ...paymentRecords.value.map(item => item.patientId)
    ].filter(Boolean))
  ];
  if (!patientIds.length) {
    patientProfiles.value = [];
    return;
  }
  try {
    patientProfiles.value = await getPatientsByIds(patientIds);
  } catch (error) {
    handleRequestFailure(error, '患者档案加载失败');
  }
}

async function loadSchedules(showFeedback = false) {
  loadingSchedules.value = true;
  try {
    schedules.value = await getSchedules();
    syncSelectedSlot();
    if (showFeedback) {
      if (scheduleOptions.value.length > 0) {
        ElMessage.success(`号源已刷新，共 ${scheduleOptions.value.length} 个未来号源`);
      } else {
        ElMessage.warning('号源已刷新，当前筛选下暂无未来号源');
      }
    }
  } catch (error) {
    handleRequestFailure(error, '号源加载失败');
  } finally {
    loadingSchedules.value = false;
  }
}

async function refreshSchedules() {
  await loadSchedules(true);
}

async function loadDepartments() {
  try {
    departments.value = await getDepartments();
  } catch (error) {
    handleRequestFailure(error, '科室加载失败');
  }
}

async function loadDoctors() {
  try {
    doctors.value = await getDoctors();
  } catch (error) {
    handleRequestFailure(error, '医生加载失败');
  }
}

async function loadRegistrationData() {
  await Promise.all([loadDepartments(), loadDoctors(), loadSchedules()]);
}

async function resolvePatientIds(keyword: string) {
  const value = normalizeIdNumber(keyword);
  if (!isValidIdCard(value)) return null;
  const patients = await searchPatientByIdNumber(value);
  return patients.map(item => patientProfileId(item)).filter(Boolean);
}

async function applyPaymentSearch() {
  searchingPayment.value = true;
  try {
    paymentSearch.patientIds = await resolvePatientIds(paymentSearch.keyword);
  } finally {
    searchingPayment.value = false;
  }
}

function clearPaymentSearch() {
  paymentSearch.keyword = '';
  paymentSearch.patientIds = null;
}

function resetPaymentSearch() {
  clearPaymentSearch();
  paymentSearch.feeType = 'ALL';
}

async function applyAppointmentRecordSearch() {
  searchingAppointmentRecords.value = true;
  try {
    appointmentRecordSearch.patientIds = await resolvePatientIds(appointmentRecordSearch.keyword);
  } finally {
    searchingAppointmentRecords.value = false;
  }
}

function clearAppointmentRecordSearch() {
  appointmentRecordSearch.keyword = '';
  appointmentRecordSearch.patientIds = null;
}

function resetAppointmentRecordSearch() {
  clearAppointmentRecordSearch();
  appointmentRecordSearch.status = '';
}

async function applyPaymentRecordSearch() {
  searchingPaymentRecords.value = true;
  try {
    paymentRecordSearch.patientIds = await resolvePatientIds(paymentRecordSearch.keyword);
  } finally {
    searchingPaymentRecords.value = false;
  }
}

function clearPaymentRecordSearch() {
  paymentRecordSearch.keyword = '';
  paymentRecordSearch.patientIds = null;
}

function resetPaymentRecordSearch() {
  clearPaymentRecordSearch();
  paymentRecordSearch.businessType = '';
  paymentRecordSearch.status = '';
}

async function applyDrugReturnSearch() {
  searchingDrugReturns.value = true;
  try {
    await nextTick();
  } finally {
    searchingDrugReturns.value = false;
  }
}

function clearDrugReturnSearch() {
  drugReturnSearch.keyword = '';
}

function resetDrugReturnSearch() {
  clearDrugReturnSearch();
}

function isRegistrationDepartment(departmentId: string) {
  const department = departments.value.find(item => item.id === departmentId);
  if (!department) return true;
  return !EXCLUDED_REGISTRATION_DEPARTMENT_KEYWORDS.some(keyword => department.name.includes(keyword));
}

function hasFutureScheduleSlot(schedule: Schedule) {
  return (schedule.timeSlots ?? []).some(slot => isFutureScheduleSlot({ schedule, slot }));
}

function isFutureScheduleSlot(item: ScheduleOption) {
  const timestamp = scheduleSlotTimestamp(item);
  return Number.isFinite(timestamp) && timestamp > nowTimestamp.value;
}

function scheduleSlotTimestamp(item: ScheduleOption) {
  const time = normalizeSlotTime(item.slot.startTime);
  if (!item.schedule.workDate || !time) return Number.NaN;
  return new Date(`${item.schedule.workDate}T${time}`).getTime();
}

function normalizeSlotTime(value: string) {
  const time = typeof value === 'string' ? value.slice(0, 8) : '';
  if (/^\d{2}:\d{2}$/.test(time)) return `${time}:00`;
  if (/^\d{2}:\d{2}:\d{2}$/.test(time)) return time;
  return '';
}

function syncSelectedSlot() {
  const current = selectedScheduleOption.value;
  if (current && current.slot.available > 0) return;
  selectedSlotId.value = scheduleOptions.value.find(item => item.slot.available > 0)?.slot.id ?? '';
}

function matchesPatientSearch(patientId: string, patientName: string, text: string, search: { keyword: string; patientIds: string[] | null }) {
  const keyword = search.keyword.trim().toLowerCase();
  if (!keyword) return true;
  if (search.patientIds) return search.patientIds.includes(patientId);
  return `${patientName} ${text}`.toLowerCase().includes(keyword);
}

async function openQr(item: PendingFeeItem) {
  qrPreparingKey.value = item.businessKey;
  try {
    const channel = defaultQrChannel();
    const payment = await createPaymentOrder({
      businessType: item.businessType,
      businessId: item.businessId,
      patientId: item.patientId,
      amount: channel === 'MEDICAL_INSURANCE' ? item.insuranceAmount : item.amount,
      paymentMethod: `${channel}_TEST`
    });
    qrDialog.item = item;
    qrDialog.checking = false;
    qrDialog.paymentId = payment.id;
    qrDialog.channel = channel;
    qrDialog.status = 'PENDING';
    qrDialog.visible = true;
    void loadQrSvg();
    if (payment.status === 'PAID') {
      await handleQrPaymentSuccess(payment, item);
    } else {
      startQrStatusPolling();
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, '生成二维码失败'));
  } finally {
    qrPreparingKey.value = '';
  }
}

function defaultQrChannel(): PaymentChannel {
  return 'WECHAT';
}

async function changeQrChannel(channel: PaymentChannel) {
  if (qrDialog.status === 'PAID' || !qrDialog.item) return;
  if (qrDialog.channel === channel) return;
  const item = qrDialog.item;
  try {
    qrDialog.qrLoading = true;
    const payment = await createPaymentOrder({
      businessType: item.businessType,
      businessId: item.businessId,
      patientId: item.patientId,
      amount: channel === 'MEDICAL_INSURANCE' ? item.insuranceAmount : item.amount,
      paymentMethod: `${channel}_TEST`
    });
    qrDialog.paymentId = payment.id;
    qrDialog.channel = channel;
  } catch (error) {
    ElMessage.error(errorMessage(error, '切换支付方式失败'));
  } finally {
    qrDialog.qrLoading = false;
  }
}

function startQrStatusPolling() {
  stopQrStatusPolling();
  void syncQrPaymentStatus();
  qrStatusTimer = window.setInterval(() => {
    void syncQrPaymentStatus();
  }, 1500);
}

function stopQrStatusPolling() {
  if (qrStatusTimer) window.clearInterval(qrStatusTimer);
  qrStatusTimer = undefined;
}

async function syncQrPaymentStatus() {
  if (!qrDialog.visible || !qrDialog.item || !qrDialog.paymentId || qrDialog.status === 'PAID') return;
  qrDialog.checking = true;
  try {
    const payments = await getPayments({
      businessId: qrDialog.item.businessId,
      businessType: qrDialog.item.businessType
    });
    const payment = payments.find(item => item.id === qrDialog.paymentId) ?? payments[0];
    if (payment?.status === 'PAID') {
      await handleQrPaymentSuccess(payment, qrDialog.item);
    }
  } catch (error) {
    if (isUnauthorized(error)) {
      stopQrStatusPolling();
      redirectToLogin();
    }
  } finally {
    qrDialog.checking = false;
  }
}

async function handleQrPaymentSuccess(payment: PaymentOrder, paidItem: PendingFeeItem) {
  if (qrDialog.status === 'PAID') return;
  qrDialog.status = 'PAID';
  qrDialog.paymentId = payment.id;
  stopQrStatusPolling();
  await loadPaymentsPage();
  if (paidItem.businessType === 'MEDICAL_ORDER') {
    const executor = medicalOrderExecutor(paidItem.businessId);
    ElMessage.success(executor ? `缴费成功，分配至：${executor}` : '缴费成功');
  } else {
    ElMessage.success('扫码支付成功');
  }
}

function resetQrDialog() {
  stopQrStatusPolling();
  qrDialog.checking = false;
  qrDialog.status = '';
  qrDialog.paymentId = '';
  qrDialog.channel = defaultQrChannel();
  qrDialog.qrSvg = '';
  qrDialog.qrLoading = false;
  qrDialog.qrError = '';
  qrDialog.item = undefined;
}

function idTypeLabel(value?: string) {
  return idTypeOptions.find(item => item.value === value)?.label ?? '证件';
}

function genderLabel(value?: string) {
  return genderOptions.find(item => item.value === value)?.label ?? '未知';
}

function normalizeIdNumber(value: string) {
  return value.trim().toUpperCase();
}

function isValidIdCard(value: string) {
  return /^\d{17}[\dX]$/.test(normalizeIdNumber(value)) && Boolean(inferBirthDate(normalizeIdNumber(value)));
}

function inferBirthDate(value: string) {
  const normalized = normalizeIdNumber(value);
  if (!/^\d{17}[\dX]$/.test(normalized)) return '';
  const raw = normalized.slice(6, 14);
  const year = raw.slice(0, 4);
  const month = raw.slice(4, 6);
  const day = raw.slice(6, 8);
  const date = new Date(`${year}-${month}-${day}T00:00:00`);
  if (date.getFullYear() !== Number(year) || date.getMonth() + 1 !== Number(month) || date.getDate() !== Number(day)) return '';
  return `${year}-${month}-${day}`;
}

function inferGender(value: string): Gender {
  const normalized = normalizeIdNumber(value);
  if (!/^\d{17}[\dX]$/.test(normalized)) return 'UNKNOWN';
  return Number(normalized.charAt(16)) % 2 === 0 ? 'FEMALE' : 'MALE';
}

function onIdTypeChange() {
  if (patientForm.idType === 'ID_CARD') {
    updateIdCardFields();
    scheduleAutoSearch();
  }
  invalidatePatientIfCertificateChanged();
}

function onCertificateInput(value: string | number) {
  patientForm.idNumber = normalizeIdNumber(String(value));
  if (patientForm.idType === 'ID_CARD') {
    updateIdCardFields();
    scheduleAutoSearch();
  }
  invalidatePatientIfCertificateChanged();
}

function updateIdCardFields() {
  const birthDate = inferBirthDate(patientForm.idNumber);
  if (birthDate) patientForm.birthDate = birthDate;
  patientForm.gender = inferGender(patientForm.idNumber);
}

function scheduleAutoSearch() {
  if (!isValidIdCard(patientForm.idNumber)) return;
  window.clearTimeout(autoSearchTimer);
  autoSearchTimer = window.setTimeout(() => {
    searchPatientWhenIdCard(true);
  }, 350);
}

async function searchPatientWhenIdCard(silent = false) {
  if (patientForm.idType !== 'ID_CARD' || !isValidIdCard(patientForm.idNumber)) return;
  const idNumber = normalizeIdNumber(patientForm.idNumber);
  if (silent && lastAutoSearchNumber.value === idNumber) return;
  lastAutoSearchNumber.value = idNumber;
  const list = await searchPatientByIdNumber(idNumber);
  const found = list[0];
  if (found) {
    selectPatient(found);
    if (!silent) ElMessage.success('已找到已有就诊人');
  } else if (!silent) {
    ElMessage.warning('未找到就诊人，可确认后建档');
  }
}

function certificateMatchesPatient(profile: PatientProfile) {
  return profile.idType === patientForm.idType && normalizeIdNumber(profile.idNumber ?? '') === normalizeIdNumber(patientForm.idNumber);
}

function invalidatePatientIfCertificateChanged() {
  if (patient.value && !certificateMatchesPatient(patient.value)) {
    patient.value = undefined;
  }
}

function fillFormFromPatient(profile: PatientProfile) {
  patientForm.idType = profile.idType ?? patientForm.idType;
  patientForm.idNumber = normalizeIdNumber(profile.idNumber ?? patientForm.idNumber);
  patientForm.name = profile.name ?? patientForm.name;
  patientForm.gender = profile.gender ?? patientForm.gender;
  patientForm.birthDate = profile.birthDate ?? patientForm.birthDate;
  patientForm.phone = profile.phone ?? patientForm.phone;
}

function selectPatient(profile: PatientProfile) {
  patient.value = profile;
  fillFormFromPatient(profile);
}

async function searchPatient() {
  if (patientForm.idType !== 'ID_CARD') {
    ElMessage.info('非身份证证件会在确认就诊人时自动去重');
    return;
  }
  if (!isValidIdCard(patientForm.idNumber)) {
    ElMessage.warning('请输入有效身份证号');
    return;
  }
  searchingPatient.value = true;
  try {
    await searchPatientWhenIdCard(false);
  } catch (error) {
    ElMessage.error(errorMessage(error, '查询就诊人失败'));
  } finally {
    searchingPatient.value = false;
  }
}

async function ensurePatientProfile() {
  if (!canConfirmPatient.value) throw new Error('请完整填写就诊人信息');
  if (patient.value && certificateMatchesPatient(patient.value)) return patient.value;
  if (patientForm.idType === 'ID_CARD') {
    const list = await searchPatientByIdNumber(normalizeIdNumber(patientForm.idNumber));
    if (list[0]) {
      selectPatient(list[0]);
      return list[0];
    }
  }
  const profile = await createOfflinePatient({
    idType: patientForm.idType,
    idNumber: normalizeIdNumber(patientForm.idNumber),
    name: patientForm.name.trim(),
    phone: patientForm.phone.trim() || undefined,
    gender: patientForm.gender,
    birthDate: patientForm.birthDate
  });
  selectPatient(profile);
  return profile;
}

async function confirmPatient() {
  savingPatient.value = true;
  try {
    const beforeId = currentPatientId.value;
    const profile = await ensurePatientProfile();
    ElMessage.success(beforeId && beforeId === patientProfileId(profile) ? '就诊人已确认' : '就诊人档案已确认');
  } catch (error) {
    ElMessage.error(errorMessage(error, '确认就诊人失败'));
  } finally {
    savingPatient.value = false;
  }
}

async function register() {
  registering.value = true;
  try {
    const profile = await ensurePatientProfile();
    const option = selectedScheduleOption.value;
    if (!option) throw new Error('请选择未来可用号源');
    if (option.slot.available <= 0) throw new Error('该号源已满，请刷新后重新选择');
    if (!isFutureScheduleSlot(option)) throw new Error('该号源已过期，请刷新后重新选择');
    if (!isRegistrationDepartment(option.schedule.departmentId)) throw new Error('该科室不支持窗口挂号');
    const department = departments.value.find(item => item.id === option.schedule.departmentId);
    lastAppointment.value = await createOfflineAppointment({
      scheduleId: option.slot.id,
      patientId: patientProfileId(profile),
      patientName: profile.name,
      doctorId: option.schedule.doctorId,
      doctorName: option.schedule.doctorName,
      departmentId: option.schedule.departmentId,
      departmentName: department?.name ?? '',
      visitDate: option.schedule.workDate,
      period: option.schedule.period,
      startTime: option.slot.startTime.slice(0, 5),
      riskLevel: 'LOW',
      triageSummary: '窗口线下挂号',
      registrationFee: registrationFee(option.schedule.doctorId)
    });
    ElMessage.success('挂号并收款成功');
    await loadSchedules();
  } catch (error) {
    ElMessage.error(errorMessage(error, '线下挂号失败'));
  } finally {
    registering.value = false;
  }
}

async function refund(row: Appointment) {
  try {
    await ElMessageBox.confirm(`确认退号并处理退款？${row.businessNo}`, '退号确认', { type: 'warning' });
    await cancelAppointment(row.id);
    ElMessage.success('退号成功，退款记录已生成');
    await Promise.all([loadSchedules(), loadAppointmentRecordsPage()]);
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(errorMessage(error, '退号失败'));
  }
}

async function refundDrug(row: DrugReturnOrder) {
  refundingReturnId.value = row.id;
  try {
    await ElMessageBox.confirm(`确认完成退药退费？${row.returnNo}`, '退药退费确认', { type: 'warning' });
    await refundDrugReturn({
      returnId: row.id,
      prescriptionId: row.prescriptionId,
      patientId: row.patientId,
      amount: row.totalAmount,
      reason: `退药单 ${row.returnNo}`
    });
    ElMessage.success('退费完成，退药状态已同步');
    await loadDrugReturnRefundPage();
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(errorMessage(error, '退药退费失败'));
  } finally {
    refundingReturnId.value = '';
  }
}

function printRegistrationSlip(row: Appointment) {
  printAppointment.value = row;
  printTime.value = new Date().toLocaleString('zh-CN');
  nextTick(() => window.print());
}

function canRefundAppointment(row: Appointment) {
  return !['CANCELLED', 'FINISHED', 'IN_VISIT'].includes(row.status);
}

function appointmentPatientIdNumber(row: Appointment) {
  return patientProfileMap.value.get(row.patientId)?.idNumber || '-';
}

function appointmentRoomName(row: Appointment) {
  const slotId = row.slotId || row.scheduleId || '';
  return slotRoomNameMap.value.get(slotId) || doctorMap.value.get(row.doctorId)?.roomName || '-';
}

function summarize(key: FeeType, label: string) {
  const items = pendingItems.value.filter(item => item.feeType === key);
  return {
    key,
    label,
    count: items.length,
    amount: items.reduce((sum, item) => sum + item.amount, 0)
  };
}

function scheduleLabel(item: ScheduleOption) {
  const dept = departments.value.find(department => department.id === item.schedule.departmentId)?.name ?? '';
  return `${dept} · ${item.schedule.doctorName} · ${item.schedule.workDate} ${item.schedule.period} ${item.slot.startTime.slice(0, 5)} · ￥${registrationFeeText(item.schedule.doctorId)} · 医保参考￥${amountText(insuranceAmount('REGISTRATION', registrationFee(item.schedule.doctorId)))} · 剩余 ${item.slot.available}`;
}

function isSeniorDoctorTitle(title: string) {
  return /主任|高级|专家/.test(title);
}

function registrationFee(doctorId: string) {
  const doctor = doctorMap.value.get(doctorId);
  return isSeniorDoctorTitle(doctor?.title || '') ? 40 : 15;
}

function registrationFeeText(doctorId: string) {
  return amountText(registrationFee(doctorId));
}

function insuranceAmount(feeType: FeeType, amount: number) {
  const ratio = feeType === 'REGISTRATION' ? 0.7 : 0.6;
  const value = Math.max(0.01, Number(amount ?? 0) * ratio);
  return Math.round(value * 100) / 100;
}

function normalizeStartTime(value: Appointment['startTime']) {
  return typeof value === 'string' ? value.slice(0, 5) : '';
}

function feeTypeLabel(type: FeeType | MedicalOrder['orderType']) {
  return {
    REGISTRATION: '挂号费',
    DRUG: '药费',
    CHECK: '检查费',
    LAB: '检验费',
    DISPOSAL: '处置费'
  }[type] ?? type;
}

function feeTagType(type: FeeType): '' | 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  if (type === 'DRUG') return 'success';
  if (type === 'CHECK') return 'primary';
  if (type === 'LAB') return 'warning';
  if (type === 'DISPOSAL') return 'info';
  return 'danger';
}

function businessTypeLabel(type: BusinessType) {
  return {
    APPOINTMENT: '挂号费',
    MEDICAL_ORDER: '医技费用',
    PRESCRIPTION: '药费'
  }[type];
}

function paymentTagType(status: string): '' | 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  if (status === 'PAID') return 'success';
  if (status === 'PENDING') return 'warning';
  if (status === 'FAILED') return 'danger';
  return 'info';
}

function appointmentTagType(appointment: Pick<Appointment, 'status' | 'paymentStatus'>): '' | 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  if (appointment.paymentStatus === 'REFUNDED') return 'info';
  if (appointment.status === 'PENDING_PAYMENT') return 'warning';
  if (appointment.status === 'CANCELLED') return 'info';
  if (appointment.status === 'FINISHED') return 'success';
  if (appointment.status === 'WAITING') return 'primary';
  if (appointment.status === 'CALLED') return 'primary';
  if (appointment.status === 'IN_VISIT') return 'danger';
  if (appointment.status === 'REVISIT_WAITING') return 'success';
  return 'info';
}

function paymentOrderStatusLabel(status: string) {
  return {
    PENDING: '待支付',
    PAID: '已支付',
    FAILED: '支付失败',
    CANCELLED: '已取消',
    REFUNDED: '已退款'
  }[status] ?? status;
}

function paymentMethodLabel(method?: string) {
  return {
    WECHAT: '微信支付',
    WECHAT_TEST: '微信支付',
    ALIPAY: '支付宝',
    ALIPAY_TEST: '支付宝',
    MEDICAL_INSURANCE: '医保卡支付',
    MEDICAL_INSURANCE_TEST: '医保卡支付',
    SIMULATED: '模拟支付',
    OFFLINE_WINDOW: '窗口收费',
    CASH: '现金',
    CARD: '银行卡'
  }[method ?? ''] ?? (method || '-');
}

function isInsurancePayment(item: PaymentOrder) {
  return item.paymentMethod?.includes('MEDICAL_INSURANCE') ?? false;
}

function urgencyLabel(value: string) {
  return value === 'EMERGENCY' ? '急诊' : '常规';
}

function prescriptionDescription(item: Prescription) {
  const drugs = (item.items ?? []).slice(0, 3).map(drug => `${drug.drugName}×${drug.quantity}`).join('、');
  return drugs || item.diagnosis || '处方药费';
}

function medicalOrderExecutor(businessId: string) {
  const order = medicalOrderMap.value.get(businessId);
  if (!order?.roomName) return '';
  return order.roomLocation ? `${order.roomName} · ${order.roomLocation}` : order.roomName;
}

function paymentRecordTitle(item: PaymentOrder) {
  if (item.businessType === 'APPOINTMENT') {
    const appointment = appointmentMap.value.get(item.businessId);
    return appointment ? `${appointment.departmentName} · ${appointment.doctorName}` : '挂号费';
  }
  if (item.businessType === 'MEDICAL_ORDER') {
    return medicalOrderMap.value.get(item.businessId)?.itemName ?? '医技费用';
  }
  return prescriptionMap.value.get(item.businessId)?.prescriptionNo ?? '处方药费';
}

function paymentRecordPatientName(item: PaymentOrder) {
  if (item.businessType === 'APPOINTMENT') return appointmentMap.value.get(item.businessId)?.patientName ?? '-';
  if (item.businessType === 'MEDICAL_ORDER') return medicalOrderMap.value.get(item.businessId)?.patientName ?? '-';
  return prescriptionMap.value.get(item.businessId)?.patientName ?? '-';
}

function paymentRecordIdNumber(item: PaymentOrder) {
  return patientProfileMap.value.get(item.patientId)?.idNumber || '-';
}

function amountText(value: number) {
  return Number(value ?? 0).toFixed(2);
}

function formatDateTime(value?: string) {
  if (!value) return '-';
  return String(value).replace('T', ' ').slice(0, 19);
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
  ElMessage.warning(errorMessage(error, fallback));
}

function redirectToLogin() {
  if (authRedirecting.value) return;
  authRedirecting.value = true;
  auth.signOut();
  ElMessage.error('登录已过期，请重新登录');
  router.replace('/login');
}

function unwrap<T>(result: PromiseSettledResult<T>, fallback: T, label: string) {
  if (result.status === 'fulfilled') return result.value;
  if (isUnauthorized(result.reason)) {
    redirectToLogin();
    return fallback;
  }
  ElMessage.warning(`${label}加载失败`);
  return fallback;
}

function logout() {
  auth.signOut();
  router.push('/login');
}

watch(scheduleOptions, syncSelectedSlot);

watch(() => qrDialog.visible, (visible) => {
  if (!visible) stopQrStatusPolling();
});

watch(qrScanUrl, () => {
  void loadQrSvg();
});

watch(registrationDepartments, (options) => {
  if (selectedDepartmentId.value && !options.some(item => item.id === selectedDepartmentId.value)) {
    selectedDepartmentId.value = '';
  }
});

watch(doctorOptions, (options) => {
  if (selectedDoctorId.value && !options.some(item => item.id === selectedDoctorId.value)) {
    selectedDoctorId.value = '';
  }
});

onMounted(async () => {
  nowTimer = window.setInterval(() => {
    nowTimestamp.value = Date.now();
  }, 60_000);
  await loadCurrentPageData();
});

onBeforeUnmount(() => {
  if (nowTimer) window.clearInterval(nowTimer);
  if (autoSearchTimer) window.clearTimeout(autoSearchTimer);
  stopQrStatusPolling();
});
</script>

<style scoped>
.cashier {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
  font-family: Inter, "Microsoft YaHei", system-ui, sans-serif;
}

.cashier-nav {
  height: 56px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #0cbdcc 0%, #0899a5 100%);
  color: #fff;
  box-shadow: 0 2px 8px rgb(0 0 0 / 18%);
  z-index: 10;
}

.cashier-nav__brand,
.cashier-nav__right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cashier-nav__logo {
  width: 30px;
  height: 30px;
  border-radius: 7px;
  display: grid;
  place-items: center;
  background: #fff;
  color: #0899a5;
  font-weight: 900;
}

.cashier-nav__title {
  font-size: 16px;
  font-weight: 700;
}

.cashier-nav__right {
  gap: 20px;
  font-size: 13px;
  font-family: inherit;
  line-height: 1;
}

.cashier-nav__right > span,
.cashier-nav__right :deep(.el-button) {
  height: 32px;
  display: inline-flex;
  align-items: center;
  font: inherit;
  line-height: 1;
}

.nav-logout {
  color: rgb(255 255 255 / 88%);
}

.cashier-body {
  height: calc(100vh - 56px);
  display: flex;
  overflow: hidden;
}

.cashier-sidebar {
  width: clamp(140px, 16vw, 180px);
  flex-shrink: 0;
  padding: 12px;
  background: #fff;
  border-right: 1px solid #e5e7eb;
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

.cashier-main {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding: 16px;
}

.work-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.page-head,
.query-bar,
.registration-footer,
.form-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.page-head h1 {
  margin: 0 0 4px;
  font-size: 22px;
  letter-spacing: 0;
}

.page-head p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.query-bar {
  justify-content: flex-start;
  flex-wrap: wrap;
  padding: 12px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e5e7eb;
}

.head-search {
  width: 260px;
}

.query-bar .head-search {
  flex: 0 1 260px;
  min-width: 220px;
}

.work-page :deep(.el-table) {
  width: 100%;
}

.stat-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.stat-box {
  padding: 12px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e5e7eb;
}

.stat-box span,
.registration-footer span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.stat-box strong,
.registration-footer strong {
  display: block;
  margin-top: 5px;
  color: #0f766e;
  font-size: 18px;
}

.stat-box em {
  color: #94a3b8;
  font-size: 12px;
  font-style: normal;
}

.registration-footer em {
  display: block;
  margin-top: 4px;
  color: #15803d;
  font-size: 12px;
  font-style: normal;
  font-weight: 700;
}

.registration-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.form-span-2 {
  grid-column: span 2;
}

.patient-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.form-actions {
  justify-content: flex-end;
  margin-top: 14px;
}

.patient-card {
  display: flex;
  gap: 12px;
  padding: 12px;
  margin-top: 14px;
  border: 1px solid #a8e8ec;
  border-radius: 8px;
  background: #f0f9fa;
}

.patient-card__avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  background: #ccf2f4;
  color: #0899a5;
  font-size: 18px;
  font-weight: 700;
}

.patient-card__info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 12px;
  color: #64748b;
}

.patient-card__info strong {
  color: #1f2937;
  font-size: 16px;
}

.registration-footer {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #e5e7eb;
}

.register-result {
  padding-bottom: 0;
}

.amount {
  color: #b45309;
}

.insurance-amount {
  color: #15803d;
  font-weight: 700;
}

.muted-cell {
  color: #94a3b8;
}

.qr-dialog {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.qr-meta {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.qr-meta span {
  color: #64748b;
  font-size: 13px;
}

.qr-meta em {
  color: #b45309;
  font-size: 22px;
  font-weight: 700;
  font-style: normal;
}

.qr-meta__insurance {
  color: #15803d !important;
  font-weight: 700;
}

.qr-channel-picker {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.qr-channel-button {
  border: 1px solid #dbe6ef;
  border-radius: 14px;
  padding: 10px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: #f8fbff;
  color: #334155;
  cursor: pointer;
  transition: all 0.2s ease;
}

.qr-channel-button strong {
  font-size: 13px;
}

.qr-channel-button span {
  font-size: 12px;
  color: #64748b;
}

.qr-channel-button:hover:not(:disabled) {
  border-color: #8ec5ff;
  transform: translateY(-1px);
}

.qr-channel-button:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.qr-channel-button--active {
  border-color: #0ea5e9;
  background: linear-gradient(180deg, #f0f9ff 0%, #e0f2fe 100%);
  box-shadow: 0 10px 22px rgb(14 165 233 / 14%);
}

.qr-card {
  width: 100%;
  padding: 14px;
  border-radius: 22px;
  background: linear-gradient(180deg, #ffffff 0%, #f7fbff 100%);
  border: 1px solid #d6e4f0;
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 80%);
}

.qr-card[data-channel='WECHAT'] {
  border-color: #b8e6c6;
  background: linear-gradient(180deg, #ffffff 0%, #f3fff6 100%);
}

.qr-card[data-channel='ALIPAY'] {
  border-color: #b5dcff;
  background: linear-gradient(180deg, #ffffff 0%, #f2f9ff 100%);
}

.qr-card[data-channel='MEDICAL_INSURANCE'] {
  border-color: #ffd9a8;
  background: linear-gradient(180deg, #ffffff 0%, #fff8ef 100%);
}

.qr-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 13px;
  color: #475569;
}

.qr-card__header em {
  font-style: normal;
  color: #0f766e;
}

.payment-qr {
  width: 100%;
  max-width: 260px;
  min-height: 260px;
  display: block;
  margin: 0 auto;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 12px 30px rgb(15 23 42 / 10%);
  overflow: hidden;
}

.payment-qr :deep(svg) {
  width: 100%;
  height: auto;
  display: block;
}

.payment-qr--loading {
  display: grid;
  place-items: center;
  color: #64748b;
  font-size: 13px;
}

.qr-hint {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.65;
  text-align: center;
}

.qr-hint--warn {
  color: #b45309;
}

.qr-footer-status {
  margin-right: auto;
  color: #475569;
  font-size: 13px;
}

.full {
  width: 100%;
}

.print-area {
  display: none;
}

@media print {
  .cashier-nav,
  .cashier-body,
  .el-overlay-container {
    display: none !important;
  }

  .cashier {
    background: #fff;
  }

  .print-area {
    display: block;
    padding: 0;
  }

  .print-slip {
    width: 720px;
    margin: 0 auto;
    padding: 24px;
    color: #111;
    font-family: "SimSun", "Microsoft YaHei", sans-serif;
  }

  .print-slip h2 {
    margin: 0;
    text-align: center;
    font-size: 22px;
    letter-spacing: 3px;
  }

  .print-rule {
    margin: 16px 0;
    border-top: 3px double #333;
  }

  .print-grid {
    display: grid;
    grid-template-columns: 100px 1fr 100px 1fr;
    gap: 12px 16px;
    font-size: 15px;
  }

  .print-grid span {
    color: #555;
  }

  .print-grid strong {
    border-bottom: 1px solid #aaa;
    padding-bottom: 2px;
  }

  .print-note {
    margin-top: 28px;
    color: #555;
    font-size: 13px;
  }
}

@media (max-width: 1100px) {
  .cashier-body {
    height: auto;
    min-height: calc(100vh - 56px);
    overflow: visible;
  }

  .cashier-main {
    overflow: visible;
  }

  .stat-strip,
  .registration-layout {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 820px) {
  .cashier-nav {
    height: auto;
    min-height: 52px;
    align-items: flex-start;
    flex-direction: column;
    padding: 10px 14px;
  }

  .cashier-body {
    flex-direction: column;
  }

  .cashier-sidebar {
    width: 100%;
    display: flex;
    overflow-x: auto;
    border-right: none;
    border-bottom: 1px solid #e5e7eb;
  }

  .nav-item {
    width: auto;
    min-width: 110px;
    margin-right: 6px;
    margin-bottom: 0;
  }

  .stat-strip,
  .registration-layout,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .form-span-2 {
    grid-column: span 1;
  }

  .query-bar .head-search {
    width: 100%;
    min-width: 0;
    flex-basis: 100%;
  }
}
</style>




