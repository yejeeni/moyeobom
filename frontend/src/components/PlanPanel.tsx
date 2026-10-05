import { useEffect, useState, type CSSProperties, type FormEvent } from 'react'
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

// 입력칸 예시가 몇 초마다 바뀐다
const EXAMPLES = ['알고리즘 3문제 풀기', '영어 단어 50개 외우기', '자료구조 강의 1개 듣기', '보고서 초안 쓰기']

/**
 * 스프린트 계획 입력. 첫 화면 소개 문구 아래에 놓인다.
 * 나중에 회원 기능이 생기면 같은 자리에서 로그인 패널과 바꿔 끼운다.
 */
export function PlanPanel({ carryover, entering, onEnter }: Props) {
  const [carry, setCarry] = useState<CarryDraft[]>(() =>
    carryover.map((t) => ({ ...t, included: true, minutes: '' })),
  )
  const [tasks, setTasks] = useState<NewTask[]>([])
  const [title, setTitle] = useState('')
  const [minutes, setMinutes] = useState('')
  const [example, setExample] = useState(0)

  useEffect(() => {
    const id = window.setInterval(() => setExample((i) => (i + 1) % EXAMPLES.length), 3200)
    return () => window.clearInterval(id)
  }, [])

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
  const plannedMinutes =
    tasks.reduce((sum, t) => sum + (t.estimatedMinutes ?? 0), 0) +
    included.reduce((sum, t) => sum + (Number(t.minutes) || 0), 0)

  const enter = () =>
    onEnter(
      tasks,
      included.map((t) => ({ fromTaskId: t.taskId, estimatedMinutes: t.minutes ? Number(t.minutes) : null })),
    )

  return (
    <section className="plan-panel rise" style={{ '--delay': '240ms' } as CSSProperties} aria-label="스프린트 계획">
      <header className="plan-head">
        <h2>오늘은 무엇을 해볼까요?</h2>
        <p className="muted small">할 일을 하나 이상 적으면 입장할 수 있어요. 예상 시간은 적지 않아도 괜찮아요.</p>
      </header>

      <form className="plan-add" onSubmit={addTask}>
        <input
          id="plan-title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder={`예: ${EXAMPLES[example]}`}
          maxLength={100}
          aria-label="할 일 제목"
          autoComplete="off"
        />
        <label className="plan-minutes">
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
        <button className="add-button" type="submit" disabled={!title.trim()} aria-label="할 일 추가">
          +
        </button>
      </form>

      {carry.length > 0 && (
        <div className="plan-group">
          <h3>
            지난번에 넘겨 둔 일
            <span className="muted small"> · 남은 작업 기준으로 예상 시간을 다시 적어 주세요</span>
          </h3>
          <ul className="plan-list">
            {carry.map((t) => (
              <li key={t.taskId} className={t.included ? '' : 'excluded'}>
                <span className="plan-bullet carry" aria-hidden="true" />
                <div className="plan-item-title">
                  {t.title}
                  <span className="muted small">지금까지 {formatDuration(t.cumulativeSeconds)}</span>
                </div>
                {t.included && (
                  <label className="plan-minutes compact">
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
                <button className="text-button" onClick={() => updateCarry(t.taskId, { included: !t.included })}>
                  {t.included ? '빼기' : '다시 넣기'}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      {tasks.length > 0 ? (
        <ul className="plan-list">
          {tasks.map((t, i) => (
            <li key={i}>
              <span className="plan-bullet" aria-hidden="true" />
              <div className="plan-item-title">
                {t.title}
                <span className="muted small">{t.estimatedMinutes ? `예상 ${t.estimatedMinutes}분` : '예상 없음'}</span>
              </div>
              <button
                className="remove-button"
                onClick={() => setTasks((list) => list.filter((_, j) => j !== i))}
                aria-label={`${t.title} 삭제`}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      ) : (
        total === 0 && <p className="empty">오늘 할 일 하나만 적어볼까요?</p>
      )}

      <button className="button primary large enter-button" onClick={enter} disabled={total === 0 || entering}>
        {entering ? (
          '자리 찾는 중…'
        ) : (
          <>
            열람실 입장하기
            {total > 0 && (
              <span className="enter-meta">
                {total}개{plannedMinutes > 0 && ` · ${formatDuration(plannedMinutes * 60)}`}
              </span>
            )}
          </>
        )}
      </button>
    </section>
  )
}
