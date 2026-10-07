import type { PointerEvent } from 'react'
import type { Occupant } from '../api/types'
import { elapsedSeconds, formatClock, useNow } from '../lib/time'
import { SeatTile } from './SeatTile'

const minutesAgo = (m: number) => new Date(Date.now() - m * 60_000).toISOString()

// 소개용 그림. 실제 열람실과 같은 규칙으로 그린다.
const DEMO: Occupant[] = [
  { nickname: '졸린 수달', character: { hair: 0, hairColor: 1, shirt: 1, skin: 1 }, state: 'FOCUS', since: minutesAgo(37), completedCount: 2, remainingCount: 1 },
  { nickname: '조용한 연필', character: { hair: 5, hairColor: 0, shirt: 7, skin: 0 }, state: 'FOCUS', since: minutesAgo(12), completedCount: 0, remainingCount: 3 },
  { nickname: '느긋한 고양이', character: { hair: 7, hairColor: 4, shirt: 3, skin: 2 }, state: 'BREAK', since: minutesAgo(4), completedCount: 1, remainingCount: 2 },
  { nickname: '부지런한 다람쥐', character: { hair: 6, hairColor: 2, shirt: 2, skin: 0 }, state: 'FOCUS', since: minutesAgo(58), completedCount: 3, remainingCount: 0 },
]

interface Props {
  onOpenGuide: () => void
}

/** 첫 화면 오른쪽의 열람실 미리보기. 가상 메이트가 섞여 있다는 안내를 항상 함께 보여준다. */
export function DemoRoom({ onOpenGuide }: Props) {
  const now = useNow()

  // 마우스 위치에 따라 화면이 살짝 기운다
  const tilt = (e: PointerEvent<HTMLDivElement>) => {
    if (e.pointerType !== 'mouse') return
    const rect = e.currentTarget.getBoundingClientRect()
    const x = (e.clientX - rect.left) / rect.width - 0.5
    const y = (e.clientY - rect.top) / rect.height - 0.5
    e.currentTarget.style.setProperty('--ry', `${(x * 8).toFixed(2)}deg`)
    e.currentTarget.style.setProperty('--rx', `${(-y * 6).toFixed(2)}deg`)
  }
  const resetTilt = (e: PointerEvent<HTMLDivElement>) => {
    e.currentTarget.style.setProperty('--ry', '0deg')
    e.currentTarget.style.setProperty('--rx', '0deg')
  }

  return (
    <div className="demo">
      <div className="demo-stage" onPointerMove={tilt} onPointerLeave={resetTilt} aria-hidden="true">
        <div className="demo-frame">
          <div className="demo-bar">
            <span className="demo-dots">
              <i />
              <i />
              <i />
            </span>
            <span className="demo-timer">{formatClock(elapsedSeconds(DEMO[0].since, now, 0))}</span>
            <span className="demo-live">
              <span className="live-dot" />
              집중 3명
            </span>
          </div>
          <div className="demo-grid">
            {DEMO.map((occupant, i) => (
              <SeatTile key={i} seatNo={i + 1} occupant={occupant} isMe={false} now={now} offsetMs={0} compact={false} />
            ))}
          </div>
        </div>

        <div className="float-card float-done">
          <span className="float-icon check">✓</span>
          <span>
            <strong>알고리즘 3문제</strong>
            <small>예상 60분 · 실제 52분</small>
          </span>
        </div>
        <div className="float-card float-lamp">
          <span className="float-icon lamp-icon" />
          <span>
            <strong>스탠드가 켜졌어요</strong>
            <small>집중을 시작했어요</small>
          </span>
        </div>
      </div>

      <p className="mate-notice">
        <strong>가상 스터디 메이트가 함께해요.</strong> 열람실이 비어 보이지 않도록 서버가 만든 메이트가 빈자리를
        채워요.{' '}
        <button className="link-button" onClick={onOpenGuide}>
          자세히 보기
        </button>
      </p>
    </div>
  )
}
