import type { CharacterParts } from '../api/types'

/**
 * 열람실 캐릭터 목록. 캐릭터 하나 = public/characters/<id>/ 아래 PNG 4장.
 *
 * - body.png              몸통. 움직이지 않는다. 책·노트북 같은 구조물도 여기에 그린다.
 * - head.png              머리. pivot을 축으로 살짝 기울어진다.
 * - hands-a.png, hands-b.png  팔·손. 집중하는 동안 번갈아 보인다.
 *
 * 네 장 모두 800x600 투명 PNG이고 같은 위치 기준으로 그린다. 캔버스 아래 끝이 책상 윗면이다.
 * 가이드는 public/characters/template.png. 새 캐릭터는 폴더를 만들고 여기에 한 줄 추가하면 된다.
 */
export interface CharacterSet {
  id: string
  name: string
  /** 머리 회전축(목) 위치. 캔버스 기준 % */
  pivot: { x: number; y: number }
  /** 손을 바꾸는 빠르기 */
  tempo: 'typing' | 'writing'
}

export const CHARACTERS: CharacterSet[] = [
  { id: 'laptop', name: '노트북', pivot: { x: 61, y: 79 }, tempo: 'typing' },
  { id: 'notebook', name: '노트 필기', pivot: { x: 61, y: 79 }, tempo: 'writing' },
]

export type CharacterLayer = 'body' | 'head' | 'hands-a' | 'hands-b'

export const layerUrl = (set: CharacterSet, layer: CharacterLayer) => `/characters/${set.id}/${layer}.png`

/** 서버가 준 파츠 번호로 캐릭터를 고른다. 같은 사람은 늘 같은 캐릭터가 된다. */
export function pickCharacter(parts: CharacterParts): CharacterSet {
  return CHARACTERS[(parts.hair + parts.shirt) % CHARACTERS.length]
}

/** 자리마다 움직임 박자가 겹치지 않도록 애니메이션 시작을 어긋나게 한다 (초) */
export function phaseOf(parts: CharacterParts): number {
  return ((parts.skin * 7 + parts.hairColor * 5 + parts.hair * 3) % 10) * 0.37
}
