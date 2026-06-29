<template>
  <div class="admin-wks">
    <header class="admin-nav">
      <div class="admin-nav__brand">
        <span class="admin-nav__logo">+</span>
        <span class="admin-nav__title">管理员工作台</span>
      </div>
      <div class="admin-nav__right">
        <span>{{ auth.user?.name }} 管理员</span>
        <span>{{ today }} {{ dayOfWeek }}</span>
        <el-button size="small" text class="nav-logout" @click="logout">退出</el-button>
      </div>
    </header>

    <div class="admin-body">
      <aside class="admin-sidebar">
        <div class="sidebar-hdr">
          <span>功能模块</span>
          <el-button :loading="pageLoading" size="small" text @click="refreshAll">刷新</el-button>
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
          <span>医生 {{ doctors.length }}</span>
          <span>排班 {{ schedules.length }}</span>
        </div>
      </aside>

      <main class="admin-main" v-loading="pageLoading">
        <section v-show="currentPage === 'overview'" class="work-page">
          <div class="page-head">
            <div>
              <h1>运营概览</h1>
              <p>查看今日门诊、医生出诊和科室负载，快速判断排班压力。</p>
            </div>
            <el-button :loading="pageLoading" @click="refreshAll">刷新数据</el-button>
          </div>

          <div class="stat-strip">
            <div class="stat-box">
              <span>今日挂号</span>
              <strong>{{ overview?.todayAppointments ?? 0 }}</strong>
              <em>全部渠道</em>
            </div>
            <div class="stat-box">
              <span>待接诊</span>
              <strong>{{ overview?.waitingVisits ?? 0 }}</strong>
              <em>候诊队列</em>
            </div>
            <div class="stat-box">
              <span>出诊医生</span>
              <strong>{{ overview?.activeDoctors ?? 0 }}</strong>
              <em>已发布排班</em>
            </div>
            <div class="stat-box">
              <span>AI 问诊</span>
              <strong>{{ overview?.aiTriageCount ?? 0 }}</strong>
              <em>辅助记录</em>
            </div>
          </div>

          <div class="overview-layout">
            <section class="work-card">
              <div class="card-head">
                <div>
                  <h2>科室负载</h2>
                  <p>按科室汇总当前挂号量。</p>
                </div>
              </div>
              <el-table :data="overview?.departmentLoads ?? []" empty-text="暂无科室负载">
                <el-table-column prop="name" label="科室" />
                <el-table-column prop="value" label="挂号量" width="120" />
              </el-table>
            </section>

            <section class="work-card">
              <div class="card-head">
                <div>
                  <h2>待处理事项</h2>
                  <p>进入对应模块完成确认或维护。</p>
                </div>
              </div>
              <div class="task-list">
                <button class="task-item" @click="currentPage = 'aiSchedule'">
                  <span>AI 排班建议</span>
                  <strong>{{ pendingSuggestions.length }}</strong>
                </button>
                <button class="task-item" @click="currentPage = 'manualSchedule'">
                  <span>当前排班</span>
                  <strong>{{ schedules.length }}</strong>
                </button>
                <button class="task-item" @click="currentPage = 'accounts'">
                  <span>医生账号</span>
                  <strong>{{ allStaffAccounts.length }}</strong>
                </button>
              </div>
            </section>
          </div>
        </section>

        <section v-show="currentPage === 'aiSchedule'" class="work-page">
          <div class="page-head">
            <div>
              <h1>AI 智能排班</h1>
              <p>输入科室需求、请假、手术和高峰规则，生成待管理员确认的排班建议。</p>
            </div>
            <el-button
              type="success"
              :disabled="pendingSuggestions.length === 0"
              :loading="publishLoading"
              @click="publishPendingSuggestions"
            >
              批量确认发布
            </el-button>
          </div>

          <div class="schedule-ai-layout">
            <section class="work-card">
              <div class="card-head">
                <div>
                  <h2>需求参数</h2>
                  <p>{{ aiCandidates.length }} 名候选医生，{{ aiDemands.length }} 条排班需求。</p>
                </div>
              </div>
              <el-form label-position="top" class="compact-form">
                <el-form-item label="排班科室">
                  <el-select v-model="aiForm.departmentId" class="full" filterable>
                    <el-option
                      v-for="department in departments"
                      :key="department.id"
                      :label="department.name"
                      :value="department.id"
                    />
                  </el-select>
                </el-form-item>
                <div class="form-grid">
                  <el-form-item label="开始日期">
                    <el-date-picker v-model="aiForm.startDate" class="full" type="date" value-format="YYYY-MM-DD" />
                  </el-form-item>
                  <el-form-item label="连续天数">
                    <el-input-number v-model="aiForm.days" class="full-number" :min="1" :max="14" />
                  </el-form-item>
                </div>
                <el-form-item label="排班时段">
                  <el-checkbox-group v-model="aiForm.periods">
                    <el-checkbox-button label="上午" />
                    <el-checkbox-button label="下午" />
                    <el-checkbox-button label="全天" />
                  </el-checkbox-group>
                </el-form-item>
                <div class="form-grid">
                  <el-form-item label="基础预计挂号量">
                    <el-input-number v-model="aiForm.baseVisits" class="full-number" :min="1" :max="100" />
                  </el-form-item>
                  <el-form-item label="风险等级">
                    <el-select v-model="aiForm.riskLevel" class="full">
                      <el-option label="低" value="LOW" />
                      <el-option label="中" value="MEDIUM" />
                      <el-option label="高" value="HIGH" />
                    </el-select>
                  </el-form-item>
                </div>
                <div class="peak-row">
                  <el-checkbox v-model="aiForm.weekendPeak">周末高峰</el-checkbox>
                  <el-slider v-model="aiForm.weekendIncrease" :min="0" :max="80" :step="5" />
                  <span>{{ aiForm.weekendIncrease }}%</span>
                </div>
                <div class="peak-row">
                  <el-checkbox v-model="aiForm.morningPeak">上午高峰</el-checkbox>
                  <el-slider v-model="aiForm.morningIncrease" :min="0" :max="80" :step="5" />
                  <span>{{ aiForm.morningIncrease }}%</span>
                </div>
                <el-button type="primary" class="full" :loading="suggestionLoading" @click="generateSuggestions">
                  生成 AI 排班建议
                </el-button>
              </el-form>
            </section>

            <section class="work-card">
              <div class="card-head">
                <div>
                  <h2>医生可用性</h2>
                  <p>请假日和手术日会作为不可排班日期传给 AI。</p>
                </div>
              </div>
              <el-form label-position="top" class="compact-form">
                <el-form-item label="医生">
                  <el-select v-model="selectedDoctorId" class="full" filterable>
                    <el-option
                      v-for="doctor in aiDoctors"
                      :key="doctor.id"
                      :label="`${doctor.employeeNo} / ${doctor.name}`"
                      :value="doctor.id"
                    />
                  </el-select>
                </el-form-item>
                <template v-if="selectedDoctorId && availability[selectedDoctorId]">
                  <el-form-item label="每周可接诊容量">
                    <el-input-number
                      v-model="availability[selectedDoctorId].weeklyCapacity"
                      class="full-number"
                      :min="1"
                      :max="120"
                    />
                  </el-form-item>
                  <el-form-item label="请假日期">
                    <el-date-picker
                      v-model="availability[selectedDoctorId].leaveDates"
                      class="full"
                      type="dates"
                      value-format="YYYY-MM-DD"
                    />
                  </el-form-item>
                  <el-form-item label="手术安排日期">
                    <el-date-picker
                      v-model="availability[selectedDoctorId].surgeryDates"
                      class="full"
                      type="dates"
                      value-format="YYYY-MM-DD"
                    />
                  </el-form-item>
                </template>
              </el-form>
            </section>

            <section class="work-card suggestions-card">
              <div class="card-head">
                <div>
                  <h2>建议确认</h2>
                  <p>确认后写入正式排班并同步号源。</p>
                </div>
              </div>
              <el-table :data="suggestions" height="430" empty-text="暂无 AI 建议">
                <el-table-column prop="workDate" label="日期" width="112" />
                <el-table-column prop="period" label="时段" width="86" />
                <el-table-column prop="doctorName" label="医生" width="112" />
                <el-table-column label="科室" width="126">
                  <template #default="{ row }">{{ departmentName(row.departmentId) }}</template>
                </el-table-column>
                <el-table-column prop="capacity" label="号源" width="78" />
                <el-table-column prop="reason" label="AI 理由" min-width="220" show-overflow-tooltip />
                <el-table-column label="状态" width="96">
                  <template #default="{ row }">
                    <el-tag v-if="isSuggestionPublished(row.suggestionId)" type="success">已发布</el-tag>
                    <el-tag v-else type="warning">待确认</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="120" fixed="right">
                  <template #default="{ row }">
                    <el-button
                      type="primary"
                      link
                      :disabled="isSuggestionPublished(row.suggestionId)"
                      @click="publishSuggestion(row)"
                    >
                      确认发布
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </section>
          </div>
        </section>

        <section v-show="currentPage === 'manualSchedule'" class="work-page">
          <div class="page-head">
            <div>
              <h1>手动排班</h1>
              <p>查询、补录和停诊已发布排班。</p>
            </div>
            <el-button @click="loadSchedules">刷新排班</el-button>
          </div>

          <div class="query-bar">
            <el-select v-model="scheduleFilter.departmentId" clearable placeholder="全部科室" filterable>
              <el-option v-for="department in departments" :key="department.id" :label="department.name" :value="department.id" />
            </el-select>
            <el-select v-model="scheduleFilter.doctorId" clearable placeholder="全部医生" filterable>
              <el-option v-for="doctor in filteredScheduleDoctors" :key="doctor.id" :label="doctor.name" :value="doctor.id" />
            </el-select>
            <el-button type="primary" @click="loadSchedules">查询</el-button>
          </div>

          <section class="work-card">
            <el-table :data="schedules" empty-text="暂无排班">
              <el-table-column prop="doctorName" label="医生" width="120" />
              <el-table-column prop="workDate" label="日期" width="120" />
              <el-table-column prop="period" label="时段" width="90" />
              <el-table-column prop="capacity" label="号源" width="80" />
              <el-table-column prop="booked" label="已约" width="80" />
              <el-table-column prop="available" label="可约" width="80" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="scheduleStatusType(row.status)">{{ scheduleStatusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="row.status === 'PUBLISHED'" type="danger" link @click="stopSchedule(row)">
                    停诊
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </section>

          <section class="work-card">
            <div class="card-head">
              <div>
                <h2>新增排班</h2>
                <p>用于临时补班或快速录入常规排班。</p>
              </div>
            </div>
            <el-form label-position="top" class="inline-form">
              <el-form-item label="医生">
                <el-select v-model="manualScheduleForm.doctorId" class="full" filterable @change="syncManualDoctor">
                  <el-option
                    v-for="doctor in doctors"
                    :key="doctor.id"
                    :label="`${doctor.employeeNo} / ${doctor.name} / ${doctor.departmentName}`"
                    :value="doctor.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="日期">
                <el-date-picker v-model="manualScheduleForm.workDate" class="full" type="date" value-format="YYYY-MM-DD" />
              </el-form-item>
              <el-form-item label="时段">
                <el-select v-model="manualScheduleForm.period" class="full">
                  <el-option label="上午" value="上午" />
                  <el-option label="下午" value="下午" />
                  <el-option label="全天" value="全天" />
                </el-select>
              </el-form-item>
              <el-form-item label="号源">
                <el-input-number v-model="manualScheduleForm.capacity" class="full-number" :min="1" :max="100" />
              </el-form-item>
              <el-button type="primary" :loading="scheduleSaving" @click="submitManualSchedule">保存排班</el-button>
            </el-form>
          </section>
        </section>

        <section v-show="currentPage === 'doctorProfile'" class="work-page">
          <div class="page-head">
            <div>
              <h1>医生档案</h1>
              <p>维护可参与门诊排班的医生目录，可同步创建登录账号。</p>
            </div>
            <el-input v-model.trim="doctorKeyword" class="head-search" clearable placeholder="搜索姓名/工号/科室" />
          </div>

          <div class="doctor-layout">
            <section class="work-card">
              <div class="card-head">
                <div>
                  <h2>新增医生</h2>
                  <p>医生档案用于排班、挂号和患者端展示。</p>
                </div>
              </div>
              <el-form label-position="top" class="compact-form">
                <div class="form-grid">
                  <el-form-item label="工号">
                    <el-input v-model.trim="doctorForm.employeeNo" placeholder="如 00010009" />
                  </el-form-item>
                  <el-form-item label="姓名">
                    <el-input v-model.trim="doctorForm.name" placeholder="医生姓名" />
                  </el-form-item>
                </div>
                <div class="form-grid">
                  <el-form-item label="职称">
                    <el-input v-model.trim="doctorForm.title" />
                  </el-form-item>
                  <el-form-item label="科室">
                    <el-select v-model="doctorForm.departmentId" class="full" filterable>
                      <el-option
                        v-for="department in departments"
                        :key="department.id"
                        :label="department.name"
                        :value="department.id"
                      />
                    </el-select>
                  </el-form-item>
                </div>
                <el-form-item label="专长">
                  <el-input v-model.trim="doctorForm.specialty" placeholder="如 头痛、眩晕、癫痫" />
                </el-form-item>
                <el-form-item>
                  <el-checkbox v-model="doctorForm.createAccount">同步创建登录账号</el-checkbox>
                </el-form-item>
                <template v-if="doctorForm.createAccount">
                  <div class="form-grid">
                    <el-form-item label="手机号">
                      <el-input v-model.trim="doctorForm.phone" placeholder="可选" />
                    </el-form-item>
                    <el-form-item label="初始密码">
                      <el-input v-model="doctorForm.password" type="password" show-password />
                    </el-form-item>
                  </div>
                </template>
                <el-button type="primary" class="full" :loading="doctorSaving" @click="submitDoctor">
                  保存医生
                </el-button>
              </el-form>
            </section>

            <section class="work-card">
              <el-table :data="filteredDoctors" height="520" empty-text="暂无医生">
                <el-table-column prop="employeeNo" label="工号" width="110" />
                <el-table-column prop="name" label="姓名" width="100" />
                <el-table-column prop="title" label="职称" width="112" />
                <el-table-column prop="departmentName" label="科室" width="130" />
                <el-table-column prop="specialty" label="专长" min-width="180" show-overflow-tooltip />
                <el-table-column label="账号" width="96">
                  <template #default="{ row }">
                    <el-tag :type="accountByEmployeeNo(row.employeeNo) ? 'success' : 'info'">
                      {{ accountByEmployeeNo(row.employeeNo) ? '已创建' : '未创建' }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </section>
          </div>
        </section>

        <section v-show="currentPage === 'accounts'" class="work-page">
          <div class="page-head">
            <div>
              <h1>医生账号</h1>
              <p>管理医生登录状态，必要时重置初始密码。</p>
            </div>
            <el-button @click="loadAccounts">刷新账号</el-button>
          </div>

          <div class="query-bar">
            <el-select v-model="accountFilter.role" clearable placeholder="全部医生角色" @change="loadAccounts">
              <el-option v-for="role in accountRoleOptions" :key="role.value" :label="role.label" :value="role.value" />
            </el-select>
          </div>

          <section class="work-card">
            <el-table :data="staffAccounts" empty-text="暂无账号">
              <el-table-column prop="employeeNo" label="工号" width="120" />
              <el-table-column prop="name" label="姓名" width="120" />
              <el-table-column label="角色" width="140">
                <template #default="{ row }">{{ roleLabel(row.role) }}</template>
              </el-table-column>
              <el-table-column prop="phone" label="手机号" width="140">
                <template #default="{ row }">{{ row.phone || '未填写' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-switch
                    v-model="row.active"
                    active-text="启用"
                    inactive-text="停用"
                    inline-prompt
                    @change="toggleAccount(row)"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="创建时间" min-width="170">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button type="primary" link @click="resetPassword(row)">重置密码</el-button>
                </template>
              </el-table-column>
            </el-table>
          </section>
        </section>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useAuthStore } from '../../store/auth';
import {
  createDoctor,
  createSchedule,
  getAiScheduleSuggestions,
  getDepartments,
  getDoctors,
  getSchedules,
  publishAiScheduleSuggestion,
  suspendSchedule,
  type AiDoctorCandidate,
  type AiScheduleDemand,
  type AiScheduleResponse,
  type AiScheduleSuggestion,
  type Department,
  type Doctor,
  type Schedule
} from '../../api/doctor';
import { getDashboardOverview, type DashboardOverview } from '../../api/dashboard';
import {
  createStaffAccount,
  getStaffAccounts,
  resetStaffAccountPassword,
  setStaffAccountActive,
  type StaffAccount
} from '../../api/auth';

type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';
type PageKey = 'overview' | 'aiSchedule' | 'manualSchedule' | 'doctorProfile' | 'accounts';

interface AvailabilitySettings {
  leaveDates: string[];
  surgeryDates: string[];
  weeklyCapacity: number;
}

const router = useRouter();
const auth = useAuthStore();

const currentPage = ref<PageKey>('overview');
const pageLoading = ref(false);
const suggestionLoading = ref(false);
const publishLoading = ref(false);
const scheduleSaving = ref(false);
const doctorSaving = ref(false);

const overview = ref<DashboardOverview | null>(null);
const departments = ref<Department[]>([]);
const doctors = ref<Doctor[]>([]);
const schedules = ref<Schedule[]>([]);
const staffAccounts = ref<StaffAccount[]>([]);
const allStaffAccounts = ref<StaffAccount[]>([]);
const aiResponse = ref<AiScheduleResponse | null>(null);
const publishedSuggestionIds = ref<string[]>([]);
const selectedDoctorId = ref('');
const doctorKeyword = ref('');

const availability = reactive<Record<string, AvailabilitySettings>>({});
const weekDays = ['日', '一', '二', '三', '四', '五', '六'];
const today = new Date().toLocaleDateString('zh-CN');
const dayOfWeek = `星期${weekDays[new Date().getDay()]}`;

const roleLabels: Record<string, string> = {
  OUTPATIENT_DOCTOR: '门诊医生',
  CHECK_DOCTOR: '检查医生',
  LAB_DOCTOR: '检验医生',
  DISPOSAL_DOCTOR: '处置医生',
  PHARMACY_DOCTOR: '药房医生'
};

const accountRoleOptions = [
  { label: '门诊医生', value: 'OUTPATIENT_DOCTOR' },
  { label: '检查医生', value: 'CHECK_DOCTOR' },
  { label: '检验医生', value: 'LAB_DOCTOR' },
  { label: '处置医生', value: 'DISPOSAL_DOCTOR' }
];

const aiForm = reactive({
  departmentId: '',
  startDate: todayIso(),
  days: 7,
  periods: ['上午', '下午'],
  baseVisits: 24,
  riskLevel: 'MEDIUM' as RiskLevel,
  weekendPeak: true,
  weekendIncrease: 35,
  morningPeak: true,
  morningIncrease: 25
});

const scheduleFilter = reactive({
  departmentId: '',
  doctorId: ''
});

const manualScheduleForm = reactive({
  doctorId: '',
  departmentId: '',
  workDate: todayIso(),
  period: '上午',
  capacity: 20
});

const doctorForm = reactive({
  employeeNo: '',
  name: '',
  title: '主治医师',
  departmentId: '',
  roleType: 'OUTPATIENT_DOCTOR',
  specialty: '',
  createAccount: true,
  phone: '',
  password: 'abc12345'
});

const accountFilter = reactive({
  role: 'OUTPATIENT_DOCTOR'
});

const departmentMap = computed(() => new Map(departments.value.map((item) => [item.id, item.name])));

const navItems = computed<Array<{ key: PageKey; label: string; badge?: number }>>(() => [
  { key: 'overview', label: '运营概览' },
  { key: 'aiSchedule', label: 'AI 智能排班', badge: pendingSuggestions.value.length || undefined },
  { key: 'manualSchedule', label: '手动排班', badge: schedules.value.length || undefined },
  { key: 'doctorProfile', label: '医生档案', badge: doctors.value.length || undefined },
  { key: 'accounts', label: '医生账号', badge: staffAccounts.value.length || undefined }
]);

const aiDoctors = computed(() => doctors.value.filter((doctor) => doctor.departmentId === aiForm.departmentId));

const filteredScheduleDoctors = computed(() =>
  doctors.value.filter((doctor) => !scheduleFilter.departmentId || doctor.departmentId === scheduleFilter.departmentId)
);

const filteredDoctors = computed(() => {
  const keyword = doctorKeyword.value.trim().toLowerCase();
  if (!keyword) return doctors.value;
  return doctors.value.filter((doctor) =>
    [doctor.employeeNo, doctor.name, doctor.departmentName, doctor.specialty]
      .filter(Boolean)
      .some((value) => value.toLowerCase().includes(keyword))
  );
});

const suggestions = computed(() => aiResponse.value?.suggestions ?? []);

const pendingSuggestions = computed(() =>
  suggestions.value.filter((suggestion) => !isSuggestionPublished(suggestion.suggestionId))
);

const aiCandidates = computed<AiDoctorCandidate[]>(() =>
  aiDoctors.value.map((doctor) => {
    const settings = ensureAvailability(doctor.id);
    return {
      doctorId: doctor.id,
      doctorName: doctor.name,
      departmentId: doctor.departmentId,
      specialty: doctor.specialty ?? '',
      weeklyCapacity: settings.weeklyCapacity,
      leaveDates: uniqueDates(settings.leaveDates),
      surgeryDates: uniqueDates(settings.surgeryDates)
    };
  })
);

const aiDemands = computed<AiScheduleDemand[]>(() => buildDemands());

watch(
  () => aiForm.departmentId,
  () => {
    const firstDoctor = aiDoctors.value[0];
    selectedDoctorId.value = firstDoctor?.id ?? '';
    if (selectedDoctorId.value) ensureAvailability(selectedDoctorId.value);
  }
);

watch(
  () => scheduleFilter.departmentId,
  () => {
    if (
      scheduleFilter.doctorId &&
      !filteredScheduleDoctors.value.some((doctor) => doctor.id === scheduleFilter.doctorId)
    ) {
      scheduleFilter.doctorId = '';
    }
  }
);

async function refreshAll() {
  pageLoading.value = true;
  try {
    const [overviewData, departmentData, doctorData, accountData, allAccountData] = await Promise.all([
      getDashboardOverview(),
      getDepartments(),
      getDoctors(),
      getStaffAccounts(accountFilter.role || undefined),
      getStaffAccounts()
    ]);
    overview.value = overviewData;
    departments.value = departmentData;
    doctors.value = doctorData;
    staffAccounts.value = accountData;
    allStaffAccounts.value = allAccountData;
    seedDefaults();
    await loadSchedules();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    pageLoading.value = false;
  }
}

async function loadSchedules() {
  schedules.value = await getSchedules({
    departmentId: scheduleFilter.departmentId || undefined,
    doctorId: scheduleFilter.doctorId || undefined
  });
}

async function loadAccounts() {
  try {
    const [accountData, allAccountData] = await Promise.all([
      getStaffAccounts(accountFilter.role || undefined),
      getStaffAccounts()
    ]);
    staffAccounts.value = accountData;
    allStaffAccounts.value = allAccountData;
  } catch (error) {
    ElMessage.error(errorMessage(error));
  }
}

function seedDefaults() {
  if (!aiForm.departmentId) {
    aiForm.departmentId = departments.value[0]?.id ?? '';
  }
  if (!doctorForm.departmentId) {
    doctorForm.departmentId = departments.value[0]?.id ?? '';
  }
  if (!manualScheduleForm.doctorId) {
    manualScheduleForm.doctorId = doctors.value[0]?.id ?? '';
    syncManualDoctor();
  }
  doctors.value.forEach((doctor) => ensureAvailability(doctor.id));
  selectedDoctorId.value = aiDoctors.value[0]?.id ?? doctors.value[0]?.id ?? '';
}

function buildDemands() {
  if (!aiForm.departmentId || !aiForm.startDate || aiForm.periods.length === 0) return [];
  const items: AiScheduleDemand[] = [];
  for (let day = 0; day < aiForm.days; day += 1) {
    const workDate = addDays(aiForm.startDate, day);
    for (const period of aiForm.periods) {
      let expectedVisits = aiForm.baseVisits;
      if (aiForm.weekendPeak && isWeekend(workDate)) {
        expectedVisits *= 1 + aiForm.weekendIncrease / 100;
      }
      if (aiForm.morningPeak && period === '上午') {
        expectedVisits *= 1 + aiForm.morningIncrease / 100;
      }
      items.push({
        departmentId: aiForm.departmentId,
        workDate,
        period,
        expectedVisits: Math.max(1, Math.round(expectedVisits)),
        riskLevel: aiForm.riskLevel
      });
    }
  }
  return items;
}

async function generateSuggestions() {
  if (!aiForm.departmentId) {
    ElMessage.warning('请先选择排班科室');
    return;
  }
  if (aiCandidates.value.length === 0) {
    ElMessage.warning('当前科室暂无候选医生');
    return;
  }
  if (aiDemands.value.length === 0) {
    ElMessage.warning('请补全排班日期和时段');
    return;
  }
  suggestionLoading.value = true;
  try {
    aiResponse.value = await getAiScheduleSuggestions({
      candidates: aiCandidates.value,
      demands: aiDemands.value
    });
    publishedSuggestionIds.value = [];
    if (suggestions.value.length === 0) {
      ElMessage.warning('AI 未生成可用建议，请检查医生请假/手术安排');
    } else {
      ElMessage.success(`已生成 ${suggestions.value.length} 条排班建议`);
    }
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    suggestionLoading.value = false;
  }
}

async function publishSuggestion(suggestion: AiScheduleSuggestion, silent = false) {
  if (isSuggestionPublished(suggestion.suggestionId)) return;
  if (!silent) {
    try {
      await ElMessageBox.confirm(
        `确认发布 ${suggestion.workDate} ${suggestion.period} ${suggestion.doctorName} 的排班？`,
        '确认发布',
        { type: 'warning' }
      );
    } catch {
      return;
    }
  }
  await publishAiScheduleSuggestion(suggestion.suggestionId, {
    ...suggestion,
    aiRecordId: aiResponse.value?.aiRecordId ?? null
  });
  publishedSuggestionIds.value = [...publishedSuggestionIds.value, suggestion.suggestionId];
  if (!silent) {
    ElMessage.success('排班已发布');
  }
  await loadSchedules();
}

async function publishPendingSuggestions() {
  if (pendingSuggestions.value.length === 0) return;
  try {
    await ElMessageBox.confirm(`确认发布 ${pendingSuggestions.value.length} 条 AI 排班建议？`, '批量确认', {
      type: 'warning'
    });
  } catch {
    return;
  }
  publishLoading.value = true;
  try {
    for (const suggestion of pendingSuggestions.value) {
      await publishSuggestion(suggestion, true);
    }
    ElMessage.success('AI 排班建议已发布');
    await loadSchedules();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    publishLoading.value = false;
  }
}

function syncManualDoctor() {
  const doctor = doctors.value.find((item) => item.id === manualScheduleForm.doctorId);
  manualScheduleForm.departmentId = doctor?.departmentId ?? '';
}

async function submitManualSchedule() {
  syncManualDoctor();
  if (!manualScheduleForm.doctorId || !manualScheduleForm.departmentId || !manualScheduleForm.workDate) {
    ElMessage.warning('请补全医生、科室和日期');
    return;
  }
  scheduleSaving.value = true;
  try {
    await createSchedule({
      doctorId: manualScheduleForm.doctorId,
      departmentId: manualScheduleForm.departmentId,
      workDate: manualScheduleForm.workDate,
      period: manualScheduleForm.period,
      capacity: manualScheduleForm.capacity
    });
    ElMessage.success('排班已保存');
    await loadSchedules();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    scheduleSaving.value = false;
  }
}

async function stopSchedule(schedule: Schedule) {
  try {
    await ElMessageBox.confirm(`确认停诊 ${schedule.workDate} ${schedule.period} ${schedule.doctorName}？`, '停诊确认', {
      type: 'warning'
    });
  } catch {
    return;
  }
  try {
    await suspendSchedule(schedule.id, '管理员停诊');
    ElMessage.success('已停诊');
    await loadSchedules();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  }
}

async function submitDoctor() {
  if (!doctorForm.employeeNo || !doctorForm.name || !doctorForm.departmentId) {
    ElMessage.warning('请补全工号、姓名和科室');
    return;
  }
  doctorSaving.value = true;
  try {
    await createDoctor({
      employeeNo: doctorForm.employeeNo,
      name: doctorForm.name,
      title: doctorForm.title,
      departmentId: doctorForm.departmentId,
      roleType: doctorForm.roleType,
      specialty: doctorForm.specialty
    });
    if (doctorForm.createAccount) {
      await createStaffAccount({
        employeeNo: doctorForm.employeeNo,
        name: doctorForm.name,
        role: doctorForm.roleType,
        phone: doctorForm.phone,
        password: doctorForm.password
      });
    }
    ElMessage.success(doctorForm.createAccount ? '医生档案和账号已创建' : '医生档案已创建');
    resetDoctorForm();
    doctors.value = await getDoctors();
    await loadAccounts();
    seedDefaults();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    doctorSaving.value = false;
  }
}

async function toggleAccount(account: StaffAccount) {
  try {
    await setStaffAccountActive(account.id, account.active);
    const sameAccount = allStaffAccounts.value.find((item) => item.id === account.id);
    if (sameAccount) {
      sameAccount.active = account.active;
    }
    ElMessage.success(account.active ? '账号已启用' : '账号已停用');
  } catch (error) {
    account.active = !account.active;
    ElMessage.error(errorMessage(error));
  }
}

async function resetPassword(account: StaffAccount) {
  let result: { value: string };
  try {
    result = await ElMessageBox.prompt(`为 ${account.name} 设置新密码`, '重置密码', {
      inputType: 'password',
      inputValue: 'abc12345',
      inputPattern: /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/,
      inputErrorMessage: '密码必须为 8-72 位且同时包含字母和数字'
    });
  } catch {
    return;
  }
  try {
    await resetStaffAccountPassword(account.id, result.value);
    ElMessage.success('密码已重置');
  } catch (error) {
    ElMessage.error(errorMessage(error));
  }
}

function resetDoctorForm() {
  doctorForm.employeeNo = '';
  doctorForm.name = '';
  doctorForm.title = '主治医师';
  doctorForm.departmentId = departments.value[0]?.id ?? '';
  doctorForm.roleType = 'OUTPATIENT_DOCTOR';
  doctorForm.specialty = '';
  doctorForm.createAccount = true;
  doctorForm.phone = '';
  doctorForm.password = 'abc12345';
}

function accountByEmployeeNo(employeeNo: string) {
  return allStaffAccounts.value.find(
    (account) => account.employeeNo === employeeNo || account.username === employeeNo
  );
}

function ensureAvailability(doctorId: string) {
  if (!availability[doctorId]) {
    availability[doctorId] = {
      leaveDates: [],
      surgeryDates: [],
      weeklyCapacity: 40
    };
  }
  return availability[doctorId];
}

function uniqueDates(dates: string[]) {
  return Array.from(new Set((dates ?? []).filter(Boolean))).sort();
}

function isSuggestionPublished(suggestionId: string) {
  return publishedSuggestionIds.value.includes(suggestionId);
}

function departmentName(id: string) {
  return departmentMap.value.get(id) ?? '未知科室';
}

function roleLabel(role: string) {
  return roleLabels[role] ?? role;
}

function scheduleStatusLabel(status: string) {
  return status === 'PUBLISHED' ? '已发布' : status === 'SUSPENDED' ? '已停诊' : status;
}

function scheduleStatusType(status: string): 'success' | 'danger' | 'info' {
  if (status === 'PUBLISHED') return 'success';
  if (status === 'SUSPENDED') return 'danger';
  return 'info';
}

function formatDateTime(value: string) {
  if (!value) return '-';
  return value.replace('T', ' ').slice(0, 16);
}

function todayIso() {
  const now = new Date();
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
  return now.toISOString().slice(0, 10);
}

function addDays(isoDate: string, days: number) {
  const date = new Date(`${isoDate}T00:00:00`);
  date.setDate(date.getDate() + days);
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
  return date.toISOString().slice(0, 10);
}

function isWeekend(isoDate: string) {
  const day = new Date(`${isoDate}T00:00:00`).getDay();
  return day === 0 || day === 6;
}

function errorMessage(error: unknown) {
  const responseMessage = (error as { response?: { data?: { message?: string } } })?.response?.data?.message;
  if (responseMessage) return responseMessage;
  return error instanceof Error ? error.message : '操作失败，请稍后重试';
}

function logout() {
  auth.signOut();
  router.push('/login');
}

onMounted(refreshAll);
</script>

<style scoped>
.admin-wks {
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
  font-family: Inter, "Microsoft YaHei", system-ui, sans-serif;
}

.admin-nav {
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

.admin-nav__brand,
.admin-nav__right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-nav__logo {
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

.admin-nav__title {
  font-size: 16px;
  font-weight: 700;
}

.admin-nav__right {
  gap: 20px;
  font-size: 13px;
}

.nav-logout {
  color: rgb(255 255 255 / 88%);
}

.admin-body {
  height: calc(100vh - 52px);
  display: flex;
  overflow: hidden;
}

.admin-sidebar {
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
  justify-content: space-between;
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

.admin-main {
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
.query-bar {
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
  line-height: 1.5;
}

.query-bar {
  justify-content: flex-start;
  flex-wrap: wrap;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.query-bar .el-select {
  width: 190px;
}

.stat-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.stat-box {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.stat-box span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.stat-box strong {
  display: block;
  margin-top: 5px;
  color: #0f766e;
  font-size: 22px;
  line-height: 1.1;
}

.stat-box em {
  display: block;
  margin-top: 3px;
  color: #94a3b8;
  font-size: 12px;
  font-style: normal;
}

.overview-layout,
.doctor-layout {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.4fr);
  gap: 16px;
}

.schedule-ai-layout {
  display: grid;
  grid-template-columns: 370px 330px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.work-card {
  min-width: 0;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.suggestions-card {
  min-height: 520px;
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.card-head h2 {
  margin: 0 0 4px;
  color: #111827;
  font-size: 16px;
  letter-spacing: 0;
}

.compact-form {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.inline-form {
  display: grid;
  grid-template-columns: minmax(220px, 1.4fr) minmax(150px, 0.8fr) minmax(120px, 0.7fr) minmax(110px, 0.6fr) auto;
  gap: 12px;
  align-items: end;
}

.inline-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.peak-row {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr) 44px;
  align-items: center;
  gap: 10px;
  min-height: 36px;
  color: #475569;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.task-item {
  height: 54px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f8fafc;
  color: #374151;
  cursor: pointer;
  font-size: 14px;
}

.task-item:hover {
  border-color: #a8e8ec;
  background: #f0f9fa;
}

.task-item strong {
  color: #0899a5;
  font-size: 22px;
}

.head-search {
  width: 260px;
}

.full {
  width: 100%;
}

.full-number {
  width: 100%;
}

@media (max-width: 1280px) {
  .schedule-ai-layout {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .suggestions-card {
    grid-column: span 2;
  }

  .inline-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .admin-body {
    height: auto;
    min-height: calc(100vh - 52px);
    flex-direction: column;
    overflow: visible;
  }

  .admin-main {
    overflow: visible;
  }

  .admin-sidebar {
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
    min-width: 116px;
    margin-right: 6px;
    margin-bottom: 0;
  }

  .overview-layout,
  .doctor-layout,
  .schedule-ai-layout,
  .stat-strip {
    grid-template-columns: 1fr;
  }

  .suggestions-card {
    grid-column: span 1;
  }
}

@media (max-width: 760px) {
  .admin-nav {
    height: auto;
    min-height: 52px;
    align-items: flex-start;
    flex-direction: column;
    padding: 10px 14px;
  }

  .admin-body {
    min-height: calc(100vh - 72px);
  }

  .page-head,
  .query-bar,
  .card-head {
    align-items: stretch;
    flex-direction: column;
  }

  .form-grid,
  .inline-form {
    grid-template-columns: 1fr;
  }

  .head-search,
  .query-bar .el-select {
    width: 100%;
  }
}
</style>
