/**
 * 열람실 칸 배경 이미지 목록. 파일은 public/backgrounds/ 에 넣고 여기에 경로를 한 줄씩 추가한다.
 *
 * - 칸을 꽉 채우도록 잘려서 보인다(가운데 기준). 칸 비율은 화면에 따라 16:9 ~ 16:11 사이라
 *   1280x800(16:10) 정도로 만들고, 중요한 부분은 가운데에 두면 안전하다.
 * - 여러 장이면 자리 번호 순서대로 돌아가며 쓴다.
 * - 목록이 비어 있으면 CSS로 그린 기본 방(벽·창문·스탠드·책상)을 보여 준다.
 */
export const SEAT_BACKGROUNDS: string[] = ['/backgrounds/default.webp']

/** 자리 번호에 맞는 배경 이미지. 없으면 null */
export function backgroundFor(seatNo: number): string | null {
  if (SEAT_BACKGROUNDS.length === 0) return null
  return SEAT_BACKGROUNDS[(seatNo - 1) % SEAT_BACKGROUNDS.length]
}
