import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import { isApiError } from '../api/client'
import type { CarriedTask, CarryoverTask, NewTask } from '../api/types'
import { IntroPanel } from '../components/IntroPanel'
import { PlanPanel } from '../components/PlanPanel'
import { useToast } from '../components/Toasts'
import { getGuestId, saveGuestId } from '../lib/guest'

/**
 * 첫 화면 = 소개(왼쪽 1/3) + 스프린트 계획(오른쪽).
 * 열린 스프린트가 있으면 바로 열람실로 보낸다. 게스트 ID는 처음 입장할 때 발급한다.
 */
export function Home() {
  const navigate = useNavigate()
  const toast = useToast()
  const [carryover, setCarryover] = useState<CarryoverTask[] | null>(() => (getGuestId() ? null : []))
  const [entering, setEntering] = useState(false)

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

  return (
    <main className="home">
      <IntroPanel />
      {carryover === null ? (
        <div className="plan-panel page-loading">오늘 계획을 불러오는 중…</div>
      ) : (
        <PlanPanel carryover={carryover} entering={entering} onEnter={enter} />
      )}
    </main>
  )
}
