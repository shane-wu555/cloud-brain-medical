function pad(value: number) {
  return String(value).padStart(2, '0');
}

function normalizeDateText(value: string) {
  return value.replace('T', ' ').replace(/\.\d+$/, '').replace(/Z$/, '');
}

export function formatDate(value?: string | null) {
  if (!value) {
    return '';
  }
  const normalized = normalizeDateText(value);
  const match = normalized.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (!match) {
    return value;
  }
  return `${match[1]}年${match[2]}月${match[3]}日`;
}

export function formatDateTime(value?: string | null) {
  if (!value) {
    return '';
  }

  const normalized = normalizeDateText(value);
  const match = normalized.match(/^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2}))?/);
  if (!match) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return `${date.getFullYear()}年${pad(date.getMonth() + 1)}月${pad(date.getDate())}日 ${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  const dateText = `${match[1]}年${match[2]}月${match[3]}日`;
  return match[4] && match[5] ? `${dateText} ${match[4]}:${match[5]}` : dateText;
}
