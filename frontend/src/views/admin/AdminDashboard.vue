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
                  <span>排班信息</span>
                  <strong>{{ schedules.length }}</strong>
                </button>
                <button class="task-item" @click="currentPage = 'doctorProfile'">
                  <span>医生账号与档案</span>
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
              <p>自动生成当前日期第 8 天到第 15 天的门诊重排建议，管理员确认后才更新正式排班。</p>
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
              <el-form label-position="top" class="compact-form ai-param-form">
                <el-form-item label="排班科室">
                  <el-select v-model="aiForm.departmentId" class="full" clearable filterable placeholder="全部门诊科室">
                    <el-option
                      v-for="department in schedulableDepartments"
                      :key="department.id"
                      :label="department.name"
                      :value="department.id"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="基础预计挂号量">
                  <el-input-number v-model="aiForm.baseVisits" class="full-number" :min="1" :max="100" />
                </el-form-item>
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
                <el-button type="primary" class="full" :loading="suggestionLoading" @click="loadAiReplanPreview(true)">
                  刷新待确认重排建议
                </el-button>
              </el-form>
            </section>

            <section class="work-card suggestions-card">
              <div class="card-head">
                <div>
                  <h2>建议确认</h2>
                  <p>确认后写入正式排班并同步号源。</p>
                </div>
              </div>
              <el-table :data="suggestions" empty-text="暂无 AI 建议">
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
              <h1>排班信息</h1>
              <p>查看、补录和停诊已发布排班。</p>
            </div>
            <div class="head-actions">
              <el-button @click="loadSchedules">刷新排班</el-button>
              <el-button type="primary" @click="openManualScheduleCreate">新增排班</el-button>
            </div>
          </div>

          <div class="query-bar">
            <el-select v-model="scheduleFilter.departmentId" clearable placeholder="全部科室" filterable>
              <el-option v-for="department in schedulableDepartments" :key="department.id" :label="department.name" :value="department.id" />
            </el-select>
            <el-select v-model="scheduleFilter.doctorId" clearable placeholder="全部医生" filterable>
              <el-option v-for="doctor in filteredScheduleDoctors" :key="doctor.id" :label="doctor.name" :value="doctor.id" />
            </el-select>
            <el-button type="primary" @click="loadSchedules">查询</el-button>
          </div>

          <section class="work-card">
            <el-table :data="schedules" empty-text="暂无排班">
              <el-table-column prop="doctorName" label="医生" min-width="140" />
              <el-table-column label="科室" min-width="160">
                <template #default="{ row }">{{ departmentName(row.departmentId) }}</template>
              </el-table-column>
              <el-table-column prop="workDate" label="日期" min-width="140" />
              <el-table-column prop="period" label="时段" min-width="110" />
              <el-table-column prop="capacity" label="号源" min-width="100" />
              <el-table-column prop="booked" label="已约" min-width="100" />
              <el-table-column prop="locked" label="锁定" min-width="100" />
              <el-table-column prop="available" label="可约" min-width="100" />
              <el-table-column label="状态" min-width="120">
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
        </section>

        <section v-show="currentPage === 'doctorProfile'" class="work-page">
          <div class="page-head">
            <div>
              <h1>医生账号与档案</h1>
              <p>账号信息优先展示，点击医生详情后维护职称、科室和专长。</p>
            </div>
            <div class="head-actions">
              <el-input v-model.trim="doctorKeyword" class="head-search" clearable placeholder="搜索姓名/工号/科室" />
              <el-button type="primary" @click="openDoctorCreate">新增医生</el-button>
            </div>
          </div>

          <section class="work-card">
            <el-table :data="filteredDoctors" empty-text="暂无医生账号">
              <el-table-column prop="employeeNo" label="工号" width="120" />
              <el-table-column prop="name" label="姓名" width="120" />
              <el-table-column label="登录账号" width="120">
                <template #default="{ row }">{{ accountByEmployeeNo(row.employeeNo)?.username || row.employeeNo }}</template>
              </el-table-column>
              <el-table-column label="角色" width="120">
                <template #default="{ row }">{{ roleLabel(row.roleType) }}</template>
              </el-table-column>
              <el-table-column label="手机号" width="140">
                <template #default="{ row }">{{ accountByEmployeeNo(row.employeeNo)?.phone || '未填写' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-switch
                    v-if="accountByEmployeeNo(row.employeeNo)"
                    :model-value="accountByEmployeeNo(row.employeeNo)?.active"
                    active-text="启用"
                    inactive-text="停用"
                    inline-prompt
                    @change="toggleDoctorAccount(row, $event)"
                  />
                  <el-tag v-else type="info">未创建</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="创建时间" min-width="160">
                <template #default="{ row }">{{ formatDateTime(accountByEmployeeNo(row.employeeNo)?.createdAt || '') }}</template>
              </el-table-column>
              <el-table-column label="操作" width="190" fixed="right">
                <template #default="{ row }">
                  <el-button type="primary" link @click="openDoctorDetail(row)">医生详情</el-button>
                  <el-button
                    type="primary"
                    link
                    :disabled="!accountByEmployeeNo(row.employeeNo)"
                    @click="resetDoctorPassword(row)"
                  >
                    重置密码
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </section>
        </section>

        <section v-show="currentPage === 'doctorEvents'" class="work-page">
          <div class="page-head">
            <div>
              <h1>医生请假/手术</h1>
              <p>仅显示当前日期之后的请假与手术安排，保存后用于后续排班参考。</p>
            </div>
            <div class="head-actions">
              <el-button :loading="eventLoading" @click="loadDoctorEvents">刷新</el-button>
              <el-button type="primary" @click="openDoctorEventCreate">新增安排</el-button>
            </div>
          </div>

          <div class="query-bar">
            <el-input v-model.trim="eventKeyword" clearable placeholder="搜索医生/科室/备注" />
            <el-select v-model="eventTypeFilter" clearable placeholder="全部类型">
              <el-option label="请假" value="LEAVE" />
              <el-option label="手术" value="SURGERY" />
            </el-select>
          </div>

          <section class="work-card">
            <el-table :data="filteredDoctorEvents" v-loading="eventLoading" empty-text="暂无未来安排">
              <el-table-column prop="doctorName" label="医生" width="120" />
              <el-table-column prop="departmentName" label="科室" width="150" />
              <el-table-column label="类型" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.eventType === 'LEAVE' ? 'warning' : 'danger'">{{ doctorEventTypeLabel(row.eventType) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="日期" min-width="220">
                <template #default="{ row }">{{ row.dates.join('、') }}</template>
              </el-table-column>
              <el-table-column label="午别" width="130">
                <template #default="{ row }">{{ row.periods.join('、') }}</template>
              </el-table-column>
              <el-table-column prop="note" label="备注" min-width="180" show-overflow-tooltip />
              <el-table-column label="操作" width="130" fixed="right">
                <template #default="{ row }">
                  <el-button type="primary" link @click="openDoctorEventEdit(row)">编辑</el-button>
                  <el-button type="danger" link @click="removeDoctorEvent(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </section>
        </section>

        <el-dialog v-model="doctorDialogVisible" title="新增医生" width="560px">
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
                    v-for="department in schedulableDepartments"
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
          </el-form>
          <template #footer>
            <el-button @click="doctorDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="doctorSaving" @click="submitDoctor">保存医生</el-button>
          </template>
        </el-dialog>

        <el-dialog v-model="manualScheduleDialogVisible" title="新增排班" width="560px">
          <el-form label-position="top" class="compact-form">
            <el-form-item label="医生">
              <el-select v-model="manualScheduleForm.doctorId" class="full" filterable @change="syncManualDoctor">
                <el-option
                  v-for="doctor in schedulableDoctors"
                  :key="doctor.id"
                  :label="`${doctor.employeeNo} / ${doctor.name} / ${doctor.departmentName}`"
                  :value="doctor.id"
                />
              </el-select>
            </el-form-item>
            <div class="form-grid">
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
            </div>
            <el-form-item label="号源">
              <el-input-number v-model="manualScheduleForm.capacity" class="full-number" :min="1" :max="100" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="manualScheduleDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="scheduleSaving" @click="submitManualSchedule">保存排班</el-button>
          </template>
        </el-dialog>

        <el-drawer v-model="doctorDetailVisible" title="医生详情" size="420px">
          <el-form label-position="top" class="compact-form">
            <el-form-item label="姓名">
              <el-input v-model.trim="doctorDetailForm.name" disabled />
            </el-form-item>
            <div class="form-grid">
              <el-form-item label="工号">
                <el-input v-model="doctorDetailForm.employeeNo" disabled />
              </el-form-item>
              <el-form-item label="职称">
                <el-input v-model.trim="doctorDetailForm.title" />
              </el-form-item>
            </div>
            <el-form-item label="科室">
              <el-select v-model="doctorDetailForm.departmentId" class="full" filterable>
                <el-option
                  v-for="department in schedulableDepartments"
                  :key="department.id"
                  :label="department.name"
                  :value="department.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="专长">
              <el-input v-model.trim="doctorDetailForm.specialty" type="textarea" :rows="4" />
            </el-form-item>
            <el-button type="primary" :loading="doctorSaving" @click="submitDoctorDetail">保存修改</el-button>
          </el-form>
        </el-drawer>

        <el-dialog v-model="eventDialogVisible" :title="editingEventId ? '编辑安排' : '新增安排'" width="560px">
          <el-form label-position="top" class="compact-form">
            <el-form-item label="医生">
              <el-select v-model="eventForm.doctorId" class="full" filterable>
                <el-option
                  v-for="doctor in schedulableDoctors"
                  :key="doctor.id"
                  :label="`${doctor.employeeNo} / ${doctor.name} / ${doctor.departmentName}`"
                  :value="doctor.id"
                />
              </el-select>
            </el-form-item>
            <div class="form-grid">
              <el-form-item label="类型">
                <el-select v-model="eventForm.eventType" class="full">
                  <el-option label="请假" value="LEAVE" />
                  <el-option label="手术" value="SURGERY" />
                </el-select>
              </el-form-item>
              <el-form-item label="午别">
                <el-checkbox-group v-model="eventForm.periods">
                  <el-checkbox-button label="上午" />
                  <el-checkbox-button label="下午" />
                </el-checkbox-group>
              </el-form-item>
            </div>
            <el-form-item label="日期">
              <el-date-picker
                v-model="eventForm.dates"
                class="full"
                type="dates"
                value-format="YYYY-MM-DD"
                :disabled-date="disablePastAndToday"
              />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model.trim="eventForm.note" type="textarea" :rows="3" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="eventDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="eventSaving" @click="submitDoctorEvent">保存安排</el-button>
          </template>
        </el-dialog>
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
  createDoctorEvent,
  createSchedule,
  deleteDoctorEvent,
  getAiScheduleSuggestions,
  getAiReplanPreview,
  getDepartments,
  getDoctorEvents,
  getDoctors,
  getSchedules,
  publishAiScheduleSuggestions,
  suspendSchedule,
  updateDoctor,
  updateDoctorEvent,
  type AiDoctorCandidate,
  type AiScheduleDemand,
  type AiScheduleResponse,
  type AiScheduleSuggestion,
  type Department,
  type Doctor,
  type DoctorEvent,
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
type PageKey = 'overview' | 'aiSchedule' | 'manualSchedule' | 'doctorProfile' | 'doctorEvents';

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
const eventLoading = ref(false);
const eventSaving = ref(false);

