export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api').replace(/\/$/, '');
const REQUEST_TIMEOUT_MS = 15000;

function extractErrorMessage(data: unknown, url: string): string {
  if (typeof data === 'string' && data.trim()) {
    return data;
  }

  if (data && typeof data === 'object') {
    const payload = data as {
      message?: string;
      error?: string;
      details?: string[];
      errors?: Array<{ message?: string } | string>;
    };

    if (typeof payload.message === 'string' && payload.message.trim()) {
      return payload.message;
    }
    if (typeof payload.error === 'string' && payload.error.trim()) {
      return payload.error;
    }
    if (Array.isArray(payload.details) && payload.details.length) {
      return payload.details.find((item) => typeof item === 'string' && item.trim()) || `Request failed: ${url}`;
    }
    if (Array.isArray(payload.errors) && payload.errors.length) {
      const first = payload.errors[0];
      if (typeof first === 'string' && first.trim()) {
        return first;
      }
      if (first && typeof first === 'object' && typeof first.message === 'string' && first.message.trim()) {
        return first.message;
      }
    }
  }

  return `Request failed: ${url}`;
}

function toError(error: unknown, url: string): Error {
  if (error instanceof Error) return error;

  const errMsg = (error as { errMsg?: string } | undefined)?.errMsg;
  if (errMsg) {
    if (errMsg.includes('timeout')) {
      return new Error(`Request timeout: ${url}`);
    }
    return new Error(errMsg);
  }

  return new Error(`Request failed: ${url}`);
}

export async function request<T>(options: UniApp.RequestOptions): Promise<T> {
  const url = `${API_BASE_URL}${options.url}`;
  const requestPath = typeof options.url === 'string' ? options.url : '';
  const isAuthRequest = requestPath.startsWith('/auth/') && requestPath !== '/auth/change-password';
  const token = isAuthRequest ? '' : uni.getStorageSync('access_token');

  return new Promise<T>((resolve, reject) => {
    console.log('miniapp request start', { url, method: options.method ?? 'GET' });

    uni.request({
      ...options,
      url,
      timeout: options.timeout ?? REQUEST_TIMEOUT_MS,
      header: {
        ...(options.header ?? {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      success(response) {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(response.data as T);
          return;
        }

        if (response.statusCode === 401 && !isAuthRequest) {
          uni.removeStorageSync('access_token');
          uni.removeStorageSync('current_user');
        }

        console.error('miniapp request bad response', {
          url,
          statusCode: response.statusCode,
          data: response.data
        });
        reject(new Error(extractErrorMessage(response.data, url)));
      },
      fail(error) {
        console.error('miniapp request failed', { url, error });
        reject(toError(error, url));
      }
    });
  });
}
