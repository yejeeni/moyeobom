import { useEffect, useState } from 'react'
import { api } from '../api'
import { isAlertSoundOn, playChime, setAlertSound } from '../lib/preferences'
import { Modal } from './Modal'
import { useToast } from './Toasts'

export function SettingsModal({ onClose }: { onClose: () => void }) {
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
      <div className="settings">
        <label className="switch-row">
          <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} disabled={!loaded} />
          <span>
            휴식 알림
            <small>한 번에 오래 집중하면 쉬어 가라고 알려 드려요</small>
          </span>
        </label>
        <label className={`field-row ${enabled ? '' : 'disabled'}`}>
          <span>알림까지 집중 시간</span>
          <span className="inline">
            <input
              type="number"
              min={1}
              value={minutes}
              onChange={(e) => setMinutes(e.target.value)}
              disabled={!enabled}
              aria-invalid={!valid}
            />
            분
          </span>
        </label>
        <label className="switch-row">
          <input type="checkbox" checked={sound} onChange={(e) => setSound(e.target.checked)} />
          <span>
            알림 소리
            <small>이 브라우저에만 저장돼요</small>
          </span>
        </label>
        <button className="link-button" type="button" onClick={playChime}>
          소리 미리 듣기
        </button>
      </div>
      <div className="modal-actions">
        <button className="button" onClick={onClose}>
          취소
        </button>
        <button className="button primary" onClick={save} disabled={!loaded || !valid || saving}>
          저장
        </button>
      </div>
    </Modal>
  )
}
