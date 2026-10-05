import type { CSSProperties } from 'react'
import { useReveal } from '../lib/useReveal'

const STEPS = [
  {
    title: '오늘 할 일 적기',
    body: '할 일과 예상 시간을 적고 바로 입장해요. 가입은 필요 없어요.',
    icon: (
      <svg viewBox="0 0 24 24">
        <rect x="5" y="3.5" width="14" height="17" rx="3" />
        <path d="M9 9h6M9 13h6M9 17h3" />
      </svg>
    ),
  },
  {
    title: '스탠드 켜고 집중',
    body: '할 일을 고르면 내 책상 스탠드가 켜지고, 집중한 시간이 그 일에 쌓여요.',
    icon: (
      <svg viewBox="0 0 24 24">
        <path d="M8 20h8M12 20v-7l5-5" />
        <path d="M13 4l7 1-2 5z" />
      </svg>
    ),
  },
  {
    title: '하루를 돌아보기',
    body: '마무리하면 예상과 실제 시간을 비교해요. 못 끝낸 일은 다음번으로 넘길 수 있어요.',
    icon: (
      <svg viewBox="0 0 24 24">
        <path d="M4 20V10M10 20V4M16 20v-8M22 20H2" />
      </svg>
    ),
  },
]

/** 첫 화면 아래쪽의 이용 순서. 스크롤하면 하나씩 나타난다. */
export function HowItWorks({ onStart }: { onStart: () => void }) {
  const ref = useReveal<HTMLElement>()

  return (
    <section className="how" ref={ref} aria-labelledby="how-title">
      <div className="how-inner">
        <p className="section-eyebrow" data-reveal>
          이렇게 공부해요
        </p>
        <h2 id="how-title" data-reveal>
          적고, 집중하고, 돌아보는
          <br />
          하루 한 번의 스프린트
        </h2>
        <ol className="steps">
          {STEPS.map((step, i) => (
            <li key={step.title} className="step" data-reveal style={{ '--delay': `${i * 120}ms` } as CSSProperties}>
              <span className="step-no">0{i + 1}</span>
              <span className="step-icon" aria-hidden="true">
                {step.icon}
              </span>
              <h3>{step.title}</h3>
              <p>{step.body}</p>
            </li>
          ))}
        </ol>
        <div className="how-cta" data-reveal>
          <button className="button primary large" onClick={onStart}>
            오늘 계획 세우기
          </button>
        </div>
      </div>
    </section>
  )
}