const overview = ref<DashboardOverview | null>(null);
const departments = ref<Department[]>([]);
const doctors = ref<Doctor[]>([]);
const schedules = ref<Schedule[]>([]);
const doctorEvents = ref<DoctorEvent[]>([]);
const staffAccounts = ref<StaffAccount[]>([]);
const allStaffAccounts = ref<StaffAccount[]>([]);
const aiResponse = ref<AiScheduleResponse | null>(null);
const publishedSuggestionIds = ref<string[]>([]);
const selectedDoctorId = ref('');
const doctorKeyword = ref('');
const eventKeyword = ref('');
const eventTypeFilter = ref('');
const doctorDialogVisible = ref(false);
const manualScheduleDialogVisible = ref(false);
const doctorDetailVisible = ref(false);
const eventDialogVisible = ref(false);
const editingEventId = ref('');
const selectedDoctorDetail = ref<Doctor | null>(null);

const availability = reactive<Record<string, AvailabilitySettings>>({});
const NON_REGISTRATION_DEPARTMENT_NAMES = ['影像检查科', '检验科', '处置科', '药房', '系统管理', '收费处'];
const REPLAN_WINDOW_START_OFFSET = 7;
const REPLAN_WINDOW_DAYS = 8;
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
  startDate: addDays(todayIso(), REPLAN_WINDOW_START_OFFSET),
  days: REPLAN_WINDOW_DAYS,
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

