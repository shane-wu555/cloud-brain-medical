<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app';
import { useAuthStore } from './stores/auth';
import { preloadMedicalIcons } from './utils/preload-icons';

onLaunch(() => {
  useAuthStore().restore();
  preloadMedicalIcons();
});
</script>

<style>
page {
  --patient-theme: #0cbdcc;
  --patient-theme-strong: #0899a5;
  --patient-theme-deep: #0b6b74;
  --patient-theme-soft: #e7fbfc;
  --patient-theme-soft-alt: #cff4f6;
  --patient-theme-softest: #f2fcfc;
  --patient-theme-border: #b7e8eb;
  --patient-theme-page-bg: #eef8f8;
  background: var(--patient-theme-page-bg);
  color: #1f2937;
  font-family: Inter, "PingFang SC", "HarmonyOS Sans SC", "MiSans", "Noto Sans CJK SC", "Source Han Sans SC", system-ui, -apple-system, BlinkMacSystemFont, "Microsoft YaHei", sans-serif;
}

view,
text,
input,
textarea,
button {
  font-family: Inter, "PingFang SC", "HarmonyOS Sans SC", "MiSans", "Noto Sans CJK SC", "Source Han Sans SC", system-ui, -apple-system, BlinkMacSystemFont, "Microsoft YaHei", sans-serif;
}

.page {
  min-height: 100vh;
  padding: 24rpx;
  box-sizing: border-box;
}

.card {
  margin-bottom: 24rpx;
  padding: 30rpx;
  border: 1px solid var(--patient-theme-border);
  border-radius: 18rpx;
  background: #fff;
  box-shadow: 0 8rpx 22rpx rgba(80, 100, 95, 0.06);
}

.title {
  color: #0d3d5c;
  font-size: 44rpx;
  font-weight: 600;
  line-height: 1.25;
}

.muted {
  color: #64748b;
  font-size: 27rpx;
  line-height: 1.55;
}

.button {
  height: 78rpx;
  margin-top: 22rpx;
  border-radius: 14rpx;
  background: linear-gradient(135deg, var(--patient-theme) 0%, var(--patient-theme-strong) 100%);
  color: #fff;
  font-size: 30rpx;
  font-weight: 600;
  line-height: 78rpx;
  box-shadow: 0 10rpx 24rpx rgba(12, 189, 204, 0.20);
}

.button[disabled] {
  background: #cbd5e1;
  color: #fff;
  box-shadow: none;
}

.input {
  margin-top: 16rpx;
  padding: 22rpx 24rpx;
  border: 1px solid var(--patient-theme-border);
  border-radius: 14rpx;
  background: var(--patient-theme-softest);
  color: #1f2937;
  font-size: 30rpx;
}
</style>
