import { FRAME_KEYS, describeMissing, type FrameKey, type Tempo } from './characters'

/**
 * 사용자가 등록한 캐릭터를 브라우저 저장소(IndexedDB)에 둔다. 서버로는 보내지 않는다.
 * 브라우저 데이터를 지우면 함께 사라진다.
 */

export interface StoredCharacter {
  id: string
  name: string
  tempo: Tempo
  createdAt: number
  frames: Partial<Record<FrameKey, Blob>>
  background: Blob | null
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
const MAX_FILE_BYTES = 3 * 1024 * 1024
const IMAGE_EXT = /\.(png|webp|jpe?g)$/i

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
    const frames: Partial<Record<FrameKey, Blob>> = {}
    let background: Blob | null = null
    for (const file of groupFiles) {
      const key = file.name.replace(IMAGE_EXT, '').toLowerCase()
      const isFrame = (FRAME_KEYS as string[]).includes(key)
      if (!isFrame && key !== 'background') continue
      if (file.size > MAX_FILE_BYTES) {
        result.problems.push(`${name}/${file.name}: 3MB보다 커서 건너뛰었어요`)
        continue
      }
      if (isFrame) frames[key as FrameKey] = file
      else background = file
    }

    const frameKeys = Object.keys(frames) as FrameKey[]
    if (frameKeys.length === 0) {
      // 캐릭터 그림이 없는 폴더(상위 폴더, 템플릿 등)는 조용히 넘어가고, 배경만 있으면 알려 준다
      if (background) result.problems.push(`${name}: 캐릭터 그림(focus-a.png 등)이 없어 건너뛰었어요`)
      continue
    }

    const sizeNote = await checkSize(frames[frameKeys[0]]!)
    describeMissing(frameKeys).forEach((note) => result.notes.push(`${name}: ${note}`))
    if (sizeNote) result.notes.push(`${name}: ${sizeNote}`)

    const id = `mine:${name}`
    const previous = existing.find((c) => c.id === id)
    await saveCharacter({
      id,
      name,
      tempo: previous?.tempo ?? 'typing',
      createdAt: previous?.createdAt ?? Date.now(),
      frames,
      background,
    })
    result.imported.push(name)
  }

  if (result.imported.length === 0 && result.problems.length === 0) {
    result.problems.push('가져올 캐릭터 그림을 찾지 못했어요. 파일 이름이 focus-a.png 같은 규칙을 따르는지 확인해 주세요')
  }
  return result
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
