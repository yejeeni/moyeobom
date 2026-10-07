import type { CSSProperties } from 'react'

// 문구가 차례로 떠오르도록 애니메이션 시작을 늦춘다
const delay = (ms: number) => ({ '--delay': `${ms}ms` }) as CSSProperties

/** 첫 화면 위쪽의 서비스 소개 문구 */
export function IntroPanel() {
  return (
    <div className="intro">
      <p className="hero-badge rise" style={delay(0)}>
        <span className="live-dot" aria-hidden="true" />
        캠·마이크 없는 온라인 열람실
      </p>
      <h1 className="rise" style={delay(80)}>
        혼자 공부해도,
        <br />
        <span className="highlight">함께 있는 것처럼.</span>
      </h1>
      <p className="lead rise" style={delay(160)}>
        스탠드 불빛 아래 모두가 조용히 집중하는 열람실이에요.
        <br className="wide-only" /> 집중한 시간이 할 일에 차곡차곡 쌓여요.
      </p>
    </div>
  )
}
