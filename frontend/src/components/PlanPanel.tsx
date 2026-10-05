import { useState, type FormEvent } from 'react'
import type { CarriedTask, CarryoverTask, NewTask } from '../api/types'
import { formatDuration } from '../lib/time'

interface CarryDraft extends CarryoverTask {
  included: boolean
  minutes: string
}

interface Props {
  carryover: CarryoverTask[]
  entering: boolean
  onEnter: (tasks: NewTask[], carriedTasks: CarriedTask[]) => void
}

/**
 * 스프린트 계획 입력. 첫 화면 오른쪽에 놓인다.
 * 나중에 회원 기능이 생기면 같은 자리에서 로그인 패널과 바꿔 끼운다.
 */
export function PlanPanel({ carryover, entering, onEnter }: Props) {
  const [carry, setCarry] = useState<CarryDraft[]>(() =>
    carryover.map((t) => ({ ...t, included: true, minutes: '' })),
  )
  const [tasks, setTasks] = useState<NewTask[]>([])
  const [title, setTitle] = useState('')
  const [minutes, setMinutes] = useState('')

  const addTask = (e: FormEvent) => {
    e.preventDefault()
    const trimmed = title.trim()
    if (!trimmed) return
    setTasks((list) => [...list, { title: trimmed, estimatedMinutes: minutes ? Number(minutes) : null }])
    setTitle('')
    setMinutes('')
  }

  const updateCarry = (taskId: number, change: Partial<CarryDraft>) =>
    setCarry((list) => list.map((t) => (t.taskId === taskId ? { ...t, ...change } : t)))

  const included = carry.filter((t) => t.included)
  const total = tasks.length + included.length

  const enter = () =>
    onEnter(
      tasks,
      included.map((t) => ({ fromTaskId: t.taskId, estimatedMinutes: t.minutes ? Number(t.minutes) : null })),
    )

  return (
    <section className="plan-panel" aria-label="스프린트 계획">
      <header className="plan-header">
        <p className="eyebrow">스프린트 계획</p>
        <h2>오늘은 무엇을 해볼까요?</h2>
        <p className="muted">할 일을 하나 이상 적으면 열람실에 들어갈 수 있어요. 예상 시간은 적지 않아도 괜찮아요.</p>
      </header>

      {carry.length > 0 && (
        <div className="card">
          <h3>지난번에 넘겨 둔 일</h3>
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
                      placeholder={t.previousEstimatedMinutes ? String(t.previousEstimatedMinutes) : '예상'}
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
        </div>
      )}

      <div className="card">
        <h3>새로운 할 일</h3>
        <form className="plan-add" onSubmit={addTask}>
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="예: 알고리즘 3문제 풀기"
            maxLength={100}
            aria-label="할 일 제목"
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
      </div>

      <div className="plan-footer">
        <button className="button primary large" onClick={enter} disabled={total === 0 || entering}>
          {entering ? '자리 찾는 중…' : `열람실 입장 (${total})`}
        </button>
      </div>
    </section>
  )
}
