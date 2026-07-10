import { request } from './http';

export interface NotificationItem {
  id: string;
  category: string;
  title: string;
  body: string | null;
  referenceType: string;
  referenceId: string;
  isRead: boolean;
  readAt: string | null;
  createdAt: string;
}

export interface UnreadCounts {
  total: number;
  [category: string]: number;
}

export function fetchUnreadCount(): Promise<UnreadCounts> {
  return request<UnreadCounts>({ url: '/patients/me/notifications/count', method: 'GET' });
}

export function fetchNotifications(
  category?: string,
  limit = 50,
  offset = 0,
): Promise<NotificationItem[]> {
  const params: string[] = [];
  if (category) params.push(`category=${encodeURIComponent(category)}`);
  params.push(`limit=${limit}`);
  params.push(`offset=${offset}`);
  return request<NotificationItem[]>({
    url: `/patients/me/notifications?${params.join('&')}`,
    method: 'GET',
  });
}

export function markRead(notificationId: string): Promise<void> {
  return request<void>({ url: `/patients/me/notifications/${notificationId}/read`, method: 'PUT' });
}

export function markAllRead(category?: string): Promise<void> {
  const query = category ? `?category=${encodeURIComponent(category)}` : '';
  return request<void>({ url: `/patients/me/notifications/read-all${query}`, method: 'PUT' });
}
