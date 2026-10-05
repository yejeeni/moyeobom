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

export interface SnapshotPayload {
  serverTime: string
  mySeatNo: number
  seats: { seatNo: number; occupant: Occupant | null }[]
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
