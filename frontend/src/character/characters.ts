import type { CharacterParts, SeatState } from '../api/types'

/**
 * 열람실 캐릭터. 캐릭터 하나 = 상태별 그림 여러 장(+ 선택으로 배경 1장).
 *
 * 상태마다 캐릭터 전체(몸, 머리, 손, 책상 위 물건)를 그린 그림을 차례로 넘겨 움직임을 만든다(플립북).
 * - focus-1, focus-2, …  집중. 빠르게 넘기고 사이사이 잠깐 멈춘다.
 * - break-1, break-2, …  휴식(대기 포함). 숨 쉬듯 느리게 넘긴다.
 * - idle-1,  idle-2, …   예전 대기 그림. 휴식 그림이 없을 때 휴식 그림으로 쓴다.
 *   예전 이름 focus-a, focus-b도 1, 2번으로 받는다. 상태마다 MAX_FRAMES장까지.
 * - focus, break, idle  (여러 장 대신) 움짤 한 장. GIF, 움직이는 WebP, APNG를 그대로 재생한다.
 *   GIF나 JPG가 하나라도 있는 캐릭터는 칸 전체를 꽉 채워 그린다(fillsSeat).
 * - 폴더에 그림이 한 장뿐이면 이름과 상관없이 집중·휴식 모두 그 그림을 쓴다.
 * - background        (선택) 책상까지 포함한 칸 전체 배경. 캐릭터와 같은 800x600 캔버스에 맞춰 그린다. 움짤도 된다.
 *
 * 그림은 800x600 투명 PNG이고 같은 위치 기준으로 그린다.
 * 기본 캐릭터는 public/characters/<id>/ 에 focus.gif, break.gif를 두고 BUILTIN_CHARACTERS에 한 줄 추가한다.
 * 사용자가 등록한 캐릭터는 브라우저(IndexedDB)에만 저장된다(library.ts).
 */

export type FrameState = 'focus' | 'break' | 'idle'
export const FRAME_STATES: FrameState[] = ['focus', 'break', 'idle']
/**
 * 상태마다 쓸 수 있는 그림 수. 그림은 화면에 그릴 때 압축이 풀린 채로 메모리에 올라가므로(800x600 한 장에 약 1.9MB)
 * 공부하는 동안 띄워 두어도 무겁지 않도록 제한한다. 더 긴 움직임은 움짤로 넣는다.
 */
export const MAX_FRAMES = 12

/** 한 장당 시간(집중 기준, ms). 휴식은 STATE_SLOWNESS배 느리게 넘긴다 */
export const FRAME_MS_MIN = 80
export const FRAME_MS_MAX = 600
export const DEFAULT_FRAME_MS = 200
export const STATE_SLOWNESS: Record<FrameState, number> = { focus: 1, idle: 8, break: 8 }

/** 대기와 휴식은 같은 그림·같은 빠르기로 보여 준다 */
export const STATE_FILE: Record<SeatState, FrameState> = { FOCUS: 'focus', BREAK: 'break', IDLE: 'break' }
const STATE_LABEL_KO = { focus: '집중', break: '휴식', idle: '휴식' } as const

export interface CharacterAsset {
  id: string
  name: string
  source: 'builtin' | 'mine'
  /** 한 장당 시간(집중 기준, ms) */
  frameMs: number
  /** 상태별 그림 주소(넘기는 순서대로). 빠진 상태는 대체 그림으로 채워져 있다. 한 장이면 넘기지 않는다 */
  frames: Record<SeatState, string[]>
  /** 칸 전체 배경. 없으면 기본 배경을 쓴다. */
  background: string | null
  /** 움짤 주소 → 첫 장면 주소. '움직임 줄이기'를 켠 사람에게 대신 보여 준다 */
  stills: Record<string, string>
  /** GIF가 들어 있는 캐릭터. 투명하든 아니든 칸 전체를 꽉 채워 그린다(장면째 그린 움짤이 많아서) */
  fillsSeat: boolean
}

/** 칸을 꽉 채워 그릴 그림 형식. GIF는 장면째 그린 움짤이 많고, JPG는 투명한 곳이 없어서 */
export function fillsSeatType(type: string): boolean {
  return type === 'image/gif' || type === 'image/jpeg'
}

/** 파일 이름(확장자 뺀)이 어느 상태의 몇 번째 그림인지. 움짤 한 장(focus 등)은 order 0 */
export interface FrameName {
  state: FrameState
  order: number
  animated: boolean
}

