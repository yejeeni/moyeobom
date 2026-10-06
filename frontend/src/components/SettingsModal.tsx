import { useEffect, useState } from 'react'
import { api } from '../api'
import { isAlertSoundOn, playChime, setAlertSound } from '../lib/preferences'
import { Icon } from './Icon'
import { Modal } from './Modal'
import { useToast } from './Toasts'

interface Props {
  onClose: () => void
  /** 있으면 '내 캐릭터' 항목을 보여 준다 */
  onOpenCharacters?: () => void
}

export function SettingsModal({ onClose, onOpenCharacters }: Props) {
  const toast = useToast()
  const [enabled, setEnabled] = useState(false)
  const [minutes, setMinutes] = useState('50')
  const [sound, setSound] = useState(isAlertSoundOn())
  const [loaded, setLoaded] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    api.getSetting()
      .then((setting) => {
        setEnabled(setting.breakAlertEnabled)
        setMinutes(String(setting.breakAlertMinutes))
        setLoaded(true)
      })
      .catch((e: Error) => toast.show(e.message, 'error'))
  }, [toast])

  const minutesValue = Number(minutes)
  const valid = Number.isInteger(minutesValue) && minutesValue > 0
  // −/+ 버튼은 5분 단위로 움직인다
  const stepMinutes = (delta: number) => setMinutes(String(Math.max(5, (valid ? minutesValue : 50) + delta)))

  const save = async () => {
    if (!valid) return
    setSaving(true)
    try {
      await api.updateSetting({ breakAlertEnabled: enabled, breakAlertMinutes: minutesValue })
      setAlertSound(sound)
      toast.show('설정을 저장했어요')
      onClose()
    } catch (e) {
      toast.show((e as Error).message, 'error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title="설정" onClose={onClose}>
      <div className="setting-group">
        <label className="setting-row">
          <span className="setting-icon">
            <Icon name="bell" size={18} />
          </span>
          <span className="setting-text">
            <strong>휴식 알림</strong>
            <small>한 번에 오래 집중하면 쉬어 가라고 알려 드려요</small>
          </span>
          <input
            type="checkbox"
            role="switch"
            className="switch"
            checked={enabled}
            onChange={(e) => setEnabled(e.target.checked)}
            disabled={!loaded}
          />
        </label>
        <div className={`setting-sub ${enabled ? '' : 'disabled'}`}>
          <span id="alert-minutes-label">알림까지 집중 시간</span>
          <div className="stepper">
            <button type="button" onClick={() => stepMinutes(-5)} disabled={!enabled} aria-label="5분 줄이기">
              <Icon name="minus" size={16} />
            </button>
            <input
              type="number"
              min={1}
              value={minutes}
              onChange={(e) => setMinutes(e.target.value)}
              disabled={!enabled}
              aria-invalid={!valid}
              aria-labelledby="alert-minutes-label"
            />
            <span className="stepper-unit">분</span>
            <button type="button" onClick={() => stepMinutes(5)} disabled={!enabled} aria-label="5분 늘리기">
              <Icon name="plus" size={16} />
            </button>
          </div>
        </div>
      </div>

      <div className="setting-group">
        <label className="setting-row">
          <span className="setting-icon">
            <Icon name="volume" size={18} />
          </span>
          <span className="setting-text">
            <strong>알림 소리</strong>
            <small>이 브라우저에만 저장돼요</small>
          </span>
          <input type="checkbox" role="switch" className="switch" checked={sound} onChange={(e) => setSound(e.target.checked)} />
        </label>
        <div className="setting-sub">
          <span>어떤 소리인지 들어 보기</span>
          <button className="preview-button" type="button" onClick={playChime}>
            <Icon name="play" size={14} />
            미리 듣기
          </button>
        </div>
      </div>

      {onOpenCharacters && (
        <div className="setting-group">
          <button className="setting-row setting-link" type="button" onClick={onOpenCharacters}>
            <span className="setting-icon">
              <Icon name="image" size={18} />
            </span>
            <span className="setting-text">
              <strong>내 캐릭터</strong>
              <small>내가 그린 캐릭터로 열람실 자리를 채워요</small>
            </span>
            <span className="setting-chevron" aria-hidden="true">
              ›
            </span>
          </button>
        </div>
      )}

      <div className="modal-actions">
        <button className="button" onClick={onClose}>
          취소
        </button>
        <button className="button primary" onClick={save} disabled={!loaded || !valid || saving}>
          {saving ? '저장 중…' : '저장'}
        </button>
      </div>
    </Modal>
  )
}
