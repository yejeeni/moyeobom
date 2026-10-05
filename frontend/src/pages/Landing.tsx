import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import type { Occupant } from '../api/types'
import { GuideModal } from '../components/GuideModal'
import { SeatTile } from '../components/SeatTile'
import { useToast } from '../components/Toasts'
import { getGuestId, saveGuestId } from '../lib/guest'
import { useNow } from '../lib/time'

const minutesAgo = (m: number) => new Date(Date.now() - m * 60_000).toISOString()

// 소개용 그림. 실제 열람실과 같은 규칙으로 그린다.
const DEMO: Occupant[] = [
  { nickname: '졸린 수달', character: { hair: 0, hairColor: 1, shirt: 1, skin: 1 }, state: 'FOCUS', since: minutesAgo(37), completedCount: 2, remainingCount: 1 },
  { nickname: '조용한 연필', character: { hair: 5, hairColor: 0, shirt: 7, skin: 0 }, state: 'FOCUS', since: minutesAgo(12), completedCount: 0, remainingCount: 3 },
  { nickname: '느긋한 고양이', character: { hair: 7, hairColor: 4, shirt: 3, skin: 2 }, state: 'BREAK', since: minutesAgo(4), completedCount: 1, remainingCount: 2 },
]

export function Landing() {
  const navigate = useNavigate()
  const toast = useToast()
  const now = useNow()
  const [checking, setChecking] = useState(() => getGuestId() !== null)
  const [starting, setStarting] = useState(false)
  const [guideOpen, setGuideOpen] = useState(false)

  // 저장된 게스트가 있으면 열린 스프린트 여부에 따라 바로 보낸다
  useEffect(() => {
    if (!getGuestId()) return
    api.me()
      .then((me) => navigate(me.hasOpenSprint ? '/room' : '/plan', { replace: true }))
      .catch(() => setChecking(false))
  }, [navigate])

  const start = async () => {
    setStarting(true)
    try {
      const { guestId } = await api.issueGuest()
      saveGuestId(guestId)
      navigate('/plan')
    } catch (e) {
      toast.show((e as Error).message, 'error')
      setStarting(false)
    }
  }

  if (checking) return <div className="page-loading">열람실 불을 켜는 중…</div>

  return (
    <main className="landing">
      <section className="landing-copy">
        <p className="eyebrow">모여봄 · 온라인 열람실</p>
        <h1>
          혼자 공부해도,
          <br />
          함께 있는 것처럼.
        </h1>
        <p className="lead">
          스탠드 불빛 아래 모두가 조용히 집중하는 열람실이에요. 오늘 할 일 하나를 적고 들어오면, 집중한 시간이 그 일에
          차곡차곡 쌓여요.
        </p>
        <ul className="landing-points">
          <li>캠도 마이크도 필요 없어요</li>
          <li>가입 없이 바로 시작해요</li>
          <li>하루를 마치면 예상과 실제 시간을 비교해 봐요</li>
        </ul>
        <div className="mate-notice">
          <strong>가상 스터디 메이트가 함께해요.</strong> 열람실이 비어 보이지 않도록 서버가 만든 메이트가 빈자리를
          채워요.{' '}
          <button className="link-button" onClick={() => setGuideOpen(true)}>
            자세히 보기
          </button>
        </div>
        <button className="button primary large" onClick={start} disabled={starting}>
          {starting ? '자리 준비 중…' : '시작하기'}
        </button>
      </section>
      <section className="landing-art" aria-label="열람실 미리보기">
        <div className="demo-grid">
          {DEMO.map((occupant, i) => (
            <SeatTile key={i} seatNo={i + 1} occupant={occupant} isMe={false} now={now} offsetMs={0} compact={false} />
          ))}
        </div>
      </section>
      {guideOpen && <GuideModal onClose={() => setGuideOpen(false)} />}
    </main>
  )
}
