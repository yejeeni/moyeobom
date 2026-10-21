import { useCallback, useEffect, useState, type PointerEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '../api'
import { isApiError } from '../api/client'
import type { CarriedTask, CarryoverTask, NewTask, SprintView } from '../api/types'
import { DemoRoom } from '../components/DemoRoom'
import { CharacterModal } from '../components/CharacterModal'
import { GuideModal } from '../components/GuideModal'
import { HowItWorks } from '../components/HowItWorks'
import { IntroPanel } from '../components/IntroPanel'
import { PlanPanel } from '../components/PlanPanel'
import { ResumePanel } from '../components/ResumePanel'
import { ReviewModal } from '../components/ReviewModal'
import type { RoomChoiceValue } from '../components/RoomChoice'
import { useToast } from '../components/Toasts'
import { getGuestId, saveGuestId } from '../lib/guest'
import { CODE_LENGTH, normalizeCode } from '../lib/roomSize'
import { useRoom } from '../room/RoomContext'

/**
 * 첫 화면 = 소개 문구와 스프린트 계획(왼쪽) + 열람실 미리보기(오른쪽), 아래로 이용 순서.
 * 열린 스프린트가 있으면 계획 카드 대신 이어 하기 카드를 보여 주고, 돌아갈지·다른 열람실로 갈지·마무리할지 고르게 한다.
 * 게스트 ID는 처음 입장할 때 발급한다.
 */
export function Home() {
  const navigate = useNavigate()
  const toast = useToast()
  const room = useRoom()
  // 초대 링크(/r/코드)로 오면 ?code=로 넘어온다
  const [params] = useSearchParams()
  const inviteCode = normalizeCode(params.get('code') ?? '').slice(0, CODE_LENGTH) || undefined
  const [carryover, setCarryover] = useState<CarryoverTask[] | null>(() => (getGuestId() ? null : []))
  // 진행 중인 스프린트. 있으면 계획 대신 이어 하기 카드를 보여 준다
  const [openSprint, setOpenSprint] = useState<SprintView | null>(null)
  const [entering, setEntering] = useState(false)
  const [reviewOpen, setReviewOpen] = useState(false)
  const [guideOpen, setGuideOpen] = useState(false)
  const [charactersOpen, setCharactersOpen] = useState(false)

  useEffect(() => {
    if (!getGuestId()) return
    ;(async () => {
      try {
        const me = await api.me()
        if (me.hasOpenSprint) {
          const { sprint } = await api.currentSprint()
          if (sprint) {
            setOpenSprint(sprint)
            setCarryover([])
            return
          }
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

  const enter = async (tasks: NewTask[], carriedTasks: CarriedTask[], choice: RoomChoiceValue, sessionMinutes: number) => {
    setEntering(true)
    try {
      if (!getGuestId()) saveGuestId((await api.issueGuest()).guestId)
      try {
        await api.startSprint(tasks, carriedTasks, sessionMinutes)
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

  /** 마무리를 확정하면 같은 화면에서 새 계획 카드(이월 할 일 포함)로 바꾼다 */
  const startFresh = useCallback(() => {
    setReviewOpen(false)
    setOpenSprint(null)
    setCarryover(null)
    api.carryover()
      .then(({ tasks }) => setCarryover(tasks))
      .catch(() => setCarryover([]))
  }, [])

  /** 이어 하기 카드에서 다른 열람실을 고른 경우. 스프린트는 이미 열려 있다 */
  const enterOther = async (choice: RoomChoiceValue) => {
    setEntering(true)
    try {
      if (choice.mode === 'join') await room.join(choice.code)
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
              {openSprint ? (
                <ResumePanel
                  sprint={openSprint}
                  inviteCode={inviteCode}
                  busy={entering}
                  onResume={() => navigate('/room')}
                  onEnter={enterOther}
                  onFinish={() => setReviewOpen(true)}
                />
              ) : carryover === null ? (
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
      {reviewOpen && <ReviewModal onClose={() => setReviewOpen(false)} onDone={startFresh} />}
    </div>
  )
}
