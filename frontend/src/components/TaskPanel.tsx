import { useState, type FormEvent } from 'react'
import type { MyStatus, Task } from '../api/types'
import { formatDuration } from '../lib/time'
import { Icon } from './Icon'

interface Props {
  tasks: Task[]
  myStatus: MyStatus | null
  liveSeconds: number
  busy: boolean
  onFocus: (task: Task) => void
  onComplete: (task: Task) => void
  onDelete: (task: Task) => void
  onRename: (task: Task, title: string, estimatedMinutes: number | null) => void
  onAdd: (title: string, estimatedMinutes: number | null) => Promise<boolean>
  onClose?: () => void
}

/** 줌의 참가자·채팅 패널 자리에 두는 오늘 할 일 목록 */
export function TaskPanel({ tasks, myStatus, liveSeconds, busy, onFocus, onComplete, onDelete, onRename, onAdd, onClose }: Props) {
  const [openId, setOpenId] = useState<number | null>(null)
  const [title, setTitle] = useState('')
  const [minutes, setMinutes] = useState('')
  const todo = tasks.filter((t) => t.status === 'TODO')
  const done = tasks.filter((t) => t.status === 'DONE')
  const focusingTaskId = myStatus?.session?.taskId ?? null
  const focusingTask = tasks.find((t) => t.taskId === focusingTaskId) ?? null
  const todaySeconds = tasks.reduce((sum, t) => sum + t.actualSeconds, 0) + liveSeconds
  const progress = tasks.length > 0 ? done.length / tasks.length : 0

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!title.trim()) return
    const ok = await onAdd(title.trim(), minutes ? Number(minutes) : null)
    if (ok) {
      setTitle('')
      setMinutes('')
    }
  }

  const row = (task: Task) => {
    const focusing = task.taskId === focusingTaskId
    const seconds = task.actualSeconds + (focusing ? liveSeconds : 0)
    const open = openId === task.taskId
    return (
      <li key={task.taskId} className={`task-item ${task.status === 'DONE' ? 'is-done' : ''} ${focusing ? 'is-focusing' : ''}`}>
        <div className="task-main">
          <button
            className="task-title"
            onClick={() => setOpenId(open ? null : task.taskId)}
            aria-expanded={open}
          >
            {task.status === 'DONE' && <span aria-hidden="true">✓ </span>}
            {task.title}
            <span className="task-meta">
              {formatDuration(seconds)}
              {task.estimatedMinutes ? ` / ${task.estimatedMinutes}분` : ''}
            </span>
          </button>
          {task.status === 'TODO' &&
            (focusing ? (
              <span className="badge-focus">집중 중</span>
            ) : (
              <button className="button small primary" onClick={() => onFocus(task)} disabled={busy}>
                시작
              </button>
            ))}
        </div>
        {open && <TaskDetail task={task} seconds={seconds} busy={busy} onComplete={onComplete} onDelete={onDelete} onRename={onRename} />}
      </li>
    )
  }

  return (
    <aside className="task-panel" aria-label="오늘 할 일">
      <div className="panel-header">
        <h2>오늘 할 일</h2>
        {onClose && (
          <button className="icon-button" onClick={onClose} aria-label="할 일 패널 닫기">
            <Icon name="close" size={18} />
          </button>
        )}
      </div>
      <section className="today-summary" aria-label="오늘 요약">
        <div className="summary-row">
          <span>
            완료 <strong>{done.length}</strong> / {tasks.length}
          </span>
          <span>
            오늘 집중 <strong>{formatDuration(todaySeconds)}</strong>
          </span>
        </div>
        <div className="progress" role="progressbar" aria-valuenow={done.length} aria-valuemin={0} aria-valuemax={tasks.length}>
          <span style={{ width: `${progress * 100}%` }} />
        </div>
        {focusingTask && (
          <div className="now-focusing">
            <span className="now-label">
              <span className="live-dot" aria-hidden="true" />
              지금 집중 중
            </span>
            <strong>{focusingTask.title}</strong>
            <span className="task-meta">
              {formatDuration(focusingTask.actualSeconds + liveSeconds)}
              {focusingTask.estimatedMinutes ? ` / 예상 ${focusingTask.estimatedMinutes}분` : ''}
            </span>
          </div>
        )}
      </section>
      <ul className="task-list">
        {todo.map(row)}
        {done.length > 0 && <li className="list-divider">완료한 일</li>}
        {done.map(row)}
      </ul>
      <form className="task-add" onSubmit={submit}>
        <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="할 일 추가" maxLength={100} aria-label="새 할 일 제목" />
        <label className="task-add-minutes">
          <input
            value={minutes}
            onChange={(e) => setMinutes(e.target.value)}
            type="number"
            min={1}
            placeholder="예상"
            aria-label="예상 시간(분, 선택)"
          />
          분
        </label>
        <button className="add-button" type="submit" disabled={!title.trim() || busy} aria-label="할 일 추가">
          <Icon name="plus" size={18} />
        </button>
      </form>
    </aside>
  )
}

function TaskDetail({
  task,
  seconds,
  busy,
  onComplete,
  onDelete,
  onRename,
}: {
  task: Task
  seconds: number
  busy: boolean
  onComplete: (task: Task) => void
  onDelete: (task: Task) => void
  onRename: (task: Task, title: string, estimatedMinutes: number | null) => void
}) {
  const [editing, setEditing] = useState(false)
  const [title, setTitle] = useState(task.title)
  const [minutes, setMinutes] = useState(task.estimatedMinutes ? String(task.estimatedMinutes) : '')
  const carried = task.carriedFromTaskId !== null

  if (editing) {
    return (
      <form
        className="task-detail"
        onSubmit={(e) => {
          e.preventDefault()
          if (!title.trim()) return
          onRename(task, title.trim(), minutes ? Number(minutes) : null)
          setEditing(false)
        }}
      >
        <input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={100} aria-label="제목" />
        <input value={minutes} onChange={(e) => setMinutes(e.target.value)} type="number" min={1} placeholder="예상 분" aria-label="예상 시간(분)" />
        <div className="detail-actions">
          <button className="button small" type="button" onClick={() => setEditing(false)}>
            취소
          </button>
          <button className="button small primary" type="submit" disabled={!title.trim()}>
            저장
          </button>
        </div>
      </form>
    )
  }

  return (
    <div className="task-detail">
      <dl>
        <dt>이번 스프린트</dt>
        <dd>{formatDuration(seconds)}</dd>
        {carried && (
          <>
            <dt>이월 포함 누적</dt>
            <dd>{formatDuration(task.cumulativeSeconds + (seconds - task.actualSeconds))}</dd>
          </>
        )}
        <dt>예상</dt>
        <dd>{task.estimatedMinutes ? `${task.estimatedMinutes}분` : '없음'}</dd>
      </dl>
      {task.status === 'TODO' && (
        <div className="detail-actions">
          <button className="button small" onClick={() => setEditing(true)} disabled={busy}>
            수정
          </button>
          {!task.hasRecords && (
            <button className="button small" onClick={() => onDelete(task)} disabled={busy}>
              삭제
            </button>
          )}
          <button className="button small primary" onClick={() => onComplete(task)} disabled={busy}>
            완료
          </button>
        </div>
      )}
    </div>
  )
}
