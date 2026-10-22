import { useCharacterLibrary } from '../character/CharacterLibrary'
import { CharacterPreview } from './CharacterModal'
import { Icon } from './Icon'
import { Modal } from './Modal'

interface Props {
  /** 창 제목에 쓸 자리 이름(닉네임 또는 '내 자리') */
  seatName: string
  isMe: boolean
  /** 지금 그 자리에 보이는 캐릭터 */
  currentId: string | null
  /** 고른 캐릭터 id. null이면 다시 무작위로 */
  onPick: (id: string | null) => void
  onClose: () => void
}

/**
 * 열람실 자리 하나의 캐릭터를 고른다. 내 화면에서만 바뀌고 다른 사람에게는 보이지 않는다.
 */
export function CharacterPickerModal({ seatName, isMe, currentId, onPick, onClose }: Props) {
  const library = useCharacterLibrary()
  const all = [...library.mine, ...library.builtins]

  return (
    <Modal title={`${seatName} 캐릭터 고르기`} onClose={onClose} className="modal-wide">
      <div className="character-picker">
        <p className="muted small">
          {isMe
            ? '고른 캐릭터는 어느 열람실에서든 내 자리에 나와요.'
            : '이 자리 주인이 자리에 있는 동안 고른 캐릭터로 보여요.'}{' '}
          내 화면에서만 바뀌어요.
        </p>
        <ul className="character-picker-list">
          {all.map((asset) => {
            const current = asset.id === currentId
            return (
              <li key={asset.id}>
                <button
                  type="button"
                  className={`character-picker-item ${current ? 'is-current' : ''}`}
                  onClick={() => onPick(asset.id)}
                  aria-pressed={current}
                >
                  <CharacterPreview asset={asset} />
                  <span title={asset.name}>{asset.name}</span>
                </button>
              </li>
            )
          })}
        </ul>
        <div className="character-picker-actions">
          <button type="button" className="preview-button" onClick={() => onPick(null)}>
            <Icon name="shuffle" size={14} />
            무작위로 돌리기
          </button>
        </div>
      </div>
    </Modal>
  )
}
