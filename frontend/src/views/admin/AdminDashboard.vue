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
            </div>
            <div class="page-actions">
              <el-button :loading="pageLoading" @click="refreshAll">刷新数据</el-button>
            </div>
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
            </div>
          </div>

          <div class="schedule-ai-layout">
            <section class="work-card">
              <div class="card-head">
                <div>
                  <h2>需求参数</h2>
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
                <el-form-item label="基础预计挂号量/诊室">
                  <el-input-number v-model="aiForm.baseVisits" class="full-number" :min="1" :max="100" />
                </el-form-item>
                <div class="peak-row">
                  <el-checkbox v-model="aiForm.weekdayPeak">工作日高峰</el-checkbox>
                  <el-slider v-model="aiForm.weekdayIncrease" :min="0" :max="80" :step="5" />
                  <span>{{ aiForm.weekdayIncrease }}%</span>
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
              <p v-if="aiBackgroundSummary" class="ai-summary">{{ aiBackgroundSummary }}</p>
            </section>

            <section class="work-card schedule-visual-card">
              <div class="card-head">
                <div>
                  <h2>排班可视化</h2>
                </div>
                <div class="schedule-board-tools">
                  <div class="schedule-legend">
                    <span><i class="legend-dot legend-dot--published"></i>正式排班</span>
                    <span><i class="legend-dot legend-dot--ai"></i>AI 新增</span>
                    <span><i class="legend-dot legend-dot--surgery"></i>手术</span>
                    <span><i class="legend-dot legend-dot--leave"></i>请假</span>
                  </div>
                  <div class="schedule-week-nav">
                    <el-button size="small" @click="moveScheduleBoardWeek('ai', -1)">上一周</el-button>
                    <span>{{ aiScheduleBoardRangeLabel }}</span>
                    <el-button size="small" @click="resetScheduleBoardWeek('ai')">起始周</el-button>
                    <el-button size="small" @click="moveScheduleBoardWeek('ai', 1)">下一周</el-button>
                  </div>
                </div>
              </div>
              <div class="schedule-board" :style="scheduleBoardStyle(aiScheduleBoardDays.length)">
                <div class="schedule-board__corner">
                  <span>人员</span>
                  <strong>时间</strong>
                </div>
                <div
                  v-for="day in aiScheduleBoardDays"
                  :key="day.date"
                  class="schedule-board__day"
                  :class="{ 'schedule-board__day--weekend': day.weekend }"
                >
                  <strong>{{ day.weekday }}</strong>
                  <span>{{ day.monthDay }}</span>
                  <em>{{ day.weekend ? '休息日' : '工作日' }}</em>
                  <div class="schedule-board__ticks">
                    <span>08</span>
                    <span>12</span>
                    <span>16</span>
                  </div>
                </div>
                <template v-for="row in aiScheduleBoardRows" :key="row.kind === 'group' ? row.id : row.doctorId">
                  <div v-if="row.kind === 'group'" class="schedule-board__group">
                    <button type="button" class="schedule-board__group-toggle" @click="toggleScheduleDepartment(row.departmentId)">
                      <span>{{ isScheduleDepartmentCollapsed(row.departmentId) ? '+' : '-' }}</span>
                      <strong>{{ row.departmentName }}</strong>
                    </button>
                    <span>{{ row.doctorCount }} 名医生</span>
                  </div>
                  <template v-else>
                    <div class="schedule-board__person">
                      <span class="doctor-avatar">{{ row.initials }}</span>
                      <div>
                        <strong>{{ row.doctorName }}</strong>
                        <em>{{ row.subtitle }}</em>
                      </div>
                    </div>
                    <div
                      v-for="cell in row.cells"
                      :key="`${row.doctorId}-${cell.date}`"
                      class="schedule-board__cell"
                      :class="{ 'schedule-board__cell--weekend': cell.weekend }"
                    >
                      <div v-for="period in SCHEDULE_BOARD_PERIODS" :key="period" class="schedule-board__period-slot">
                        <div
                          v-for="item in scheduleBoardSlotEntries(cell.entries, period)"
                          :key="`${period}-${item.id}`"
                          class="schedule-shift"
                          :class="scheduleShiftClasses(item)"
                        >
                          <div class="schedule-shift__head">
                            <strong>{{ item.period }}</strong>
                            <span v-if="item.source === 'ai'">{{ item.published ? '已发布' : '新建议' }}</span>
                          </div>
                          <p>{{ item.timeRange }}</p>
                          <em>{{ item.roomName || item.note || '未分配诊室' }}</em>
                          <small v-if="item.source === 'event'">{{ item.eventType === 'SURGERY' ? '手术安排' : '请假安排' }}</small>
                          <small v-else>号源 {{ item.capacity }}</small>
                        </div>
                      </div>
                    </div>
                  </template>
                </template>
              </div>
              <div class="schedule-board-footer">
                <el-button
                  type="success"
                  :disabled="pendingSuggestions.length === 0"
                  :loading="publishLoading"
                  @click="publishPendingSuggestions"
                >
                  批量确认发布
                </el-button>
              </div>
            </section>

            <section class="work-card suggestions-card">
              <div class="card-head">
                <div>
                  <h2>建议确认</h2>
                </div>
                <el-tag v-if="aiSourceLabel" :type="aiResponse?.fallbackUsed ? 'warning' : 'success'" effect="plain">
                  {{ aiSourceLabel }}
                </el-tag>
              </div>
              <el-table :data="suggestions" empty-text="暂无 AI 建议" width="100%">
                <el-table-column prop="workDate" label="日期" min-width="112" />
                <el-table-column prop="period" label="时段" min-width="86" />
                <el-table-column prop="doctorName" label="医生" min-width="112" />
                <el-table-column prop="roomName" label="诊室" min-width="130" />
                <el-table-column label="科室" min-width="126">
                  <template #default="{ row }">{{ departmentName(row.departmentId) }}</template>
                </el-table-column>
                <el-table-column prop="capacity" label="号源" min-width="78" />
                <el-table-column label="状态" min-width="96">
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
            </div>
          </div>

          <section class="work-card schedule-visual-card">
            <div class="card-head">
              <div>
                <h2>排班可视化</h2>
              </div>
              <div class="schedule-board-tools">
                <div class="schedule-legend">
                  <span><i class="legend-dot legend-dot--published"></i>正式排班</span>
                  <span><i class="legend-dot legend-dot--surgery"></i>手术</span>
                  <span><i class="legend-dot legend-dot--leave"></i>请假</span>
                </div>
                <div class="schedule-week-nav">
                  <el-button size="small" @click="moveScheduleBoardWeek('manual', -1)">上一周</el-button>
                  <span>{{ manualScheduleBoardRangeLabel }}</span>
                  <el-button size="small" @click="resetScheduleBoardWeek('manual')">本周</el-button>
                  <el-button size="small" @click="moveScheduleBoardWeek('manual', 1)">下一周</el-button>
                </div>
              </div>
            </div>
            <div class="schedule-board" :style="scheduleBoardStyle(manualScheduleBoardDays.length)">
              <div class="schedule-board__corner">
                <span>人员</span>
                <strong>时间</strong>
              </div>
              <div
                v-for="day in manualScheduleBoardDays"
                :key="day.date"
                class="schedule-board__day"
                :class="{ 'schedule-board__day--weekend': day.weekend }"
              >
                <strong>{{ day.weekday }}</strong>
                <span>{{ day.monthDay }}</span>
                <em>{{ day.weekend ? '休息日' : '工作日' }}</em>
                <div class="schedule-board__ticks">
                  <span>08</span>
                  <span>12</span>
                  <span>16</span>
                </div>
              </div>
              <template v-for="row in manualScheduleBoardRows" :key="row.kind === 'group' ? row.id : row.doctorId">
                <div v-if="row.kind === 'group'" class="schedule-board__group">
                  <button type="button" class="schedule-board__group-toggle" @click="toggleScheduleDepartment(row.departmentId)">
                    <span>{{ isScheduleDepartmentCollapsed(row.departmentId) ? '+' : '-' }}</span>
                    <strong>{{ row.departmentName }}</strong>
                  </button>
                  <span>{{ row.doctorCount }} 名医生</span>
                </div>
                <template v-else>
                  <div class="schedule-board__person">
                    <span class="doctor-avatar">{{ row.initials }}</span>
                    <div>
                      <strong>{{ row.doctorName }}</strong>
                      <em>{{ row.subtitle }}</em>
                    </div>
                  </div>
                  <div
                    v-for="cell in row.cells"
                    :key="`${row.doctorId}-${cell.date}`"
                    class="schedule-board__cell"
                    :class="{ 'schedule-board__cell--weekend': cell.weekend }"
                  >
                    <div v-for="period in SCHEDULE_BOARD_PERIODS" :key="period" class="schedule-board__period-slot">
                      <div
                        v-for="item in scheduleBoardSlotEntries(cell.entries, period)"
                        :key="`${period}-${item.id}`"
                        class="schedule-shift"
                        :class="scheduleShiftClasses(item)"
                      >
                        <div class="schedule-shift__head">
                          <strong>{{ item.period }}</strong>
                        </div>
                        <p>{{ item.timeRange }}</p>
                        <em>{{ item.roomName || item.note || '未分配诊室' }}</em>
                        <small v-if="item.source === 'event'">{{ item.eventType === 'SURGERY' ? '手术安排' : '请假安排' }}</small>
                        <small v-else>号源 {{ item.capacity }} / 可约 {{ item.available }}</small>
                      </div>
                    </div>
                  </div>
                </template>
              </template>
            </div>
          </section>

          <div class="query-bar">
            <el-select v-model="scheduleFilter.departmentId" clearable placeholder="全部科室" filterable>
              <el-option v-for="department in schedulableDepartments" :key="department.id" :label="department.name" :value="department.id" />
            </el-select>
            <el-select v-model="scheduleFilter.doctorId" clearable placeholder="全部医生" filterable>
              <el-option v-for="doctor in filteredScheduleDoctors" :key="doctor.id" :label="doctor.name" :value="doctor.id" />
            </el-select>
            <el-button type="primary" @click="loadSchedules">查询</el-button>
            <el-button @click="resetScheduleFilters">重置</el-button>
            <el-button type="primary" @click="openManualScheduleCreate">新增排班</el-button>
          </div>

          <section class="work-card">
            <el-table :data="schedules" empty-text="暂无排班">
              <el-table-column prop="doctorName" label="医生" min-width="140" />
              <el-table-column prop="roomName" label="诊室" min-width="140" />
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
            </div>
          </div>

          <div class="query-bar">
            <el-input v-model.trim="doctorKeyword" class="head-search" clearable placeholder="搜索姓名/工号/科室" />
            <el-button type="primary" @click="queryDoctors">查询</el-button>
            <el-button @click="resetDoctorSearch">重置</el-button>
            <el-button type="primary" @click="openDoctorCreate">新增医生</el-button>
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
            </div>
          </div>

          <div class="query-bar">
            <el-input v-model.trim="eventKeyword" clearable placeholder="搜索医生/科室/备注" />
            <el-select v-model="eventTypeFilter" clearable placeholder="全部类型">
              <el-option label="请假" value="LEAVE" />
              <el-option label="手术" value="SURGERY" />
            </el-select>
            <el-button type="primary" :loading="eventLoading" @click="queryDoctorEvents">查询</el-button>
            <el-button @click="resetDoctorEventsSearch">重置</el-button>
            <el-button type="primary" @click="openDoctorEventCreate">新增安排</el-button>
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
  publishAiScheduleSuggestion,
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

