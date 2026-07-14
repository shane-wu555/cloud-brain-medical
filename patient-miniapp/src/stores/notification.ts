import { defineStore } from 'pinia';
import { request } from '../api/http';
import { fetchNotifications, fetchUnreadCount, markRead, type NotificationItem } from '../api/notification';

let calledPollTimer: ReturnType<typeof setInterval> | null = null;
let unreadPollTimer: ReturnType<typeof setInterval> | null = null;
let calledModalOpen = false;
const handledCalledIds = new Set<string>();
const excludedNotificationRoutes = new Set(['pages/login/index']);

interface Appointment {
  id: string;
  paymentStatus: string;
}

interface MedicalOrder {
  id: string;
  orderType: 'CHECK' | 'LAB' | 'DISPOSAL';
  paymentStatus: string;
  status: string;
}

interface Prescription {
  id: string;
  status: string;
}

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
    examTodoCount: 0,
    disposalTodoCount: 0,
    dispenseTodoCount: 0,
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
      return this.pendingPaymentTodoCount;
    },
    examAndReportCount(): number {
      return this.examTodoCount;
    },
    disposalCompletedCount(): number {
      return this.disposalTodoCount;
    },
    drugsDispensedCount(): number {
      return this.dispenseTodoCount;
    },
  },

  actions: {
    async refreshUnreadCount(): Promise<void> {
      if (!canPollNotifications()) return;
      try {
        const [counts] = await Promise.all([
          fetchUnreadCount(),
          this.refreshBusinessTodoCounts(),
        ]);
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

    setBusinessTodoCounts(counts: {
      pendingPayment: number;
      exam: number;
      disposal: number;
      dispense: number;
    }): void {
      this.pendingPaymentTodoCount = Math.max(0, Number(counts.pendingPayment) || 0);
      this.examTodoCount = Math.max(0, Number(counts.exam) || 0);
      this.disposalTodoCount = Math.max(0, Number(counts.disposal) || 0);
      this.dispenseTodoCount = Math.max(0, Number(counts.dispense) || 0);
    },

    async refreshBusinessTodoCounts(): Promise<void> {
      const patient = uni.getStorageSync('bound_patient');
      if (!patient?.id) {
        this.setBusinessTodoCounts({
          pendingPayment: 0,
          exam: 0,
          disposal: 0,
          dispense: 0,
        });
        return;
      }
      try {
        const patientQuery = `patientId=${encodeURIComponent(patient.id)}`;
        const [appointments, medicalOrders, prescriptions] = await Promise.all([
          request<Appointment[]>({ url: `/appointments?${patientQuery}`, method: 'GET' }),
          request<MedicalOrder[]>({ url: `/medical-orders?${patientQuery}&view=PAYMENT_RECORD`, method: 'GET' }),
          request<Prescription[]>({ url: `/prescriptions?${patientQuery}&view=PAYMENT_RECORD`, method: 'GET' }),
        ]);
        const registrationCount = appointments
          .filter((item) => item.paymentStatus === 'UNPAID' || item.paymentStatus === 'FAILED')
          .length;
        const medicalOrderCount = medicalOrders
          .filter((item) => item.paymentStatus === 'UNPAID')
          .length;
        const prescriptionCount = prescriptions
          .filter((item) => item.status === 'PENDING_PAYMENT' || item.status === 'CONFIRMED')
          .length;
        const examCount = medicalOrders
          .filter((item) => (item.orderType === 'CHECK' || item.orderType === 'LAB')
            && !['COMPLETED', 'MISSED', 'REPORT_PENDING'].includes(item.status))
          .length;
        const disposalCount = medicalOrders
          .filter((item) => item.orderType === 'DISPOSAL'
            && (item.paymentStatus === 'UNPAID' || !['COMPLETED', 'MISSED', 'REPORT_PENDING'].includes(item.status)))
          .length;
        const dispenseCount = prescriptions
          .filter((item) => ['CONFIRMED', 'PENDING_PAYMENT', 'PAID', 'WAITING_DISPENSE'].includes(item.status))
          .length;
        this.setBusinessTodoCounts({
          pendingPayment: registrationCount + medicalOrderCount + prescriptionCount,
          exam: examCount,
          disposal: disposalCount,
          dispense: dispenseCount,
        });
      } catch {
        // keep existing todo counts when business data polling fails
      }
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

    startUnreadPolling(): void {
      if (unreadPollTimer) {
        return;
      }
      this.refreshUnreadCount();
      unreadPollTimer = setInterval(() => {
        this.refreshUnreadCount();
      }, 10000);
    },

    stopCalledPolling(): void {
      if (calledPollTimer) {
        clearInterval(calledPollTimer);
        calledPollTimer = null;
      }
    },

    stopUnreadPolling(): void {
      if (unreadPollTimer) {
        clearInterval(unreadPollTimer);
        unreadPollTimer = null;
      }
    },
  },
});
