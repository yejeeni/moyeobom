import { useEffect, useState, type PointerEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '../api'
import { isApiError } from '../api/client'
import type { CarriedTask, CarryoverTask, NewTask } from '../api/types'
import { DemoRoom } from '../components/DemoRoom'
import { CharacterModal } from '../components/CharacterModal'
import { GuideModal } from '../components/GuideModal'
import { HowItWorks } from '../components/HowItWorks'
import { IntroPanel } from '../components/IntroPanel'
import { PlanPanel } from '../components/PlanPanel'
import type { RoomChoiceValue } from '../components/RoomChoice'
import { useToast } from '../components/Toasts'
import { getGuestId, saveGuestId } from '../lib/guest'
import { CODE_LENGTH, normalizeCode } from '../lib/roomSize'
import { useRoom } from '../room/RoomContext'

/**
 * 첫 화면 = 소개 문구와 스프린트 계획(왼쪽) + 열람실 미리보기(오른쪽), 아래로 이용 순서.
 * 열린 스프린트가 있으면 바로 열람실로 보낸다. 게스트 ID는 처음 입장할 때 발급한다.
 */
export function Home() {
  const navigate = useNavigate()
  const toast = useToast()
  const room = useRoom()
  // 초대 링크(/r/코드)로 오면 ?code=로 넘어온다
  const [params] = useSearchParams()
  const inviteCode = normalizeCode(params.get('code') ?? '').slice(0, CODE_LENGTH) || undefined
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
          // 이미 공부 중인 사람이 초대 링크로 오면 그 방으로 옮긴다
          if (inviteCode) await joinOrWarn(inviteCode)
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
    // 처음 한 번만 실행한다
  }, [])

  /** 코드로 들어가고, 실패하면 이유를 알려 준다(열람실 화면에서 새 방이 열린다) */
  const joinOrWarn = async (code: string) => {
    try {
      await room.join(code)
    } catch (e) {
      toast.show(`코드로 들어가지 못했어요. ${(e as Error).message}`, 'error')
    }
  }

  const enter = async (tasks: NewTask[], carriedTasks: CarriedTask[], choice: RoomChoiceValue) => {
    setEntering(true)
    try {
      if (!getGuestId()) saveGuestId((await api.issueGuest()).guestId)
      try {
        await api.startSprint(tasks, carriedTasks)
      } catch (e) {
        if (!isApiError(e, 'SPRINT_ALREADY_OPEN')) throw e
      }
      if (choice.mode === 'join') await joinOrWarn(choice.code)
      else await room.create(choice.config)
      navigate('/room')
    } catch (e) {
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
                <PlanPanel carryover={carryover} entering={entering} onEnter={enter} initialCode={inviteCode} />
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
