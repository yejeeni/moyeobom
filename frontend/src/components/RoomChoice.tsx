import { useEffect, useState } from 'react'
import { api } from '../api'
import { isApiError } from '../api/client'
import { CODE_LENGTH, MAX_ROOM_SIZE, normalizeCode, type RoomConfig } from '../lib/roomSize'
import { Icon } from './Icon'

export type RoomChoiceValue = { mode: 'create'; config: RoomConfig } | { mode: 'join'; code: string }

/** 코드 확인 결과. ready면 들어갈 수 있다 */
export type CodeStatus =
  | { kind: 'empty' }
  | { kind: 'checking' }
  | { kind: 'ready'; waiting: number; seatCount: number }
  | { kind: 'full' }
  | { kind: 'notFound' }
  | { kind: 'error'; message: string }

interface Props {
  value: RoomChoiceValue
  onChange: (value: RoomChoiceValue) => void
  /** 코드 확인 결과가 바뀌면 알려 준다(입장 버튼을 켜고 끄는 데 쓴다) */
  onCodeStatus?: (status: CodeStatus) => void
}

/**
 * 새 열람실(가상 메이트 n + 실제 자리 n) 또는 코드로 입장 중 하나를 고른다.
 * 첫 화면 계획 패널과 열람실 설정에서 함께 쓴다.
 */
export function RoomChoice({ value, onChange, onCodeStatus }: Props) {
  const [status, setStatus] = useState<CodeStatus>({ kind: 'empty' })
  const code = value.mode === 'join' ? value.code : ''

  // 코드를 6자리까지 입력하면 방이 있는지, 자리가 남았는지 확인한다
  useEffect(() => {
    if (value.mode !== 'join') return
    if (code.length !== CODE_LENGTH) {
      setStatus({ kind: 'empty' })
      return
    }
    let cancelled = false
    setStatus({ kind: 'checking' })
    api.lookupRoom(code)
      .then((room) => {
        if (cancelled) return
        setStatus(room.waitingSeatCount > 0 ? { kind: 'ready', waiting: room.waitingSeatCount, seatCount: room.seatCount } : { kind: 'full' })
      })
      .catch((e) => {
        if (cancelled) return
        setStatus(isApiError(e, 'ROOM_NOT_FOUND') ? { kind: 'notFound' } : { kind: 'error', message: (e as Error).message })
      })
    return () => {
      cancelled = true
    }
  }, [value.mode, code])

  useEffect(() => onCodeStatus?.(value.mode === 'join' ? status : { kind: 'empty' }), [status, value.mode, onCodeStatus])

  const config = value.mode === 'create' ? value.config : null
  const setConfig = (next: RoomConfig) => onChange({ mode: 'create', config: next })

  return (
    <div className="room-choice">
      <div className="segmented room-mode" role="radiogroup" aria-label="열람실 고르기">
        <label className={value.mode === 'create' ? 'selected' : ''}>
          <input
            type="radio"
            name="room-mode"
            checked={value.mode === 'create'}
            onChange={() => onChange({ mode: 'create', config: config ?? { virtualSeats: 8, realSeats: 1 } })}
          />
          새 열람실
        </label>
        <label className={value.mode === 'join' ? 'selected' : ''}>
          <input type="radio" name="room-mode" checked={value.mode === 'join'} onChange={() => onChange({ mode: 'join', code })} />
          코드로 입장
        </label>
      </div>

      {config && (
        <div className="room-config">
          <Stepper
            label="가상 메이트"
            hint="함께 공부하는 메이트"
            value={config.virtualSeats}
            min={0}
            max={MAX_ROOM_SIZE - config.realSeats}
            onChange={(virtualSeats) => setConfig({ ...config, virtualSeats })}
          />
          <Stepper
            label="실제 자리"
            hint="나를 포함해 코드로 들어올 사람"
            value={config.realSeats}
            min={1}
            max={MAX_ROOM_SIZE - config.virtualSeats}
            onChange={(realSeats) => setConfig({ ...config, realSeats })}
          />
          <p className="muted small room-config-note">
            모두 {config.virtualSeats + config.realSeats}명
            {config.realSeats > 1 ? ' · 들어간 뒤 입장 코드를 나눠 주세요' : ' · 혼자 쓰는 열람실이에요'}
          </p>
        </div>
      )}

      {value.mode === 'join' && (
        <div className="room-join">
          <input
            className="code-input"
            value={code}
            onChange={(e) => onChange({ mode: 'join', code: normalizeCode(e.target.value).slice(0, CODE_LENGTH) })}
            placeholder="입장 코드 6자리"
            aria-label="입장 코드"
            autoComplete="off"
            spellCheck={false}
            inputMode="text"
          />
          <p className={`small code-status code-${status.kind}`} role="status">
            {status.kind === 'empty' && '함께할 사람에게 받은 코드를 입력해 주세요'}
            {status.kind === 'checking' && '코드를 확인하는 중…'}
            {status.kind === 'ready' && `들어갈 수 있어요 · 남은 자리 ${status.waiting}개`}
            {status.kind === 'full' && '자리가 모두 찼어요'}
            {status.kind === 'notFound' && '코드를 찾을 수 없어요. 열람실이 닫혔을 수도 있어요'}
            {status.kind === 'error' && status.message}
          </p>
        </div>
      )}
    </div>
  )
}

function Stepper({
  label,
  hint,
  value,
  min,
  max,
  onChange,
}: {
  label: string
  hint: string
  value: number
  min: number
  max: number
  onChange: (value: number) => void
}) {
  return (
    <div className="room-config-row">
      <span className="room-config-label">
        <strong>{label}</strong>
        <small className="muted">{hint}</small>
      </span>
      <div className="stepper">
        <button type="button" onClick={() => onChange(value - 1)} disabled={value <= min} aria-label={`${label} 한 명 줄이기`}>
          <Icon name="minus" size={16} />
        </button>
        <output className="stepper-value" aria-label={label}>
          {value}
        </output>
        <span className="stepper-unit">명</span>
        <button type="button" onClick={() => onChange(value + 1)} disabled={value >= max} aria-label={`${label} 한 명 늘리기`}>
          <Icon name="plus" size={16} />
        </button>
      </div>
    </div>
  )
}
