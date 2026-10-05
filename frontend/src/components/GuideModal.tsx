import { Icon, type IconName } from './Icon'
import { Modal } from './Modal'

const ITEMS: { icon: IconName; title: string; body: string }[] = [
  {
    icon: 'users',
    title: '가상 스터디 메이트가 함께 있어요',
    body: '혼자 들어와도 열람실이 비어 보이지 않도록, 서버가 만든 가상 메이트가 빈자리를 채워요. 메이트도 실제 사람처럼 집중하고, 쉬고, 자리를 떠나요. 어느 자리가 가상인지는 따로 표시하지 않아요.',
  },
  {
    icon: 'videoOff',
    title: '캠도, 마이크도 필요 없어요',
    body: '내 자리에는 캐릭터와 상태만 보여요. 할 일을 골라 집중을 시작하면 내 책상 스탠드가 켜져요.',
  },
  {
    icon: 'clock',
    title: '집중한 시간은 할 일에 쌓여요',
    body: '하루를 마칠 때 ‘오늘 마무리’를 누르면 예상한 시간과 실제 걸린 시간을 비교해 볼 수 있어요.',
  },
  {
    icon: 'motion',
    title: '캐릭터가 움직이지 않나요?',
    body: '운영체제에서 애니메이션을 꺼 두면 캐릭터도 멈춰 있어요. Windows는 설정 → 접근성 → 시각 효과 → 애니메이션 효과, Mac은 시스템 설정 → 손쉬운 사용 → 디스플레이 → 동작 줄이기에서 바꿀 수 있어요.',
  },
]

export function GuideModal({ onClose }: { onClose: () => void }) {
  return (
    <Modal title="모여봄 열람실 안내" onClose={onClose}>
      <ul className="guide-list">
        {ITEMS.map((item) => (
          <li key={item.title}>
            <span className="guide-icon">
              <Icon name={item.icon} size={20} />
            </span>
            <div>
              <strong>{item.title}</strong>
              <p>{item.body}</p>
            </div>
          </li>
        ))}
      </ul>
      <p className="guide-note">
        기록은 이 브라우저에 저장된 게스트 정보로 이어져요. 브라우저 데이터를 지우면 기록을 다시 불러올 수 없어요.
      </p>
      <button className="button primary modal-cta" onClick={onClose}>
        알겠어요
      </button>
    </Modal>
  )
}
