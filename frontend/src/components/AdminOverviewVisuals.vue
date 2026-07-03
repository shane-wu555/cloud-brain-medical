<template>
  <div class="overview-dashboard">
    <section class="overview-hero">
      <div class="overview-hero__copy">
        <span class="overview-hero__eyebrow">智慧运营驾驶舱</span>
        <h2>把挂号、排班、人员和风险放进同一张首页</h2>
        <p>用更直观的图表快速看清今天的门诊热度、未来 7 日号源供给和待处理运营风险。</p>
      </div>
      <div class="overview-hero__chips">
        <div class="hero-chip">
          <span>未来 7 日号源</span>
          <strong>{{ totalSevenDayCapacity }}</strong>
          <em>基于当前排班汇总</em>
        </div>
        <div class="hero-chip">
          <span>启用账号</span>
          <strong>{{ activeAccountCount }}/{{ staffAccounts.length || 0 }}</strong>
          <em>{{ activeAccountRate }}% 处于启用状态</em>
        </div>
        <div class="hero-chip">
          <span>重点风险</span>
          <strong>{{ totalRiskSignals }}</strong>
          <em>待确认 AI 排班、事件、停用账号</em>
        </div>
      </div>
    </section>

    <div class="stat-strip">
      <article
        v-for="card in kpiCards"
        :key="card.label"
        :class="['stat-card', `stat-card--${card.tone}`]"
      >
        <div class="stat-card__top">
          <span>{{ card.label }}</span>
          <em>{{ card.badge }}</em>
        </div>
        <strong>{{ card.value }}</strong>
        <p>{{ card.note }}</p>
      </article>
    </div>

    <div class="overview-grid">
      <section class="viz-card viz-card--wide">
        <div class="card-head">
          <div>
            <h3>科室挂号负载</h3>
            <p>柱状图展示当前重点科室的接诊热度。</p>
          </div>
          <div class="pill-row">
            <span v-for="item in topDepartmentHighlights" :key="item.name" class="metric-pill">
              {{ item.name }} {{ item.value }}
            </span>
          </div>
        </div>
        <div ref="departmentChartRef" class="chart-surface"></div>
      </section>

      <section class="viz-card">
        <div class="card-head">
          <div>
            <h3>账号角色分布</h3>
            <p>环形图展示当前后台账号构成。</p>
          </div>
        </div>
        <div ref="roleChartRef" class="chart-surface chart-surface--compact"></div>
      </section>

      <section class="viz-card viz-card--wide">
        <div class="card-head">
          <div>
            <h3>未来 7 日号源趋势</h3>
            <p>折线面积图对比总号源、已预约与可预约容量。</p>
          </div>
        </div>
        <div ref="trendChartRef" class="chart-surface"></div>
      </section>

      <section class="viz-card">
        <div class="card-head">
          <div>
            <h3>运营画像雷达</h3>
            <p>从热度、压力、覆盖率和排班完备度观察整体状态。</p>
          </div>
        </div>
        <div ref="radarChartRef" class="chart-surface chart-surface--compact"></div>
      </section>
    </div>

    <div class="overview-bottom">
      <section class="viz-card">
        <div class="card-head">
          <div>
            <h3>科室资源排行</h3>
            <p>按排班容量和医生配置查看当前资源重心。</p>
          </div>
        </div>
        <div v-if="departmentRanking.length" class="ranking-list">
          <article v-for="item in departmentRanking" :key="item.name" class="ranking-item">
            <div class="ranking-item__head">
              <strong>{{ item.name }}</strong>
              <span>{{ item.capacity }} 号源</span>
            </div>
            <div class="ranking-bar">
              <span :style="{ width: `${item.rate}%`, background: item.fill }"></span>
            </div>
            <div class="ranking-item__meta">
              <em>{{ item.doctorCount }} 名医生</em>
              <em>{{ item.scheduleCount }} 个班次</em>
            </div>
          </article>
        </div>
        <div v-else class="empty-state">暂无可展示的排班资源数据</div>
      </section>

      <section class="viz-card viz-card--tasks">
        <div class="card-head">
          <div>
            <h3>待处理事项</h3>
            <p>把最需要管理员立即操作的模块放在右侧。</p>
          </div>
        </div>
        <div class="task-grid">
          <button
            v-for="item in actionCards"
            :key="item.label"
            :class="['task-card', `task-card--${item.tone}`]"
            @click="emit('navigate', item.page)"
          >
            <span class="task-card__label">{{ item.label }}</span>
            <strong>{{ item.count }}</strong>
            <em>{{ item.note }}</em>
            <span class="task-card__action">进入模块</span>
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { BarChart, LineChart, PieChart, RadarChart } from 'echarts/charts';
import {
  GridComponent,
  LegendComponent,
  RadarComponent,
  TitleComponent,
  TooltipComponent
} from 'echarts/components';
import { use, init, graphic, type ECharts } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import type { StaffAccount } from '../api/auth';
import type { DashboardOverview } from '../api/dashboard';
import type { Doctor, DoctorEvent, Schedule } from '../api/doctor';