const doctorDetailForm = reactive({
  id: '',
  employeeNo: '',
  name: '',
  title: '',
  departmentId: '',
  specialty: ''
});

const eventForm = reactive({
  doctorId: '',
  eventType: 'LEAVE' as DoctorEvent['eventType'],
  dates: [] as string[],
  periods: ['上午'] as string[],
  note: ''
});

const accountFilter = reactive({
  role: 'OUTPATIENT_DOCTOR'
});

const departmentMap = computed(() => new Map(departments.value.map((item) => [item.id, item.name])));
const schedulableDepartments = computed(() =>
  departments.value.filter((department) => !NON_REGISTRATION_DEPARTMENT_NAMES.includes(department.name))
);
const schedulableDepartmentIds = computed(() => new Set(schedulableDepartments.value.map((department) => department.id)));
const schedulableDoctors = computed(() =>
  doctors.value.filter((doctor) => schedulableDepartmentIds.value.has(doctor.departmentId))
);

const navItems = computed<Array<{ key: PageKey; label: string; badge?: number }>>(() => [
  { key: 'overview', label: '运营概览' },
  { key: 'aiSchedule', label: 'AI 智能排班', badge: pendingSuggestions.value.length || undefined },
  { key: 'manualSchedule', label: '排班信息', badge: schedules.value.length || undefined },
  { key: 'doctorProfile', label: '医生账号与档案', badge: doctors.value.length || undefined },
  { key: 'doctorEvents', label: '医生请假/手术', badge: doctorEvents.value.length || undefined }
]);

