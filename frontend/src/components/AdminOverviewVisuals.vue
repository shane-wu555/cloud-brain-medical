<template>
  <div class="overview-dashboard">
    <section class="overview-hero">
      <div class="overview-hero__copy">
        <span class="overview-hero__eyebrow">智慧运营驾驶舱</span>
        <h2>实时运营总览</h2>
      </div>

      <div class="overview-hero__chips">
        <div v-for="chip in heroChips" :key="chip.label" class="hero-chip">
          <span>{{ chip.label }}</span>
          <strong>{{ chip.value }}</strong>
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
        </div>
        <strong>{{ card.value }}</strong>
      </article>
    </div>

    <div class="overview-grid">
      <section class="viz-card viz-card--tasks">
        <div class="card-head">
          <div>
            <h3>待处理事项</h3>
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
            <span class="task-card__action">进入模块</span>
          </button>
        </div>
      </section>

      <section class="viz-card viz-card--wide viz-card--ocean">
        <div class="card-head">
          <div>
            <h3>未来 7 日号源趋势</h3>
          </div>
        </div>
        <div ref="trendChartRef" class="chart-surface"></div>
      </section>
    </div>

    <div class="candidate-grid">
      <section class="viz-card candidate-card candidate-card--narrow viz-card--aurora">
        <div class="card-head">
          <div>
            <h3>预约转化漏斗</h3>
          </div>
        </div>
        <div class="funnel-surface chart-surface--candidate">
          <svg viewBox="0 0 620 320" class="funnel-svg" role="img" aria-label="预约转化漏斗">
            <g v-for="stage in funnelSvgStages" :key="stage.name">
              <polygon
                :points="stage.points"
                :fill="stage.color"
                :stroke="stage.color"
                stroke-width="18"
                stroke-linejoin="round"
              />
              <text :x="stage.centerX" :y="stage.centerY - 6" text-anchor="middle" class="funnel-svg__label">
                {{ stage.name }}
              </text>
              <text :x="stage.centerX" :y="stage.centerY + 14" text-anchor="middle" class="funnel-svg__value">
                {{ stage.value }}
              </text>
            </g>
          </svg>
        </div>
      </section>

      <section class="viz-card candidate-card candidate-card--wide viz-card--violet">
        <div class="card-head">
          <div>
            <h3>分时段热力图</h3>
          </div>
        </div>
        <div ref="heatmapChartRef" class="chart-surface chart-surface--candidate"></div>
      </section>
    </div>

    <div class="overview-bottom">
      <section class="viz-card viz-card--sunset">
        <div class="card-head">
          <div>
            <h3>账号角色管理</h3>
          </div>
        </div>
        <div ref="roleChartRef" class="chart-surface chart-surface--compact chart-surface--role"></div>
      </section>

      <section class="viz-card viz-card--wide viz-card--mint">
        <div class="card-head">
          <div>
            <h3>科室号源排行</h3>
          </div>
          <div class="pill-row">
            <span v-for="item in departmentRanking" :key="item.name" class="metric-pill">
              {{ item.name }} {{ item.capacity }}
            </span>
          </div>
        </div>
        <div v-if="departmentRanking.length" ref="rankingChartRef" class="chart-surface"></div>
        <div v-else class="empty-state">暂无数据</div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { BarChart, HeatmapChart, LineChart, PieChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TitleComponent, TooltipComponent, VisualMapComponent } from 'echarts/components';
import { use, init, graphic, type ECharts } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import type { StaffAccount } from '../api/auth';
import type { DashboardOverview } from '../api/dashboard';
import type { Doctor, DoctorEvent, Schedule } from '../api/doctor';

type OverviewPageKey = 'aiSchedule' | 'manualSchedule' | 'doctorProfile' | 'doctorEvents';
type ChartKey = 'ranking' | 'role' | 'trend' | 'heatmap';

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

const rankingChartRef = ref<HTMLDivElement | null>(null);
const roleChartRef = ref<HTMLDivElement | null>(null);
const trendChartRef = ref<HTMLDivElement | null>(null);
const heatmapChartRef = ref<HTMLDivElement | null>(null);

use([
  BarChart,
  HeatmapChart,
  LineChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  VisualMapComponent,
  CanvasRenderer
]);

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

