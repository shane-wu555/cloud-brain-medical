<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app';
import { useAuthStore } from './stores/auth';

onLaunch(() => useAuthStore().restore());
</script>

<style>
page {
  background: #f2f7ff;
  color: #1f2937;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif;
}

.page {
  min-height: 100vh;
  padding: 24rpx;
  box-sizing: border-box;
}

.card {
  margin-bottom: 24rpx;
  padding: 30rpx;
  border-radius: 18rpx;
  background: #fff;
  box-shadow: 0 10rpx 30rpx rgba(31, 84, 140, 0.08);
}

.title {
  color: #172033;
  font-size: 44rpx;
  font-weight: 800;
  line-height: 1.25;
}

.muted {
  color: #718096;
  font-size: 27rpx;
  line-height: 1.55;
}

.button {
  height: 78rpx;
  margin-top: 22rpx;
  border-radius: 12rpx;
  background: linear-gradient(135deg, #4aa5ff 0%, #2f80ed 100%);
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 78rpx;
}

.button[disabled] {
  background: #cbd5e1;
  color: #fff;
}

.input {
  margin-top: 16rpx;
  padding: 22rpx 24rpx;
  border: 1px solid #d9e6f6;
  border-radius: 12rpx;
  background: #f8fbff;
  color: #1f2937;
  font-size: 30rpx;
}
</style>
