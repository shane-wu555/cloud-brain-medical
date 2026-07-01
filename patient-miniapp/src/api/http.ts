export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api').replace(/\/$/, '');
const REQUEST_TIMEOUT_MS = 15000;

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
  const isAuthRequest = requestPath.startsWith('/auth/');
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

        reject(
          new Error(
            (response.data as { message?: string } | undefined)?.message ?? `Request failed: ${url}`
          )
        );
      },
      fail(error) {
        console.error('miniapp request failed', { url, error });
        reject(toError(error, url));
      }
    });
  });
}
