/**
 * 마지막으로 있던 열람실. 서버가 다시 켜져 방이 사라지면 이 정보로 같은 코드의 방을 되살린다.
 * 같이 쓰던 사람들도 각자 같은 코드로 되살리므로 같은 방에 다시 모인다. 이 브라우저에만 저장한다.
 */
export interface LastRoom {
  code: string
  virtualSeats: number
  realSeats: number
  matesAlwaysFocus: boolean
  matesStay: boolean
  /** 그 방의 주인이었는지. 되살린 방에 늦게 들어가도 주인을 돌려받는다 */
  wasOwner: boolean
}

const KEY = 'moyeobom.lastRoom'

export function getLastRoom(): LastRoom | null {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) ?? 'null') as LastRoom | null
    return saved && typeof saved.code === 'string' ? saved : null
  } catch {
    return null
  }
}

export function setLastRoom(room: LastRoom) {
  try {
    localStorage.setItem(KEY, JSON.stringify(room))
  } catch {
    // 저장할 수 없으면 방이 사라질 때 새 방을 연다
  }
}

/** 스스로 나왔으면 되살리지 않는다 */
export function clearLastRoom() {
  try {
    localStorage.removeItem(KEY)
  } catch {
    // 무시
  }
}
