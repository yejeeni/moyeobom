import { request } from './client'
import type {
  CarriedTask,
  CarryoverTask,
  CloseAction,
  CloseResult,
  GuestMe,
  MyStatus,
  NewTask,
  Review,
  RoomEnter,
  RoomLookup,
  Setting,
  SprintView,
  Task,
} from './types'

export const api = {
  issueGuest: () => request<{ guestId: string }>('POST', '/guests'),
  me: () => request<GuestMe>('GET', '/guests/me'),
  getSetting: () => request<Setting>('GET', '/guests/me/setting'),
  updateSetting: (body: Partial<Setting>) => request<Setting>('PATCH', '/guests/me/setting', body),

  currentSprint: () => request<{ sprint: SprintView | null }>('GET', '/sprints/current'),
  carryover: () => request<{ tasks: CarryoverTask[] }>('GET', '/sprints/carryover'),
  startSprint: (tasks: NewTask[], carriedTasks: CarriedTask[]) =>
    request<{ sprint: SprintView }>('POST', '/sprints', { tasks, carriedTasks }),

  addTask: (task: NewTask) => request<Task>('POST', '/sprints/current/tasks', task),
  updateTask: (taskId: number, body: { title?: string; estimatedMinutes?: number; sortOrder?: number }) =>
    request<Task>('PATCH', `/tasks/${taskId}`, body),
  deleteTask: (taskId: number) => request<void>('DELETE', `/tasks/${taskId}`),
  completeTask: (taskId: number) =>
    request<{ task: Task; refreshMessage: string }>('POST', `/tasks/${taskId}/complete`),

  startFocus: (taskId: number) => request<MyStatus>('POST', `/tasks/${taskId}/focus`),
  stopFocus: (reason: 'STOPPED' | 'BREAK') => request<MyStatus>('POST', '/focus/stop', { reason }),
  currentFocus: () => request<MyStatus>('GET', '/focus/current'),

  createRoom: (virtualSeats: number, realSeats: number) =>
    request<RoomEnter>('POST', '/rooms', { virtualSeats, realSeats }),
  joinRoom: (code: string) => request<RoomEnter>('POST', '/rooms/join', { code }),
  lookupRoom: (code: string) => request<RoomLookup>('GET', `/rooms/lookup?code=${encodeURIComponent(code)}`),
  currentRoom: () => request<RoomEnter>('GET', '/rooms/current'),
  leaveRoom: () => request<void>('POST', '/rooms/leave'),

  review: () => request<Review>('GET', '/sprints/current/review'),
  closeSprint: (decisions: { taskId: number; action: CloseAction }[]) =>
    request<CloseResult>('POST', '/sprints/current/close', { decisions }),
}
