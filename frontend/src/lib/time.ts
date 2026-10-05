import { useEffect, useState } from 'react'

/** 1초마다 다시 그리기 위한 현재 시각 */
export function useNow(intervalMs = 1000): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), intervalMs)
    return () => window.clearInterval(id)
  }, [intervalMs])
  return now
}

/** 서버 시각 기준 경과 초. offsetMs = 서버 시각 - 내 PC 시각 */
export function elapsedSeconds(since: string | null | undefined, now: number, offsetMs: number): number {
  if (!since) return 0
  return Math.max(0, Math.floor((now + offsetMs - Date.parse(since)) / 1000))
}

/** 상단 타이머용 00:00 또는 0:00:00 */
export function formatClock(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`
}

/** 1시간 5분, 42분, 30초 */
export function formatDuration(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  if (h > 0) return m > 0 ? `${h}시간 ${m}분` : `${h}시간`
  if (m > 0) return `${m}분`
  return seconds > 0 ? `${seconds}초` : '0분'
}

/** 자리에 표시하는 짧은 경과 시간: 42분, 1시간 5분 */
export function formatShort(seconds: number): string {
  const m = Math.floor(seconds / 60)
  if (m < 1) return '방금'
  return formatDuration(seconds)
}

/** 회고의 차이: +20분, -5분, 정확해요 */
export function formatDiff(diffSeconds: number): string {
  const minutes = Math.round(diffSeconds / 60)
  if (minutes === 0) return '딱 맞았어요'
  return minutes > 0 ? `+${minutes}분 더 걸렸어요` : `${-minutes}분 빨랐어요`
}

export const STATE_LABEL = { FOCUS: '집중', BREAK: '휴식', IDLE: '대기' } as const
