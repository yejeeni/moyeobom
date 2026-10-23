import { useEffect, useRef, useState } from 'react'
import { hasNewVersion } from '../lib/appVersion'
import { useRoom } from '../room/RoomContext'
import { Icon } from './Icon'

/** 열어 둔 화면에서도 새 배포를 알아차리도록 이 간격으로 한 번씩 확인한다 */
const CHECK_INTERVAL_MS = 10 * 60 * 1000

/**
 * 새로 배포되면 새로고침을 권한다. 집중 중에 화면이 갑자기 바뀌지 않도록 자동으로 새로고침하지는 않는다.
 * 배포하면 서버가 다시 켜지며 실시간 연결이 끊겼다 이어지므로 그때 바로 확인하고, 탭으로 돌아올 때와 10분마다도 확인한다.
 */
export function UpdateBanner() {
  const { connection } = useRoom()
  const [available, setAvailable] = useState(false)
  const [dismissed, setDismissed] = useState(false)
  const previousConnection = useRef(connection)

  useEffect(() => {
    const check = () => {
      hasNewVersion().then((found) => found && setAvailable(true))
    }
    const onVisible = () => document.visibilityState === 'visible' && check()
    const timer = window.setInterval(check, CHECK_INTERVAL_MS)
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [])

  useEffect(() => {
    const reconnected = previousConnection.current === 'reconnecting' && connection === 'connected'
    previousConnection.current = connection
    if (reconnected) hasNewVersion().then((found) => found && setAvailable(true))
  }, [connection])

  if (!available || dismissed) return null
  return (
    <div className="update-banner" role="status">
      <span>새 버전이 나왔어요. 새로고침하면 바로 적용돼요</span>
      <button type="button" className="button primary" onClick={() => window.location.reload()}>
        새로고침
      </button>
      <button type="button" className="update-banner-close" onClick={() => setDismissed(true)} aria-label="나중에 하기">
        <Icon name="close" size={16} />
      </button>
    </div>
  )
}
