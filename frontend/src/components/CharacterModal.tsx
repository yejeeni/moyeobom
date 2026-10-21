import { useEffect, useRef, useState, type ChangeEvent } from 'react'
import { FRAME_MS_MAX, FRAME_MS_MIN, type CharacterAsset } from '../character/characters'
import { useCharacterLibrary } from '../character/CharacterLibrary'
import type { ImportResult } from '../character/library'
import { Character } from './Character'
import { Icon } from './Icon'
import { Modal } from './Modal'
import { MotionImage } from './MotionImage'
import { useToast } from './Toasts'

const SEATS = 9

/**
 * 내 캐릭터 관리. 그림은 이 브라우저에만 저장되고 서버로는 보내지 않는다.
 * 열람실을 만들 때(또는 새 메이트가 들어올 때) 자리마다 목록에서 무작위로 뽑는다.
 */
export function CharacterModal({ onClose }: { onClose: () => void }) {
  const library = useCharacterLibrary()
  const toast = useToast()
  const folderInput = useRef<HTMLInputElement>(null)
  const [importing, setImporting] = useState(false)
  const [report, setReport] = useState<ImportResult | null>(null)

  // React가 모르는 속성이라 직접 붙인다: 폴더째 고르기
  useEffect(() => {
    folderInput.current?.setAttribute('webkitdirectory', '')
  }, [])

  const onPick = async (e: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? [])
    e.target.value = ''
    if (files.length === 0) return
    setImporting(true)
    try {
      const result = await library.importFolder(files)
      setReport(result)
      if (result.imported.length > 0) {
        // 새 캐릭터가 바로 보이도록 지금 열람실 배정을 새로 뽑는다
        library.reshuffle()
        toast.show(`캐릭터 ${result.imported.length}개를 가져와 열람실에 반영했어요`)
      }
    } catch {
      toast.show('그림을 저장하지 못했어요. 브라우저 저장 공간을 확인해 주세요.', 'error')
    } finally {
      setImporting(false)
    }
  }

  const remove = async (asset: CharacterAsset) => {
    if (!window.confirm(`'${asset.name}' 캐릭터를 지울까요? 이 브라우저에서만 지워져요.`)) return
    await library.remove(asset.id)
  }

  const { mine, builtins, pool, prefs } = library
  const shortage = !prefs.allowDuplicates && pool.length < SEATS
  const all = [...mine, ...builtins]

  return (
    <Modal title="내 캐릭터" onClose={onClose} className="modal-wide modal-xl">
      <div className="modal-columns character-columns">
        <div className="modal-column">
          <div className="setting-group">
            <div className="character-import">
              <p>
                상태마다 그림을 차례로 넘기거나(상태마다 12장까지), 움짤 한 장을 그대로 재생해서 캐릭터가 움직여요.
                캐릭터마다 폴더를 하나 만들고 아래 이름으로 그림을 넣은 뒤, <strong>폴더째</strong> 가져오세요. 여러 캐릭터
                폴더를 담은 상위 폴더를 골라도 돼요.
              </p>
              <pre className="folder-spec">
                {`고양이/
  focus-1.png … focus-12.png  ← 차례로 넘김
  break.webp       ← 또는 움짤 한 장
  background.gif   ← 배경(움짤도 돼요)`}
              </pre>
              <p className="muted small">
                focus는 집중, break는 휴식(집중하지 않는 동안) 그림이에요. 예전 idle 그림은 휴식 그림이 없을 때 대신 써요. background는 책상까지 포함한 칸 전체 배경으로, 없어도
                돼요. 움짤은 GIF, 움직이는 WebP, APNG를 받아요. GIF는 투명한 가장자리가 거칠게 보여서 WebP나 APNG를
                추천해요. GIF가 들어 있는 캐릭터는 투명하든 아니든 칸 전체를 꽉 채워 보여 줘요. 그림 한 장은 5MB까지이고, 예전 이름(focus-a, focus-b)도 그대로 받아요.
              </p>
              <p className="muted small">
                800x600 투명 PNG로, 같은 위치 기준으로 그려 주세요. 빠진 그림은 다른 그림으로 대신해요. 같은 이름의 폴더를
                다시 가져오면 새 그림으로 바뀌어요.
              </p>
              <div className="character-import-actions">
                <button className="button primary" onClick={() => folderInput.current?.click()} disabled={importing}>
                  <Icon name="upload" size={16} />
                  {importing ? '가져오는 중…' : '폴더 가져오기'}
                </button>
                <a className="preview-button" href="/characters/template.png" download="moyeobom-character-template.png">
                  그리기 가이드 받기
                </a>
              </div>
              <input ref={folderInput} type="file" multiple accept="image/png,image/webp,image/jpeg" hidden onChange={onPick} />
            </div>
            {report && (report.notes.length > 0 || report.problems.length > 0) && (
              <ul className="import-report">
                {report.problems.map((p) => (
                  <li key={p} className="is-problem">
                    {p}
                  </li>
                ))}
                {report.notes.map((n) => (
                  <li key={n}>{n}</li>
                ))}
              </ul>
            )}
          </div>

          <div className="setting-group">
            <label className={`setting-row ${mine.length === 0 ? 'is-disabled' : ''}`}>
              <span className="setting-icon">
                <Icon name="users" size={18} />
              </span>
              <span className="setting-text">
                <strong>기본 캐릭터도 함께 쓰기</strong>
                <small>{mine.length === 0 ? '내 캐릭터가 없으면 기본 캐릭터만 나와요' : '끄면 내 캐릭터만 자리에 나와요'}</small>
              </span>
              <input
                type="checkbox"
                role="switch"
                className="switch"
                checked={mine.length === 0 || prefs.useBuiltIn}
                disabled={mine.length === 0}
                onChange={(e) => library.setPrefs({ useBuiltIn: e.target.checked })}
              />
            </label>
            <label className="setting-row">
              <span className="setting-icon">
                <Icon name="image" size={18} />
              </span>
              <span className="setting-text">
                <strong>같은 캐릭터가 여러 자리에 나와도 괜찮아요</strong>
                <small>
                  {prefs.allowDuplicates
                    ? '자리마다 목록에서 자유롭게 뽑아요'
                    : shortage
                      ? `겹치지 않게 9자리를 채우려면 9개가 필요해요. 지금 ${pool.length}개라 모자라면 기본 캐릭터로, 그래도 모자라면 겹쳐서 채워요`
                      : '자리마다 서로 다른 캐릭터를 뽑아요'}
                </small>
              </span>
              <input
                type="checkbox"
                role="switch"
                className="switch"
                checked={prefs.allowDuplicates}
                onChange={(e) => library.setPrefs({ allowDuplicates: e.target.checked })}
              />
            </label>
            <div className="setting-sub">
              <span>설정은 다음에 들어오는 자리부터 적용돼요</span>
              <button
                className="preview-button"
                type="button"
                onClick={() => {
                  library.reshuffle()
                  toast.show('지금 열람실의 캐릭터를 새로 뽑았어요')
                }}
              >
                <Icon name="shuffle" size={14} />
                지금 다시 뽑기
              </button>
            </div>
          </div>

          <p className="guide-note">
            그림은 이 브라우저에만 저장되고 서버로 보내지 않아요. 브라우저 데이터를 지우면 함께 사라지니 원본 폴더는 따로
            보관해 주세요. 다른 사람의 열람실에는 보이지 않아요.
          </p>
        </div>

        <div className="modal-column character-list-column">
          <div className="character-list-header">
            <strong>캐릭터 {all.length}개</strong>
            <span className="muted small">별을 누르면 내 자리에 늘 그 캐릭터가 나와요</span>
          </div>
          <ul className="character-list">
            {all.map((asset) => {
              const isMine = prefs.myCharacterId === asset.id
              const inPool = pool.some((c) => c.id === asset.id)
              return (
                <li key={asset.id} className={`character-card ${inPool ? '' : 'is-off'}`}>
                  <CharacterPreview asset={asset} />
                  <div className="character-card-body">
                    <div className="character-card-title">
                      <strong title={asset.name}>{asset.name}</strong>
                      <span className={`source-badge source-${asset.source}`}>
                        {asset.source === 'mine' ? '내 캐릭터' : '기본'}
                      </span>
                    </div>
                    <div className="character-card-actions">
                      <button
                        className={`icon-toggle ${isMine ? 'is-on' : ''}`}
                        onClick={() => library.setPrefs({ myCharacterId: isMine ? null : asset.id })}
                        aria-pressed={isMine}
                        aria-label={isMine ? '내 자리 고정 풀기' : '내 자리에 이 캐릭터 쓰기'}
                        title={isMine ? '내 자리 고정 풀기' : '내 자리에 이 캐릭터 쓰기'}
                      >
                        <Icon name="star" size={16} />
                      </button>
                      {asset.source === 'mine' && (
                        <>
                          {Object.values(asset.frames).some((list) => list.length > 1) && (
                            <FrameMsSlider
                              name={asset.name}
                              value={asset.frameMs}
                              onCommit={(frameMs) => library.setFrameMs(asset.id, frameMs)}
                            />
                          )}
                          <button className="icon-toggle" onClick={() => remove(asset)} aria-label={`${asset.name} 지우기`} title="지우기">
                            <Icon name="trash" size={16} />
                          </button>
                        </>
                      )}
                    </div>
                  </div>
                </li>
              )
            })}
          </ul>
        </div>
      </div>

    </Modal>
  )
}