const aiDoctors = computed(() => schedulableDoctors.value.filter((doctor) => doctor.departmentId === aiForm.departmentId));

const filteredScheduleDoctors = computed(() =>
  schedulableDoctors.value.filter((doctor) => !scheduleFilter.departmentId || doctor.departmentId === scheduleFilter.departmentId)
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

const filteredDoctorEvents = computed(() => {
  const keyword = eventKeyword.value.trim().toLowerCase();
  return doctorEvents.value.filter((event) => {
    const matchesType = !eventTypeFilter.value || event.eventType === eventTypeFilter.value;
    const matchesKeyword =
      !keyword ||
      [event.doctorName, event.departmentName, event.note, doctorEventTypeLabel(event.eventType)]
        .filter(Boolean)
        .some((value) => value.toLowerCase().includes(keyword));
    return matchesType && matchesKeyword;
  });
});

const suggestions = computed(() => aiResponse.value?.suggestions ?? []);

const pendingSuggestions = computed(() =>
  suggestions.value.filter((suggestion) => !isSuggestionPublished(suggestion.suggestionId))
);

const aiCandidates = computed<AiDoctorCandidate[]>(() =>
  aiDoctors.value.map((doctor) => {
    const settings = ensureAvailability(doctor.id);
    const unavailableSlots = doctorEvents.value
      .filter((event) => event.doctorId === doctor.id)
      .flatMap((event) =>
        event.dates.flatMap((date) =>
          event.periods.map((period) => ({ date, period, type: event.eventType }))
        )
      );
    return {
      doctorId: doctor.id,
      doctorName: doctor.name,
      departmentId: doctor.departmentId,
      specialty: doctor.specialty ?? '',
      weeklyCapacity: settings.weeklyCapacity,
      leaveDates: uniqueDates(settings.leaveDates),
      surgeryDates: uniqueDates(settings.surgeryDates),
      unavailableSlots
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
    const [overviewData, departmentData, doctorData, accountData, allAccountData, eventData] = await Promise.all([
      getDashboardOverview(),
      getDepartments(),
      getDoctors(),
      getStaffAccounts(accountFilter.role || undefined),
      getStaffAccounts(),
      getDoctorEvents()
    ]);
    overview.value = overviewData;
    departments.value = departmentData;
    doctors.value = doctorData;
    staffAccounts.value = accountData;
    allStaffAccounts.value = allAccountData;
    doctorEvents.value = eventData;
    seedDefaults();
    syncAvailabilityFromEvents();
    await loadSchedules();
    await loadAiReplanPreview(false);
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    pageLoading.value = false;
  }
}

async function loadSchedules() {
  schedules.value = await getSchedules({
    departmentId: scheduleFilter.departmentId || undefined,
    doctorId: scheduleFilter.doctorId || undefined,
    bookingWindowOnly: false
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

async function loadDoctorEvents() {
  eventLoading.value = true;
  try {
    doctorEvents.value = await getDoctorEvents();
    syncAvailabilityFromEvents();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    eventLoading.value = false;
  }
}

async function loadAiReplanPreview(showFeedback = false) {
  suggestionLoading.value = true;
  try {
    aiForm.startDate = addDays(todayIso(), REPLAN_WINDOW_START_OFFSET);
    aiForm.days = REPLAN_WINDOW_DAYS;
    aiResponse.value = await getAiReplanPreview({
      departmentId: aiForm.departmentId || undefined,
      baseVisits: aiForm.baseVisits,
      riskLevel: aiForm.riskLevel,
      weekendPeak: aiForm.weekendPeak,
      weekendIncrease: aiForm.weekendIncrease,
      morningPeak: aiForm.morningPeak,
      morningIncrease: aiForm.morningIncrease
    });
    publishedSuggestionIds.value = [];
    if (showFeedback) {
      if (suggestions.value.length > 0) {
        ElMessage.success(`已生成 ${suggestions.value.length} 条第 8-15 天待确认排班建议`);
      } else {
        ElMessage.warning('当前窗口暂无可用重排建议');
      }
    }
  } catch (error) {
    if (showFeedback) {
      ElMessage.error(errorMessage(error));
    }
  } finally {
    suggestionLoading.value = false;
  }
}

function seedDefaults() {
  if (!doctorForm.departmentId) {
    doctorForm.departmentId = schedulableDepartments.value[0]?.id ?? '';
  }
  if (!manualScheduleForm.doctorId) {
    manualScheduleForm.doctorId = schedulableDoctors.value[0]?.id ?? '';
    syncManualDoctor();
  }
  schedulableDoctors.value.forEach((doctor) => ensureAvailability(doctor.id));
  selectedDoctorId.value = aiDoctors.value[0]?.id ?? schedulableDoctors.value[0]?.id ?? '';
  if (!eventForm.doctorId) {
    eventForm.doctorId = schedulableDoctors.value[0]?.id ?? '';
  }
}

function buildDemands() {
  if (!aiForm.departmentId || !aiForm.startDate) return [];
  const items: AiScheduleDemand[] = [];
  for (let day = 0; day < aiForm.days; day += 1) {
    const workDate = addDays(aiForm.startDate, day);
    for (const period of ['上午', '下午']) {
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
  await publishAiScheduleSuggestions({
    aiRecordId: aiResponse.value?.aiRecordId ?? null,
    suggestions: [{
      ...suggestion,
      aiRecordId: aiResponse.value?.aiRecordId ?? null
    }]
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
    await ElMessageBox.confirm(`确认发布 ${pendingSuggestions.value.length} 条 AI 排班建议并更新对应日期窗口？`, '批量确认', {
      type: 'warning'
    });
  } catch {
    return;
  }
  publishLoading.value = true;
  try {
    await publishAiScheduleSuggestions({
      aiRecordId: aiResponse.value?.aiRecordId ?? null,
      suggestions: pendingSuggestions.value.map((suggestion) => ({
        ...suggestion,
        aiRecordId: aiResponse.value?.aiRecordId ?? null
      }))
    });
    publishedSuggestionIds.value = suggestions.value.map((suggestion) => suggestion.suggestionId);
    ElMessage.success('AI 排班建议已确认并更新正式排班');
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

function openManualScheduleCreate() {
  if (!manualScheduleForm.doctorId) {
    manualScheduleForm.doctorId = schedulableDoctors.value[0]?.id ?? '';
  }
  syncManualDoctor();
  manualScheduleDialogVisible.value = true;
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
    manualScheduleDialogVisible.value = false;
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

function openDoctorCreate() {
  resetDoctorForm();
  doctorDialogVisible.value = true;
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
    doctorDialogVisible.value = false;
    doctors.value = await getDoctors();
    await loadAccounts();
    seedDefaults();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    doctorSaving.value = false;
  }
}

function openDoctorDetail(doctor: Doctor) {
  selectedDoctorDetail.value = doctor;
  doctorDetailForm.id = doctor.id;
  doctorDetailForm.employeeNo = doctor.employeeNo;
  doctorDetailForm.name = doctor.name;
  doctorDetailForm.title = doctor.title;
  doctorDetailForm.departmentId = doctor.departmentId;
  doctorDetailForm.specialty = doctor.specialty ?? '';
  doctorDetailVisible.value = true;
}

async function submitDoctorDetail() {
  if (!doctorDetailForm.id || !doctorDetailForm.departmentId) {
    ElMessage.warning('请补全科室');
    return;
  }
  doctorSaving.value = true;
  try {
    const saved = await updateDoctor(doctorDetailForm.id, {
      name: doctorDetailForm.name,
      title: doctorDetailForm.title,
      departmentId: doctorDetailForm.departmentId,
      specialty: doctorDetailForm.specialty
    });
    const index = doctors.value.findIndex((item) => item.id === saved.id);
    if (index >= 0) {
      doctors.value.splice(index, 1, saved);
    }
    doctorDetailVisible.value = false;
    ElMessage.success('医生详情已保存');
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    doctorSaving.value = false;
  }
}

function openDoctorEventCreate() {
  resetDoctorEventForm();
  eventDialogVisible.value = true;
}

function openDoctorEventEdit(event: DoctorEvent) {
  editingEventId.value = event.id;
  eventForm.doctorId = event.doctorId;
  eventForm.eventType = event.eventType;
  eventForm.dates = [...event.dates];
  eventForm.periods = [...event.periods];
  eventForm.note = event.note ?? '';
  eventDialogVisible.value = true;
}

async function submitDoctorEvent() {
  if (!eventForm.doctorId || eventForm.dates.length === 0 || eventForm.periods.length === 0) {
    ElMessage.warning('请补全医生、日期和午别');
    return;
  }
  eventSaving.value = true;
  try {
    const payload = {
      doctorId: eventForm.doctorId,
      eventType: eventForm.eventType,
      dates: uniqueDates(eventForm.dates),
      periods: [...eventForm.periods],
      note: eventForm.note
    };
    if (editingEventId.value) {
      await updateDoctorEvent(editingEventId.value, payload);
      ElMessage.success('安排已更新');
    } else {
      await createDoctorEvent(payload);
      ElMessage.success('安排已新增');
    }
    eventDialogVisible.value = false;
    resetDoctorEventForm();
    await loadDoctorEvents();
    if (payload.dates.some((date) => isWithinReplanWindow(date))) {
      currentPage.value = 'aiSchedule';
      await loadAiReplanPreview(true);
    }
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    eventSaving.value = false;
  }
}

async function removeDoctorEvent(event: DoctorEvent) {
  try {
    await ElMessageBox.confirm(`确认删除 ${event.doctorName} 的${doctorEventTypeLabel(event.eventType)}安排？`, '删除确认', {
      type: 'warning'
    });
  } catch {
    return;
  }
  try {
    await deleteDoctorEvent(event.id);
    ElMessage.success('安排已删除');
    await loadDoctorEvents();
  } catch (error) {
    ElMessage.error(errorMessage(error));
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

async function toggleDoctorAccount(doctor: Doctor, active: string | number | boolean) {
  const account = accountByEmployeeNo(doctor.employeeNo);
  if (!account) return;
  account.active = Boolean(active);
  await toggleAccount(account);
}

function resetDoctorPassword(doctor: Doctor) {
  const account = accountByEmployeeNo(doctor.employeeNo);
  if (account) resetPassword(account);
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
  doctorForm.departmentId = schedulableDepartments.value[0]?.id ?? '';
  doctorForm.roleType = 'OUTPATIENT_DOCTOR';
  doctorForm.specialty = '';
  doctorForm.createAccount = true;
  doctorForm.phone = '';
  doctorForm.password = 'abc12345';
}

function resetDoctorEventForm() {
  editingEventId.value = '';
  eventForm.doctorId = schedulableDoctors.value[0]?.id ?? '';
  eventForm.eventType = 'LEAVE';
  eventForm.dates = [];
  eventForm.periods = ['上午'];
  eventForm.note = '';
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

function syncAvailabilityFromEvents() {
  doctors.value.forEach((doctor) => {
    availability[doctor.id] = {
      leaveDates: [],
      surgeryDates: [],
      weeklyCapacity: ensureAvailability(doctor.id).weeklyCapacity
    };
  });
  doctorEvents.value.forEach((event) => {
    const settings = ensureAvailability(event.doctorId);
    if (event.eventType === 'LEAVE') {
      settings.leaveDates = uniqueDates([...settings.leaveDates, ...event.dates]);
    } else if (event.eventType === 'SURGERY') {
      settings.surgeryDates = uniqueDates([...settings.surgeryDates, ...event.dates]);
    }
  });
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

function doctorEventTypeLabel(type: string) {
  return type === 'LEAVE' ? '请假' : type === 'SURGERY' ? '手术' : type;
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

function disablePastAndToday(date: Date) {
  const earliest = new Date();
  earliest.setHours(0, 0, 0, 0);
  earliest.setDate(earliest.getDate() + REPLAN_WINDOW_START_OFFSET);
  return date < earliest;
}

function isWithinReplanWindow(isoDate: string) {
  const start = addDays(todayIso(), REPLAN_WINDOW_START_OFFSET);
  const end = addDays(start, REPLAN_WINDOW_DAYS - 1);
  return isoDate >= start && isoDate <= end;
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

.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
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

.query-bar .el-input {
  width: 260px;
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
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.work-card {
  min-width: 0;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.suggestions-card {
  width: 100%;
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

.ai-param-form {
  display: grid;
  grid-template-columns: minmax(240px, 1.2fr) minmax(180px, 0.8fr) minmax(280px, 1.4fr) minmax(280px, 1.4fr) minmax(180px, 0.8fr);
  gap: 12px;
  align-items: end;
}

.ai-param-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.ai-param-form > .peak-row {
  min-height: 32px;
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
  .ai-param-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
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
  .stat-strip {
    grid-template-columns: 1fr;
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
  .inline-form,
  .ai-param-form {
    grid-template-columns: 1fr;
  }

  .head-search,
  .query-bar .el-select,
  .query-bar .el-input {
    width: 100%;
  }

  .head-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
