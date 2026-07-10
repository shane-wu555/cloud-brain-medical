import { ref, onMounted, onUnmounted, type Ref } from 'vue'

const DEFAULT_INTERVAL_MS = 15_000

/**
 * 定期轮询刷新队列数据，当用户缴费后队列自动更新。
 * 轮询期间若用户正在编辑表单则跳过刷新，避免覆盖正在输入的内容。
 *
 * @param isEditing - 用户是否正在编辑表单
 * @param refreshFn - 刷新队列的异步函数
 * @param intervalMs - 轮询间隔（默认 15 秒）
 */
export function useQueuePolling(
  isEditing: Ref<boolean>,
  refreshFn: () => Promise<void>,
  intervalMs: number = DEFAULT_INTERVAL_MS
) {
  const polling = ref(false)
  let timer: ReturnType<typeof setInterval> | undefined

  function start() {
    if (timer !== undefined) return
    timer = setInterval(async () => {
      if (isEditing.value) return
      polling.value = true
      try {
        await refreshFn()
      } catch {
        // 静默失败，网络抖动不影响用户体验
      } finally {
        polling.value = false
      }
    }, intervalMs)
  }

  function stop() {
    if (timer !== undefined) {
      clearInterval(timer)
      timer = undefined
    }
  }

  onMounted(start)
  onUnmounted(stop)

  return { polling, stop, start }
}
