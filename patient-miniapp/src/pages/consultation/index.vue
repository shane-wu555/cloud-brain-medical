<template>
  <patient-nav-bar title="AI 智能问诊" />
  <view class="page consultation-page">
    <scroll-view class="chat-panel" scroll-y :scroll-into-view="scrollAnchor">
      <view class="intro">
        <view class="intro-title">先说说哪里不舒服</view>
        <view class="intro-subtitle">AI 会根据描述继续追问，信息足够后再推荐科室和就诊建议。</view>
      </view>

      <view v-for="message in messages" :id="message.id" :key="message.id" class="message-row" :class="message.role">
        <view class="avatar">{{ message.role === 'ai' ? 'AI' : '我' }}</view>
        <view class="bubble">
          <view class="bubble-text">{{ message.content }}</view>
          <view v-if="message.questions?.length" class="question-list">
            <button
              v-for="question in message.questions"
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

interface ChatMessage {
  id: string;
  role: 'ai' | 'patient';
  content: string;
  questions?: string[];
}

const auth = useAuthStore();
const input = ref('');
const loading = ref(false);
const consultationId = ref('');
const result = ref<ConsultationResponse>();
const scrollAnchor = ref('chat-bottom');
const messages = ref<ChatMessage[]>([
  {
    id: 'welcome',
    role: 'ai',
    content: '您好，我会先帮您整理症状信息。请描述主要不适、开始时间、持续多久，以及是否突然加重。'
  }
]);

const placeholder = computed(() =>
  consultationId.value ? '补充回答 AI 的追问...' : '例如：头痛 2 天，伴有恶心，夜间加重...'
);
const buttonText = computed(() => (consultationId.value ? '继续问诊' : '开始问诊'));
const riskClass = computed(() => `risk-${(result.value?.riskLevel || 'LOW').toLowerCase()}`);
const riskText = computed(() => {
  const risk = result.value?.riskLevel;
  if (risk === 'HIGH') return '高风险';
  if (risk === 'MEDIUM') return '中风险';
  return '低风险';
});

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
  consultationId.value = response.consultationId || consultationId.value;
  result.value = response;
  uni.setStorageSync('last_ai_consultation', response);

  if (response.needsFollowUp) {
    pushMessage({
      role: 'ai',
      content: response.summary || '我还需要再确认几个信息，方便给出更准确的分诊建议。',
      questions: response.followUpQuestions?.length
        ? response.followUpQuestions
        : ['请继续补充症状开始时间、持续多久、主要部位和伴随症状。']
    });
    return;
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

async function scrollToBottom() {
  await nextTick();
  scrollAnchor.value = loading.value ? 'loading-message' : result.value && !result.value.needsFollowUp ? 'recommendation-card' : 'chat-bottom';
}

function goBooking() {
  uni.navigateTo({ url: '/pages/booking/index?fromAi=1' });
}
</script>

<style scoped>
.consultation-page {
  height: calc(100vh - var(--status-bar-height) - 96rpx);
  min-height: 0;
  padding: 0;
  background: #eef5fb;
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
  padding: 24rpx;
  border-left: 6rpx solid #2f80ed;
  background: #ffffff;
  border-radius: 8rpx;
}

.intro-title {
  color: #102033;
  font-size: 34rpx;
  font-weight: 700;
}

.intro-subtitle {
  margin-top: 8rpx;
  color: #5d7188;
  font-size: 26rpx;
  line-height: 1.5;
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
  width: 58rpx;
  height: 58rpx;
  border-radius: 50%;
  background: #2f80ed;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 24rpx;
  font-weight: 700;
}

.message-row.patient .avatar {
  background: #22a06b;
}

.bubble {
  max-width: 570rpx;
  padding: 20rpx 22rpx;
  border-radius: 8rpx;
  background: #ffffff;
  color: #1f2937;
  box-shadow: 0 8rpx 22rpx rgba(32, 65, 105, 0.08);
}

.message-row.patient .bubble {
  background: #daf2e7;
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
  padding: 14rpx 18rpx;
  border: 1px solid #bfd8f5;
  border-radius: 8rpx;
  background: #f4f9ff;
  color: #1f5f9f;
  font-size: 25rpx;
  line-height: 1.45;
  text-align: left;
}

.typing {
  display: flex;
  gap: 8rpx;
  align-items: center;
  min-height: 34rpx;
}

.dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: #8aa1b7;
}

.recommendation {
  margin: 8rpx 0 24rpx 72rpx;
  padding: 24rpx;
  border-radius: 8rpx;
  background: #ffffff;
  box-shadow: 0 12rpx 30rpx rgba(32, 65, 105, 0.1);
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
  font-weight: 800;
}

.risk-badge {
  flex-shrink: 0;
  padding: 8rpx 14rpx;
  border-radius: 8rpx;
  font-size: 24rpx;
  font-weight: 700;
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
  padding: 16rpx;
  border-radius: 8rpx;
  background: #f6f9fc;
}

.doctor-name {
  color: #102033;
  font-size: 28rpx;
  font-weight: 700;
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
  border-radius: 8rpx;
  background: #2f80ed;
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 82rpx;
}

.chat-bottom {
  height: 24rpx;
}

.composer {
  flex-shrink: 0;
  padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));
  background: #ffffff;
  border-top: 1px solid #dce7f2;
  display: flex;
  gap: 16rpx;
  align-items: flex-end;
}

.composer-input {
  flex: 1;
  min-height: 76rpx;
  max-height: 190rpx;
  padding: 18rpx 20rpx;
  border: 1px solid #c9d9e8;
  border-radius: 8rpx;
  background: #f8fbff;
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
  border-radius: 8rpx;
  background: #2f80ed;
  color: #ffffff;
  font-size: 27rpx;
  font-weight: 700;
  line-height: 76rpx;
}

.send-button[disabled] {
  background: #a9bdd1;
  color: #ffffff;
}
</style>