type OverviewPageKey = 'aiSchedule' | 'manualSchedule' | 'doctorProfile' | 'doctorEvents';
type ChartKey = 'department' | 'role' | 'trend' | 'radar';

const props = defineProps<{
  active: boolean;
  overview: DashboardOverview | null;
  schedules: Schedule[];
  doctors: Doctor[];
  doctorEvents: DoctorEvent[];
  staffAccounts: StaffAccount[];
  pendingSuggestions: number;
}>();

const emit = defineEmits<{
  navigate: [page: OverviewPageKey];
}>();

const departmentChartRef = ref<HTMLDivElement | null>(null);
const roleChartRef = ref<HTMLDivElement | null>(null);
const trendChartRef = ref<HTMLDivElement | null>(null);
const radarChartRef = ref<HTMLDivElement | null>(null);

use([BarChart, LineChart, PieChart, RadarChart, GridComponent, LegendComponent, RadarComponent, TitleComponent, TooltipComponent, CanvasRenderer]);

const chartInstances = new Map<ChartKey, ECharts>();
let renderFrame = 0;

const ROLE_LABELS: Record<string, string> = {
  ADMIN: '管理员',
  CASHIER: '收费窗口',
  OUTPATIENT_DOCTOR: '门诊医生',
  CHECK_DOCTOR: '检查医生',
  LAB_DOCTOR: '检验医生',
  DISPOSAL_DOCTOR: '处置医生',
  PHARMACY_STAFF: '药房人员'
};

const PIE_COLORS = ['#0899a5', '#14b8a6', '#38bdf8', '#6366f1', '#f59e0b', '#f97316', '#ef4444'];
const RANKING_FILLS = [
  'linear-gradient(90deg, #0fbac6 0%, #0899a5 100%)',
  'linear-gradient(90deg, #27c4a5 0%, #0f766e 100%)',
  'linear-gradient(90deg, #59b7ff 0%, #2563eb 100%)',
  'linear-gradient(90deg, #f8c44d 0%, #f97316 100%)',
  'linear-gradient(90deg, #fb7185 0%, #e11d48 100%)'
];
const BAR_GRADIENTS: Array<[string, string]> = [
  ['#12c2c8', '#0f766e'],
  ['#3ec7f4', '#0284c7'],
  ['#f8cd65', '#f97316'],
  ['#8b8cfb', '#6366f1'],
  ['#fb7185', '#e11d48']
];

const departmentLoadData = computed(() => {
  const direct = props.overview?.departmentLoads ?? [];
  if (direct.length) return direct;

  const loadMap = new Map<string, number>();
  props.schedules.forEach((schedule) => {
    if (schedule.status === 'SUSPENDED') return;
    const name = schedule.departmentName || '未命名科室';
    loadMap.set(name, (loadMap.get(name) ?? 0) + (schedule.booked || 0));
  });

  return Array.from(loadMap.entries())
    .map(([name, value]) => ({ name, value }))
    .sort((left, right) => right.value - left.value)
    .slice(0, 6);
});

const roleDistribution = computed(() =>
  Array.from(
    props.staffAccounts.reduce((map, account) => {
      const label = ROLE_LABELS[account.role] ?? account.role;
      map.set(label, (map.get(label) ?? 0) + 1);
      return map;
    }, new Map<string, number>())
  ).map(([name, value], index) => ({
    name,
    value,
    color: PIE_COLORS[index % PIE_COLORS.length]
  }))
);

