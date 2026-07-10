<template>
  <patient-nav-bar title="AI 智能问诊" />
  <view class="page consultation-page" :style="{ height: consultationPageHeight }">
    <scroll-view class="chat-panel" scroll-y :scroll-into-view="scrollAnchor">
      <view class="intro">
        <view class="intro-icon">🤒</view>
        <view class="intro-title">先说说哪里不舒服</view>
        <view class="intro-subtitle">AI 会根据描述继续追问，信息足够后再推荐科室和就诊建议。</view>
        <view class="symptom-chips">
          <view
            v-for="symptom in quickSymptoms"
            :key="symptom"
            :class="['symptom-chip', selectedSymptoms.includes(symptom) ? 'selected' : '']"
            @tap="quickSymptom(symptom)"
          >
            {{ symptom }}
          </view>
        </view>
      </view>

      <view v-for="message in messages" :id="message.id" :key="message.id" class="message-row" :class="message.role">
        <view class="avatar">{{ message.role === 'ai' ? 'AI' : '我' }}</view>
        <view class="bubble">
          <view class="bubble-text">{{ message.content }}</view>
          <view v-if="message.questions?.length" class="question-list">
            <button
              v-for="question in message.questions.slice(0, 2)"
              :key="question"
              class="question-chip"
              @tap="fillQuestion(question)"
            >
              {{ question }}
            </button>
          </view>
        </view>
      </view>

      <view v-if="loading" id="loading-message" class="message-row ai">
        <view class="avatar">AI</view>
        <view class="bubble typing">
          <view class="dot" />
          <view class="dot" />
          <view class="dot" />
        </view>
      </view>

      <view v-if="result && !result.needsFollowUp" id="recommendation-card" class="recommendation">
        <view class="recommendation-header">
          <view>
            <view class="recommendation-label">推荐科室</view>
            <view class="department-name">{{ result.recommendedDepartmentName || '全科医学科' }}</view>
          </view>
          <view class="risk-badge" :class="riskClass">{{ riskText }}</view>
        </view>

        <view v-if="result.summary" class="summary">{{ result.summary }}</view>

        <view v-if="upcomingDates.length" class="schedule-dates">
          <view class="schedule-dates-title">近期可挂号日期</view>
          <view class="date-chips">
            <view v-for="d in upcomingDates" :key="d.workDate + d.period" class="date-chip">
              <view class="date-chip-day">{{ formatDateLabel(d.workDate) }}</view>
              <view class="date-chip-period">{{ d.period }}</view>
              <view class="date-chip-avail">余{{ d.available }}号</view>
            </view>
          </view>
        </view>

        <view v-if="result.suggestOfflineUrgent" class="urgent-notice">
          当前描述存在急危风险信号，请优先线下急诊就医。
        </view>

        <view v-if="result.recommendedDoctors.length" class="doctor-list">
          <view v-for="doctor in result.recommendedDoctors" :key="doctor.doctorId || doctor.doctorName" class="doctor-item">
            <view class="doctor-name">{{ doctor.doctorName || '推荐医生' }}</view>
            <view class="doctor-reason">{{ doctor.reason }}</view>
          </view>
        </view>

        <view v-if="result.recordDraft" class="draft">{{ result.recordDraft }}</view>
        <button class="primary-button" @tap="goBooking()">按推荐去挂号</button>
      </view>

      <view id="chat-bottom" class="chat-bottom" />
    </scroll-view>

    <view class="composer">
      <textarea
        v-model="input"
        class="composer-input"
        auto-height
        maxlength="1000"
        :placeholder="placeholder"
        placeholder-class="placeholder"
      />
      <button class="send-button" :disabled="loading || !input.trim()" @tap="sendMessage()">
        {{ loading ? '发送中' : buttonText }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue';
import { request } from '../../api/http';
import { useAuthStore } from '../../stores/auth';

interface DoctorRecommendation {
  doctorId: string;
  doctorName: string;
  reason: string;
}

interface ConsultationResponse {
  patientId?: string;
  consultationId?: string;
  aiRecordId?: string;
  summary: string;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | string;
  recommendedDepartmentId: string;
  recommendedDepartmentName: string;
  recommendedDoctors: DoctorRecommendation[];
  suggestOfflineUrgent: boolean;
  needsFollowUp: boolean;
  followUpQuestions: string[];
  recordDraft: string;
  provider: string;
  model: string;
  fallbackUsed: boolean;
  knowledgeSources?: Array<Record<string, unknown>>;
  safetyNotice?: string;
  createdAt?: string;
}

interface ScheduleSummary {
  doctorId: string;
  doctorName: string;
  workDate: string;
  period: string;
  available: number;
}

interface ChatMessage {
  id: string;
  role: 'ai' | 'patient';
  content: string;
  questions?: string[];
}

const auth = useAuthStore();
const statusBarHeight = uni.getSystemInfoSync().statusBarHeight ?? 0;
const input = ref('');
const loading = ref(false);
const consultationId = ref('');
const result = ref<ConsultationResponse>();
const availableSchedules = ref<ScheduleSummary[]>([]);
const scrollAnchor = ref('chat-bottom');
const messages = ref<ChatMessage[]>([
  {
    id: 'welcome',
    role: 'ai',
    content: '您好，我会先帮您整理症状信息。请描述主要不适、开始时间、持续多久，以及是否突然加重。'
  }
]);

const quickSymptoms = ['头晕', '恶心', '发烧', '头痛', '咳嗽', '腹痛', '胸闷', '乏力'];
const selectedSymptoms = ref<string[]>([]);
const consultationPageHeight = computed(() => `calc(100vh - ${statusBarHeight}px - 96rpx)`);

const placeholder = computed(() =>
  consultationId.value ? '补充回答 AI 的追问...' : '例如：头痛 2 天，伴有恶心，夜间加重...'
);
const buttonText = computed(() => (consultationId.value ? '继续问诊' : '开始问诊'));
const riskClass = computed(() => {
  const level = (result.value?.riskLevel || 'low').toLowerCase();
  if (level === 'high' || level === '高') return 'risk-high';
  if (level === 'medium' || level === '中') return 'risk-medium';
  return 'risk-low';
});
const riskText = computed(() => {
  const level = (result.value?.riskLevel || '').toUpperCase();
  if (level === 'HIGH' || level === '高') return '高风险';
  if (level === 'MEDIUM' || level === '中') return '中风险';
  return '低风险';
});

function aiConsultationStorageKey(patientId: string) {
  return `last_ai_consultation_${patientId}`;
}

async function sendMessage() {
  const text = input.value.trim();
  if (loading.value || !text) {
    return;
  }

  let patient;
  try {
    await auth.loadProfile();
    patient = auth.requireBoundPatient();
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' });
    uni.navigateTo({ url: '/pages/real-name/index' });
    return;
  }

  pushMessage({ role: 'patient', content: text });
  input.value = '';
  loading.value = true;
  result.value = undefined;
  await scrollToBottom();

  try {
    const response = consultationId.value
      ? await request<ConsultationResponse>({
          url: `/ai/consultations/${consultationId.value}/messages`,
          method: 'POST',
          timeout: 70000,
          data: { message: text, symptomTags: [] }
        })
      : await request<ConsultationResponse>({
          url: '/ai/consultations',
          method: 'POST',
          timeout: 70000,
          data: { patientId: patient.id, description: text, symptomTags: [] }
        });

    handleAiResponse(response);
  } catch (error) {
    pushMessage({
      role: 'ai',
      content: `问诊暂时没有成功：${(error as Error).message}`
    });
    uni.showToast({ title: (error as Error).message, icon: 'none' });
  } finally {
    loading.value = false;
    await scrollToBottom();
  }
}

function handleAiResponse(response: ConsultationResponse) {
  const currentPatientId = auth.boundPatient?.id || response.patientId || '';
  const scopedResponse: ConsultationResponse = {
    ...response,
    patientId: currentPatientId || undefined
  };
  consultationId.value = response.consultationId || consultationId.value;
  result.value = scopedResponse;
  uni.setStorageSync('last_ai_consultation', scopedResponse);
  if (currentPatientId) {
    uni.setStorageSync(aiConsultationStorageKey(currentPatientId), scopedResponse);
  }

  if (response.needsFollowUp) {
    pushMessage({
      role: 'ai',
      content: response.summary || '我还需要再确认几个信息，方便给出更准确的分诊建议。',
      questions: response.followUpQuestions?.length
        ? response.followUpQuestions
        : ['请描述症状开始时间、主要部位和伴随症状。']
    });
    return;
  }

  if (response.recommendedDepartmentId) {
    fetchSchedules(response.recommendedDepartmentId);
  }

  pushMessage({
    role: 'ai',
    content: buildRecommendationMessage(response)
  });
  uni.showToast({ title: '问诊完成', icon: 'success' });
}

function buildRecommendationMessage(response: ConsultationResponse) {
  const department = response.recommendedDepartmentName || '全科医学科';
  const urgent = response.suggestOfflineUrgent ? '当前存在急危风险信号，建议优先线下急诊。' : '';
  return [`已整理好本次问诊信息，建议优先选择 ${department}。`, urgent, response.safetyNotice || ''].filter(Boolean).join('\n');
}

function pushMessage(message: Omit<ChatMessage, 'id'>) {
  messages.value.push({
    id: `message-${Date.now()}-${messages.value.length}`,
    ...message
  });
}

function fillQuestion(question: string) {
  input.value = question;
}

function quickSymptom(symptom: string) {
  const idx = selectedSymptoms.value.indexOf(symptom);
  if (idx >= 0) {
    selectedSymptoms.value.splice(idx, 1);
  } else {
    selectedSymptoms.value.push(symptom);
  }
  input.value = selectedSymptoms.value.join('，');
}

const upcomingDates = computed(() => {
  const today = new Date().toISOString().slice(0, 10);
  return availableSchedules.value
    .filter((s) => s.available > 0 && s.workDate >= today)
    .sort((a, b) => a.workDate.localeCompare(b.workDate))
    .slice(0, 5);
});

async function fetchSchedules(departmentId: string) {
  try {
    const data = await request<Array<Record<string, unknown>>>({
      url: `/schedules?departmentId=${departmentId}`,
      method: 'GET',
      timeout: 10000
    });
    const recommendedDoctorIds = new Set(
      (result.value?.recommendedDoctors || []).map((d) => d.doctorId)
    );
    availableSchedules.value = (data || [])
      .map((item: Record<string, unknown>) => ({
        doctorId: String(item.doctorId || ''),
        doctorName: String(item.doctorName || ''),
        workDate: String(item.workDate || ''),
        period: String(item.period || ''),
        available: Number(item.available || 0)
      }))
      .filter((s: ScheduleSummary) => recommendedDoctorIds.has(s.doctorId) || recommendedDoctorIds.size === 0);
  } catch {
    availableSchedules.value = [];
  }
}

async function scrollToBottom() {
  await nextTick();
  scrollAnchor.value = loading.value ? 'loading-message' : result.value && !result.value.needsFollowUp ? 'recommendation-card' : 'chat-bottom';
}

function goBooking() {
  uni.navigateTo({ url: '/pages/booking/index?fromAi=1' });
}

function formatDateLabel(dateStr: string) {
  const d = new Date(dateStr.replace(/-/g, '/'));
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = new Date(d);
  target.setHours(0, 0, 0, 0);
  const diff = Math.round((target.getTime() - today.getTime()) / 86400000);
  if (diff === 0) return '今天';
  if (diff === 1) return '明天';
  if (diff === 2) return '后天';
  return `${d.getMonth() + 1}月${d.getDate()}日`;
}
</script>

<style scoped>
.consultation-page {
  min-height: 0;
  padding: 0;
  background: var(--patient-theme-page-bg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-panel {
  flex: 1;
  min-height: 0;
  height: auto;
  box-sizing: border-box;
  padding: 24rpx 24rpx 18rpx;
}

.intro {
  margin-bottom: 24rpx;
  padding: 28rpx 24rpx 22rpx;
  border-radius: 18rpx;
  background: linear-gradient(135deg, #e6f9fa 0%, #f0fbfc 50%, #ffffff 100%);
  box-shadow: 0 8rpx 22rpx rgba(80, 100, 95, 0.06);
}

.intro-icon {
  font-size: 44rpx;
  margin-bottom: 8rpx;
}

.intro-title {
  color: #102033;
  font-size: 34rpx;
  font-weight: 600;
}

.intro-subtitle {
  margin-top: 8rpx;
  color: #5d7188;
  font-size: 26rpx;
  line-height: 1.5;
}

.symptom-chips {
  margin-top: 18rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.symptom-chip {
  padding: 10rpx 22rpx;
  border-radius: 999rpx;
  background: #ffffff;
  border: 1px solid var(--patient-theme-border);
  color: var(--patient-theme-deep);
  font-size: 24rpx;
  font-weight: 500;
  line-height: 1.4;
  transition: all 0.2s;
}

.symptom-chip:active {
  background: var(--patient-theme-soft);
  border-color: var(--patient-theme-strong);
  color: var(--patient-theme-strong);
  transform: scale(0.96);
}

.symptom-chip.selected {
  background: var(--patient-theme-soft);
  border-color: var(--patient-theme-strong);
  color: var(--patient-theme-strong);
  font-weight: 600;
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
  margin-bottom: 22rpx;
}

.message-row.patient {
  flex-direction: row-reverse;
}

.avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--patient-theme) 0%, var(--patient-theme-strong) 100%);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 26rpx;
  font-weight: 600;
  box-shadow: 0 4rpx 12rpx rgba(12, 189, 204, 0.18);
}

.message-row.patient .avatar {
  background: linear-gradient(135deg, #22a06b 0%, #1a7f54 100%);
  box-shadow: 0 4rpx 12rpx rgba(34, 160, 107, 0.18);
}

.bubble {
  max-width: 570rpx;
  padding: 20rpx 24rpx;
  border-radius: 18rpx;
  background: #ffffff;
  color: #1f2937;
  box-shadow: 0 6rpx 18rpx rgba(80, 100, 95, 0.05);
}

.message-row.patient .bubble {
  background: #e6f9fa;
  border-top-right-radius: 8rpx;
}

.message-row.ai .bubble {
  border-top-left-radius: 8rpx;
}

.bubble-text {
  font-size: 29rpx;
  line-height: 1.6;
  white-space: pre-line;
}

.question-list {
  margin-top: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.question-chip {
  width: 100%;
  margin: 0;
  padding: 16rpx 20rpx;
  border: 1px solid var(--patient-theme-border);
  border-radius: 14rpx;
  background: var(--patient-theme-softest);
  color: var(--patient-theme-deep);
  font-size: 26rpx;
  font-weight: 500;
  line-height: 1.45;
  text-align: left;
  transition: all 0.15s;
}

.question-chip:active {
  background: var(--patient-theme-soft);
  border-color: var(--patient-theme-strong);
  transform: scale(0.98);
}

.typing {
  display: flex;
  gap: 8rpx;
  align-items: center;
  min-height: 34rpx;
}

.dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #8aa1b7;
  animation: dotPulse 1.4s ease-in-out infinite;
}

.dot:nth-child(2) {
  animation-delay: 0.2s;
}

.dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes dotPulse {
  0%, 60%, 100% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  30% {
    opacity: 1;
    transform: scale(1);
  }
}

.recommendation {
  margin: 8rpx 0 24rpx 72rpx;
  padding: 28rpx;
  border-radius: 18rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 22rpx rgba(80, 100, 95, 0.06);
  border: 1px solid var(--patient-theme-border);
}

.recommendation-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
}

.recommendation-label {
  color: #667b91;
  font-size: 24rpx;
}

.department-name {
  margin-top: 6rpx;
  color: #102033;
  font-size: 38rpx;
  font-weight: 600;
}

.risk-badge {
  flex-shrink: 0;
  padding: 8rpx 16rpx;
  border-radius: 12rpx;
  font-size: 24rpx;
  font-weight: 600;
}

.risk-low {
  background: #e6f6ee;
  color: #127047;
}

.risk-medium {
  background: #fff2d8;
  color: #96620a;
}

.risk-high {
  background: #ffe2e0;
  color: #b42318;
}

.summary,
.draft,
.urgent-notice {
  margin-top: 18rpx;
  color: #3a4a5c;
  font-size: 27rpx;
  line-height: 1.6;
}

.schedule-dates {
  margin-top: 18rpx;
  padding: 18rpx;
  border-radius: 14rpx;
  background: #f6fbfb;
  border: 1px solid var(--patient-theme-border);
}

.schedule-dates-title {
  color: #102033;
  font-size: 26rpx;
  font-weight: 600;
  margin-bottom: 12rpx;
}

.date-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

.date-chip {
  display: flex;
  align-items: center;
  gap: 10rpx;
  padding: 10rpx 16rpx;
  border-radius: 12rpx;
  background: #ffffff;
  border: 1px solid var(--patient-theme-border);
}

.date-chip-day {
  color: #102033;
  font-size: 25rpx;
  font-weight: 600;
  white-space: nowrap;
}

.date-chip-period {
  color: #5d7188;
  font-size: 22rpx;
}

.date-chip-avail {
  color: var(--patient-theme-strong);
  font-size: 22rpx;
  font-weight: 600;
}

.urgent-notice {
  padding: 16rpx;
  border-radius: 8rpx;
  background: #fff1f0;
  color: #b42318;
  font-weight: 700;
}

.doctor-list {
  margin-top: 18rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.doctor-item {
  padding: 18rpx 20rpx;
  border-radius: 14rpx;
  background: #f6f9fc;
  border: 1px solid #eef3f7;
}

.doctor-name {
  color: #102033;
  font-size: 28rpx;
  font-weight: 600;
}

.doctor-reason {
  margin-top: 6rpx;
  color: #667b91;
  font-size: 25rpx;
  line-height: 1.45;
}

.primary-button {
  margin-top: 22rpx;
  width: 100%;
  height: 82rpx;
  border-radius: 14rpx;
  background: linear-gradient(135deg, var(--patient-theme) 0%, var(--patient-theme-strong) 100%);
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 600;
  line-height: 82rpx;
  box-shadow: 0 10rpx 24rpx rgba(12, 189, 204, 0.20);
}

.chat-bottom {
  height: 24rpx;
}

.composer {
  flex-shrink: 0;
  padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));
  background: #ffffff;
  border-top: 1px solid var(--patient-theme-border);
  display: flex;
  gap: 16rpx;
  align-items: flex-end;
}

.composer-input {
  flex: 1;
  min-height: 76rpx;
  max-height: 190rpx;
  padding: 18rpx 20rpx;
  border: 1px solid var(--patient-theme-border);
  border-radius: 14rpx;
  background: var(--patient-theme-softest);
  box-sizing: border-box;
  color: #1f2937;
  font-size: 28rpx;
  line-height: 1.45;
}

.placeholder {
  color: #9aabba;
}

.send-button {
  width: 164rpx;
  height: 76rpx;
  margin: 0;
  border-radius: 14rpx;
  background: linear-gradient(135deg, var(--patient-theme) 0%, var(--patient-theme-strong) 100%);
  color: #ffffff;
  font-size: 27rpx;
  font-weight: 600;
  line-height: 76rpx;
  box-shadow: 0 8rpx 20rpx rgba(12, 189, 204, 0.18);
}

.send-button[disabled] {
  background: #a9bdd1;
  color: #ffffff;
}
</style>
