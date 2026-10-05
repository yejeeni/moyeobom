import type { CharacterParts, SeatState } from '../api/types'

// 파츠 번호만 받아 직접 그린다. 실제 유저와 가상 메이트가 같은 규칙으로 그려진다.
const SKIN = ['#FFE3CC', '#F6CDA8', '#E5B088', '#C68D63', '#8E5B3D']
const HAIR_COLOR = ['#3A2B27', '#6B4330', '#B98245', '#E3BE77', '#94403F', '#4B4A5E']
const SHIRT = ['#F2A9A9', '#A9C6F0', '#B6DDB0', '#F4D488', '#C8B4EA', '#F4EFE6', '#8EB8B3', '#E9A6C9']

interface Props {
  parts: CharacterParts
  state: SeatState
}

/**
 * 책상 뒤에 앉은 상반신. viewBox 0 0 160 120, 책상 윗면은 y=96.
 * 집중: 눈을 내리깔고 펜을 움직인다. 휴식: 책상에 엎드린다. 대기: 바로 앉아 있다.
 */
export function Character({ parts, state }: Props) {
  const skin = SKIN[parts.skin % SKIN.length]
  const hair = HAIR_COLOR[parts.hairColor % HAIR_COLOR.length]
  const shirt = SHIRT[parts.shirt % SHIRT.length]
  const style = parts.hair % 8
  const resting = state === 'BREAK'

  return (
    <svg className={`character character-${state.toLowerCase()}`} viewBox="0 0 160 120" aria-hidden="true">
      <g className={resting ? 'body body-resting' : 'body'}>
        {/* 몸통 */}
        <path d={`M48 120 Q48 84 80 82 Q112 84 112 120 Z`} fill={shirt} />
        <path d="M70 84 L80 94 L90 84" fill="none" stroke="rgba(0,0,0,0.12)" strokeWidth="2" />
        <g className="head">
          <BackHair style={style} color={hair} />
          <circle cx="80" cy="56" r="26" fill={skin} />
          <Face state={state} />
          <FrontHair style={style} color={hair} />
        </g>
      </g>
      {/* 책상 위 팔과 소품 */}
      {resting ? (
        <g>
          <ellipse cx="62" cy="98" rx="20" ry="7" fill={shirt} />
          <ellipse cx="98" cy="98" rx="20" ry="7" fill={shirt} />
        </g>
      ) : (
        <g>
          <rect x="64" y="96" width="34" height="5" rx="1" fill="#fbf7ef" />
          <ellipse cx="60" cy="97" rx="9" ry="5" fill={skin} />
          <g className={state === 'FOCUS' ? 'pen pen-writing' : 'pen'}>
            <ellipse cx="96" cy="96" rx="8" ry="5" fill={skin} />
            <rect x="94" y="80" width="3" height="16" rx="1.5" fill="#5a6b8c" transform="rotate(20 96 92)" />
          </g>
        </g>
      )}
      {resting && (
        <text className="zzz" x="112" y="54" fontSize="13" fill="rgba(255,255,255,0.75)">
          z
        </text>
      )}
    </svg>
  )
}

function Face({ state }: { state: SeatState }) {
  const cheeks = (
    <>
      <ellipse cx="64" cy="64" rx="5" ry="3" fill="#f4a3a3" opacity="0.55" />
      <ellipse cx="96" cy="64" rx="5" ry="3" fill="#f4a3a3" opacity="0.55" />
    </>
  )
  if (state === 'BREAK') {
    return (
      <g stroke="#3a2b27" strokeWidth="2" strokeLinecap="round" fill="none">
        <path d="M66 58 Q71 61 76 58" />
        <path d="M84 58 Q89 61 94 58" />
        {cheeks}
      </g>
    )
  }
  if (state === 'FOCUS') {
    return (
      <g>
        <path d="M66 60 Q71 63 76 60" stroke="#3a2b27" strokeWidth="2" strokeLinecap="round" fill="none" />
        <path d="M84 60 Q89 63 94 60" stroke="#3a2b27" strokeWidth="2" strokeLinecap="round" fill="none" />
        <path d="M77 70 Q80 71 83 70" stroke="#a0625a" strokeWidth="1.6" strokeLinecap="round" fill="none" />
        {cheeks}
      </g>
    )
  }
  return (
    <g>
      <circle cx="71" cy="58" r="2.6" fill="#3a2b27" />
      <circle cx="89" cy="58" r="2.6" fill="#3a2b27" />
      <path d="M76 68 Q80 71 84 68" stroke="#a0625a" strokeWidth="1.6" strokeLinecap="round" fill="none" />
      {cheeks}
    </g>
  )
}

function BackHair({ style, color }: { style: number; color: string }) {
  switch (style) {
    case 5: // 긴 머리
      return <path d="M52 56 Q52 28 80 28 Q108 28 108 56 L110 96 Q80 104 50 96 Z" fill={color} />
    case 6: // 단발
      return <path d="M52 56 Q52 28 80 28 Q108 28 108 56 L108 78 Q80 84 52 78 Z" fill={color} />
    case 2: // 똥머리
      return <circle cx="80" cy="26" r="11" fill={color} />
    case 3: // 양갈래
      return (
        <>
          <ellipse cx="50" cy="66" rx="8" ry="13" fill={color} />
          <ellipse cx="110" cy="66" rx="8" ry="13" fill={color} />
        </>
      )
    default:
      return null
  }
}

function FrontHair({ style, color }: { style: number; color: string }) {
  switch (style) {
    case 1: // 옆가르마
      return <path d="M53 54 Q52 28 80 29 Q107 30 107 54 Q98 40 72 42 Q60 44 53 54 Z" fill={color} />
    case 4: // 삐죽 머리
      return (
        <path
          d="M54 52 L56 34 L64 40 L68 26 L76 36 L82 24 L88 36 L96 28 L98 40 L106 36 L106 52 Q96 42 80 42 Q64 42 54 52 Z"
          fill={color}
        />
      )
    case 6: // 일자 앞머리
      return <path d="M54 50 Q54 30 80 30 Q106 30 106 50 L106 52 L54 52 Z" fill={color} />
    case 7: // 비니
      return (
        <>
          <path d="M53 50 Q53 24 80 24 Q107 24 107 50 Z" fill={color} />
          <rect x="51" y="44" width="58" height="9" rx="4.5" fill={color} stroke="rgba(255,255,255,0.25)" />
        </>
      )
    default: // 0 바가지, 2·3·5 공통 앞머리
      return <path d="M54 54 Q53 29 80 29 Q107 29 106 54 Q100 42 80 41 Q60 42 54 54 Z" fill={color} />
  }
}
