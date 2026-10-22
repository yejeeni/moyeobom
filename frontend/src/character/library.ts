import { DEFAULT_FRAME_MS, MAX_FRAMES, describeMissing, fillsSeatType, parseFrameName, type FrameName, type FrameState } from './characters'

/**
 * 사용자가 등록한 캐릭터를 브라우저 저장소(IndexedDB)에 둔다. 서버로는 보내지 않는다.
 * 브라우저 데이터를 지우면 함께 사라진다.
 */

export interface StoredCharacter {
  id: string
  name: string
  /** 한 장당 시간(집중 기준, ms). 예전에 저장한 캐릭터에는 없고 tempo만 있다 */
  frameMs?: number
  /** 예전 빠르기 설정. typing은 빠르게, writing은 느리게 */
  tempo?: 'typing' | 'writing'
  createdAt: number
  /** 파일 이름(확장자 뺀) → 그림. focus-1, focus-a(예전 이름), focus(움짤 한 장) 등 */
  frames: Record<string, Blob>
  background: Blob | null
  /** 움짤 한 장(focus 등)과 배경의 첫 장면. '움직임 줄이기'용이며 예전에 저장한 캐릭터에는 없다 */
  stills?: Partial<Record<FrameState | 'background', Blob>>
}

/** 예전 빠르기 설정까지 고려한 한 장당 시간 */
export function frameMsOf(character: StoredCharacter): number {
  if (character.frameMs) return character.frameMs
  return character.tempo === 'writing' ? 330 : DEFAULT_FRAME_MS
}

/** 저장된 그림을 상태별로 넘기는 순서대로 묶는다. 움짤 한 장이 있으면 그 상태는 그것만 */
export function groupFrames<T>(frames: Record<string, T>): Partial<Record<FrameState, T[]>> {
  const named = Object.entries(frames)
    .map(([key, value]) => ({ name: parseFrameName(key), value }))
    .filter((entry): entry is { name: FrameName; value: T } => entry.name !== null)
  const grouped: Partial<Record<FrameState, T[]>> = {}
  for (const state of ['focus', 'break', 'idle'] as const) {
    const mine = named.filter((entry) => entry.name.state === state)
    const animated = mine.find((entry) => entry.name.animated)
    if (animated) grouped[state] = [animated.value]
    else if (mine.length > 0) grouped[state] = mine.sort((a, b) => a.name.order - b.name.order).map((entry) => entry.value)
  }
  return grouped
}

export interface ImportResult {
  imported: string[]
  /** 캐릭터 이름별 안내(대체 그림, 크기 등) */
  notes: string[]
  /** 건너뛴 이유 */
  problems: string[]
}

