import { clearGuestId, getGuestId } from '../lib/guest'

const BASE = '/api/v1'

export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

export async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  const guestId = getGuestId()
  if (guestId) headers['X-Guest-Id'] = guestId
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let response: Response
  try {
    response = await fetch(BASE + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'NETWORK', '서버에 연결할 수 없어요. 잠시 뒤 다시 시도해 주세요.')
  }

  const text = await response.text()
  const data = text ? JSON.parse(text) : undefined
  if (!response.ok) {
    if (response.status === 401) clearGuestId()
    throw new ApiError(response.status, data?.code ?? 'UNKNOWN', data?.message ?? '요청을 처리하지 못했어요.')
  }
  return data as T
}

export function isApiError(error: unknown, code?: string): error is ApiError {
  return error instanceof ApiError && (code === undefined || error.code === code)
}