/**
 * 한 장당 시간. 끄는 동안은 숫자만 바꾸고, 손을 떼면 저장한다(저장하면 그림을 다시 불러오므로).
 */
function FrameMsSlider({ name, value, onCommit }: { name: string; value: number; onCommit: (frameMs: number) => void }) {
  const [draft, setDraft] = useState(value)
  const commit = () => draft !== value && onCommit(draft)
  return (
    <label className="frame-ms" title="집중할 때 한 장을 보여 주는 시간. 휴식은 이보다 느리게 넘겨요">
      <input
        type="range"
        min={FRAME_MS_MIN}
        max={FRAME_MS_MAX}
        step={20}
        value={draft}
        onChange={(e) => setDraft(Number(e.target.value))}
        onPointerUp={commit}
        onKeyUp={commit}
        onBlur={commit}
        aria-label={`${name} 한 장당 시간`}
      />
      <span>{(draft / 1000).toFixed(2)}초</span>
    </label>
  )
}

function CharacterPreview({ asset }: { asset: CharacterAsset }) {
  return (
    <div className="character-preview seat-focus" aria-hidden="true">
      {asset.background || asset.fillsSeat ? (
        <div className="paired-stack">
          {asset.background && <MotionImage className="scene-image" src={asset.background} still={asset.stills[asset.background]} />}
          <Character asset={asset} state="FOCUS" phase={0} paired />
        </div>
      ) : (
        <div className="character-wrap">
          <Character asset={asset} state="FOCUS" phase={0} />
        </div>
      )}
    </div>
  )
}
