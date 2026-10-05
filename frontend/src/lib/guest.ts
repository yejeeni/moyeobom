// 비회원 식별자는 브라우저에만 저장한다. 브라우저 데이터를 지우면 사라진다.
const KEY = 'moyeobom.guestId'

export function getGuestId(): string | null {
  try {
    return localStorage.getItem(KEY)
  } catch {
    return null
  }
}

export function saveGuestId(guestId: string) {
  try {
    localStorage.setItem(KEY, guestId)
  } catch {
    // 저장할 수 없는 환경이면 이번 탭에서만 쓴다
  }
}

export function clearGuestId() {
  try {
    localStorage.removeItem(KEY)
  } catch {
    // 무시
  }
}