const PIE_COLORS = ['#0f766e', '#0284c7', '#f97316', '#7c3aed', '#ec4899', '#14b8a6', '#f59e0b'];
const BAR_GRADIENTS: Array<[string, string]> = [
  ['#12c2c8', '#0f766e'],
  ['#4facfe', '#2563eb'],
  ['#f8cd65', '#f97316'],
  ['#c084fc', '#7c3aed'],
  ['#fb7185', '#e11d48']
];
const HEATMAP_PERIODS = ['上午', '下午'];
const FUNNEL_COLORS = ['#2563eb', '#14b8a6', '#f59e0b', '#ec4899'];

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

  return Array.from(rankingMap.values())
    .filter((item) => item.capacity > 0 || item.doctorIds.size > 0)
    .sort((left, right) => right.capacity - left.capacity || right.doctorIds.size - left.doctorIds.size)
    .slice(0, 5)
    .map((item) => ({
      name: item.name,
      capacity: item.capacity,
      scheduleCount: item.scheduleCount,
      doctorCount: item.doctorIds.size
    }));
});

const scheduleHeatmapCells = computed(() =>
  HEATMAP_PERIODS.flatMap((period, periodIndex) =>
    scheduleTrend.value.map((day, dateIndex) => {
      const items = props.schedules.filter(
        (schedule) =>
          schedule.workDate === day.date &&
          schedule.period === period &&
          schedule.status !== 'SUSPENDED'
      );
      const capacity = items.reduce((sum, item) => sum + (item.capacity || 0), 0);
      const booked = items.reduce((sum, item) => sum + (item.booked || 0), 0);
      const available = items.reduce((sum, item) => sum + (item.available || 0), 0);
      const utilization = capacity ? clampPercent((booked / capacity) * 100) : 0;

      return {
        date: day.date,
        label: day.label,
        period,
        dateIndex,
        periodIndex,
        capacity,
        booked,
        available,
        utilization
      };
    })
  )
);

const activeAccountCount = computed(() => props.staffAccounts.filter((account) => account.active).length);
const totalSevenDayCapacity = computed(() => scheduleTrend.value.reduce((sum, item) => sum + item.capacity, 0));
const totalSevenDayBooked = computed(() => scheduleTrend.value.reduce((sum, item) => sum + item.booked, 0));
const waitingPressureRate = computed(() => {
  const waitingVisits = props.overview?.waitingVisits ?? 0;
  const todayAppointments = props.overview?.todayAppointments ?? 0;
  return safePercent(waitingVisits, todayAppointments || waitingVisits || 1);
});
const doctorCoverageRate = computed(() => {
  return clampPercent(props.overview?.roomCoverageRate ?? 0);
});
const aiCoverageRate = computed(() => {
  const aiTriageCount = props.overview?.aiTriageCount ?? 0;
  const todayAppointments = props.overview?.todayAppointments ?? 0;
  return safePercent(aiTriageCount, todayAppointments || aiTriageCount || 1);
});

const funnelData = computed(() => {
  const todayAppointments = props.overview?.todayAppointments ?? 0;
  const aiTriageCount = props.overview?.aiTriageCount ?? 0;
  const waitingVisits = props.overview?.waitingVisits ?? 0;

  const totalSupply = Math.max(totalSevenDayCapacity.value, totalSevenDayBooked.value, todayAppointments);
  const bookedStage = Math.min(totalSupply, Math.max(totalSevenDayBooked.value, todayAppointments));
  const aiStage = Math.min(bookedStage, aiTriageCount || bookedStage);
  const waitingStage = Math.min(aiStage, waitingVisits || aiStage);

  return [
    { name: '总号源', value: totalSupply },
    { name: '已预约', value: bookedStage },
    { name: 'AI 问诊', value: aiStage },
    { name: '待接诊', value: waitingStage }
  ];
});

