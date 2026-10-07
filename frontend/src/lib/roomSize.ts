import { useLayoutEffect, useState, type RefObject } from 'react'

/** 열람실 인원(나 포함 1~9명). 브라우저에만 기억한다. */
const KEY = 'moyeobom.roomSize'
export const MIN_ROOM_SIZE = 1
export const MAX_ROOM_SIZE = 9
export const DEFAULT_ROOM_SIZE = 9

export function getRoomSize(): number {
  try {
    const value = Number(localStorage.getItem(KEY))
    return Number.isInteger(value) && value >= MIN_ROOM_SIZE && value <= MAX_ROOM_SIZE ? value : DEFAULT_ROOM_SIZE
  } catch {
    return DEFAULT_ROOM_SIZE
  }
}

export function setRoomSize(size: number) {
  try {
    localStorage.setItem(KEY, String(size))
  } catch {
    // 저장할 수 없으면 이번 화면에서만 쓴다
  }
}

/** 칸 비율(가로:세로). 배경 그림 비율에 맞춘다. */
const TILE_RATIO = 16 / 10

/**
 * 인원별로 쓸 수 있는 열 수. 9명까지는 3열을 넘지 않고,
 * 적은 인원은 화면 모양(가로로 넓은지, 세로로 긴지)에 따라 한 줄 또는 한 칸씩 쌓는다.
 */
const COLUMN_OPTIONS: Record<number, number[]> = {
  1: [1],
  2: [2, 1],
  3: [3, 1],
  4: [2, 4, 1],
  5: [3, 2],
  6: [3, 2],
  7: [3],
  8: [3],
  9: [3],
}

export interface TileLayout {
  columns: number
  width: number
  height: number
}

/** 다른 배치가 이만큼 더 커야 기본 배치(목록의 앞쪽) 대신 쓴다. 비슷하면 익숙한 모양을 유지한다 */
const SWITCH_GAIN = 1.1

/** 주어진 영역에서 칸이 가장 크게 보이는 배치를 고른다. 칸은 16:10을 유지한다. */
export function bestLayout(count: number, areaWidth: number, areaHeight: number, gap: number): TileLayout {
  const options = COLUMN_OPTIONS[count] ?? [3]
  let best: TileLayout | null = null
  for (const columns of options) {
    const rows = Math.ceil(count / columns)
    const byWidth = (areaWidth - gap * (columns - 1)) / columns
    const byHeight = ((areaHeight - gap * (rows - 1)) / rows) * TILE_RATIO
    const width = Math.max(0, Math.floor(Math.min(byWidth, byHeight)))
    if (!best || width > best.width * SWITCH_GAIN) best = { columns, width, height: Math.floor(width / TILE_RATIO) }
  }
  return best!
}

/** 요소 크기가 바뀔 때마다 칸 배치를 다시 계산한다. padding을 뺀 안쪽 크기를 쓴다. */
export function useTileLayout(ref: RefObject<HTMLElement | null>, count: number, gap: number): TileLayout | null {
  const [layout, setLayout] = useState<TileLayout | null>(null)

  useLayoutEffect(() => {
    const element = ref.current
    if (!element) return
    const measure = () => {
      const style = getComputedStyle(element)
      const width = element.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight)
      const height = element.clientHeight - parseFloat(style.paddingTop) - parseFloat(style.paddingBottom)
      const next = bestLayout(count, width, height, gap)
      setLayout((prev) =>
        prev && prev.columns === next.columns && prev.width === next.width && prev.height === next.height ? prev : next,
      )
    }
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(element)
    return () => observer.disconnect()
  }, [ref, count, gap])

  return layout
}