const DB_NAME = 'moyeobom'
const STORE = 'characters'
// 움짤은 용량이 커서 그림 한 장당 5MB까지 받는다
const MAX_FILE_BYTES = 5 * 1024 * 1024
const IMAGE_EXT = /\.(png|apng|webp|gif|jpe?g)$/i

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1)
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains(STORE)) {
        request.result.createObjectStore(STORE, { keyPath: 'id' })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

async function run<T>(mode: IDBTransactionMode, work: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await openDb()
  try {
    return await new Promise<T>((resolve, reject) => {
      const tx = db.transaction(STORE, mode)
      const request = work(tx.objectStore(STORE))
      tx.oncomplete = () => resolve(request.result)
      tx.onerror = () => reject(tx.error)
      tx.onabort = () => reject(tx.error)
    })
  } finally {
    db.close()
  }
}

export async function listCharacters(): Promise<StoredCharacter[]> {
  const all = await run<StoredCharacter[]>('readonly', (store) => store.getAll() as IDBRequest<StoredCharacter[]>)
  return all.sort((a, b) => a.createdAt - b.createdAt)
}

export function saveCharacter(character: StoredCharacter) {
  return run('readwrite', (store) => store.put(character))
}

export function deleteCharacter(id: string) {
  return run('readwrite', (store) => store.delete(id))
}

/**
 * 폴더에서 고른 파일들을 캐릭터별로 묶어 저장한다.
 * - 상위 폴더를 고르면 그 안의 캐릭터 폴더들을, 캐릭터 폴더 하나를 고르면 그 캐릭터만 가져온다.
 * - 캐릭터 이름은 그림이 들어 있는 폴더 이름. 같은 이름이 있으면 새 그림으로 바꾼다.
 */
export async function importFiles(files: File[], existing: StoredCharacter[]): Promise<ImportResult> {
  const result: ImportResult = { imported: [], notes: [], problems: [] }
  const groups = new Map<string, { name: string; files: File[] }>()

  for (const file of files) {
    if (!IMAGE_EXT.test(file.name)) continue
    const parts = (file.webkitRelativePath || file.name).split('/')
    const folderPath = parts.slice(0, -1).join('/')
    const name = parts.length >= 2 ? parts[parts.length - 2] : '내 캐릭터'
    const group = groups.get(folderPath) ?? { name, files: [] }
    group.files.push(file)
    groups.set(folderPath, group)
  }

  for (const { name, files: groupFiles } of groups.values()) {
    const candidates: { key: string; frame: FrameName; file: File }[] = []
    const stills: Partial<Record<FrameState | 'background', Blob>> = {}
    let background: Blob | null = null
    // 규칙에 없는 이름의 그림. 폴더에 이것 한 장뿐이면 집중·휴식 모두 이 그림으로 쓴다
    const unnamed: File[] = []
    for (const file of groupFiles) {
      const key = file.name.replace(IMAGE_EXT, '').toLowerCase()
      const frame = parseFrameName(key)
      if (!frame && key !== 'background') {
        unnamed.push(file)
        continue
      }
      if (file.size > MAX_FILE_BYTES) {
        result.problems.push(`${name}/${file.name}: 5MB보다 커서 건너뛰었어요`)
        continue
      }
      if (frame) {
        candidates.push({ key, frame, file })
        continue
      }
      background = file
      const still = await firstFrame(file)
      if (still) stills.background = still
    }

    const single = candidates.length === 0 && unnamed.length === 1 ? unnamed[0] : null
    if (single) {
      if (single.size > MAX_FILE_BYTES) {
        result.problems.push(`${name}/${single.name}: 5MB보다 커서 건너뛰었어요`)
        continue
      }
      candidates.push({ key: 'focus', frame: { state: 'focus', order: 0, animated: true }, file: single })
    }

    // 상태마다 앞 MAX_FRAMES장만 저장한다. 움짤 한 장은 첫 장면을 따로 만들어 둔다
    const frames: Record<string, Blob> = {}
    for (const state of ['focus', 'break', 'idle'] as const) {
      const mine = candidates.filter((c) => c.frame.state === state)
      const animated = mine.find((c) => c.frame.animated)
      const kept = animated ? [animated] : mine.sort((a, b) => a.frame.order - b.frame.order).slice(0, MAX_FRAMES)
      for (const c of kept) frames[c.key] = c.file
      if (animated) {
        const still = await firstFrame(animated.file)
        if (still) stills[state] = still
      }
    }

    const frameKeys = Object.keys(frames)
    if (frameKeys.length === 0) {
      // 캐릭터 그림이 없는 폴더(상위 폴더 등)는 조용히 넘어가고, 배경만 있거나 이름 없는 그림이 여러 장이면 알려 준다
      if (unnamed.length > 1) {
        result.problems.push(`${name}: 그림 이름이 focus, break 규칙을 따르지 않아 건너뛰었어요. 한 장만 두면 이름과 상관없이 써요`)
      } else if (background) {
        result.problems.push(`${name}: 캐릭터 그림(focus-1.png 등)이 없어 건너뛰었어요`)
      }
      continue
    }

    // GIF와 JPG는 칸을 꽉 채워 그리므로 캔버스 비율을 따지지 않는다
    const first = frames[frameKeys[0]]!
    const sizeNote = fillsSeatType(first.type) ? null : await checkSize(first)
    // 한 장짜리는 일부러 같은 그림을 쓰는 것이라 대체 안내를 하지 않는다
    if (!single) describeMissing(candidates.map((c) => c.frame)).forEach((note) => result.notes.push(`${name}: ${note}`))
    if (sizeNote) result.notes.push(`${name}: ${sizeNote}`)

    const id = `mine:${name}`
    const previous = existing.find((c) => c.id === id)
    await saveCharacter({
      id,
      name,
      frameMs: previous ? frameMsOf(previous) : DEFAULT_FRAME_MS,
      createdAt: previous?.createdAt ?? Date.now(),
      frames,
      background,
      stills,
    })
    result.imported.push(name)
  }

  if (result.imported.length === 0 && result.problems.length === 0) {
    result.problems.push('가져올 캐릭터 그림을 찾지 못했어요. 파일 이름이 focus-1.png나 focus.gif 같은 규칙을 따르는지 확인해 주세요')
  }
  return result
}

/** 그림의 첫 장면을 멈춘 PNG로 만든다. 움짤이 아니어도 같은 그림이 나올 뿐이다 */
async function firstFrame(image: Blob): Promise<Blob | null> {
  try {
    const bitmap = await createImageBitmap(image)
    const canvas = document.createElement('canvas')
    canvas.width = bitmap.width
    canvas.height = bitmap.height
    canvas.getContext('2d')?.drawImage(bitmap, 0, 0)
    bitmap.close()
    return await new Promise((resolve) => canvas.toBlob(resolve, 'image/png'))
  } catch {
    return null
  }
}

/** 캔버스는 800x600(4:3) 기준. 비율이 다르면 위치가 어긋날 수 있다고 알려 준다. */
async function checkSize(image: Blob): Promise<string | null> {
  try {
    const bitmap = await createImageBitmap(image)
    const { width, height } = bitmap
    bitmap.close()
    if (Math.abs(width / height - 4 / 3) > 0.02) {
      return `그림 크기가 ${width}x${height}예요. 800x600(4:3)이 아니면 위치가 어긋나 보일 수 있어요`
    }
    return null
  } catch {
    return null
  }
}