const funnelSvgStages = computed(() => {
  const svgWidth = 620;
  const stageHeight = 66;
  const stageGap = 12;
  const startY = 10;
  const total = funnelData.value[0]?.value || 1;
  const minWidth = svgWidth * 0.42;
  const maxWidth = svgWidth * 0.9;

  return funnelData.value.map((item, index) => {
    const ratio = item.value / total;
    const nextValue = funnelData.value[index + 1]?.value ?? item.value;
    const nextRatio = nextValue / total;
    const currentWidth = minWidth + (maxWidth - minWidth) * ratio;
    const nextWidth = index === funnelData.value.length - 1
      ? currentWidth
      : minWidth + (maxWidth - minWidth) * nextRatio;
    const y = startY + index * (stageHeight + stageGap);
    const x1 = (svgWidth - currentWidth) / 2;
    const x2 = x1 + currentWidth;
    const x4 = (svgWidth - nextWidth) / 2;
    const x3 = x4 + nextWidth;

    return {
      ...item,
      color: FUNNEL_COLORS[index % FUNNEL_COLORS.length],
      points: `${x1},${y} ${x2},${y} ${x3},${y + stageHeight} ${x4},${y + stageHeight}`,
      centerX: svgWidth / 2,
      centerY: y + stageHeight / 2
    };
  });
});

const heroChips = computed(() => [
  { label: '未来 7 日号源', value: totalSevenDayCapacity.value },
  { label: '启用账号', value: `${activeAccountCount.value}/${props.staffAccounts.length || 0}` },
  { label: '候诊压力', value: `${waitingPressureRate.value}%` },
  { label: '诊室出诊覆盖率', value: `${doctorCoverageRate.value}%` },
  { label: 'AI 问诊覆盖率', value: `${aiCoverageRate.value}%` }
]);

const kpiCards = computed(() => {
  const todayAppointments = props.overview?.todayAppointments ?? 0;
  const waitingVisits = props.overview?.waitingVisits ?? 0;
  const activeDoctors = props.overview?.activeDoctors ?? 0;
  const aiTriageCount = props.overview?.aiTriageCount ?? 0;

  return [
    { label: '今日挂号', value: todayAppointments, tone: 'teal' },
    { label: '待接诊', value: waitingVisits, tone: 'cyan' },
    { label: '出诊医生', value: activeDoctors, tone: 'amber' },
    { label: 'AI 问诊', value: aiTriageCount, tone: 'rose' }
  ];
});

const actionCards = computed(() => [
  { label: 'AI 排班建议', count: props.pendingSuggestions, page: 'aiSchedule' as const, tone: 'amber' },
  { label: '排班信息', count: props.schedules.length, page: 'manualSchedule' as const, tone: 'teal' },
  { label: '账号角色管理', count: props.staffAccounts.length, page: 'doctorProfile' as const, tone: 'cyan' },
  { label: '请假 / 手术事件', count: props.doctorEvents.length, page: 'doctorEvents' as const, tone: 'rose' }
]);