const scheduleTrend = computed(() =>
  Array.from({ length: 7 }, (_, index) => addDays(todayIso(), index)).map((date) => {
    const items = props.schedules.filter((schedule) => schedule.workDate === date && schedule.status !== 'SUSPENDED');
    return {
      date,
      label: formatMonthDay(date),
      capacity: items.reduce((sum, item) => sum + (item.capacity || 0), 0),
      booked: items.reduce((sum, item) => sum + (item.booked || 0), 0),
      available: items.reduce((sum, item) => sum + (item.available || 0), 0)
    };
  })
);

const departmentRanking = computed(() => {
  const rankingMap = new Map<
    string,
    { name: string; capacity: number; scheduleCount: number; doctorIds: Set<string> }
  >();

  props.doctors.forEach((doctor) => {
    const key = doctor.departmentId || doctor.departmentName || doctor.id;
    const existing = rankingMap.get(key) ?? {
      name: doctor.departmentName || '未命名科室',
      capacity: 0,
      scheduleCount: 0,
      doctorIds: new Set<string>()
    };
    existing.doctorIds.add(doctor.id);
    rankingMap.set(key, existing);
  });

  props.schedules.forEach((schedule) => {
    if (schedule.status === 'SUSPENDED') return;
    const key = schedule.departmentId || schedule.departmentName || schedule.id;
    const existing = rankingMap.get(key) ?? {
      name: schedule.departmentName || '未命名科室',
      capacity: 0,
      scheduleCount: 0,
      doctorIds: new Set<string>()
    };
    existing.capacity += schedule.capacity || 0;
    existing.scheduleCount += 1;
    if (schedule.doctorId) existing.doctorIds.add(schedule.doctorId);
    rankingMap.set(key, existing);
  });

  const rows = Array.from(rankingMap.values()).filter((item) => item.capacity > 0 || item.doctorIds.size > 0);
  const maxCapacity = Math.max(...rows.map((item) => item.capacity), 1);

  return rows
    .sort((left, right) => right.capacity - left.capacity || right.doctorIds.size - left.doctorIds.size)
    .slice(0, 5)
    .map((item, index) => ({
      name: item.name,
      capacity: item.capacity,
      scheduleCount: item.scheduleCount,
      doctorCount: item.doctorIds.size,
      rate: Math.max(10, Math.round((item.capacity / maxCapacity) * 100)),
      fill: RANKING_FILLS[index % RANKING_FILLS.length]
    }));
});

const activeAccountCount = computed(() => props.staffAccounts.filter((account) => account.active).length);
const activeAccountRate = computed(() => safePercent(activeAccountCount.value, props.staffAccounts.length));
const totalSevenDayCapacity = computed(() =>
  scheduleTrend.value.reduce((sum, item) => sum + item.capacity, 0)
);
const inactiveAccountCount = computed(() => Math.max(props.staffAccounts.length - activeAccountCount.value, 0));
const totalRiskSignals = computed(() => props.pendingSuggestions + props.doctorEvents.length + inactiveAccountCount.value);
const scheduleCompletionRate = computed(() => {
  const total = props.schedules.length + props.pendingSuggestions;
  return total === 0 ? 100 : Math.round((props.schedules.length / total) * 100);
});

const topDepartmentHighlights = computed(() => departmentLoadData.value.slice(0, 3));

const kpiCards = computed(() => {
  const todayAppointments = props.overview?.todayAppointments ?? 0;
  const waitingVisits = props.overview?.waitingVisits ?? 0;
  const activeDoctors = props.overview?.activeDoctors ?? 0;
  const aiTriageCount = props.overview?.aiTriageCount ?? 0;

  return [
    {
      label: '今日挂号',
      value: todayAppointments,
      note: '覆盖线上与线下全部挂号渠道',
      badge: `${departmentLoadData.value.length || 0} 个重点科室`,
      tone: 'teal'
    },
    {
      label: '待接诊',
      value: waitingVisits,
      note: '当前候诊队列需要持续消化',
      badge: `候诊压力 ${safePercent(waitingVisits, todayAppointments || waitingVisits || 1)}%`,
      tone: 'cyan'
    },
    {
      label: '出诊医生',
      value: activeDoctors,
      note: '结合已发布排班统计当前在岗医生',
      badge: `覆盖率 ${safePercent(activeDoctors, props.doctors.length || activeDoctors || 1)}%`,
      tone: 'amber'
    },
    {
      label: 'AI 问诊',
      value: aiTriageCount,
      note: '用于辅助诊前记录与分诊判断',
      badge: `覆盖率 ${safePercent(aiTriageCount, todayAppointments || aiTriageCount || 1)}%`,
      tone: 'rose'
    }
  ];
});

