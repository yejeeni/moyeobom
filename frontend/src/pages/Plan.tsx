import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import { isApiError } from '../api/client'
import type { CarryoverTask, NewTask } from '../api/types'
import { useToast } from '../components/Toasts'
import { getGuestId } from '../lib/guest'
import { formatDuration } from '../lib/time'

interface CarryDraft extends CarryoverTask {
  included: boolean
  minutes: string
}

export function Plan() {
  const navigate = useNavigate()
  const toast = useToast()
  const [loading, setLoading] = useState(true)
  const [carry, setCarry] = useState<CarryDraft[]>([])
  const [tasks, setTasks] = useState<NewTask[]>([])
  const [title, setTitle] = useState('')
  const [minutes, setMinutes] = useState('')
  const [entering, setEntering] = useState(false)

  useEffect(() => {
    if (!getGuestId()) {
      navigate('/', { replace: true })
      return
    }
    ;(async () => {
      try {
        const { sprint } = await api.currentSprint()
        if (sprint) {
          navigate('/room', { replace: true })
          return
        }
        const { tasks: carried } = await api.carryover()
        setCarry(carried.map((t) => ({ ...t, included: true, minutes: '' })))
        setLoading(false)
      } catch (e) {
        if (isApiError(e, 'GUEST_NOT_FOUND')) navigate('/', { replace: true })
        else toast.show((e as Error).message, 'error')
      }
    })()
  }, [navigate, toast])

  const addTask = (e: FormEvent) => {
    e.preventDefault()
    const trimmed = title.trim()
    if (!trimmed) return
    setTasks((list) => [...list, { title: trimmed, estimatedMinutes: minutes ? Number(minutes) : null }])
    setTitle('')
    setMinutes('')
  }

  const included = carry.filter((t) => t.included)
  const total = tasks.length + included.length

  const enter = async () => {
    setEntering(true)
    try {
      await api.startSprint(
        tasks,
        included.map((t) => ({ fromTaskId: t.taskId, estimatedMinutes: t.minutes ? Number(t.minutes) : null })),
      )
      navigate('/room')
    } catch (e) {
      if (isApiError(e, 'SPRINT_ALREADY_OPEN')) {
        navigate('/room')
        return
      }
      toast.show((e as Error).message, 'error')
      setEntering(false)
    }
  }

  const updateCarry = (taskId: number, change: Partial<CarryDraft>) =>
    setCarry((list) => list.map((t) => (t.taskId === taskId ? { ...t, ...change } : t)))

  if (loading) return <div className="page-loading">오늘 계획을 불러오는 중…</div>

  return (
    <main className="plan">
      <header className="plan-header">
        <p className="eyebrow">스프린트 계획</p>
        <h1>오늘은 무엇을 해볼까요?</h1>
        <p className="muted">할 일을 하나 이상 적으면 열람실에 들어갈 수 있어요. 예상 시간은 적지 않아도 괜찮아요.</p>
      </header>

      {carry.length > 0 && (
        <section className="card">
          <h2>지난번에 넘겨 둔 일</h2>
          <p className="muted small">남은 작업을 기준으로 예상 시간을 다시 적어 주세요. 실제 시간은 새로 재요.</p>
          <ul className="plan-list">
            {carry.map((t) => (
              <li key={t.taskId} className={t.included ? '' : 'excluded'}>
                <div className="plan-item-title">
                  {t.title}
                  <span className="muted small">지금까지 {formatDuration(t.cumulativeSeconds)}</span>
                </div>
                {t.included && (
                  <label className="inline">
                    <input
                      type="number"
                      min={1}
                      value={t.minutes}
                      placeholder={t.previousEstimatedMinutes ? String(t.previousEstimatedMinutes) : '분'}
                      onChange={(e) => updateCarry(t.taskId, { minutes: e.target.value })}
                      aria-label={`${t.title} 남은 예상 시간(분)`}
                    />
                    분
                  </label>
                )}
                <button className="button small" onClick={() => updateCarry(t.taskId, { included: !t.included })}>
                  {t.included ? '빼기' : '다시 넣기'}
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="card">
        <h2>새로운 할 일</h2>
        <form className="plan-add" onSubmit={addTask}>
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="예: 알고리즘 3문제 풀기"
            maxLength={100}
            aria-label="할 일 제목"
            autoFocus
          />
          <label className="inline">
            <input
              type="number"
              min={1}
              value={minutes}
              onChange={(e) => setMinutes(e.target.value)}
              placeholder="예상"
              aria-label="예상 시간(분, 선택)"
            />
            분
          </label>
          <button className="button" type="submit" disabled={!title.trim()}>
            추가
          </button>
        </form>
        {tasks.length > 0 ? (
          <ul className="plan-list">
            {tasks.map((t, i) => (
              <li key={i}>
                <div className="plan-item-title">
                  {t.title}
                  <span className="muted small">{t.estimatedMinutes ? `예상 ${t.estimatedMinutes}분` : '예상 없음'}</span>
                </div>
                <button className="button small" onClick={() => setTasks((list) => list.filter((_, j) => j !== i))}>
                  삭제
                </button>
              </li>
            ))}
          </ul>
        ) : (
          total === 0 && <p className="empty">오늘 할 일 하나만 적어볼까요?</p>
        )}
      </section>

      <div className="plan-footer">
        <button className="button primary large" onClick={enter} disabled={total === 0 || entering}>
          {entering ? '자리 찾는 중…' : `열람실 입장 (${total})`}
        </button>
      </div>
    </main>
  )
}