type PageKey = 'overview' | 'aiSchedule' | 'manualSchedule' | 'doctorProfile' | 'doctorEvents';

interface AvailabilitySettings {
  weeklyCapacity: number;
}

interface ScheduleBoardDay {
  date: string;
  weekday: string;
  monthDay: string;
  weekend: boolean;
}

interface ScheduleBoardEntry {
  id: string;
  source: 'published' | 'ai' | 'event';
  period: string;
  periodKey: 'morning' | 'afternoon' | 'full';
  timeRange: string;
  roomName: string;
  capacity: number;
  available: number;
  published: boolean;
  eventType?: DoctorEvent['eventType'];
  note?: string;
}

interface ScheduleBoardRow {
  kind: 'doctor';
  doctorId: string;
  doctorName: string;
  subtitle: string;
  initials: string;
  cells: Array<{
    date: string;
    weekend: boolean;
    entries: ScheduleBoardEntry[];
  }>;
}

interface ScheduleBoardGroupRow {
  kind: 'group';
  id: string;
  departmentId: string;
  departmentName: string;
  doctorCount: number;
}

type ScheduleBoardDisplayRow = ScheduleBoardRow | ScheduleBoardGroupRow;

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
const collapsedScheduleDepartments = ref<string[]>([]);
const aiScheduleBoardWeekOffset = ref(0);
const manualScheduleBoardWeekOffset = ref(0);
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
const SCHEDULE_BOARD_MANUAL_DAYS = 7;
const SCHEDULE_WEEKDAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
const SCHEDULE_BOARD_PERIODS = ['上午', '下午'];
const weekDays = ['日', '一', '二', '三', '四', '五', '六'];
const today = new Date().toLocaleDateString('zh-CN');
const dayOfWeek = `星期${weekDays[new Date().getDay()]}`;

