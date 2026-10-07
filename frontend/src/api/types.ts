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
