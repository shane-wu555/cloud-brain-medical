import { defineStore } from 'pinia';
import { fetchUnreadCount } from '../api/notification';

export const useNotificationStore = defineStore('notification', {
  state: () => ({
    unreadTotal: 0,
    unreadByCategory: {} as Record<string, number>,
  }),

  getters: {
    hasUnread(): boolean {
      return this.unreadTotal > 0;
    },
    pendingPaymentCount(): number {
      return this.unreadByCategory['PENDING_PAYMENT'] || 0;
    },
    examAndReportCount(): number {
      return (this.unreadByCategory['EXAM_COMPLETED'] || 0)
        + (this.unreadByCategory['REPORT_PUBLISHED'] || 0);
    },
    disposalCompletedCount(): number {
      return this.unreadByCategory['DISPOSAL_COMPLETED'] || 0;
    },
    drugsDispensedCount(): number {
      return this.unreadByCategory['DRUGS_DISPENSED'] || 0;
    },
  },

  actions: {
    async refreshUnreadCount(): Promise<void> {
      const token = uni.getStorageSync('access_token');
      if (!token) return;
      const boundPatient = uni.getStorageSync('bound_patient');
      if (!boundPatient) return;
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
  },
});
