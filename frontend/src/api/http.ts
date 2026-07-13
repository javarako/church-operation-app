import { authState } from '../auth/authStore';

export async function postJson<TRequest, TResponse>(path: string, body: TRequest): Promise<TResponse> {
  return requestJson<TResponse>(path, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export async function postEmpty<TRequest>(path: string, body: TRequest): Promise<void> {
  await request(path, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export async function getJson<TResponse>(path: string): Promise<TResponse> {
  return requestJson<TResponse>(path);
}

export async function putJson<TRequest, TResponse>(path: string, body: TRequest): Promise<TResponse> {
  return requestJson<TResponse>(path, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
}

export async function deleteJson<TResponse>(path: string): Promise<TResponse> {
  return requestJson<TResponse>(path, {
    method: 'DELETE',
  });
}

export async function getBlob(path: string): Promise<Blob> {
  const response = await request(path);
  return response.blob();
}

export async function putFile<TResponse>(path: string, field: string, file: File): Promise<TResponse> {
  const formData = new FormData();
  formData.append(field, file);
  return requestJson<TResponse>(path, {
    method: 'PUT',
    body: formData,
  });
}

export async function deleteEmpty(path: string): Promise<void> {
  await request(path, { method: 'DELETE' });
}

async function requestJson<TResponse>(path: string, init: RequestInit = {}): Promise<TResponse> {
  const response = await request(path, init);
  return response.json() as Promise<TResponse>;
}

async function request(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  if (!(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }
  if (authState.currentUser?.token) {
    headers.set('Authorization', `Bearer ${authState.currentUser.token}`);
  }

  const response = await fetch(path, {
    ...init,
    headers,
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: 'Request failed.' }));
    throw new Error(error.message ?? 'Request failed.');
  }

  return response;
}