export function parseFrameName(name: string): FrameName | null {
  const match = /^(focus|break|idle)(?:-(a|b|\d{1,2}))?$/.exec(name.toLowerCase())
  if (!match) return null
  const state = match[1] as FrameState
  const suffix = match[2]
  if (!suffix) return { state, order: 0, animated: true }
  const order = suffix === 'a' ? 1 : suffix === 'b' ? 2 : Number(suffix)
  return order >= 1 ? { state, order, animated: false } : null
}

/**
 * 상태별 그림 목록을 채운다. 대기(IDLE)는 휴식과 같은 그림을 쓴다.
 * - 움짤 한 장(focus 등)이 있으면 그 상태는 그것만 보여 준다(움짤이 스스로 움직인다).
 * - 휴식 그림이 없으면 예전 대기(idle) 그림을, 그것도 없으면 집중 그림을 쓴다. 집중 그림이 없으면 휴식 그림을 쓴다.
 * 그림이 한 장도 없으면 null.
 */
export function resolveFrames(available: Partial<Record<FrameState, string[]>>): Record<SeatState, string[]> | null {
  const listOf = (state: FrameState) => {
    const list = available[state]
    return list && list.length > 0 ? list.slice(0, MAX_FRAMES) : null
  }
  const focus = listOf('focus')
  const rest = listOf('break') ?? listOf('idle')
  const any = focus ?? rest
  if (!any) return null
  return {
    FOCUS: focus ?? any,
    BREAK: rest ?? any,
    IDLE: rest ?? any,
  }
}

/** 빠진 그림이 있으면 무엇으로 대신하는지 사람이 읽을 문장으로 */
export function describeMissing(names: FrameName[]): string[] {
  const notes: string[] = []
  const hasBreak = names.some((n) => n.state === 'break')
  for (const state of FRAME_STATES) {
    // 예전 대기 그림은 휴식 그림이 없을 때만 쓴다
    if (state === 'idle' && (hasBreak || !names.some((n) => n.state === 'idle'))) continue
    const mine = names.filter((n) => n.state === state)
    if (state === 'break' && mine.length === 0 && names.some((n) => n.state === 'idle')) continue
    if (mine.length === 0) notes.push(`${STATE_LABEL_KO[state]} 그림이 없어 다른 상태 그림으로 대신해요`)
    else if (mine.some((n) => n.animated)) continue
    else if (mine.length === 1) notes.push(`${STATE_LABEL_KO[state]} 그림이 한 장뿐이라 움직이지 않아요`)
    else if (mine.length > MAX_FRAMES) {
      notes.push(`${STATE_LABEL_KO[state]} 그림이 ${mine.length}장이라 앞 ${MAX_FRAMES}장만 썼어요. 더 긴 움직임은 움짤로 넣어 주세요`)
    }
  }
  return notes
}

/** 집중·휴식마다 GIF 한 장인 기본 캐릭터. GIF라서 칸을 꽉 채운다 */
function builtinGif(id: string, name: string): CharacterAsset {
  const frames = resolveFrames({ focus: [`/characters/${id}/focus.gif`], break: [`/characters/${id}/break.gif`] })!
  return { id: `builtin:${id}`, name, source: 'builtin', frameMs: DEFAULT_FRAME_MS, frames, background: null, stills: {}, fillsSeat: true }
}

// TODO 공개 배포 전에 바꾼다. 저작권이 있는 그림(치이카와)이라 비공개 테스트에서 자리를 채우는 용도로만 쓴다
export const BUILTIN_CHARACTERS: CharacterAsset[] = [
  builtinGif('chiikawa', '치이카와'),
  builtinGif('hachiware', '하치와레'),
  builtinGif('usagi', '우사기'),
  builtinGif('chiikawa2', '치이카와 2'),
]

/** 서버가 준 파츠 번호로 기본 캐릭터를 고른다(소개 화면 미리보기용). */
export function pickBuiltin(parts: CharacterParts): CharacterAsset {
  return BUILTIN_CHARACTERS[(parts.hair + parts.shirt) % BUILTIN_CHARACTERS.length]
}

/** 자리마다 움직임 박자가 겹치지 않도록 애니메이션 시작을 어긋나게 한다 (초) */
export function phaseOf(parts: CharacterParts): number {
  return ((parts.skin * 7 + parts.hairColor * 5 + parts.hair * 3) % 10) * 0.37
}
