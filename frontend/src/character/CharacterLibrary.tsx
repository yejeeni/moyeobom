import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { BUILTIN_CHARACTERS, resolveFrames, type CharacterAsset, type FrameKey, type Tempo } from './characters'
import { deleteCharacter, importFiles, listCharacters, saveCharacter, type ImportResult, type StoredCharacter } from './library'

export interface CharacterPrefs {
  /** 내 캐릭터가 있어도 기본 캐릭터를 함께 쓴다 */
  useBuiltIn: boolean
  /** 같은 캐릭터가 여러 자리에 나와도 된다 */
  allowDuplicates: boolean
  /** 내 자리에 늘 쓸 캐릭터. null이면 무작위 */
  myCharacterId: string | null
}

const PREFS_KEY = 'moyeobom.characterPrefs'
const DEFAULT_PREFS: CharacterPrefs = { useBuiltIn: true, allowDuplicates: false, myCharacterId: null }

function readPrefs(): CharacterPrefs {
  try {
    return { ...DEFAULT_PREFS, ...JSON.parse(localStorage.getItem(PREFS_KEY) ?? '{}') }
  } catch {
    return DEFAULT_PREFS
  }
}

interface LibraryApi {
  ready: boolean
  mine: CharacterAsset[]
  builtins: CharacterAsset[]
  /** 자리에 배정할 후보. 내 캐릭터가 없으면 기본 캐릭터 */
  pool: CharacterAsset[]
  prefs: CharacterPrefs
  /** 지금 열람실 배정을 새로 뽑을 때 늘어난다 */
  shuffleVersion: number
  importFolder: (files: File[]) => Promise<ImportResult>
  remove: (id: string) => Promise<void>
  setTempo: (id: string, tempo: Tempo) => Promise<void>
  setPrefs: (change: Partial<CharacterPrefs>) => void
  reshuffle: () => void
}

const LibraryContext = createContext<LibraryApi | null>(null)

export function CharacterLibraryProvider({ children }: { children: ReactNode }) {
  const [stored, setStored] = useState<StoredCharacter[]>([])
  const [mine, setMine] = useState<CharacterAsset[]>([])
  const [ready, setReady] = useState(false)
  const [prefs, setPrefsState] = useState<CharacterPrefs>(readPrefs)
  const [shuffleVersion, setShuffleVersion] = useState(0)
  const urlsRef = useRef<string[]>([])

  const reload = useCallback(async () => {
    let list: StoredCharacter[] = []
    try {
      list = await listCharacters()
    } catch {
      // 저장소를 쓸 수 없는 환경(사생활 보호 모드 등)이면 기본 캐릭터만 쓴다
    }
    urlsRef.current.forEach((url) => URL.revokeObjectURL(url))
    const urls: string[] = []
    const toUrl = (blob: Blob) => {
      const url = URL.createObjectURL(blob)
      urls.push(url)
      return url
    }
    const assets = list.flatMap((c): CharacterAsset[] => {
      const available = Object.fromEntries(
        Object.entries(c.frames).map(([key, blob]) => [key, toUrl(blob)]),
      ) as Partial<Record<FrameKey, string>>
      const frames = resolveFrames(available)
      if (!frames) return []
      return [{ id: c.id, name: c.name, source: 'mine', tempo: c.tempo, frames, background: c.background ? toUrl(c.background) : null }]
    })
    urlsRef.current = urls
    setStored(list)
    setMine(assets)
    setReady(true)
  }, [])

  useEffect(() => {
    reload()
    return () => urlsRef.current.forEach((url) => URL.revokeObjectURL(url))
  }, [reload])

  const setPrefs = useCallback((change: Partial<CharacterPrefs>) => {
    setPrefsState((prev) => {
      const next = { ...prev, ...change }
      try {
        localStorage.setItem(PREFS_KEY, JSON.stringify(next))
      } catch {
        // 저장할 수 없으면 이번 탭에서만 쓴다
      }
      return next
    })
  }, [])

  const importFolder = useCallback(
    async (files: File[]) => {
      const result = await importFiles(files, stored)
      await reload()
      return result
    },
    [stored, reload],
  )

  const remove = useCallback(
    async (id: string) => {
      await deleteCharacter(id)
      if (prefs.myCharacterId === id) setPrefs({ myCharacterId: null })
      await reload()
    },
    [prefs.myCharacterId, setPrefs, reload],
  )

  const setTempo = useCallback(
    async (id: string, tempo: Tempo) => {
      const target = stored.find((c) => c.id === id)
      if (!target) return
      await saveCharacter({ ...target, tempo })
      await reload()
    },
    [stored, reload],
  )

  const reshuffle = useCallback(() => setShuffleVersion((v) => v + 1), [])

  const pool = useMemo(
    () => (mine.length === 0 ? BUILTIN_CHARACTERS : prefs.useBuiltIn ? [...mine, ...BUILTIN_CHARACTERS] : mine),
    [mine, prefs.useBuiltIn],
  )

  const value = useMemo<LibraryApi>(
    () => ({
      ready,
      mine,
      builtins: BUILTIN_CHARACTERS,
      pool,
      prefs,
      shuffleVersion,
      importFolder,
      remove,
      setTempo,
      setPrefs,
      reshuffle,
    }),
    [ready, mine, pool, prefs, shuffleVersion, importFolder, remove, setTempo, setPrefs, reshuffle],
  )

  return <LibraryContext.Provider value={value}>{children}</LibraryContext.Provider>
}

export function useCharacterLibrary(): LibraryApi {
  const library = useContext(LibraryContext)
  if (!library) throw new Error('CharacterLibraryProvider 안에서 써야 합니다.')
  return library
}
