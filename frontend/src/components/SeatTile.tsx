import { useEffect, useState } from 'react'
import type { Occupant } from '../api/types'
import { backgroundFor } from '../character/backgrounds'
import { phaseOf, pickBuiltin, type CharacterAsset } from '../character/characters'
import { STATE_LABEL, elapsedSeconds, formatShort } from '../lib/time'
import { Character } from './Character'

// 줌 화면의 각 칸처럼 자리마다 방 분위기를 조금씩 다르게 한다
const ROOM_TONES = ['tone-rose', 'tone-lilac', 'tone-peach', 'tone-sage', 'tone-sky', 'tone-sand']
// 캐릭터가 모두 같은 쪽을 보지 않도록 몇 자리는 좌우를 뒤집는다
const FLIPPED_SEATS = new Set([2, 3, 6, 7, 9])

interface Props {
  seatNo: number
  occupant: Occupant | null
  isMe: boolean
  now: number
  offsetMs: number
  compact: boolean
  /** 이 자리에 배정된 캐릭터. 없으면 기본 캐릭터 */
  character?: CharacterAsset
  /** 비어 있는 실제 사람 자리(코드로 누군가 들어오기를 기다린다) */
  waiting?: boolean
  /** 초대 대기 칸에 보여 줄 입장 코드 */
  code?: string | null
  /** 초대 대기 칸을 누르면 초대 링크를 복사한다 */
  onInvite?: () => void
}

interface SeatEvent {
  id: string
  text: string
  kind: 'done' | 'lamp'
}

/**
 * 같은 사람의 완료 개수가 늘면 '할 일 완료', 내가 집중을 시작하면 '스탠드가 켜졌어요'를 잠깐 띄운다.
 * 사람이 바뀐 경우(다른 사람이 앉음)는 이벤트로 보지 않는다.
 */
function useSeatEvent(occupant: Occupant | null, isMe: boolean): SeatEvent | null {
  const key = occupant ? `${occupant.nickname}|${occupant.completedCount}|${occupant.state}` : ''
  const [prevKey, setPrevKey] = useState(key)
  const [event, setEvent] = useState<SeatEvent | null>(null)

  if (key !== prevKey) {
    setPrevKey(key)
    const [prevName, prevCount, prevState] = prevKey.split('|')
    if (occupant && prevName === occupant.nickname) {
      if (occupant.completedCount > Number(prevCount)) {
        setEvent({ id: key, text: '할 일 하나 끝!', kind: 'done' })
      } else if (isMe && occupant.state === 'FOCUS' && prevState !== 'FOCUS') {
        setEvent({ id: key, text: '스탠드가 켜졌어요', kind: 'lamp' })
      }
    }
  }

  useEffect(() => {
    if (!event) return
    const id = window.setTimeout(() => setEvent(null), 3200)
    return () => window.clearTimeout(id)
  }, [event])

  return event
}

export function SeatTile({ seatNo, occupant, isMe, now, offsetMs, compact, character, waiting = false, code, onInvite }: Props) {
  const event = useSeatEvent(occupant, isMe)

  if (!occupant) {
    return waiting ? (
      <button
        type="button"
        className="seat seat-empty seat-waiting"
        onClick={onInvite}
        aria-label={`${seatNo}번 자리, 초대 대기${code ? `, 코드 ${code}. 누르면 초대 링크를 복사해요` : ''}`}
      >
        <span className="waiting-mark" aria-hidden="true" />
        <span>초대 대기</span>
        {code && <span className="waiting-code">코드 {code}</span>}
      </button>
    ) : (
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
  const flipped = FLIPPED_SEATS.has(seatNo)
  const asset = character ?? pickBuiltin(occupant.character)
  const phase = phaseOf(occupant.character)
  // 캐릭터에 짝 배경이 있으면 배경과 캐릭터를 같은 칸 크기로 겹쳐 위치를 맞추고, 뒤집을 때도 함께 뒤집는다
  const paired = asset.background !== null
  const background = asset.background ?? backgroundFor(seatNo)

  return (
    <div
      className={`seat ${tone} seat-${state.toLowerCase()} ${isMe ? 'seat-me' : ''}`}
      aria-label={`${name}, ${stateText}, 완료 ${occupant.completedCount}개, 남은 ${occupant.remainingCount}개`}
    >
      <div className="scene" aria-hidden="true">
        {paired ? (
          <div className={`paired-stack ${flipped ? 'is-flipped' : ''}`}>
            <img className="scene-image" src={background!} alt="" draggable={false} />
            <Character asset={asset} state={state} phase={phase} paired />
          </div>
        ) : (
          <>
            {background ? (
              <img className="scene-image" src={background} alt="" draggable={false} />
            ) : (
              <>
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
              </>
            )}
            <div className="character-wrap">
              <Character asset={asset} state={state} phase={phase} flipped={flipped} />
            </div>
          </>
        )}
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
      {event && (
        <div key={event.id} className={`seat-event seat-event-${event.kind}`} aria-hidden="true">
          {event.kind === 'done' ? '✓' : <span className="event-lamp" />}
          {event.text}
        </div>
      )}
      {(!compact || isMe) && (
        <div className="name-label">
          {isMe && <span className="me-badge">나</span>}
          {!compact && occupant.nickname}
        </div>
      )}
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