const radarMetrics = computed(() => {
  const todayAppointments = props.overview?.todayAppointments ?? 0;
  const waitingVisits = props.overview?.waitingVisits ?? 0;
  const activeDoctors = props.overview?.activeDoctors ?? 0;
  const aiTriageCount = props.overview?.aiTriageCount ?? 0;

  return [
    { name: '挂号热度', value: clampPercent((todayAppointments / 60) * 100) },
    { name: '候诊压力', value: clampPercent((waitingVisits / 20) * 100) },
    { name: '医生覆盖', value: clampPercent((activeDoctors / Math.max(props.doctors.length || 1, 1)) * 100) },
    { name: 'AI 覆盖', value: clampPercent((aiTriageCount / Math.max(todayAppointments || 1, 1)) * 100) },
    { name: '账号稳定', value: clampPercent((activeAccountCount.value / Math.max(props.staffAccounts.length || 1, 1)) * 100) },
    { name: '排班完备', value: clampPercent(scheduleCompletionRate.value) }
  ];
});

const actionCards = computed(() => [
  {
    label: 'AI 排班建议',
    count: props.pendingSuggestions,
    note: '查看待确认的智能排班结果',
    page: 'aiSchedule' as const,
    tone: 'amber'
  },
  {
    label: '排班信息',
    count: props.schedules.length,
    note: '进入人工排班与班次调整模块',
    page: 'manualSchedule' as const,
    tone: 'teal'
  },
  {
    label: '医生账号与档案',
    count: props.staffAccounts.length,
    note: '统一维护人员账号与医生信息',
    page: 'doctorProfile' as const,
    tone: 'cyan'
  },
  {
    label: '请假 / 手术事件',
    count: props.doctorEvents.length,
    note: '检查影响排班的特殊事件安排',
    page: 'doctorEvents' as const,
    tone: 'rose'
  }
]);

watch(
  [
    () => props.active,
    departmentLoadData,
    roleDistribution,
    scheduleTrend,
    radarMetrics
  ],
  () => {
    if (props.active) {
      queueRenderCharts();
    }
  },
  { deep: true }
);

onMounted(() => {
  if (props.active) queueRenderCharts();
  window.addEventListener('resize', resizeCharts);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts);
  cancelAnimationFrame(renderFrame);
  chartInstances.forEach((chart) => chart.dispose());
  chartInstances.clear();
});

function queueRenderCharts() {
  cancelAnimationFrame(renderFrame);
  renderFrame = requestAnimationFrame(() => {
    nextTick(() => {
      renderDepartmentChart();
      renderRoleChart();
      renderTrendChart();
      renderRadarChart();
      resizeCharts();
    });
  });
}

function resizeCharts() {
  chartInstances.forEach((chart) => chart.resize());
}

function ensureChart(key: ChartKey, element: HTMLDivElement | null) {
  if (!element) return null;
  const existing = chartInstances.get(key);
  if (existing) return existing;

  const chart = init(element);
  chartInstances.set(key, chart);
  return chart;
}

function renderDepartmentChart() {
  const chart = ensureChart('department', departmentChartRef.value);
  if (!chart) return;

  chart.setOption(
    {
      animationDuration: 700,
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        backgroundColor: '#0f172a',
        borderWidth: 0,
        textStyle: { color: '#e2e8f0' }
      },
      grid: { left: 10, right: 18, top: 24, bottom: 28, containLabel: true },
      xAxis: {
        type: 'category',
        data: departmentLoadData.value.map((item) => item.name),
        axisTick: { show: false },
        axisLine: { lineStyle: { color: '#d7e3ec' } },
        axisLabel: { color: '#475569', interval: 0 }
      },
      yAxis: {
        type: 'value',
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#94a3b8' },
        splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.18)' } }
      },
      series: [
        {
          name: '挂号量',
          type: 'bar',
          barWidth: 28,
          showBackground: true,
          backgroundStyle: {
            color: 'rgba(148, 163, 184, 0.08)',
            borderRadius: [10, 10, 0, 0]
          },
          label: {
            show: true,
            position: 'top',
            color: '#0f172a',
            fontWeight: 700
          },
          data: departmentLoadData.value.map((item, index) => ({
            value: item.value,
            itemStyle: {
              color: buildBarGradient(index),
              borderRadius: [10, 10, 0, 0]
            }
          }))
        }
      ]
    },
    true
  );
}

