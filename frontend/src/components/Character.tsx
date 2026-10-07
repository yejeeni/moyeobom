import type { CSSProperties } from 'react'
import type { SeatState } from '../api/types'
import type { CharacterAsset } from '../character/characters'

interface Props {
  asset: CharacterAsset
  state: SeatState
  /** 애니메이션 시작을 어긋나게 할 시간(초) */
  phase: number
  /** 좌우 반전 */
  flipped?: boolean
  /** 배경과 짝인 캐릭터: 칸을 배경과 같은 방식으로 꽉 채워 위치를 맞춘다 */
  paired?: boolean
}

const STATES: SeatState[] = ['FOCUS', 'BREAK', 'IDLE']

/**
 * 상태별 그림 2장(A·B)을 번갈아 보여 준다. 실제 유저와 가상 메이트가 같은 규칙으로 그려진다.
 * 상태가 바뀔 때 깜빡이지 않도록 여섯 장을 모두 그려 두고 지금 상태의 두 장만 보이게 한다.
 */
export function Character({ asset, state, phase, flipped = false, paired = false }: Props) {
  const style = { '--phase': `-${phase.toFixed(2)}s` } as CSSProperties

  return (
    <div
      className={`character character-${state.toLowerCase()} tempo-${asset.tempo} ${flipped ? 'character-flipped' : ''} ${paired ? 'is-paired' : ''}`}
      style={style}
      aria-hidden="true"
    >
      {STATES.flatMap((s) =>
        asset.frames[s].map((src, i) => (
          <img
            key={`${s}-${i}`}
            className={`frame frame-${s.toLowerCase()} frame-${i === 0 ? 'a' : 'b'}`}
            src={src}
            alt=""
            draggable={false}
          />
        )),
      )}
    </div>
  )
}
