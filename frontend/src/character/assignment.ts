import { useEffect, useMemo, useRef } from 'react'
import type { Occupant } from '../api/types'
import { BUILTIN_CHARACTERS, type CharacterAsset } from './characters'
import { useCharacterLibrary, type CharacterPrefs } from './CharacterLibrary'

/**
 * 열람실 자리마다 캐릭터를 무작위로 배정하고, 방 ID별로 기억해 새로고침이나 재연결 뒤에도 같은 모습을 유지한다.
 * 자리 주인이 바뀌면(퇴장 후 새 메이트) 그 자리만 새로 뽑는다. 방이 바뀌면 모두 새로 뽑는다.
 */

const STORAGE_KEY = 'moyeobom.characterAssignment'

interface StoredAssignment {
  roomId: string
  /** "자리번호:닉네임" → 캐릭터 id */
  seats: Record<string, string>
}

function read(): StoredAssignment | null {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null')
  } catch {
    return null
  }
}

function write(value: StoredAssignment) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(value))
  } catch {
    // 저장할 수 없으면 이번 화면에서만 유지된다
  }
}

const seatKey = (seatNo: number, occupant: Occupant) => `${seatNo}:${occupant.nickname}`
const pickRandom = <T,>(list: T[]): T => list[Math.floor(Math.random() * list.length)]

export function assignCharacters(
  previous: Record<string, string>,
  seats: Record<number, Occupant | null>,
  mySeatNo: number | null,
  pool: CharacterAsset[],
  available: CharacterAsset[],
  prefs: CharacterPrefs,
): { bySeat: Record<number, CharacterAsset>; stored: Record<string, string> } {
  const byId = new Map(available.map((c) => [c.id, c]))
  const poolIds = new Set(pool.map((c) => c.id))
  const occupied = Object.entries(seats)
    .filter((entry): entry is [string, Occupant] => entry[1] !== null)
    .map(([no, occupant]) => ({ seatNo: Number(no), occupant }))
    // 내 자리를 먼저 정해야 고정 캐릭터가 다른 자리에 먼저 뽑히지 않는다
    .sort((a, b) => Number(b.seatNo === mySeatNo) - Number(a.seatNo === mySeatNo) || a.seatNo - b.seatNo)

  const bySeat: Record<number, CharacterAsset> = {}
  const stored: Record<string, string> = {}
  const used = new Set<string>()
  const myFixed = prefs.myCharacterId ? byId.get(prefs.myCharacterId) ?? null : null

  // 이미 배정된 자리는 그대로 둔다(후보에서 빠진 캐릭터는 새로 뽑는다)
  for (const { seatNo, occupant } of occupied) {
    const key = seatKey(seatNo, occupant)
    const kept = byId.get(previous[key])
    const isMe = seatNo === mySeatNo
    const valid = kept && (isMe && myFixed ? kept.id === myFixed.id : poolIds.has(kept.id))
    if (valid) {
      bySeat[seatNo] = kept
      stored[key] = kept.id
      used.add(kept.id)
    }
  }

  for (const { seatNo, occupant } of occupied) {
    if (bySeat[seatNo]) continue
    let chosen: CharacterAsset
    if (seatNo === mySeatNo && myFixed) {
      chosen = myFixed
    } else if (prefs.allowDuplicates) {
      chosen = pickRandom(pool)
    } else {
      // 중복 없이: 안 쓴 후보 → 모자라면 안 쓴 기본 캐릭터 → 그래도 없으면 겹쳐서라도 채운다
      const unused = pool.filter((c) => !used.has(c.id))
      const unusedBuiltins = BUILTIN_CHARACTERS.filter((c) => !used.has(c.id))
      chosen = pickRandom(unused.length > 0 ? unused : unusedBuiltins.length > 0 ? unusedBuiltins : pool)
    }
    bySeat[seatNo] = chosen
    stored[seatKey(seatNo, occupant)] = chosen.id
    used.add(chosen.id)
  }
  return { bySeat, stored }
}

export function useSeatCharacters(
  roomId: string | null,
  seats: Record<number, Occupant | null>,
  mySeatNo: number | null,
): Record<number, CharacterAsset> {
  const library = useCharacterLibrary()
  const lastShuffle = useRef(library.shuffleVersion)

  // 상태나 숫자만 바뀐 이벤트에는 다시 계산하지 않도록 자리 주인만으로 키를 만든다
  const occupantsKey = Object.entries(seats)
    .map(([no, o]) => (o ? `${no}:${o.nickname}` : `${no}:-`))
    .join('|')

  const { bySeat, stored } = useMemo(() => {
    const saved = read()
    const reshuffled = lastShuffle.current !== library.shuffleVersion
    const previous = !reshuffled && saved && saved.roomId === roomId ? saved.seats : {}
    return assignCharacters(previous, seats, mySeatNo, library.pool, [...library.mine, ...library.builtins], library.prefs)
    // seats는 occupantsKey로 대신한다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId, occupantsKey, mySeatNo, library.pool, library.mine, library.builtins, library.prefs, library.shuffleVersion])

  useEffect(() => {
    // 입장 직후 스냅샷이 오기 전(자리 정보가 비어 있을 때)에 저장하면 기존 배정이 지워진다
    if (!roomId || !library.ready || Object.keys(stored).length === 0) return
    lastShuffle.current = library.shuffleVersion
    write({ roomId, seats: stored })
  }, [roomId, stored, library.ready, library.shuffleVersion])

  return bySeat
}
