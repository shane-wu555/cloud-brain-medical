const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

export async function request<T>(options: UniApp.RequestOptions): Promise<T> {
  const token = uni.getStorageSync('access_token');
  return new Promise<T>((resolve, reject) => {
    uni.request({
      ...options,
      url: `${API_BASE_URL}${options.url}`,
      header: {
        ...(options.header ?? {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      success(response) {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(response.data as T);
          return;
        }
        reject(new Error((response.data as { message?: string })?.message ?? '请求失败'));
      },
      fail: reject
    });
  });
}
