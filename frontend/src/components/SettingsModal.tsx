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
  /** 지금 열람실 인원. 있으면 인원 항목을 보여 준다 */
  roomSize?: number
  /** 인원을 바꿔 저장하면 부른다(새 방으로 들어간다) */
  onRoomSizeChange?: (size: number) => Promise<void>
}

export function SettingsModal({ onClose, onOpenCharacters, roomSize, onRoomSizeChange }: Props) {
  const toast = useToast()
  const [enabled, setEnabled] = useState(false)
  const [minutes, setMinutes] = useState('50')
  const [sound, setSound] = useState(isAlertSoundOn())
  const [loaded, setLoaded] = useState(false)
  const [saving, setSaving] = useState(false)
  const [size, setSize] = useState(roomSize ?? 9)

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
      if (roomSize !== undefined && size !== roomSize && onRoomSizeChange) {
        await onRoomSizeChange(size)
        toast.show(`${size}명 열람실로 옮겼어요`)
      } else {
        toast.show('설정을 저장했어요')
      }
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

      {roomSize !== undefined && (
        <div className="setting-group">
          <div className="setting-row">
            <span className="setting-icon">
              <Icon name="users" size={18} />
            </span>
            <span className="setting-text">
              <strong id="room-size-label">열람실 인원</strong>
              <small>나를 포함한 자리 수예요. 바꾸면 새 열람실로 옮겨요</small>
            </span>
            <div className="stepper">
              <button type="button" onClick={() => setSize((v) => Math.max(1, v - 1))} disabled={size <= 1} aria-label="한 명 줄이기">
                <Icon name="minus" size={16} />
              </button>
              <output className="stepper-value" aria-labelledby="room-size-label">
                {size}
              </output>
              <span className="stepper-unit">명</span>
              <button type="button" onClick={() => setSize((v) => Math.min(9, v + 1))} disabled={size >= 9} aria-label="한 명 늘리기">
                <Icon name="plus" size={16} />
              </button>
            </div>
          </div>
        </div>
      )}

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
