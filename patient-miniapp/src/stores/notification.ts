import { defineStore } from 'pinia';
import { fetchNotifications, fetchUnreadCount, markRead, type NotificationItem } from '../api/notification';

let calledPollTimer: ReturnType<typeof setInterval> | null = null;
let calledModalOpen = false;
const handledCalledIds = new Set<string>();
const excludedNotificationRoutes = new Set(['pages/login/index']);

function currentRoute() {
  const pages = getCurrentPages();
  return pages.length > 0 ? pages[pages.length - 1].route : '';
}

function canPollNotifications() {
  const route = currentRoute();
  return Boolean(
    route
      && !excludedNotificationRoutes.has(route)
      && uni.getStorageSync('access_token')
      && uni.getStorageSync('bound_patient')
  );
}

function calledContent(item: NotificationItem) {
  return item.body || item.title || '您已被叫号，请及时前往对应诊室或执行科室。';
}

export const useNotificationStore = defineStore('notification', {
  state: () => ({
    unreadTotal: 0,
    unreadByCategory: {} as Record<string, number>,
    pendingPaymentTodoCount: 0,
  }),

  getters: {
    hasUnread(): boolean {
      return this.badgeTotal > 0;
    },
    badgeTotal(): number {
      return this.pendingPaymentCount
        + this.examAndReportCount
        + this.disposalCompletedCount
        + this.drugsDispensedCount;
    },
    pendingPaymentCount(): number {
      return Math.max(this.pendingPaymentTodoCount, this.unreadByCategory['PENDING_PAYMENT'] || 0);
    },
    examAndReportCount(): number {
      return this.unreadByCategory['EXAM_ARRANGEMENT'] || 0;
    },
    disposalCompletedCount(): number {
      return this.unreadByCategory['DISPOSAL_ARRANGEMENT'] || 0;
    },
    drugsDispensedCount(): number {
      return this.unreadByCategory['DISPENSE_ARRANGEMENT'] || 0;
    },
  },

  actions: {
    async refreshUnreadCount(): Promise<void> {
      if (!canPollNotifications()) return;
      try {
        const counts = await fetchUnreadCount();
        this.unreadTotal = counts.total || 0;
        this.unreadByCategory = counts;
      } catch {
        // silently ignore — polling, so failures are expected occasionally
      }
    },

    clearCategory(category: string): void {
      const removed = this.unreadByCategory[category] || 0;
      this.unreadTotal = Math.max(0, this.unreadTotal - removed);
      this.unreadByCategory = { ...this.unreadByCategory, [category]: 0 };
    },

    clearCategories(categories: string[]): void {
      let totalRemoved = 0;
      const updated = { ...this.unreadByCategory };
      for (const cat of categories) {
        totalRemoved += updated[cat] || 0;
        updated[cat] = 0;
      }
      this.unreadTotal = Math.max(0, this.unreadTotal - totalRemoved);
      this.unreadByCategory = updated;
    },

    setPendingPaymentTodoCount(count: number): void {
      this.pendingPaymentTodoCount = Math.max(0, Number(count) || 0);
    },

    async checkCalledAlert(): Promise<void> {
      if (calledModalOpen || !canPollNotifications()) {
        return;
      }
      try {
        const items = await fetchNotifications('CALLED', 5);
        const called = items
          .filter((item) => !item.isRead && !handledCalledIds.has(item.id))
          .sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0];
        if (!called) {
          return;
        }

        handledCalledIds.add(called.id);
        calledModalOpen = true;
        uni.showModal({
          title: '叫号提醒',
          content: calledContent(called),
          showCancel: false,
          confirmText: '我知道了',
          complete: () => {
            calledModalOpen = false;
            markRead(called.id)
              .catch(() => {})
              .finally(() => {
                this.refreshUnreadCount();
              });
          },
        });
      } catch {
        // polling can fail when the app is switching pages or auth state is changing
      }
    },

    startCalledPolling(): void {
      if (calledPollTimer) {
        return;
      }
      this.checkCalledAlert();
      calledPollTimer = setInterval(() => {
        this.checkCalledAlert();
      }, 15000);
    },

    stopCalledPolling(): void {
      if (calledPollTimer) {
        clearInterval(calledPollTimer);
        calledPollTimer = null;
      }
    },
  },
});
