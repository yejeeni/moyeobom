import { useCallback, useEffect, useState } from 'react'
import { api } from '../api'
import { isApiError } from '../api/client'
import type { RoomEnter, SprintView } from '../api/types'
import { formatDuration } from '../lib/time'
import { getRoomConfig } from '../lib/roomSize'
import { RoomChoice, type CodeStatus, type RoomChoiceValue } from './RoomChoice'

/** 타임박스 남은 시간 문구. 화면을 연 시점 기준이다 */
function remainingText(endsAt: string): string {
  const seconds = Math.floor((Date.parse(endsAt) - Date.now()) / 1000)
  return seconds > 0 ? `${formatDuration(seconds)} 남음` : '계획한 시간이 지났어요'
}

interface Props {
  sprint: SprintView
  /** 초대 링크로 왔으면 그 코드 */
  inviteCode?: string
  busy: boolean
  /** 원래 있던 열람실로 돌아간다 */
  onResume: () => void
  /** 다른 열람실로 들어간다(새로 만들기 또는 코드) */
  onEnter: (room: RoomChoiceValue) => void
  /** 오늘 마무리(회고)로 간다 */
  onFinish: () => void
}

/**
 * 진행 중인 스프린트가 있을 때 첫 화면 오른쪽 계획 카드 자리에 보여 준다.
 * 바로 열람실로 보내지 않고, 돌아갈지·나와서 다른 열람실을 고를지·오늘을 마무리할지 고르게 한다.
 */
export function ResumePanel({ sprint, inviteCode, busy, onResume, onEnter, onFinish }: Props) {
  // undefined: 확인 중, null: 지금 들어가 있는 방이 없음(나왔거나 서버 재시작으로 닫힘)
  const [current, setCurrent] = useState<RoomEnter | null | undefined>(undefined)
  const [leaving, setLeaving] = useState(false)
  const [choice, setChoice] = useState<RoomChoiceValue>(() =>
    inviteCode ? { mode: 'join', code: inviteCode } : { mode: 'create', config: getRoomConfig() },
  )
  const [codeStatus, setCodeStatus] = useState<CodeStatus>({ kind: 'empty' })
  const onCodeStatus = useCallback((status: CodeStatus) => setCodeStatus(status), [])

  useEffect(() => {
    api.currentRoom()
      .then(setCurrent)
      .catch((e) => {
        if (!isApiError(e, 'ROOM_NOT_FOUND')) console.warn(e)
        setCurrent(null)
      })
  }, [])

  const leave = async () => {
    setLeaving(true)
    try {
      await api.leaveRoom()
      setCurrent(null)
    } finally {
      setLeaving(false)
    }
  }

  const done = sprint.tasks.filter((t) => t.status === 'DONE').length
  const focusedSeconds = sprint.tasks.reduce((sum, t) => sum + t.actualSeconds, 0)
  const canEnter = choice.mode === 'create' || codeStatus.kind === 'ready'
  // 초대 링크로 왔으면 원래 방이 있어도 옮길 곳을 먼저 보여 준다
  const choosing = current === null || (current !== undefined && inviteCode !== undefined && current.code !== inviteCode)
  // 다른 방에 있다가 초대 링크로 온 경우. 초대받은 방을 먼저 권하고 원래 방은 아래에 둔다
  const invited = Boolean(current) && choosing

  const chooseBlock = () => (
    <div className="resume-choose">
      <p className="muted small">
        {invited
          ? '초대받은 열람실로 옮길 수 있어요. 할 일과 기록은 그대로예요'
          : '할 일과 기록은 그대로예요. 들어갈 열람실을 골라 주세요'}
      </p>
      <RoomChoice value={choice} onChange={setChoice} onCodeStatus={onCodeStatus} />
      <button className="button primary large enter-button" onClick={() => onEnter(choice)} disabled={busy || !canEnter}>
        {busy ? '자리 찾는 중…' : choice.mode === 'join' ? '코드로 입장하기' : '새 열람실 입장하기'}
      </button>
    </div>
  )

  return (
    <section className="plan-panel resume-panel" aria-busy={current === undefined}>
      <div className="plan-head">
        <h2>{current ? '참여 중이던 열람실이 있어요' : '진행 중인 공부가 있어요'}</h2>
        <p className="muted">
          할 일 {sprint.tasks.length}개 중 {done}개 완료
          {focusedSeconds > 0 && ` · 지금까지 ${formatDuration(focusedSeconds)} 집중`}
          {sprint.endsAt && ` · ${remainingText(sprint.endsAt)}`}
        </p>
      </div>

      {current === undefined && <p className="muted small">열람실을 확인하는 중…</p>}

      {invited && chooseBlock()}

      {current && (
        <div className="resume-room">
          <div>
            <strong>{current.nickname}</strong>
            <span className="muted small">
              {' '}
              · {current.seatCount}자리 열람실
              {current.realSeatCount > 1 && ` · 코드 ${current.code}`}
            </span>
          </div>
          <div className="resume-actions">
            <button
              className={invited ? 'button' : 'button primary large enter-button'}
              onClick={onResume}
              disabled={busy || leaving}
            >
              {invited ? '원래 열람실로 돌아가기' : '열람실로 돌아가기'}
            </button>
            <button className="button" onClick={leave} disabled={busy || leaving}>
              {leaving ? '나오는 중…' : '열람실에서 나오기'}
            </button>
          </div>
        </div>
      )}

      {current === null && chooseBlock()}

      <button className="text-button resume-finish" onClick={onFinish} disabled={busy}>
        오늘은 여기까지 · 마무리하고 회고 보기
      </button>
    </section>
  )
}