watch(
  [() => props.active, departmentRanking, roleDistribution, scheduleHeatmapCells, scheduleTrend],
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
      renderHeatmapChart();
      renderRankingChart();
      renderRoleChart();
      renderTrendChart();
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

function renderRankingChart() {
  const chart = ensureChart('ranking', rankingChartRef.value);
  if (!chart) return;

  chart.setOption(
    {
      animationDuration: 700,
      tooltip: {
        trigger: 'item',
        backgroundColor: '#0f172a',
        borderWidth: 0,
        textStyle: { color: '#e2e8f0' },
        formatter: (params: { name: string; dataIndex: number }) => {
          const item = departmentRanking.value[params.dataIndex];
          if (!item) return params.name;
          return [
            `${item.name}`,
            `号源 ${item.capacity}`,
            `医生 ${item.doctorCount}`,
            `班次 ${item.scheduleCount}`
          ].join('<br/>');
        }
      },
      grid: { left: 18, right: 24, top: 12, bottom: 18, containLabel: true },
      xAxis: {
        type: 'value',
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#94a3b8' },
        splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.18)' } }
      },
      yAxis: {
        type: 'category',
        inverse: true,
        data: departmentRanking.value.map((item) => item.name),
        axisTick: { show: false },
        axisLine: { show: false },
        axisLabel: { color: '#334155', fontWeight: 600 }
      },
      series: [
        {
          name: '号源',
          type: 'bar',
          barWidth: 18,
          showBackground: true,
          backgroundStyle: {
            color: 'rgba(148, 163, 184, 0.08)',
            borderRadius: 999
          },
          label: {
            show: true,
            position: 'right',
            color: '#0f172a',
            fontWeight: 700
          },
          data: departmentRanking.value.map((item, index) => ({
            value: item.capacity,
            itemStyle: {
              color: buildBarGradient(index),
              borderRadius: 999
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
        subtext: '账号',
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
        bottom: 4,
        itemWidth: 10,
        itemHeight: 10,
        textStyle: { color: '#475569', fontSize: 12 }
      },
      series: [
        {
          name: '账号角色',
          type: 'pie',
          top: 10,
          bottom: 34,
          radius: ['42%', '64%'],
          center: ['50%', '46%'],
          avoidLabelOverlap: true,
          itemStyle: { borderColor: '#fff', borderWidth: 4 },
          label: hasData
            ? {
                distanceToLabelLine: 2,
                formatter: '{name|{b}}\n{percent|{d}%}',
                rich: {
                  name: { color: '#334155', fontSize: 11, lineHeight: 16, fontWeight: 600 },
                  percent: { color: '#64748b', fontSize: 11 }
                }
              }
            : { show: false },
          labelLine: { show: hasData, length: 8, length2: 8 },
          data: seriesData
        }
      ]
    },
    true
  );
}

function renderHeatmapChart() {
  const chart = ensureChart('heatmap', heatmapChartRef.value);
  if (!chart) return;

  chart.setOption(
    {
      animationDuration: 700,
      tooltip: {
        trigger: 'item',
        backgroundColor: '#0f172a',
        borderWidth: 0,
        textStyle: { color: '#e2e8f0' },
        formatter: (params: { value: [number, number, number] }) => {
          const cell = scheduleHeatmapCells.value.find(
            (item) => item.dateIndex === params.value[0] && item.periodIndex === params.value[1]
          );
          if (!cell) return '';
          return [
            `${cell.label} ${cell.period}`,
            `拥挤度 ${cell.utilization}%`,
            `已预约 ${cell.booked}`,
            `可预约 ${cell.available}`
          ].join('<br/>');
        }
      },
      grid: { left: 10, right: 10, top: 12, bottom: 46, containLabel: true },
      xAxis: {
        type: 'category',
        data: scheduleTrend.value.map((item) => item.label),
        splitArea: { show: true, areaStyle: { color: ['#fbfdff', '#f8fafc'] } },
        axisLine: { lineStyle: { color: '#d7e3ec' } },
        axisTick: { show: false },
        axisLabel: { color: '#475569' }
      },
      yAxis: {
        type: 'category',
        data: HEATMAP_PERIODS,
        splitArea: { show: true, areaStyle: { color: ['#fbfdff', '#f8fafc'] } },
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#334155', fontWeight: 600 }
      },
      visualMap: {
        min: 0,
        max: 100,
        calculable: false,
        orient: 'horizontal',
        left: 'center',
        bottom: 0,
        itemWidth: 120,
        itemHeight: 12,
        text: ['高', '低'],
        textStyle: { color: '#64748b' },
        inRange: {
          color: ['#eff6ff', '#60a5fa', '#14b8a6', '#f59e0b', '#ef4444']
        }
      },
      series: [
        {
          name: '分时段热度',
          type: 'heatmap',
          data: scheduleHeatmapCells.value.map((item) => [item.dateIndex, item.periodIndex, item.utilization]),
          label: {
            show: true,
            formatter: (params: { value: [number, number, number] }) => `${params.value[2]}%`,
            color: '#0f172a',
            fontWeight: 700
          },
          itemStyle: {
            borderRadius: 10,
            borderColor: '#fff',
            borderWidth: 2
          },
          emphasis: {
            itemStyle: {
              shadowBlur: 12,
              shadowColor: 'rgba(15, 23, 42, 0.18)'
            }
          }
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

function buildBarGradient(index: number) {
  const [start, end] = BAR_GRADIENTS[index % BAR_GRADIENTS.length];
  return new graphic.LinearGradient(0, 0, 1, 0, [
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
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: 18px;
  border-radius: 24px;
  background:
    radial-gradient(circle at 84% 18%, rgb(255 255 255 / 20%) 0 8%, transparent 8%),
    radial-gradient(circle at 92% 76%, rgb(255 255 255 / 12%) 0 9%, transparent 9%),
    linear-gradient(135deg, #0f766e 0%, #0899a5 42%, #1d4ed8 74%, #f59e0b 100%);
  color: #fff;
  box-shadow: 0 18px 36px rgb(8 153 165 / 18%);
}

.overview-hero__copy {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 6px;
}

.overview-hero__copy h2 {
  margin: 0;
  font-size: 30px;
  line-height: 1.12;
}

.overview-hero__eyebrow {
  height: 28px;
  width: fit-content;
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
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  align-content: start;
}

.hero-chip {
  min-height: 98px;
  padding: 14px;
  border: 1px solid rgb(255 255 255 / 16%);
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: linear-gradient(180deg, rgb(255 255 255 / 20%), rgb(255 255 255 / 10%));
  backdrop-filter: blur(10px);
}

.hero-chip span {
  color: rgb(240 253 250 / 84%);
  font-size: 12px;
}

.hero-chip strong {
  margin-top: 8px;
  color: #fff;
  font-size: 24px;
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
  padding: 18px;
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
  gap: 10px;
}

.stat-card__top span {
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.stat-card strong {
  display: block;
  margin-top: 12px;
  color: #0f172a;
  font-size: 32px;
  line-height: 1;
}

.overview-grid,
.overview-bottom,
.candidate-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
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
  grid-column: span 8;
}

.viz-card--full {
  grid-column: span 12;
}

.overview-grid > .viz-card:not(.viz-card--wide):not(.viz-card--full),
.overview-bottom > .viz-card:not(.viz-card--wide) {
  grid-column: span 4;
}

.candidate-card--wide {
  grid-column: span 8;
}

.candidate-card--narrow {
  grid-column: span 4;
}

.viz-card--ocean {
  background:
    radial-gradient(circle at top right, rgb(2 132 199 / 12%), transparent 32%),
    linear-gradient(180deg, #fff, #f6fbff);
}

.viz-card--mint {
  background:
    radial-gradient(circle at top right, rgb(225 29 72 / 12%), transparent 30%),
    linear-gradient(180deg, #fff, #fff7fa);
}

.viz-card--sunset {
  background:
    radial-gradient(circle at top right, rgb(249 115 22 / 12%), transparent 30%),
    linear-gradient(180deg, #fff, #fff8f1);
}

.viz-card--aurora {
  background:
    radial-gradient(circle at top right, rgb(15 118 110 / 12%), transparent 30%),
    linear-gradient(180deg, #fff, #f4fffc);
}

.viz-card--violet {
  background:
    radial-gradient(circle at top right, rgb(225 29 72 / 12%), transparent 30%),
    linear-gradient(180deg, #fff, #fff7fb);
}

.viz-card--tasks {
  background:
    radial-gradient(circle at top right, rgb(15 118 110 / 10%), transparent 34%),
    linear-gradient(180deg, #fff, #f7fffc);
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

.chart-surface--role {
  height: 318px;
}

.chart-surface--candidate {
  height: 316px;
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

.task-card__action {
  color: #0899a5;
  font-size: 12px;
  font-weight: 700;
}

.funnel-surface {
  height: 316px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.funnel-svg {
  width: 100%;
  height: 100%;
  display: block;
}

.funnel-svg__label,
.funnel-svg__value {
  fill: #fff;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  text-anchor: middle;
}

.funnel-svg__label {
  font-size: 13px;
  font-weight: 700;
}

.funnel-svg__value {
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
  .viz-card--full,
  .overview-grid > .viz-card:not(.viz-card--wide):not(.viz-card--full),
  .overview-bottom > .viz-card:not(.viz-card--wide),
  .candidate-card--wide,
  .candidate-card--narrow {
    grid-column: span 12;
  }
}

@media (max-width: 860px) {
  .overview-hero {
    padding: 20px 18px;
  }

  .overview-hero__copy h2 {
    font-size: 24px;
  }

  .stat-strip,
  .overview-hero__chips,
  .task-grid {
    grid-template-columns: 1fr;
  }

  .pill-row {
    justify-content: flex-start;
  }
}
</style>
