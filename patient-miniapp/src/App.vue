<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app';
import { useAuthStore } from './stores/auth';

onLaunch(() => useAuthStore().restore());
</script>

<style>
page { background: #f5f7fb; color: #1f2937; font-family: sans-serif; }
.page { padding: 28rpx; }
.card { margin-bottom: 24rpx; padding: 28rpx; border-radius: 20rpx; background: #fff; }
.title { font-size: 40rpx; font-weight: 700; }
.muted { color: #64748b; font-size: 26rpx; }
.button { margin-top: 20rpx; background: #0f766e; color: #fff; }
.input { margin-top: 16rpx; padding: 20rpx; border: 1px solid #d1d5db; border-radius: 12rpx; }
</style>
