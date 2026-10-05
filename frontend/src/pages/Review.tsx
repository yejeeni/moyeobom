import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import { isApiError } from '../api/client'
import type { CloseAction, CloseResult, Review as ReviewData } from '../api/types'
import { useToast } from '../components/Toasts'
import { getGuestId } from '../lib/guest'
import { formatDiff, formatDuration } from '../lib/time'
import { useRoom } from '../room/RoomContext'

export function Review() {
  const navigate = useNavigate()
  const toast = useToast()
  const room = useRoom()
  const [review, setReview] = useState<ReviewData | null>(null)
  const [actions, setActions] = useState<Record<number, CloseAction>>({})
  const [closing, setClosing] = useState(false)
  const [result, setResult] = useState<CloseResult | null>(null)

  useEffect(() => {
    if (!getGuestId()) {
      navigate('/', { replace: true })
      return
    }
    api.review()
      .then((data) => {
        setReview(data)
        const initial: Record<number, CloseAction> = {}
        data.tasks.filter((t) => t.status === 'TODO').forEach((t) => (initial[t.taskId] = 'CARRY'))
        setActions(initial)
      })
      .catch((e) => {
        if (isApiError(e, 'SPRINT_NOT_FOUND')) navigate('/plan', { replace: true })
        else toast.show((e as Error).message, 'error')
      })
  }, [navigate, toast])

  const confirm = async () => {
    setClosing(true)
    try {
      const closed = await api.closeSprint(
        Object.entries(actions).map(([taskId, action]) => ({ taskId: Number(taskId), action })),
      )
      room.leave()
      setResult(closed)
    } catch (e) {
      toast.show((e as Error).message, 'error')
      setClosing(false)
    }
  }

  if (result) {
    return (
      <main className="review review-done">
        <div className="done-lamp" aria-hidden="true" />
        <h1>오늘도 수고했어요</h1>
        <p className="refresh-message">{result.refreshMessage}</p>
        <p className="muted">
          {result.carriedCount > 0 ? `${result.carriedCount}개는 다음 계획에 미리 채워 둘게요.` : '남은 일 없이 깔끔하게 마쳤어요.'}
        </p>
        <button className="button primary large" onClick={() => navigate('/plan')}>
          새 스프린트 시작
        </button>
      </main>
    )
  }

  if (!review) return <div className="page-loading">오늘 기록을 모으는 중…</div>

  const unfinished = review.tasks.filter((t) => t.status === 'TODO')

  return (
    <main className="review">
      <header className="plan-header">
        <p className="eyebrow">스프린트 회고</p>
        <h1>오늘 하루를 돌아봐요</h1>
      </header>

      <section className="summary">
        <div className="summary-item">
          <span className="summary-label">총 집중 시간</span>
          <strong>{formatDuration(review.totalFocusSeconds)}</strong>
        </div>
        <div className="summary-item">
          <span className="summary-label">완료한 일</span>
          <strong>{review.completedCount}개</strong>
        </div>
        <div className="summary-item">
          <span className="summary-label">예상 대비 실제</span>
          <strong>
            {review.estimatedSecondsOfDone > 0
              ? `${formatDuration(review.estimatedSecondsOfDone)} → ${formatDuration(review.actualSecondsOfDone)}`
              : '비교할 기록 없음'}
          </strong>
          <span className="muted small">예상 시간을 적은 완료 할 일 기준</span>
        </div>
      </section>

      <section className="card">
        <h2>할 일별 예상과 실제</h2>
        <table className="review-table">
          <thead>
            <tr>
              <th scope="col">할 일</th>
              <th scope="col">예상</th>
              <th scope="col">실제</th>
              <th scope="col">차이</th>
            </tr>
          </thead>
          <tbody>
            {review.tasks.map((t) => (
              <tr key={t.taskId}>
                <td>
                  {t.status === 'DONE' ? '✓ ' : ''}
                  {t.title}
                  {t.cumulativeSeconds > t.actualSeconds && (
                    <span className="muted small"> · 이월 포함 {formatDuration(t.cumulativeSeconds)}</span>
                  )}
                </td>
                <td>{t.estimatedSeconds !== null ? formatDuration(t.estimatedSeconds) : <span className="muted">예상 없음</span>}</td>
                <td>{formatDuration(t.actualSeconds)}</td>
                <td className={t.diffSeconds !== null && t.diffSeconds > 0 ? 'over' : ''}>
                  {t.diffSeconds !== null ? formatDiff(t.diffSeconds) : '-'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {unfinished.length > 0 && (
        <section className="card">
          <h2>못 끝낸 일은 어떻게 할까요?</h2>
          <ul className="plan-list">
            {unfinished.map((t) => (
              <li key={t.taskId}>
                <div className="plan-item-title">{t.title}</div>
                <div className="segmented" role="radiogroup" aria-label={`${t.title} 처리`}>
                  {(['CARRY', 'DROP'] as const).map((action) => (
                    <label key={action} className={actions[t.taskId] === action ? 'selected' : ''}>
                      <input
                        type="radio"
                        name={`action-${t.taskId}`}
                        checked={actions[t.taskId] === action}
                        onChange={() => setActions((a) => ({ ...a, [t.taskId]: action }))}
                      />
                      {action === 'CARRY' ? '다음에 이어서' : '여기서 닫기'}
                    </label>
                  ))}
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="plan-footer">
        <button className="button" onClick={() => navigate('/room')} disabled={closing}>
          열람실로 돌아가기
        </button>
        <button className="button primary large" onClick={confirm} disabled={closing}>
          오늘 마무리 확정
        </button>
      </div>
    </main>
  )
}
