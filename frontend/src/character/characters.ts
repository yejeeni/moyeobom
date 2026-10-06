import type { CharacterParts, SeatState } from '../api/types'

/**
 * 열람실 캐릭터 목록. 캐릭터 하나 = public/characters/<id>/ 아래 PNG 6장.
 *
 * 상태마다 캐릭터 전체(몸, 머리, 손, 책상 위 물건)를 그린 그림 2장을 번갈아 보여 준다.
 * - focus-a.png, focus-b.png  집중. 빠르게 바뀌고 사이사이 잠깐 멈춘다.
 * - break-a.png, break-b.png  휴식. 숨 쉬듯 느리게 바뀐다.
 * - idle-a.png,  idle-b.png   대기. 그 사이 빠르기로 바뀐다.
 *
 * 여섯 장 모두 800x600 투명 PNG이고 같은 위치 기준으로 그린다.
 * 캔버스 전체가 칸 안에 들어가도록 줄여서 칸 아래 가운데에 놓인다. 칸 배경은 backgrounds.ts.
 * 가이드는 public/characters/template.png. 새 캐릭터는 폴더를 만들고 여기에 한 줄 추가하면 된다.
 */
export interface CharacterSet {
  id: string
  name: string
  /** 집중할 때 그림을 바꾸는 빠르기. 타자는 빠르게, 필기는 조금 느리게 */
  tempo: 'typing' | 'writing'
}

export const CHARACTERS: CharacterSet[] = [
  { id: 'laptop', name: '노트북', tempo: 'typing' },
]

export type Frame = 'a' | 'b'

const STATE_FILE: Record<SeatState, string> = { FOCUS: 'focus', BREAK: 'break', IDLE: 'idle' }

export const frameUrl = (set: CharacterSet, state: SeatState, frame: Frame) =>
  `/characters/${set.id}/${STATE_FILE[state]}-${frame}.png`

/** 서버가 준 파츠 번호로 캐릭터를 고른다. 같은 사람은 늘 같은 캐릭터가 된다. */
export function pickCharacter(parts: CharacterParts): CharacterSet {
  return CHARACTERS[(parts.hair + parts.shirt) % CHARACTERS.length]
}

/** 자리마다 움직임 박자가 겹치지 않도록 애니메이션 시작을 어긋나게 한다 (초) */
export function phaseOf(parts: CharacterParts): number {
  return ((parts.skin * 7 + parts.hairColor * 5 + parts.hair * 3) % 10) * 0.37
}
