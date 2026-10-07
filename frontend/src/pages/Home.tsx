import { useEffect, useState, type PointerEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import { isApiError } from '../api/client'
import type { CarriedTask, CarryoverTask, NewTask } from '../api/types'
import { DemoRoom } from '../components/DemoRoom'
import { CharacterModal } from '../components/CharacterModal'
import { GuideModal } from '../components/GuideModal'
import { HowItWorks } from '../components/HowItWorks'
import { IntroPanel } from '../components/IntroPanel'
import { PlanPanel } from '../components/PlanPanel'
import { useToast } from '../components/Toasts'
import { getGuestId, saveGuestId } from '../lib/guest'

/**
 * 첫 화면 = 소개 문구와 스프린트 계획(왼쪽) + 열람실 미리보기(오른쪽), 아래로 이용 순서.
 * 열린 스프린트가 있으면 바로 열람실로 보낸다. 게스트 ID는 처음 입장할 때 발급한다.
 */
export function Home() {
  const navigate = useNavigate()
  const toast = useToast()
  const [carryover, setCarryover] = useState<CarryoverTask[] | null>(() => (getGuestId() ? null : []))
  const [entering, setEntering] = useState(false)
  const [guideOpen, setGuideOpen] = useState(false)
  const [charactersOpen, setCharactersOpen] = useState(false)

  useEffect(() => {
    if (!getGuestId()) return
    ;(async () => {
      try {
        const me = await api.me()
        if (me.hasOpenSprint) {
          navigate('/room', { replace: true })
          return
        }
        setCarryover((await api.carryover()).tasks)
      } catch (e) {
        // 모르는 게스트면 저장값을 지우고 처음 온 사람처럼 보여준다
        if (!isApiError(e, 'GUEST_NOT_FOUND')) toast.show((e as Error).message, 'error')
        setCarryover([])
      }
    })()
  }, [navigate, toast])

  const enter = async (tasks: NewTask[], carriedTasks: CarriedTask[]) => {
    setEntering(true)
    try {
      if (!getGuestId()) saveGuestId((await api.issueGuest()).guestId)
      await api.startSprint(tasks, carriedTasks)
      navigate('/room')
    } catch (e) {
      if (isApiError(e, 'SPRINT_ALREADY_OPEN')) {
        navigate('/room')
        return
      }
      toast.show((e as Error).message, 'error')
      setEntering(false)
    }
  }

  // 마우스를 따라 스탠드 불빛이 움직인다
  const moveSpotlight = (e: PointerEvent<HTMLElement>) => {
    const rect = e.currentTarget.getBoundingClientRect()
    e.currentTarget.style.setProperty('--mx', `${e.clientX - rect.left}px`)
    e.currentTarget.style.setProperty('--my', `${e.clientY - rect.top}px`)
  }

  const goToPlan = () => {
    const input = document.getElementById('plan-title')
    input?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    input?.focus({ preventScroll: true })
  }

  return (
    <div className="home">
      <header className="home-nav">
        <div className="home-nav-inner">
          <span className="brand">
            <span className="brand-mark" aria-hidden="true" />
            모여봄
          </span>
          <div className="nav-links">
            <button className="nav-link" onClick={() => setCharactersOpen(true)}>
              내 캐릭터
            </button>
            <button className="nav-link" onClick={() => setGuideOpen(true)}>
              열람실 안내
            </button>
          </div>
        </div>
      </header>

      <main>
        <section className="hero" onPointerMove={moveSpotlight}>
          <div className="hero-inner">
            <div className="hero-copy">
              <IntroPanel />
              {carryover === null ? (
                <div className="plan-panel plan-loading" aria-busy="true">
                  오늘 계획을 불러오는 중…
                </div>
              ) : (
                <PlanPanel carryover={carryover} entering={entering} onEnter={enter} />
              )}
            </div>
            <DemoRoom onOpenGuide={() => setGuideOpen(true)} />
          </div>
        </section>

        <HowItWorks onStart={goToPlan} />
      </main>

      <footer className="home-footer">© 모여봄 · 혼자 공부해도, 함께 있는 것처럼</footer>
      {guideOpen && <GuideModal onClose={() => setGuideOpen(false)} />}
      {charactersOpen && <CharacterModal onClose={() => setCharactersOpen(false)} />}
    </div>
  )
}
