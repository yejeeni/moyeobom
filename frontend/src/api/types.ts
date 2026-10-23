// 백엔드 DTO와 같은 모양. 기준은 Swagger(/v3/api-docs)다.

export type SeatState = 'FOCUS' | 'BREAK' | 'IDLE'
export type TaskStatus = 'TODO' | 'DONE' | 'CARRIED' | 'DROPPED'

export interface GuestMe {
  guestId: string
  createdAt: string
  hasOpenSprint: boolean
}

export interface Setting {
  breakAlertEnabled: boolean
  breakAlertMinutes: number
}

export interface Task {
  taskId: number
  title: string
  estimatedMinutes: number | null
  status: TaskStatus
  sortOrder: number
  carriedFromTaskId: number | null
  completedAt: string | null
  actualSeconds: number
  cumulativeSeconds: number
  hasRecords: boolean
}

export interface SprintView {
  sprintId: number
  startedAt: string
  /** 오늘 공부할 시간(분). 타임박스 없이 시작했으면 null */
  plannedMinutes: number | null
  /** 타임박스가 끝나는 시각 */
  endsAt: string | null
  tasks: Task[]
}

export interface CarryoverTask {
  taskId: number
  title: string
  previousEstimatedMinutes: number | null
  cumulativeSeconds: number
}

export interface NewTask {
  title: string
  estimatedMinutes: number | null
}

export interface CarriedTask {
  fromTaskId: number
  estimatedMinutes: number | null
}

export interface MyStatus {
  state: SeatState
  since: string | null
  serverTime: string
  session: {
    sessionId: number
    taskId: number
    startedAt: string
    elapsedSeconds: number
  } | null
}

export interface RoomEnter {
  roomId: string
  /** 입장 코드. 실제 자리가 2개 이상이면 다른 사람에게 나눠 준다 */
  code: string
  seatCount: number
  /** 실제 사람 자리 수(나 포함) */
  realSeatCount: number
  seatNo: number
  nickname: string
  /** 가상 메이트가 쉬지 않고 계속 집중한다 */
  matesAlwaysFocus: boolean
  /** 가상 메이트가 자리를 뜨지 않는다 */
  matesStay: boolean
  /** 내가 방 주인(설정을 바꿀 수 있는 사람)인지 */
  owner: boolean
}

/** 메이트 설정. 휴식 없음(쉬지 않고 집중)과 이탈 없음(떠나지 않음)는 따로 켜고 끈다 */
export interface MateSettings {
  matesAlwaysFocus: boolean
  matesStay: boolean
}

/** 방 설정. 방 주인만 바꿀 수 있다 */
export interface RoomSettings {
  matesAlwaysFocus: boolean
  matesStay: boolean
  /** 방 주인의 자리 번호 */
  ownerSeatNo: number
}

export interface CharacterParts {
  hair: number
  hairColor: number
  shirt: number
  skin: number
}

export interface Occupant {
  nickname: string
  character: CharacterParts
  state: SeatState
  since: string
  completedCount: number
  remainingCount: number
}

export type RoomEventType =
  | 'ROOM_SNAPSHOT'
  | 'SEAT_JOINED'
  | 'SEAT_LEFT'
  | 'STATE_CHANGED'
  | 'COUNTS_CHANGED'
  | 'ROOM_SETTINGS_CHANGED'
  | 'BREAK_ALERT'
  | 'ROOM_NOT_FOUND'

export interface RoomEvent<P = unknown> {
  type: RoomEventType
  roomId: string | null
  seatNo: number | null
  at: string
  payload: P
}

export interface RoomLookup {
  code: string
  seatCount: number
  realSeatCount: number
  /** 아직 비어 있는 실제 자리 수 */
  waitingSeatCount: number
}

export interface SnapshotPayload {
  serverTime: string
  code: string
  seatCount: number
  mySeatNo: number
  /** waiting: 비어 있는 실제 사람 자리(초대 대기) */
  seats: { seatNo: number; occupant: Occupant | null; waiting: boolean }[]
  matesAlwaysFocus: boolean
  matesStay: boolean
  ownerSeatNo: number
}

export interface ReviewTask {
  taskId: number
  title: string
  status: TaskStatus
  estimatedSeconds: number | null
  actualSeconds: number
  diffSeconds: number | null
  cumulativeSeconds: number
}

export interface Review {
  sprintId: number
  /** 계획한 공부 시간(초). 타임박스 없이 시작했으면 null */
  plannedSeconds: number | null
  totalFocusSeconds: number
  completedCount: number
  estimatedSecondsOfDone: number
  actualSecondsOfDone: number
  tasks: ReviewTask[]
}

export type CloseAction = 'CARRY' | 'DROP'

export interface CloseResult {
  sprintId: number
  carriedCount: number
  droppedCount: number
  refreshMessage: string
}
