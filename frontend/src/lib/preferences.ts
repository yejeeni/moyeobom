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
