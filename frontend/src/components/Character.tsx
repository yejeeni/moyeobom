import type { CSSProperties } from 'react'
import type { CharacterParts, SeatState } from '../api/types'
import { frameUrl, phaseOf, pickCharacter } from '../character/characters'

interface Props {
  parts: CharacterParts
  state: SeatState
  /** 좌우 반전 */
  flipped?: boolean
}

const STATES: SeatState[] = ['FOCUS', 'BREAK', 'IDLE']

/**
 * 상태별 그림 2장(A·B)을 번갈아 보여 준다. 실제 유저와 가상 메이트가 같은 규칙으로 그려진다.
 * 상태가 바뀔 때 깜빡이지 않도록 여섯 장을 모두 그려 두고 지금 상태의 두 장만 보이게 한다.
 */
export function Character({ parts, state, flipped = false }: Props) {
  const set = pickCharacter(parts)
  const style = { '--phase': `-${phaseOf(parts).toFixed(2)}s` } as CSSProperties

  return (
    <div
      className={`character character-${state.toLowerCase()} tempo-${set.tempo} ${flipped ? 'character-flipped' : ''}`}
      style={style}
      aria-hidden="true"
    >
      {STATES.flatMap((s) =>
        (['a', 'b'] as const).map((frame) => (
          <img
            key={`${s}-${frame}`}
            className={`frame frame-${s.toLowerCase()} frame-${frame}`}
            src={frameUrl(set, s, frame)}
            alt=""
            draggable={false}
          />
        )),
      )}
    </div>
  )
}
