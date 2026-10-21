import { useEffect, useState } from 'react'
import { api } from '../api'
import type { CloseAction, CloseResult, Review } from '../api/types'
import { isAlertSoundOn, playChime } from '../lib/preferences'
import { formatDiff, formatDuration } from '../lib/time'
import { useRoom } from '../room/RoomContext'
import { Modal } from './Modal'
import { useToast } from './Toasts'

/** 마무리 안내를 보여 주고 첫 화면으로 넘어가기까지의 시간 */
const DONE_DELAY_MS = 4000

interface Props {
  /** 확정하지 않고 닫았을 때 */
  onClose: () => void
  /** 확정 후 안내가 끝났을 때(자동 또는 바로 가기). 첫 화면으로 보낸다 */
  onDone: () => void
}

/**
 * 오늘 마무리: 회고(계획 대비 집중, 할 일별 예상과 실제, 못 끝낸 일 처리)를 모달로 보여 주고,
 * 확정하면 같은 모달에서 마무리 인사를 띄운 뒤 잠시 후 첫 화면으로 보낸다.
 * 모니터에서 쓰는 화면이라 왼쪽에 요약, 오른쪽에 표와 이월 선택을 두는 가로형으로 그린다.
 */
export function ReviewModal({ onClose, onDone }: Props) {
  const toast = useToast()
  const room = useRoom()
  const [review, setReview] = useState<Review | null>(null)
  const [actions, setActions] = useState<Record<number, CloseAction>>({})
  const [closing, setClosing] = useState(false)
  const [result, setResult] = useState<CloseResult | null>(null)

  useEffect(() => {
    api.review()
      .then((data) => {
        setReview(data)
        const initial: Record<number, CloseAction> = {}
        data.tasks.filter((t) => t.status === 'TODO').forEach((t) => (initial[t.taskId] = 'CARRY'))
        setActions(initial)
      })
      .catch((e) => {
        toast.show((e as Error).message, 'error')
        onClose()
      })
    // 열 때 한 번만 불러온다
  }, [])

  // 마무리 인사가 뜨면 잠시 보여 준 뒤 첫 화면으로
  useEffect(() => {
    if (!result) return
    const id = window.setTimeout(onDone, DONE_DELAY_MS)
    return () => window.clearTimeout(id)
  }, [result, onDone])

  const confirm = async () => {
    setClosing(true)
    try {
      const closed = await api.closeSprint(
        Object.entries(actions).map(([taskId, action]) => ({ taskId: Number(taskId), action })),
      )
      room.leave()
      if (isAlertSoundOn()) playChime()
      setResult(closed)
    } catch (e) {
      toast.show((e as Error).message, 'error')
      setClosing(false)
    }
  }

  if (result) {
    return (
      <Modal title="오늘도 수고했어요" onClose={onDone} className="modal-wide review-modal is-done">
        <div className="review-done">
          <div className="done-lamp" aria-hidden="true" />
          <p className="refresh-message">{result.refreshMessage}</p>
          <p className="muted">
            {result.carriedCount > 0 ? `${result.carriedCount}개는 다음 계획에 미리 채워 둘게요.` : '남은 일 없이 깔끔하게 마쳤어요.'}
          </p>
          <p className="muted small">잠시 뒤 첫 화면으로 이동해요</p>
          <div className="done-progress" style={{ animationDuration: `${DONE_DELAY_MS}ms` }} aria-hidden="true" />
          <button className="button" onClick={onDone}>
            바로 이동
          </button>
        </div>
      </Modal>
    )
  }

  const unfinished = review?.tasks.filter((t) => t.status === 'TODO') ?? []

  return (
    <Modal title="오늘 하루를 돌아봐요" onClose={closing ? () => {} : onClose} className="modal-wide review-modal">
      {!review ? (
        <p className="muted">오늘 기록을 모으는 중…</p>
      ) : (
        <div className="modal-columns review-columns">
          <section className="summary">
            <div className="summary-item">
              <span className="summary-label">{review.plannedSeconds ? '계획한 시간 대비 집중' : '총 집중 시간'}</span>
              <strong>
                {review.plannedSeconds
                  ? `${formatDuration(review.plannedSeconds)} → ${formatDuration(review.totalFocusSeconds)}`
                  : formatDuration(review.totalFocusSeconds)}
              </strong>
              {review.plannedSeconds ? (
                <span className="muted small">
                  계획한 시간의 {Math.round((review.totalFocusSeconds / review.plannedSeconds) * 100)}%를 집중했어요
                </span>
              ) : null}
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

          <div className="modal-column">
            <section>
              <h3 className="review-section-title">할 일별 예상과 실제</h3>
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
                      {/* 끝내지 못한 일은 아직 걸린 시간을 모르므로 차이를 매기지 않는다 */}
                      <td className={t.status === 'DONE' && t.diffSeconds !== null && t.diffSeconds > 0 ? 'over' : ''}>
                        {t.status !== 'DONE' ? (
                          <span className="muted">끝나면 비교</span>
                        ) : t.diffSeconds !== null ? (
                          formatDiff(t.diffSeconds)
                        ) : (
                          '-'
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>

            {unfinished.length > 0 && (
              <section>
                <h3 className="review-section-title">못 끝낸 일은 어떻게 할까요?</h3>
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
          </div>
        </div>
      )}

      <div className="modal-actions is-row">
        <button className="button" onClick={onClose} disabled={closing}>
          조금 더 할게요
        </button>
        <button className="button primary" onClick={confirm} disabled={!review || closing}>
          {closing ? '마무리하는 중…' : '오늘 마무리 확정'}
        </button>
      </div>
    </Modal>
  )
}
