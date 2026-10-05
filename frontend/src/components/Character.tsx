import type { CSSProperties } from 'react'
import type { CharacterParts, SeatState } from '../api/types'
import { layerUrl, phaseOf, pickCharacter } from '../character/characters'

interface Props {
  parts: CharacterParts
  state: SeatState
  /** 좌우 반전 */
  flipped?: boolean
}

/**
 * 몸통·머리·팔손 PNG를 겹쳐 그린다. 실제 유저와 가상 메이트가 같은 규칙으로 그려진다.
 * 집중: 머리가 살짝 흔들리고 손 A·B가 번갈아 보인다. 휴식: 머리를 떨군다. 대기: 멈춰 있다.
 */
export function Character({ parts, state, flipped = false }: Props) {
  const set = pickCharacter(parts)
  const style = {
    '--pivot': `${set.pivot.x}% ${set.pivot.y}%`,
    '--phase': `-${phaseOf(parts).toFixed(2)}s`,
  } as CSSProperties

  return (
    <div
      className={`character character-${state.toLowerCase()} tempo-${set.tempo} ${flipped ? 'character-flipped' : ''}`}
      style={style}
      aria-hidden="true"
    >
      <div className="character-layers">
        <img className="layer" src={layerUrl(set, 'body')} alt="" draggable={false} />
        <img className="layer layer-head" src={layerUrl(set, 'head')} alt="" draggable={false} />
        <img className="layer layer-hands-a" src={layerUrl(set, 'hands-a')} alt="" draggable={false} />
        <img className="layer layer-hands-b" src={layerUrl(set, 'hands-b')} alt="" draggable={false} />
      </div>
      {state === 'BREAK' && <span className="zzz">z</span>}
    </div>
  )
}
