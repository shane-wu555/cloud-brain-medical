import { ref } from 'vue';

interface UnreadBadgeSnapshot {
  initialized: boolean;
  seenIds: string[];
}

const MAX_TRACKED_IDS = 500;

function normalizeIds(ids: string[]) {
  return [...new Set(ids.filter(Boolean))];
}

export function useUnreadBadgeTracker(storageKey: string) {
  const unreadCount = ref(0);
  let initialized = false;
  let seenIds = new Set<string>();

  function persist() {
    if (typeof window === 'undefined') return;
    const snapshot: UnreadBadgeSnapshot = {
      initialized,
      seenIds: [...seenIds].slice(-MAX_TRACKED_IDS)
    };
    window.localStorage.setItem(storageKey, JSON.stringify(snapshot));
  }

  function load() {
    if (initialized || typeof window === 'undefined') return;
    initialized = true;
    try {
      const raw = window.localStorage.getItem(storageKey);
      if (!raw) return;
      const snapshot = JSON.parse(raw) as Partial<UnreadBadgeSnapshot> | null;
      if (!snapshot?.initialized || !Array.isArray(snapshot.seenIds)) return;
      seenIds = new Set(normalizeIds(snapshot.seenIds));
    } catch {
      seenIds = new Set<string>();
    }
  }

  function sync(ids: string[]) {
    if (typeof window === 'undefined') return;
    load();
    const normalizedIds = normalizeIds(ids);
    if (seenIds.size === 0 && !window.localStorage.getItem(storageKey)) {
      seenIds = new Set(normalizedIds);
      unreadCount.value = 0;
      persist();
      return;
    }
    unreadCount.value = normalizedIds.filter((id) => !seenIds.has(id)).length;
  }

  function markRead(ids: string[]) {
    if (typeof window === 'undefined') return;
    load();
    const normalizedIds = normalizeIds(ids);
    seenIds = new Set([...seenIds, ...normalizedIds].slice(-MAX_TRACKED_IDS));
    unreadCount.value = 0;
    persist();
  }

  return {
    unreadCount,
    markRead,
    sync
  };
}