const roleLabels: Record<string, string> = {
  OUTPATIENT_DOCTOR: '门诊医生',
  CHECK_DOCTOR: '检查医生',
  LAB_DOCTOR: '检验医生',
  DISPOSAL_DOCTOR: '处置医生',
  PHARMACY_STAFF: '药房工作人员'
};

const accountRoleOptions = [
  { label: '门诊医生', value: 'OUTPATIENT_DOCTOR' },
  { label: '检查医生', value: 'CHECK_DOCTOR' },
  { label: '检验医生', value: 'LAB_DOCTOR' },
  { label: '处置医生', value: 'DISPOSAL_DOCTOR' },
  { label: '药房工作人员', value: 'PHARMACY_STAFF' }
];

const aiForm = reactive({
  departmentId: '',
  startDate: addDays(todayIso(), REPLAN_WINDOW_START_OFFSET),
  days: REPLAN_WINDOW_DAYS,
  baseVisits: 24,
  weekdayPeak: true,
  weekdayIncrease: 35,
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
const aiRooms = computed(() => {
  const map = new Map<string, { roomId: string; roomName: string }>();
  aiDoctors.value.forEach((doctor) => {
    const roomId = doctor.roomId || '';
    if (roomId && !map.has(roomId)) {
      map.set(roomId, { roomId, roomName: doctor.roomName || roomId });
    }
  });
  return Array.from(map.values());
});

const filteredScheduleDoctors = computed(() =>
  schedulableDoctors.value.filter((doctor) => !scheduleFilter.departmentId || doctor.departmentId === scheduleFilter.departmentId)
);

const filteredDoctors = computed(() => {
  const keyword = doctorKeyword.value.trim().toLowerCase();
  if (!keyword) return doctors.value;
  return doctors.value.filter((doctor) =>
    [doctor.employeeNo, doctor.name, doctor.departmentName, doctor.roomName, doctor.specialty]
      .filter((value): value is string => Boolean(value))
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
const aiSourceLabel = computed(() => {
  if (!aiResponse.value) return '';
  const provider = aiResponse.value.provider || (aiResponse.value.fallbackUsed ? 'backend' : 'AI');
  const model = aiResponse.value.model ? `/${aiResponse.value.model}` : '';
  return `${provider}${model}${aiResponse.value.fallbackUsed ? ' 兜底' : ''}`;
});
const aiBackgroundSummary = computed(() => aiResponse.value?.backgroundSummary || '');

const pendingSuggestions = computed(() =>
  suggestions.value.filter((suggestion) => !isSuggestionPublished(suggestion.suggestionId))
);

const aiScheduleBoardStartDate = computed(() => addDays(aiForm.startDate, aiScheduleBoardWeekOffset.value * 7));
const manualScheduleBoardStartDate = computed(() => addDays(todayIso(), manualScheduleBoardWeekOffset.value * 7));
const aiScheduleBoardDays = computed(() => buildScheduleBoardDays(aiScheduleBoardStartDate.value, SCHEDULE_BOARD_MANUAL_DAYS));
const manualScheduleBoardDays = computed(() => buildScheduleBoardDays(manualScheduleBoardStartDate.value, SCHEDULE_BOARD_MANUAL_DAYS));
const aiScheduleBoardRows = computed(() => buildScheduleBoardRows(aiScheduleBoardDays.value, true));
const manualScheduleBoardRows = computed(() => buildScheduleBoardRows(manualScheduleBoardDays.value, false));
const aiScheduleBoardRangeLabel = computed(() => scheduleBoardRangeLabel(aiScheduleBoardDays.value));
const manualScheduleBoardRangeLabel = computed(() => scheduleBoardRangeLabel(manualScheduleBoardDays.value));

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
      roomId: doctor.roomId || '',
      roomName: doctor.roomName || '',
      specialty: doctor.specialty ?? '',
      weeklyCapacity: settings.weeklyCapacity,
      historicalAverageVisits: 0,
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
    if (needsAutomaticReplan()) {
      await loadAiReplanPreview(false, false);
    } else {
      aiResponse.value = {
        aiRecordId: null,
        suggestions: [],
        provider: 'backend',
        model: 'not-required',
        fallbackUsed: false,
        backgroundSummary: '当前排班窗口已覆盖，暂不需要 AI 重排。',
        knowledgeSources: []
      };
    }
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

async function resetScheduleFilters() {
  scheduleFilter.departmentId = '';
  scheduleFilter.doctorId = '';
  await loadSchedules();
}

function queryDoctors() {
  doctorKeyword.value = doctorKeyword.value.trim();
}

function resetDoctorSearch() {
  doctorKeyword.value = '';
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

async function queryDoctorEvents() {
  eventKeyword.value = eventKeyword.value.trim();
  await loadDoctorEvents();
}

function resetDoctorEventsSearch() {
  eventKeyword.value = '';
  eventTypeFilter.value = '';
}

async function loadAiReplanPreview(showFeedback = false, force = showFeedback) {
  suggestionLoading.value = true;
  try {
    aiForm.startDate = addDays(todayIso(), REPLAN_WINDOW_START_OFFSET);
    aiForm.days = REPLAN_WINDOW_DAYS;
    aiResponse.value = await getAiReplanPreview({
      departmentId: aiForm.departmentId || undefined,
      baseVisits: aiForm.baseVisits,
      weekdayPeak: aiForm.weekdayPeak,
      weekdayIncrease: aiForm.weekdayIncrease,
      morningPeak: aiForm.morningPeak,
      morningIncrease: aiForm.morningIncrease,
      force
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
  const rooms = aiRooms.value.length > 0 ? aiRooms.value : [{ roomId: '', roomName: '' }];
  for (let day = 0; day < aiForm.days; day += 1) {
    const workDate = addDays(aiForm.startDate, day);
    for (const room of rooms) {
      for (const period of ['上午', '下午']) {
        let expectedVisits = aiForm.baseVisits;
        if (aiForm.weekdayPeak && isWeekday(workDate)) {
          expectedVisits *= 1 + aiForm.weekdayIncrease / 100;
        }
        if (aiForm.morningPeak && period === '上午') {
          expectedVisits *= 1 + aiForm.morningIncrease / 100;
        }
        items.push({
          departmentId: aiForm.departmentId,
          roomId: room.roomId,
          roomName: room.roomName,
          workDate,
          period,
          expectedVisits: Math.max(1, Math.round(expectedVisits)),
        });
      }
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
  const eventDoctor = doctors.value.find((doctor) => doctor.id === eventForm.doctorId);
  const replanDepartmentId = eventDoctor?.departmentId || '';
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
      ElMessage.success('安排已更新');
    }
    eventDialogVisible.value = false;
    resetDoctorEventForm();
    await loadDoctorEvents();
    if (payload.dates.some((date) => isWithinReplanWindow(date))) {
      aiForm.departmentId = replanDepartmentId;
      aiScheduleBoardWeekOffset.value = 0;
      publishedSuggestionIds.value = [];
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
    ElMessage.success('安排已更新');
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
      weeklyCapacity: 40
    };
  }
  return availability[doctorId];
}

function syncAvailabilityFromEvents() {
  doctors.value.forEach((doctor) => {
    availability[doctor.id] = {
      weeklyCapacity: ensureAvailability(doctor.id).weeklyCapacity
    };
  });
}

function uniqueDates(dates: string[]) {
  return Array.from(new Set((dates ?? []).filter(Boolean))).sort();
}

function buildScheduleBoardDays(startDate: string, count: number): ScheduleBoardDay[] {
  const start = startOfWeekMonday(startDate || todayIso());
  return Array.from({ length: Math.max(1, count) }, (_, index) => {
    const date = addDays(start, index);
    const native = new Date(`${date}T00:00:00`);
    const day = native.getDay();
    return {
      date,
      weekday: SCHEDULE_WEEKDAY_LABELS[day],
      monthDay: date.slice(5).replace('-', '/'),
      weekend: day === 0 || day === 6
    };
  });
}

function startOfWeekMonday(isoDate: string) {
  const date = new Date(`${isoDate}T00:00:00`);
  const day = date.getDay();
  const offset = day === 0 ? -6 : 1 - day;
  return addDays(isoDate, offset);
}

function buildScheduleBoardRows(days: ScheduleBoardDay[], includeAiSuggestions: boolean): ScheduleBoardDisplayRow[] {
  const dateSet = new Set(days.map((day) => day.date));
  const doctorMap = new Map<string, { doctorId: string; doctorName: string; subtitle: string; departmentId: string; roomName: string }>();
  const entries = new Map<string, ScheduleBoardEntry[]>();
  const selectedDepartmentId = includeAiSuggestions ? aiForm.departmentId : scheduleFilter.departmentId;
  const selectedDoctor = includeAiSuggestions ? '' : scheduleFilter.doctorId;

  const ensureDoctor = (doctorId: string, doctorName: string, departmentId: string, roomName = '') => {
    if (!doctorId || doctorMap.has(doctorId)) return;
    const doctor = doctors.value.find((item) => item.id === doctorId);
    doctorMap.set(doctorId, {
      doctorId,
      doctorName: doctor?.name || doctorName || doctorId,
      departmentId: doctor?.departmentId || departmentId,
      roomName: doctor?.roomName || roomName || '',
      subtitle: doctor?.roomName || roomName || departmentName(doctor?.departmentId || departmentId)
    });
  };

  const pushEntry = (doctorId: string, date: string, entry: ScheduleBoardEntry) => {
    const key = `${doctorId}:${date}`;
    const items = entries.get(key) ?? [];
    items.push(entry);
    entries.set(key, items);
  };

  schedules.value
    .filter((schedule) => schedule.status === 'PUBLISHED')
    .filter((schedule) => dateSet.has(schedule.workDate))
    .filter((schedule) => !selectedDepartmentId || schedule.departmentId === selectedDepartmentId)
    .filter((schedule) => !selectedDoctor || schedule.doctorId === selectedDoctor)
    .forEach((schedule) => {
      ensureDoctor(schedule.doctorId, schedule.doctorName, schedule.departmentId, schedule.roomName || '');
      pushEntry(schedule.doctorId, schedule.workDate, {
        id: schedule.id,
        source: 'published',
        period: schedule.period,
        periodKey: schedulePeriodKey(schedule.period),
        timeRange: scheduleTimeRange(schedule.period),
        roomName: schedule.roomName || '',
        capacity: schedule.capacity,
        available: schedule.available,
        published: true
      });
    });

  doctorEvents.value.forEach((event) => {
    const doctor = doctors.value.find((item) => item.id === event.doctorId);
    const departmentId = doctor?.departmentId || '';
    if (selectedDepartmentId && departmentId !== selectedDepartmentId) return;
    if (selectedDoctor && event.doctorId !== selectedDoctor) return;
    event.dates
      .filter((date) => dateSet.has(date))
      .forEach((date) => {
        event.periods.forEach((period) => {
          ensureDoctor(event.doctorId, event.doctorName, departmentId, doctor?.roomName || event.departmentName || '');
          pushEntry(event.doctorId, date, {
            id: `${event.id}-${date}-${period}`,
            source: 'event',
            period,
            periodKey: schedulePeriodKey(period),
            timeRange: scheduleTimeRange(period),
            roomName: '',
            capacity: 0,
            available: 0,
            published: true,
            eventType: event.eventType,
            note: event.note || event.departmentName
          });
        });
      });
  });

  if (includeAiSuggestions) {
    suggestions.value
      .filter((suggestion) => dateSet.has(suggestion.workDate))
      .filter((suggestion) => !selectedDepartmentId || suggestion.departmentId === selectedDepartmentId)
      .forEach((suggestion) => {
        ensureDoctor(suggestion.doctorId, suggestion.doctorName, suggestion.departmentId, suggestion.roomName || '');
        pushEntry(suggestion.doctorId, suggestion.workDate, {
          id: suggestion.suggestionId,
          source: 'ai',
          period: suggestion.period,
          periodKey: schedulePeriodKey(suggestion.period),
          timeRange: scheduleTimeRange(suggestion.period),
          roomName: suggestion.roomName || '',
          capacity: suggestion.capacity,
          available: suggestion.capacity,
          published: isSuggestionPublished(suggestion.suggestionId)
        });
      });

    if (selectedDepartmentId) {
      aiDoctors.value.forEach((doctor) => ensureDoctor(doctor.id, doctor.name, doctor.departmentId, doctor.roomName || ''));
    }
  } else if (selectedDepartmentId || selectedDoctor) {
    filteredScheduleDoctors.value.forEach((doctor) => ensureDoctor(doctor.id, doctor.name, doctor.departmentId, doctor.roomName || ''));
  }

  const doctorRows = Array.from(doctorMap.values())
    .sort(compareScheduleBoardDoctors)
    .map((doctor) => ({
      kind: 'doctor' as const,
      doctorId: doctor.doctorId,
      doctorName: doctor.doctorName,
      subtitle: doctor.subtitle,
      initials: doctorInitials(doctor.doctorName),
      cells: days.map((day) => ({
        date: day.date,
        weekend: day.weekend,
        entries: (entries.get(`${doctor.doctorId}:${day.date}`) ?? []).sort(compareScheduleEntries)
      }))
    }));
  const groupedRows: ScheduleBoardDisplayRow[] = [];
  let currentDepartmentId = '';
  let currentGroup: ScheduleBoardGroupRow | null = null;
  doctorRows.forEach((row, index) => {
    const doctor = doctorMap.get(row.doctorId);
    const departmentId = doctor?.departmentId || '';
    if (departmentId !== currentDepartmentId) {
      currentDepartmentId = departmentId;
      currentGroup = {
        kind: 'group',
        id: `group-${departmentId || index}`,
        departmentId,
        departmentName: departmentName(departmentId),
        doctorCount: 0
      };
      groupedRows.push(currentGroup);
    }
    if (currentGroup) currentGroup.doctorCount += 1;
    if (!isScheduleDepartmentCollapsed(departmentId)) {
      groupedRows.push(row);
    }
  });
  return groupedRows;
}

function isScheduleDepartmentCollapsed(departmentId: string) {
  return collapsedScheduleDepartments.value.includes(departmentId);
}

function toggleScheduleDepartment(departmentId: string) {
  if (isScheduleDepartmentCollapsed(departmentId)) {
    collapsedScheduleDepartments.value = collapsedScheduleDepartments.value.filter((id) => id !== departmentId);
  } else {
    collapsedScheduleDepartments.value = [...collapsedScheduleDepartments.value, departmentId];
  }
}

function compareScheduleBoardDoctors(
  left: { doctorName: string; departmentId: string; roomName: string },
  right: { doctorName: string; departmentId: string; roomName: string }
) {
  return (
    departmentName(left.departmentId).localeCompare(departmentName(right.departmentId)) ||
    (left.roomName || '').localeCompare(right.roomName || '') ||
    left.doctorName.localeCompare(right.doctorName)
  );
}

function scheduleBoardSlotEntries(entries: ScheduleBoardEntry[], period: string) {
  return entries.filter((entry) => entry.period === period).sort(compareScheduleEntries);
}

function scheduleShiftClasses(item: ScheduleBoardEntry) {
  return [
    `schedule-shift--${item.periodKey}`,
    item.source === 'ai' ? 'schedule-shift--ai' : '',
    item.published && item.source === 'ai' ? 'schedule-shift--published-ai' : '',
    item.source === 'event' ? 'schedule-shift--event' : '',
    item.eventType === 'SURGERY' ? 'schedule-shift--surgery' : '',
    item.eventType === 'LEAVE' ? 'schedule-shift--leave' : ''
  ];
}

function compareScheduleEntries(left: ScheduleBoardEntry, right: ScheduleBoardEntry) {
  return schedulePeriodOrder(left.period) - schedulePeriodOrder(right.period) || left.source.localeCompare(right.source);
}

function scheduleBoardStyle(dayCount: number) {
  return {
    gridTemplateColumns: `190px repeat(${dayCount}, minmax(150px, 1fr))`
  };
}

function moveScheduleBoardWeek(board: 'ai' | 'manual', delta: number) {
  if (board === 'ai') {
    aiScheduleBoardWeekOffset.value += delta;
  } else {
    manualScheduleBoardWeekOffset.value += delta;
  }
}

function resetScheduleBoardWeek(board: 'ai' | 'manual') {
  if (board === 'ai') {
    aiScheduleBoardWeekOffset.value = 0;
  } else {
    manualScheduleBoardWeekOffset.value = 0;
  }
}

function scheduleBoardRangeLabel(days: ScheduleBoardDay[]) {
  if (days.length === 0) return '';
  return `${days[0].date} - ${days[days.length - 1].date}`;
}

function schedulePeriodKey(period: string): ScheduleBoardEntry['periodKey'] {
  if (period === '下午') return 'afternoon';
  return 'morning';
}

function schedulePeriodOrder(period: string) {
  if (period === '上午') return 0;
  if (period === '下午') return 1;
  return 9;
}

function scheduleTimeRange(period: string) {
  if (period === '下午') return '14:00-17:00';
  return '08:00-12:00';
}

function doctorInitials(name: string) {
  const clean = (name || '').trim();
  return clean ? clean.slice(Math.max(0, clean.length - 2)) : '医生';
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

function isWeekday(isoDate: string) {
  const day = new Date(`${isoDate}T00:00:00`).getDay();
  return day >= 1 && day <= 5;
}

function disablePastAndToday(date: Date) {
  const earliest = new Date();
  earliest.setHours(0, 0, 0, 0);
  earliest.setDate(earliest.getDate() + REPLAN_WINDOW_START_OFFSET);
  return date < earliest;
}

function needsAutomaticReplan() {
  const start = addDays(todayIso(), REPLAN_WINDOW_START_OFFSET);
  const end = addDays(start, REPLAN_WINDOW_DAYS - 1);
  const doctorDepartmentIds = new Set(schedulableDoctors.value.map((doctor) => doctor.departmentId));
  const departmentIds = aiForm.departmentId
    ? [aiForm.departmentId]
    : schedulableDepartments.value.map((department) => department.id).filter((id) => doctorDepartmentIds.has(id));

  return departmentIds.some((departmentId) => {
    const windowSchedules = schedules.value.filter(
      (schedule) =>
        schedule.departmentId === departmentId &&
        schedule.status === 'PUBLISHED' &&
        schedule.workDate >= start &&
        schedule.workDate <= end
    );
    for (let day = 0; day < REPLAN_WINDOW_DAYS; day += 1) {
      const workDate = addDays(start, day);
      if (!coversSchedulePeriod(windowSchedules, workDate, '上午') || !coversSchedulePeriod(windowSchedules, workDate, '下午')) {
        return true;
      }
    }
    return windowSchedules.some((schedule) => hasDoctorEventConflict(schedule, start, end));
  });
}

function coversSchedulePeriod(items: Schedule[], workDate: string, period: string) {
  return items.some((schedule) => schedule.workDate === workDate && schedule.period === period);
}

function hasDoctorEventConflict(schedule: Schedule, start: string, end: string) {
  return doctorEvents.value.some(
    (event) =>
      event.doctorId === schedule.doctorId &&
      event.dates.some((date) => date >= start && date <= end && date === schedule.workDate) &&
      event.periods.some((period) => period === schedule.period)
  );
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

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
  flex-direction: column;
  gap: 8px;
}

.query-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.page-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
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

.ai-summary {
  margin: 12px 0 0;
  padding-top: 10px;
  border-top: 1px solid #e5e7eb;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.schedule-visual-card {
  overflow: hidden;
}

.schedule-board-tools {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 14px;
  flex-wrap: wrap;
}

.schedule-legend {
  display: flex;
  align-items: center;
  gap: 14px;
  color: #64748b;
  font-size: 12px;
  white-space: nowrap;
}

.schedule-legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.schedule-week-nav {
  display: flex;
  align-items: center;
  gap: 6px;
}

.schedule-week-nav span {
  min-width: 176px;
  color: #334155;
  font-size: 12px;
  text-align: center;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  display: inline-block;
}

.legend-dot--published {
  background: #9bdcf2;
  border-left: 3px solid #2f91b4;
}

.legend-dot--ai {
  background: #fff3c4;
  border: 1px dashed #d97706;
}

.legend-dot--surgery {
  background: #ffe4e6;
  border: 1px solid #e11d48;
}

.legend-dot--leave {
  background: #e5e7eb;
  border: 1px solid #94a3b8;
}

.schedule-board {
  display: grid;
  max-height: 560px;
  overflow: auto;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.schedule-board-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}

.schedule-board__corner,
.schedule-board__day,
.schedule-board__person,
.schedule-board__cell {
  min-width: 0;
  border-right: 1px solid #e5e7eb;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}

.schedule-board__corner {
  position: sticky;
  top: 0;
  left: 0;
  z-index: 5;
  min-height: 82px;
  padding: 12px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  background:
    linear-gradient(27deg, transparent 49.2%, #e5e7eb 50%, transparent 50.8%),
    #f8fafc;
  color: #64748b;
  font-size: 13px;
}

.schedule-board__corner strong {
  align-self: flex-start;
  color: #334155;
}

.schedule-board__day {
  position: sticky;
  top: 0;
  z-index: 3;
  min-height: 82px;
  padding: 10px 10px 8px;
  text-align: center;
  background: #f8fafc;
}

.schedule-board__day strong {
  display: inline-block;
  margin-right: 8px;
  color: #111827;
  font-size: 14px;
}

.schedule-board__day > span {
  color: #334155;
  font-weight: 700;
}

.schedule-board__day em {
  display: block;
  margin-top: 4px;
  color: #0891b2;
  font-size: 12px;
  font-style: normal;
}

.schedule-board__day--weekend em {
  color: #64748b;
}

.schedule-board__ticks {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin-top: 10px;
  color: #94a3b8;
  font-size: 11px;
}

.schedule-board__person {
  position: sticky;
  left: 0;
  z-index: 2;
  min-height: 96px;
  padding: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
}

.schedule-board__group {
  position: sticky;
  left: 0;
  z-index: 2;
  grid-column: 1 / -1;
  min-height: 38px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-right: 1px solid #e5e7eb;
  border-bottom: 1px solid #dbe4ee;
  background: #eef6f8;
}

.schedule-board__group strong {
  color: #0f766e;
  font-size: 13px;
}

.schedule-board__group span {
  color: #64748b;
  font-size: 12px;
}

.schedule-board__group-toggle {
  border: 0;
  padding: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: transparent;
  cursor: pointer;
}

.schedule-board__group-toggle span {
  width: 16px;
  color: #0f766e;
  font-size: 11px;
}

.schedule-board__group-toggle:hover strong {
  color: #0d9488;
}

.doctor-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  background: linear-gradient(135deg, #dbeafe, #ccfbf1);
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.schedule-board__person strong {
  display: block;
  color: #111827;
  font-size: 14px;
}

.schedule-board__person em {
  display: block;
  margin-top: 4px;
  color: #94a3b8;
  font-size: 12px;
  font-style: normal;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.schedule-board__cell {
  position: relative;
  min-height: 150px;
  padding: 8px;
  display: grid;
  grid-template-rows: repeat(2, minmax(62px, 1fr));
  gap: 6px;
}

.schedule-board__cell--weekend {
  background:
    repeating-linear-gradient(135deg, rgb(148 163 184 / 10%) 0 8px, transparent 8px 16px),
    #fff;
}

.schedule-board__period-slot {
  min-height: 62px;
  border: 1px dashed #e2e8f0;
  border-radius: 6px;
  padding: 4px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: rgb(248 250 252 / 58%);
}

.schedule-board__empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #cbd5e1;
  font-size: 22px;
  line-height: 1;
  pointer-events: none;
}

.schedule-shift {
  position: relative;
  z-index: 1;
  min-height: 72px;
  border-radius: 6px;
  padding: 8px 8px 7px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  overflow: hidden;
  border-left: 4px solid #2f91b4;
  background: #d9f1fb;
  color: #0f172a;
  box-shadow: 0 1px 2px rgb(15 23 42 / 8%);
}

.schedule-shift--morning {
  border-left-color: #4aa564;
  background: #ddf7dc;
}

.schedule-shift--afternoon {
  border-left-color: #2f91b4;
  background: #d9f1fb;
}

.schedule-shift--full {
  border-left-color: #14b8a6;
  background: #ccfbf1;
}

.schedule-shift--ai {
  border: 1px dashed #d97706;
  border-left: 4px solid #f59e0b;
  background: #fff3c4;
}

.schedule-shift--published-ai {
  border-style: solid;
  background: #ecfdf5;
  border-color: #22c55e;
}

.schedule-shift--event {
  box-shadow: none;
}

.schedule-shift--surgery {
  border-left-color: #e11d48;
  background: #ffe4e6;
}

.schedule-shift--leave {
  border-left-color: #94a3b8;
  background: #e5e7eb;
}

.schedule-shift__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.schedule-shift__head strong {
  min-width: 0;
  color: #111827;
  font-size: 13px;
  line-height: 1.2;
}

.schedule-shift__head span {
  height: 18px;
  padding: 0 5px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  background: #f59e0b;
  color: #fff;
  font-size: 11px;
}

.schedule-shift--published-ai .schedule-shift__head span {
  background: #16a34a;
}

.schedule-shift p,
.schedule-shift em,
.schedule-shift small {
  margin: 0;
  color: #334155;
  font-size: 12px;
  line-height: 1.25;
  font-style: normal;
}

.schedule-shift em,
.schedule-shift small {
  color: #64748b;
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

  .page-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
