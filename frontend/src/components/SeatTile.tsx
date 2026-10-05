import type { Occupant } from '../api/types'
import { STATE_LABEL, elapsedSeconds, formatShort } from '../lib/time'
import { Character } from './Character'

// 줌 화면의 각 칸처럼 자리마다 방 분위기를 조금씩 다르게 한다
const ROOM_TONES = ['tone-rose', 'tone-lilac', 'tone-peach', 'tone-sage', 'tone-sky', 'tone-sand']

interface Props {
  seatNo: number
  occupant: Occupant | null
  isMe: boolean
  now: number
  offsetMs: number
  compact: boolean
}

export function SeatTile({ seatNo, occupant, isMe, now, offsetMs, compact }: Props) {
  if (!occupant) {
    return (
      <div className="seat seat-empty" aria-label={`${seatNo}번 빈자리`}>
        <span>빈자리</span>
      </div>
    )
  }

  const { state } = occupant
  const elapsed = elapsedSeconds(occupant.since, now, offsetMs)
  const stateText = state === 'IDLE' ? '대기' : `${STATE_LABEL[state]} ${formatShort(elapsed)}`
  const name = isMe ? `나 · ${occupant.nickname}` : occupant.nickname
  const tone = ROOM_TONES[(seatNo * 5) % ROOM_TONES.length]

  return (
    <div
      className={`seat ${tone} seat-${state.toLowerCase()} ${isMe ? 'seat-me' : ''}`}
      aria-label={`${name}, ${stateText}, 완료 ${occupant.completedCount}개, 남은 ${occupant.remainingCount}개`}
    >
      <div className="scene" aria-hidden="true">
        <div className="window">
          <span className="moon" />
          <span className="star s1" />
          <span className="star s2" />
          <span className="star s3" />
        </div>
        {seatNo % 3 === 0 && <div className="shelf" />}
        {seatNo % 3 === 1 && <div className="plant" />}
        <div className="glow" />
        <div className="desk" />
        <Lamp on={state === 'FOCUS'} />
        <div className="character-wrap">
          <Character parts={occupant.character} state={state} />
        </div>
      </div>

      <div className={`state-chip chip-${state.toLowerCase()}`}>
        <span className="dot" aria-hidden="true" />
        {stateText}
      </div>
      {!compact && (
        <div className="counts">
          완료 {occupant.completedCount} · 남은 {occupant.remainingCount}
        </div>
      )}
      {!compact && <div className="name-label">{name}</div>}
      {compact && isMe && <div className="name-label">나</div>}
    </div>
  )
}

function Lamp({ on }: { on: boolean }) {
  return (
    <svg className={`lamp ${on ? 'lamp-on' : ''}`} viewBox="0 0 60 80" aria-hidden="true">
      <ellipse cx="22" cy="76" rx="16" ry="4" fill="#b8925a" />
      <path d="M22 76 L22 46 L40 26" stroke="#c9a46a" strokeWidth="3.5" fill="none" strokeLinecap="round" />
      <path d="M28 18 L52 20 L48 36 Z" fill={on ? '#ffd88a' : '#8b7a63'} />
      {on && <ellipse cx="48" cy="38" rx="6" ry="3" fill="#fff4d0" />}
    </svg>
  )
}