function renderRoleChart() {
  const chart = ensureChart('role', roleChartRef.value);
  if (!chart) return;

  const hasData = roleDistribution.value.some((item) => item.value > 0);
  const seriesData = hasData
    ? roleDistribution.value.map((item) => ({
        name: item.name,
        value: item.value,
        itemStyle: { color: item.color }
      }))
    : [{ name: '暂无数据', value: 1, itemStyle: { color: '#cbd5e1' } }];

  chart.setOption(
    {
      animationDuration: 700,
      title: {
        text: `${props.staffAccounts.length || 0}`,
        subtext: '员工账号',
        left: 'center',
        top: '38%',
        textStyle: { color: '#0f172a', fontSize: 26, fontWeight: 700 },
        subtextStyle: { color: '#64748b', fontSize: 12, lineHeight: 16 }
      },
      tooltip: {
        trigger: 'item',
        backgroundColor: '#0f172a',
        borderWidth: 0,
        textStyle: { color: '#e2e8f0' }
      },
      legend: {
        show: hasData,
        bottom: 0,
        itemWidth: 10,
        itemHeight: 10,
        textStyle: { color: '#475569', fontSize: 12 }
      },
      series: [
        {
          name: '账号角色分布',
          type: 'pie',
          radius: ['48%', '72%'],
          center: ['50%', '42%'],
          avoidLabelOverlap: false,
          itemStyle: { borderColor: '#fff', borderWidth: 4 },
          label: hasData
            ? {
                formatter: '{name|{b}}\n{percent|{d}%}',
                rich: {
                  name: { color: '#334155', fontSize: 11, lineHeight: 16, fontWeight: 600 },
                  percent: { color: '#64748b', fontSize: 11 }
                }
              }
            : { show: false },
          labelLine: { show: hasData, length: 10, length2: 10 },
          data: seriesData
        }
      ]
    },
    true
  );
}

function renderTrendChart() {
  const chart = ensureChart('trend', trendChartRef.value);
  if (!chart) return;

  chart.setOption(
    {
      animationDuration: 700,
      tooltip: {
        trigger: 'axis',
        backgroundColor: '#0f172a',
        borderWidth: 0,
        textStyle: { color: '#e2e8f0' }
      },
      legend: {
        top: 0,
        icon: 'roundRect',
        itemWidth: 12,
        itemHeight: 8,
        textStyle: { color: '#475569' }
      },
      grid: { left: 10, right: 18, top: 42, bottom: 24, containLabel: true },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: scheduleTrend.value.map((item) => item.label),
        axisTick: { show: false },
        axisLine: { lineStyle: { color: '#d7e3ec' } },
        axisLabel: { color: '#475569' }
      },
      yAxis: {
        type: 'value',
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#94a3b8' },
        splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.18)' } }
      },
      series: [
        {
          name: '总号源',
          type: 'line',
          smooth: true,
          symbolSize: 8,
          data: scheduleTrend.value.map((item) => item.capacity),
          lineStyle: { color: '#0899a5', width: 3 },
          itemStyle: { color: '#0899a5' },
          areaStyle: {
            color: new graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(8, 153, 165, 0.38)' },
              { offset: 1, color: 'rgba(8, 153, 165, 0.04)' }
            ])
          }
        },
        {
          name: '已预约',
          type: 'line',
          smooth: true,
          symbolSize: 7,
          data: scheduleTrend.value.map((item) => item.booked),
          lineStyle: { color: '#f59e0b', width: 2.5 },
          itemStyle: { color: '#f59e0b' }
        },
        {
          name: '可预约',
          type: 'line',
          smooth: true,
          symbolSize: 7,
          data: scheduleTrend.value.map((item) => item.available),
          lineStyle: { color: '#6366f1', width: 2, type: 'dashed' },
          itemStyle: { color: '#6366f1' }
        }
      ]
    },
    true
  );
}

