import type { MateSettings } from '../api/types'

// 알림 소리는 서버가 아니라 브라우저에만 저장한다
const SOUND_KEY = 'moyeobom.alertSound'

export function isAlertSoundOn(): boolean {
  try {
    return localStorage.getItem(SOUND_KEY) === 'on'
  } catch {
    return false
  }
}

export function setAlertSound(on: boolean) {
  try {
    localStorage.setItem(SOUND_KEY, on ? 'on' : 'off')
  } catch {
    // 무시
  }
}

// 새 열람실을 만들 때 쓸 메이트 설정. 마지막으로 고른 값을 기억한다
const ALWAYS_FOCUS_KEY = 'moyeobom.matesAlwaysFocus'
const STAY_KEY = 'moyeobom.matesStay'

export function getMateSettings(): MateSettings {
  try {
    const alwaysFocus = localStorage.getItem(ALWAYS_FOCUS_KEY) === 'on'
    const stay = localStorage.getItem(STAY_KEY)
    // 예전 '계속 집중'은 이탈 없음까지 포함했으므로, 이탈 없음를 따로 고른 적 없으면 그 값을 따른다
    return { matesAlwaysFocus: alwaysFocus, matesStay: stay === null ? alwaysFocus : stay === 'on' }
  } catch {
    return { matesAlwaysFocus: false, matesStay: false }
  }
}

export function setMateSettings({ matesAlwaysFocus, matesStay }: MateSettings) {
  try {
    localStorage.setItem(ALWAYS_FOCUS_KEY, matesAlwaysFocus ? 'on' : 'off')
    localStorage.setItem(STAY_KEY, matesStay ? 'on' : 'off')
  } catch {
    // 무시
  }
}

/** 짧고 부드러운 차임. 파일 없이 브라우저에서 만든다. */
export function playChime() {
  try {
    const AudioCtx = window.AudioContext
    const ctx = new AudioCtx()
    ;[660, 880].forEach((freq, i) => {
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()
      osc.type = 'sine'
      osc.frequency.value = freq
      const start = ctx.currentTime + i * 0.18
      gain.gain.setValueAtTime(0, start)
      gain.gain.linearRampToValueAtTime(0.15, start + 0.02)
      gain.gain.exponentialRampToValueAtTime(0.001, start + 0.6)
      osc.connect(gain).connect(ctx.destination)
      osc.start(start)
      osc.stop(start + 0.65)
    })
    window.setTimeout(() => ctx.close(), 1200)
  } catch {
    // 소리를 낼 수 없는 환경이면 조용히 넘어간다
  }
}
