interface Props {
  src: string
  /** 움짤의 첫 장면. 있으면 운영체제의 '움직임 줄이기'를 켠 사람에게 이 그림을 대신 보여 준다 */
  still?: string
  className: string
}

/** 캐릭터 그림과 배경. 움짤(GIF, 움직이는 WebP, APNG)도 그대로 재생된다 */
export function MotionImage({ src, still, className }: Props) {
  const image = <img className={className} src={src} alt="" draggable={false} />
  if (!still) return image
  return (
    <picture>
      <source media="(prefers-reduced-motion: reduce)" srcSet={still} />
      {image}
    </picture>
  )
}