function renderRadarChart() {
  const chart = ensureChart('radar', radarChartRef.value);
  if (!chart) return;

  chart.setOption(
    {
      animationDuration: 700,
      tooltip: {
        trigger: 'item',
        backgroundColor: '#0f172a',
        borderWidth: 0,
        textStyle: { color: '#e2e8f0' }
      },
      radar: {
        center: ['50%', '53%'],
        radius: '64%',
        splitNumber: 4,
        indicator: radarMetrics.value.map((item) => ({ name: item.name, max: 100 })),
        axisName: { color: '#334155', fontSize: 12 },
        axisLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.26)' } },
        splitLine: { lineStyle: { color: 'rgba(14, 165, 164, 0.16)' } },
        splitArea: {
          areaStyle: {
            color: ['rgba(240, 253, 250, 0.72)', 'rgba(239, 246, 255, 0.78)']
          }
        }
      },
      series: [
        {
          name: '运营画像',
          type: 'radar',
          data: [
            {
              value: radarMetrics.value.map((item) => item.value),
              areaStyle: { color: 'rgba(8, 153, 165, 0.28)' },
              lineStyle: { color: '#0899a5', width: 3 },
              itemStyle: { color: '#f59e0b' },
              symbol: 'circle',
              symbolSize: 7
            }
          ]
        }
      ]
    },
    true
  );
}

function buildBarGradient(index: number) {
  const [start, end] = BAR_GRADIENTS[index % BAR_GRADIENTS.length];
  return new graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: start },
    { offset: 1, color: end }
  ]);
}

function clampPercent(value: number) {
  return Math.max(0, Math.min(100, Math.round(value)));
}

function safePercent(numerator: number, denominator: number) {
  if (!denominator) return 0;
  return clampPercent((numerator / denominator) * 100);
}

function todayIso() {
  return new Date().toLocaleDateString('en-CA');
}

function addDays(isoDate: string, days: number) {
  const date = new Date(`${isoDate}T00:00:00`);
  date.setDate(date.getDate() + days);
  return date.toLocaleDateString('en-CA');
}

function formatMonthDay(isoDate: string) {
  const date = new Date(`${isoDate}T00:00:00`);
  return `${String(date.getMonth() + 1).padStart(2, '0')}/${String(date.getDate()).padStart(2, '0')}`;
}
</script>

