// 오늘 공부할 시간(타임박스). 마지막으로 고른 값을 브라우저에 기억해 다음 계획 때 미리 채운다
const KEY = 'moyeobom.plannedMinutes'

export const PLANNED_MIN = 30
export const PLANNED_MAX = 720
export const PLANNED_STEP = 30
export const DEFAULT_PLANNED = 120
/** 시간이 다 됐을 때 한 번에 늘리는 시간 */
export const EXTEND_MINUTES = 30

export function getPlannedMinutes(): number {
  try {
    const saved = Number(localStorage.getItem(KEY))
    if (saved >= PLANNED_MIN && saved <= PLANNED_MAX) return saved
  } catch {
    // 무시
  }
  return DEFAULT_PLANNED
}

export function setPlannedMinutes(minutes: number) {
  try {
    localStorage.setItem(KEY, String(minutes))
  } catch {
    // 무시
  }
}

// 시간이 다 됐다는 안내를 이 끝 시각에 대해 이미 보여 줬는지. 새로고침해도 다시 띄우지 않는다
const PROMPTED_KEY = 'moyeobom.timeboxPrompted'

export function wasPrompted(endsAt: string): boolean {
  try {
    return localStorage.getItem(PROMPTED_KEY) === endsAt
  } catch {
    return false
  }
}

export function markPrompted(endsAt: string) {
  try {
    localStorage.setItem(PROMPTED_KEY, endsAt)
  } catch {
    // 무시
  }
}
