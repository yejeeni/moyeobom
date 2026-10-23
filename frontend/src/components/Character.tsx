import { useEffect, useState } from 'react'
import type { SeatState } from '../api/types'
import { STATE_FILE, STATE_SLOWNESS, type CharacterAsset } from '../character/characters'
import { useMediaQuery } from '../lib/useMediaQuery'
import { MotionImage } from './MotionImage'

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

// 대기는 휴식과 같은 그림이라 두 상태 그림만 깔아 둔다
const STATES: SeatState[] = ['FOCUS', 'BREAK']
/** 집중 중에는 이만큼 넘긴 뒤 잠깐 멈춰, 쉬지 않고 움직이는 기계처럼 보이지 않게 한다 */
const FOCUS_FLIPS_BEFORE_PAUSE = 6
const FOCUS_PAUSE_FRAMES = 3

/**
 * 상태별 그림을 차례로 넘겨 보여 준다(플립북). 실제 유저와 가상 메이트가 같은 규칙으로 그려진다.
 * 상태가 바뀔 때 기다리지 않도록 모든 상태의 그림을 미리 받아 두고 지금 그림만 보이게 한다.
 * 다른 상태의 그림은 그리지 않아(display: none) 움짤이 보이지 않는 곳에서 계속 재생되지 않게 한다.
 * 움짤 한 장이거나 '움직임 줄이기'를 켰으면 넘기지 않는다.
 */
export function Character({ asset, state: seatState, phase, flipped = false, paired = false }: Props) {
  const state: SeatState = seatState === 'FOCUS' ? 'FOCUS' : 'BREAK'
  const frames = asset.frames[state]
  const reducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)')
  const frameIndex = useFlipbook(frames.length, asset.frameMs * STATE_SLOWNESS[STATE_FILE[state]], state === 'FOCUS', phase, reducedMotion)

  return (
    <div
      className={`character character-${state.toLowerCase()} ${flipped ? 'character-flipped' : ''} ${paired ? 'is-paired' : ''}`}
      aria-hidden="true"
    >
      {STATES.flatMap((s) =>
        asset.frames[s].map((src, i) => (
          <MotionImage
            key={`${s}-${i}`}
            className={`frame ${s !== state ? 'is-standby' : i === frameIndex ? 'is-current' : ''}`}
            src={src}
            still={asset.stills[src]}
          />
        )),
      )}
    </div>
  )
}

/** 지금 보여 줄 그림 번호. 자리마다 phase만큼 어긋난 곳에서 시작한다 */
function useFlipbook(count: number, frameMs: number, pauses: boolean, phase: number, reducedMotion: boolean): number {
  const start = count > 1 ? Math.floor((phase * 1000) / frameMs) % count : 0
  const [tick, setTick] = useState({ key: '', index: start })
  const key = `${count}:${frameMs}:${pauses}:${reducedMotion}`
  // 상태나 빠르기가 바뀌면 시작 위치부터 다시 넘긴다
  const index = tick.key === key ? tick.index : start

  useEffect(() => {
    if (count <= 1 || reducedMotion) return
    let current = start
    let flips = 0
    let timer = 0
    const next = () => {
      current = (current + 1) % count
      flips += 1
      setTick({ key, index: current })
      const pause = pauses && flips % FOCUS_FLIPS_BEFORE_PAUSE === 0 ? frameMs * FOCUS_PAUSE_FRAMES : 0
      timer = window.setTimeout(next, frameMs + pause)
    }
    timer = window.setTimeout(next, frameMs)
    return () => window.clearTimeout(timer)
  }, [count, frameMs, pauses, reducedMotion, start, key])

  return reducedMotion ? 0 : index
}
