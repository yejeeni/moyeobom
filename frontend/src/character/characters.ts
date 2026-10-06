import type { CharacterParts, SeatState } from '../api/types'

/**
 * 열람실 캐릭터. 캐릭터 하나 = 상태별 그림 2장씩 6장(+ 선택으로 배경 1장).
 *
 * 상태마다 캐릭터 전체(몸, 머리, 손, 책상 위 물건)를 그린 그림 2장을 번갈아 보여 준다.
 * - focus-a, focus-b  집중. 빠르게 바뀌고 사이사이 잠깐 멈춘다.
 * - break-a, break-b  휴식. 숨 쉬듯 느리게 바뀐다.
 * - idle-a,  idle-b   대기. 그 사이 빠르기로 바뀐다.
 * - background        (선택) 책상까지 포함한 칸 전체 배경. 캐릭터와 같은 800x600 캔버스에 맞춰 그린다.
 *
 * 그림은 800x600 투명 PNG이고 같은 위치 기준으로 그린다. 가이드는 public/characters/template.png.
 * 기본 캐릭터는 public/characters/<id>/ 에 두고 BUILTIN_CHARACTERS에 한 줄 추가한다.
 * 사용자가 등록한 캐릭터는 브라우저(IndexedDB)에만 저장된다(library.ts).
 */

export type Tempo = 'typing' | 'writing'
export type FrameKey = 'focus-a' | 'focus-b' | 'break-a' | 'break-b' | 'idle-a' | 'idle-b'
export const FRAME_KEYS: FrameKey[] = ['focus-a', 'focus-b', 'break-a', 'break-b', 'idle-a', 'idle-b']

export const STATE_FILE: Record<SeatState, 'focus' | 'break' | 'idle'> = { FOCUS: 'focus', BREAK: 'break', IDLE: 'idle' }
const STATE_LABEL_KO = { focus: '집중', break: '휴식', idle: '대기' } as const

export interface CharacterAsset {
  id: string
  name: string
  source: 'builtin' | 'mine'
  /** 집중할 때 그림을 바꾸는 빠르기. 타자는 빠르게, 필기는 조금 느리게 */
  tempo: Tempo
  /** 상태별 [A, B] 그림 주소. 빠진 그림은 대체 그림으로 채워져 있다. */
  frames: Record<SeatState, [string, string]>
  /** 칸 전체 배경. 없으면 기본 배경을 쓴다. */
  background: string | null
}

/**
 * 있는 그림만으로 상태별 [A, B]를 채운다.
 * - B가 없으면 A만 보여 준다(움직이지 않음).
 * - 그 상태 그림이 아예 없으면 집중 → 대기 → 휴식 순서로 있는 그림을 쓴다.
 * 그림이 한 장도 없으면 null.
 */
export function resolveFrames(available: Partial<Record<FrameKey, string>>): Record<SeatState, [string, string]> | null {
  const pairOf = (state: 'focus' | 'break' | 'idle'): [string, string] | null => {
    const a = available[`${state}-a`] ?? available[`${state}-b`]
    if (!a) return null
    return [a, available[`${state}-b`] ?? a]
  }
  const focus = pairOf('focus')
  const idle = pairOf('idle')
  const rest = pairOf('break')
  const any = focus ?? idle ?? rest
  if (!any) return null
  return {
    FOCUS: focus ?? any,
    IDLE: idle ?? focus ?? any,
    BREAK: rest ?? idle ?? focus ?? any,
  }
}

/** 빠진 그림이 있으면 무엇으로 대신하는지 사람이 읽을 문장으로 */
export function describeMissing(keys: FrameKey[]): string[] {
  const has = new Set(keys)
  const notes: string[] = []
  for (const state of ['focus', 'break', 'idle'] as const) {
    const a = has.has(`${state}-a`)
    const b = has.has(`${state}-b`)
    if (!a && !b) notes.push(`${STATE_LABEL_KO[state]} 그림이 없어 다른 상태 그림으로 대신해요`)
    else if (!a || !b) notes.push(`${STATE_LABEL_KO[state]} 그림이 한 장뿐이라 움직이지 않아요`)
  }
  return notes
}

function builtin(id: string, name: string, tempo: Tempo): CharacterAsset {
  const available = Object.fromEntries(FRAME_KEYS.map((key) => [key, `/characters/${id}/${key}.webp`])) as Record<FrameKey, string>
  return { id: `builtin:${id}`, name, source: 'builtin', tempo, frames: resolveFrames(available)!, background: null }
}

export const BUILTIN_CHARACTERS: CharacterAsset[] = [
  builtin('laptop', '동글이', 'typing'),
  builtin('rabbit', '토끼', 'typing'),
  builtin('penguin-blue', '파랑 펭귄', 'typing'),
  builtin('tabby-cat', '고등어 고양이', 'typing'),
  builtin('cream-cat', '크림 고양이', 'typing'),
  builtin('penguin', '펭귄', 'typing'),
]

/** 서버가 준 파츠 번호로 기본 캐릭터를 고른다(소개 화면 미리보기용). */
export function pickBuiltin(parts: CharacterParts): CharacterAsset {
  return BUILTIN_CHARACTERS[(parts.hair + parts.shirt) % BUILTIN_CHARACTERS.length]
}

/** 자리마다 움직임 박자가 겹치지 않도록 애니메이션 시작을 어긋나게 한다 (초) */
export function phaseOf(parts: CharacterParts): number {
  return ((parts.skin * 7 + parts.hairColor * 5 + parts.hair * 3) % 10) * 0.37
}