<style scoped>
.overview-dashboard {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.overview-hero {
  position: relative;
  overflow: hidden;
  padding: 24px 24px 22px;
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
  gap: 20px;
  border-radius: 20px;
  background:
    radial-gradient(circle at 84% 18%, rgb(255 255 255 / 26%) 0 8%, transparent 8%),
    radial-gradient(circle at 92% 76%, rgb(255 255 255 / 14%) 0 9%, transparent 9%),
    linear-gradient(135deg, #0f766e 0%, #0899a5 48%, #f59e0b 100%);
  color: #fff;
  box-shadow: 0 18px 36px rgb(8 153 165 / 18%);
}

.overview-hero__copy h2 {
  margin: 8px 0 10px;
  font-size: 28px;
  line-height: 1.15;
}

.overview-hero__copy p {
  max-width: 660px;
  margin: 0;
  color: rgb(240 253 250 / 92%);
  font-size: 14px;
  line-height: 1.7;
}

.overview-hero__eyebrow {
  height: 28px;
  padding: 0 12px;
  border: 1px solid rgb(255 255 255 / 22%);
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  background: rgb(255 255 255 / 14%);
  color: #fef3c7;
  font-size: 12px;
  letter-spacing: 0.08em;
}

.overview-hero__chips {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.hero-chip {
  min-height: 118px;
  padding: 16px;
  border: 1px solid rgb(255 255 255 / 16%);
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background: linear-gradient(180deg, rgb(255 255 255 / 20%), rgb(255 255 255 / 10%));
  backdrop-filter: blur(10px);
}

.hero-chip span,
.hero-chip em {
  color: rgb(240 253 250 / 84%);
  font-size: 12px;
  font-style: normal;
}

.hero-chip strong {
  margin: 8px 0;
  color: #fff;
  font-size: 30px;
  line-height: 1;
}

.stat-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.stat-card {
  position: relative;
  overflow: hidden;
  padding: 18px 18px 16px;
  border: 1px solid #e6edf3;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 10px 26px rgb(15 23 42 / 4%);
}

.stat-card::after {
  content: '';
  position: absolute;
  right: -22px;
  top: -26px;
  width: 94px;
  height: 94px;
  border-radius: 50%;
  opacity: 0.14;
}

.stat-card--teal::after {
  background: #0f766e;
}

.stat-card--cyan::after {
  background: #0284c7;
}

.stat-card--amber::after {
  background: #f59e0b;
}

.stat-card--rose::after {
  background: #e11d48;
}

.stat-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.stat-card__top span {
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.stat-card__top em {
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  background: #f8fafc;
  color: #64748b;
  font-size: 11px;
  font-style: normal;
}

.stat-card strong {
  display: block;
  margin-top: 18px;
  color: #0f172a;
  font-size: 32px;
  line-height: 1;
}

.stat-card p {
  margin: 12px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 16px;
}

.overview-bottom {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 0.92fr);
  gap: 16px;
}

.viz-card {
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 20px;
  background: linear-gradient(180deg, #fff, #fbfdff);
  box-shadow: 0 12px 30px rgb(15 23 42 / 4%);
}

.viz-card--wide {
  grid-column: span 7;
}

.overview-grid > .viz-card:not(.viz-card--wide) {
  grid-column: span 5;
}

.viz-card--tasks {
  background:
    radial-gradient(circle at top right, rgb(14 165 164 / 9%), transparent 34%),
    linear-gradient(180deg, #fff, #f8fbfd);
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}

.card-head h3 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
}

.card-head p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.pill-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.metric-pill {
  height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  background: linear-gradient(135deg, #ecfeff, #f0fdf4);
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.chart-surface {
  width: 100%;
  height: 320px;
}

.chart-surface--compact {
  height: 286px;
}

.ranking-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ranking-item {
  padding: 14px 14px 12px;
  border: 1px solid #edf2f7;
  border-radius: 16px;
  background: #fff;
}

.ranking-item__head,
.ranking-item__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ranking-item__head strong {
  color: #0f172a;
  font-size: 14px;
}

.ranking-item__head span,
.ranking-item__meta em {
  color: #64748b;
  font-size: 12px;
  font-style: normal;
}

.ranking-bar {
  height: 10px;
  margin: 10px 0 8px;
  border-radius: 999px;
  overflow: hidden;
  background: #eef2f7;
}

.ranking-bar span {
  display: block;
  height: 100%;
  border-radius: 999px;
}

.task-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.task-card {
  position: relative;
  overflow: hidden;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  text-align: left;
  background: #fff;
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.task-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 30px rgb(15 23 42 / 9%);
}

.task-card::before {
  content: '';
  position: absolute;
  inset: 0 auto auto 0;
  width: 100%;
  height: 4px;
}

.task-card--teal::before {
  background: linear-gradient(90deg, #0fbac6, #0f766e);
}

.task-card--cyan::before {
  background: linear-gradient(90deg, #38bdf8, #0284c7);
}

.task-card--amber::before {
  background: linear-gradient(90deg, #f8cd65, #f97316);
}

.task-card--rose::before {
  background: linear-gradient(90deg, #fb7185, #e11d48);
}

.task-card__label {
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.task-card strong {
  color: #0f172a;
  font-size: 28px;
  line-height: 1;
}

.task-card em {
  min-height: 38px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
  font-style: normal;
}

.task-card__action {
  color: #0899a5;
  font-size: 12px;
  font-weight: 700;
}

.empty-state {
  min-height: 188px;
  border: 1px dashed #d5deea;
  border-radius: 18px;
  display: grid;
  place-items: center;
  color: #94a3b8;
  font-size: 13px;
  background: #fbfdff;
}

@media (max-width: 1280px) {
  .overview-hero {
    grid-template-columns: 1fr;
  }

  .overview-hero__chips {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .stat-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .viz-card--wide,
  .overview-grid > .viz-card:not(.viz-card--wide) {
    grid-column: span 12;
  }

  .overview-bottom {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .overview-hero {
    padding: 20px 18px;
  }

  .overview-hero__copy h2 {
    font-size: 24px;
  }

  .overview-hero__chips,
  .task-grid,
  .stat-strip {
    grid-template-columns: 1fr;
  }

  .pill-row {
    justify-content: flex-start;
  }
}
</style>
