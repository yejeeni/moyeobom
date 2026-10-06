import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import { isApiError } from '../api/client'
import type { MyStatus, Task } from '../api/types'
import { useSeatCharacters } from '../character/assignment'
import { CharacterModal } from '../components/CharacterModal'
import { GuideModal } from '../components/GuideModal'
import { Icon, type IconName } from '../components/Icon'
import { SeatTile } from '../components/SeatTile'
import { SettingsModal } from '../components/SettingsModal'
import { TaskPanel } from '../components/TaskPanel'
import { useToast } from '../components/Toasts'
import { getGuestId } from '../lib/guest'
import { STATE_LABEL, elapsedSeconds, formatClock, useNow } from '../lib/time'
import { useMediaQuery } from '../lib/useMediaQuery'
import { useRoom } from '../room/RoomContext'

const SEAT_NUMBERS = [1, 2, 3, 4, 5, 6, 7, 8, 9]

export function Room() {
  const navigate = useNavigate()
  const toast = useToast()
  const room = useRoom()
  const now = useNow()
  const compact = useMediaQuery('(max-width: 760px)')
  const [tasks, setTasks] = useState<Task[]>([])
  const [myStatus, setMyStatus] = useState<MyStatus | null>(null)
  const [statusOffset, setStatusOffset] = useState(0)
  const [busy, setBusy] = useState(false)
  // 넓은 화면에서는 기본으로 펼치고, 좁은 화면(컴팩트)에서는 접어 둔다
  const [panelWide, setPanelWide] = useState(true)
  const [panelCompact, setPanelCompact] = useState(false)
  const panelOpen = compact ? panelCompact : panelWide
  const setPanelOpen = compact ? setPanelCompact : setPanelWide
  const [modal, setModal] = useState<'guide' | 'settings' | 'characters' | null>(null)

  const applyStatus = useCallback((status: MyStatus) => {
    setStatusOffset(Date.parse(status.serverTime) - Date.now())
    setMyStatus(status)
  }, [])

  const loadTasks = useCallback(async () => {
    const { sprint } = await api.currentSprint()
    if (!sprint) {
      navigate('/', { replace: true })
      return
    }
    setTasks(sprint.tasks)
  }, [navigate])

  const loadAll = useCallback(async () => {
    await loadTasks()
    applyStatus(await api.currentFocus())
  }, [loadTasks, applyStatus])

  // 입장: 할 일과 내 상태를 불러오고, 방이 없으면 들어간다
  useEffect(() => {
    if (!getGuestId()) {
      navigate('/', { replace: true })
      return
    }
    loadAll().catch((e) => toast.show((e as Error).message, 'error'))
    if (!room.roomId) {
      room.enter().catch((e) => {
        if (isApiError(e, 'NO_TASK_FOR_ROOM')) navigate('/', { replace: true })
        else toast.show((e as Error).message, 'error')
      })
    }
    // 처음 한 번만 실행한다
  }, [])

  // 다시 연결되면 끊긴 동안 바뀐 내 상태(세션 자동 종료 등)를 다시 불러온다
  useEffect(() => {
    if (room.snapshotVersion > 1) loadAll().catch(() => {})
  }, [room.snapshotVersion, loadAll])

  const run = async (action: () => Promise<void>) => {
    setBusy(true)
    try {
      await action()
    } catch (e) {
      toast.show((e as Error).message, 'error')
    } finally {
      setBusy(false)
    }
  }

  const focus = (task: Task) =>
    run(async () => {
      applyStatus(await api.startFocus(task.taskId))
      await loadTasks()
    })

  const stop = (reason: 'STOPPED' | 'BREAK') =>
    run(async () => {
      applyStatus(await api.stopFocus(reason))
      await loadTasks()
    })

  const complete = (task: Task) =>
    run(async () => {
      const { refreshMessage } = await api.completeTask(task.taskId)
      toast.show(`"${task.title}" 완료! ${refreshMessage}`, 'refresh')
      await loadAll()
    })

  const remove = (task: Task) =>
    run(async () => {
      await api.deleteTask(task.taskId)
      await loadTasks()
    })

  const rename = (task: Task, title: string, estimatedMinutes: number | null) =>
    run(async () => {
      await api.updateTask(task.taskId, { title, ...(estimatedMinutes ? { estimatedMinutes } : {}) })
      await loadTasks()
    })

  const add = async (title: string, estimatedMinutes: number | null) => {
    try {
      await api.addTask({ title, estimatedMinutes })
      await loadTasks()
      return true
    } catch (e) {
      toast.show((e as Error).message, 'error')
      return false
    }
  }

  // 대기·휴식 중에는 아래 툴바 가운데 버튼으로 다음 할 일을 바로 시작한다
  const nextTask = tasks.find((t) => t.status === 'TODO') ?? null
  const startNext = () => {
    if (nextTask) {
      focus(nextTask)
      return
    }
    setPanelOpen(true)
    toast.show('먼저 오늘 할 일을 추가해 주세요')
  }

  const finishToday = () =>
    run(async () => {
      if (myStatus?.state === 'FOCUS') applyStatus(await api.stopFocus('STOPPED'))
      navigate('/review')
    })

  const state = myStatus?.state ?? 'IDLE'
  const focusingTask = useMemo(
    () => tasks.find((t) => t.taskId === myStatus?.session?.taskId) ?? null,
    [tasks, myStatus],
  )
  const myElapsed = state === 'IDLE' ? 0 : elapsedSeconds(myStatus?.since, now, statusOffset)

  const seatCharacters = useSeatCharacters(room.roomId, room.seats, room.mySeatNo)
  const occupied = SEAT_NUMBERS.filter((n) => room.seats[n]).length
  const focusing = SEAT_NUMBERS.filter((n) => room.seats[n]?.state === 'FOCUS').length
  const hasSeats = occupied > 0

  return (
    <div className={`room-page ${compact ? 'is-compact' : ''} ${panelOpen ? 'panel-open' : ''}`}>
      <header className="room-topbar">
        <div className="topbar-left">
          <span className="logo">
            <span className="logo-mark" aria-hidden="true" />
            모여봄
          </span>
          {hasSeats && (
            <span className="focus-count">
              <span className="live-dot" aria-hidden="true" />
              {occupied}명 중 <strong>{focusing}명</strong> 집중 중
            </span>
          )}
        </div>
        <div className={`topbar-timer timer-${state.toLowerCase()}`} aria-live="off">
          {state === 'IDLE' ? (
            <div className="timer-prompt">할 일을 골라 집중을 시작해요</div>
          ) : (
            <div className="timer-value">{formatClock(myElapsed)}</div>
          )}
          <div className="timer-caption">
            <span className={`my-state chip-${state.toLowerCase()}`}>
              <span className="dot" aria-hidden="true" />
              {STATE_LABEL[state]}
            </span>
            {state === 'FOCUS' && <span className="caption-text">{focusingTask ? focusingTask.title : '집중 중'}</span>}
            {state === 'BREAK' && <span className="caption-text">잠깐 쉬는 중이에요</span>}
          </div>
        </div>
        <div className="topbar-right">{room.nickname && <span className="me-chip">나 · {room.nickname}</span>}</div>
      </header>

      {room.connection === 'reconnecting' && (
        <div className="banner" role="status">
          다시 연결하는 중이에요…
        </div>
      )}

      <div className="room-body">
        <main className="seat-grid" aria-label="열람실 자리">
          {hasSeats
            ? SEAT_NUMBERS.map((n) => (
                <SeatTile
                  key={n}
                  seatNo={n}
                  occupant={room.seats[n] ?? null}
                  isMe={n === room.mySeatNo}
                  now={now}
                  offsetMs={room.offsetMs}
                  compact={compact}
                  character={seatCharacters[n]}
                />
              ))
            : SEAT_NUMBERS.map((n) => (
                <div key={n} className="seat seat-empty seat-loading">
                  {n === 5 && <span>자리 찾는 중…</span>}
                </div>
              ))}
        </main>
        {/* 넓은 화면에서는 늘 그려 두고 CSS로 밀어 넣고 뺀다 */}
        {(panelOpen || !compact) && (
          <TaskPanel
            tasks={tasks}
            myStatus={myStatus}
            liveSeconds={state === 'FOCUS' ? myElapsed : 0}
            busy={busy}
            onFocus={focus}
            onComplete={complete}
            onDelete={remove}
            onRename={rename}
            onAdd={add}
            onClose={compact ? () => setPanelOpen(false) : undefined}
          />
        )}
      </div>

      <footer className="room-toolbar">
        <div className="toolbar-group">
          <ToolButton icon="list" label="할 일" active={panelOpen} onClick={() => setPanelOpen((v) => !v)} />
          <ToolButton icon="help" label="안내" onClick={() => setModal('guide')} />
          {/* 좁은 창에서는 자리가 모자라 설정 안에서 연다 */}
          {!compact && <ToolButton icon="image" label="캐릭터" onClick={() => setModal('characters')} />}
          <ToolButton icon="settings" label="설정" onClick={() => setModal('settings')} />
        </div>
        <div className="toolbar-group toolbar-center">
          {state === 'FOCUS' ? (
            <>
              <ToolButton icon="coffee" label="휴식" disabled={busy} onClick={() => stop('BREAK')} />
              <ToolButton icon="stop" label="중단" disabled={busy} onClick={() => stop('STOPPED')} />
              <ToolButton
                icon="check"
                label="완료"
                disabled={!focusingTask || busy}
                onClick={() => focusingTask && complete(focusingTask)}
              />
            </>
          ) : (
            <button className="start-button" onClick={startNext} disabled={busy}>
              <Icon name="play" size={18} />
              <span className="start-text">
                <strong>{state === 'BREAK' ? '다시 집중' : '집중 시작'}</strong>
                {nextTask && <small>{nextTask.title}</small>}
              </span>
            </button>
          )}
        </div>
        <div className="toolbar-group">
          <button className="end-button" onClick={finishToday} disabled={busy} aria-label="오늘 마무리">
            <Icon name="moon" size={16} />
            {compact ? '마무리' : '오늘 마무리'}
          </button>
        </div>
      </footer>

      {modal === 'guide' && <GuideModal onClose={() => setModal(null)} />}
      {modal === 'settings' && (
        <SettingsModal onClose={() => setModal(null)} onOpenCharacters={() => setModal('characters')} />
      )}
      {modal === 'characters' && <CharacterModal onClose={() => setModal(null)} />}
    </div>
  )
}

function ToolButton({
  icon,
  label,
  onClick,
  disabled,
  active,
}: {
  icon: IconName
  label: string
  onClick: () => void
  disabled?: boolean
  active?: boolean
}) {
  return (
    <button
      className={`tool-button ${active ? 'is-active' : ''}`}
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      aria-pressed={active}
    >
      <span className="tool-icon">
        <Icon name={icon} size={22} />
      </span>
      <span className="tool-label">{label}</span>
    </button>
  )
}
