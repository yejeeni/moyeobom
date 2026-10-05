import { Modal } from './Modal'

export function GuideModal({ onClose }: { onClose: () => void }) {
  return (
    <Modal title="모여봄 열람실 안내" onClose={onClose}>
      <div className="guide">
        <p>
          <strong>가상 스터디 메이트가 함께 있어요.</strong>
          <br />
          혼자 들어와도 열람실이 비어 보이지 않도록, 서버가 만든 가상 메이트가 빈자리를 채워요. 메이트도 실제
          사람처럼 집중하고, 쉬고, 자리를 떠나요. 어느 자리가 가상인지는 따로 표시하지 않아요.
        </p>
        <p>
          <strong>캠도, 마이크도 필요 없어요.</strong>
          <br />
          내 자리에는 캐릭터와 상태만 보여요. 할 일을 골라 집중을 시작하면 내 책상 스탠드가 켜져요.
        </p>
        <p>
          <strong>집중한 시간은 할 일에 쌓여요.</strong>
          <br />
          하루를 마칠 때 &lsquo;오늘 마무리&rsquo;를 누르면 예상한 시간과 실제 걸린 시간을 비교해 볼 수 있어요.
        </p>
        <p className="muted small">
          기록은 이 브라우저에 저장된 게스트 정보로 이어져요. 브라우저 데이터를 지우면 기록을 다시 불러올 수 없어요.
        </p>
      </div>
      <div className="modal-actions">
        <button className="button primary" onClick={onClose}>
          알겠어요
        </button>
      </div>
    </Modal>
  )
}
