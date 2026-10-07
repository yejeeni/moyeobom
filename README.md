# 모여봄

가상 스터디 메이트로 채워진 온라인 열람실에서, 할 일 단위로 집중하고 하루를 정산하는 PC 웹 서비스의 백엔드.

- 기획·설계 문서: [`docs/`](docs)

## 기술 스택

Java 21 · Spring Boot 4.1 · Gradle · Spring Data JPA · Flyway · MySQL 8.4 · Spring WebSocket(STOMP) · springdoc-openapi · Testcontainers

## 로컬 실행

```bash
cp .env.example .env        # 처음 한 번, 비밀번호 수정
docker compose up -d        # MySQL 8.4
./gradlew bootRun           # 기본 프로필 local, .env를 읽는다
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- 헬스 체크: http://localhost:8080/actuator/health
- PC에서 이미 MySQL이 3306을 쓰고 있으면 `.env`의 `DB_PORT`를 3307 등으로 바꾼다.

## 프론트엔드 연동 요약

1. `POST /api/v1/guests`로 받은 `guestId`를 브라우저에 저장하고, 모든 요청에 `X-Guest-Id` 헤더로 보낸다.
2. `POST /api/v1/sprints` → `POST /api/v1/rooms/enter`로 `roomId`를 받는다.
3. `/ws`에 STOMP로 연결한다(CONNECT 헤더 `X-Guest-Id`, heart-beat `10000,10000`).
4. `/topic/rooms/{roomId}`를 먼저 구독하고, 이어서 `/user/queue/room-snapshot`, `/user/queue/notices`를 구독한다.
   - 스냅샷 큐 구독 직후 `ROOM_SNAPSHOT`이 한 번 온다. 방이 없으면 `ROOM_NOT_FOUND`가 오며, 이때는 `POST /rooms/enter`를 다시 부른다.
5. 모든 동작(집중, 휴식, 완료 등)은 REST로 보내고, 화면 변화는 이벤트로 받는다. 경과 시간은 `since`와 스냅샷의 `serverTime`으로 클라이언트가 계산한다.
6. 오늘 마무리를 누르면 집중 중일 때 `POST /focus/stop`(`STOPPED`)을 먼저 보내고 `GET /sprints/current/review`를 조회한다.

## 프론트엔드 실행

React + Vite + TypeScript. 개발 서버가 `/api`와 `/ws`를 백엔드(8080)로 넘겨 준다.

```bash
cd frontend
npm install                 # 처음 한 번
npm run dev                 # http://localhost:5173
```

화면: 첫 화면(`/`, 왼쪽 소개 + 오른쪽 스프린트 계획) → 열람실(`/room`) → 회고(`/review`). 창 폭이 760px 이하이면 컴팩트 모드로 바뀐다.

## 테스트

```bash
./gradlew test              # Docker가 실행 중이어야 한다 (Testcontainers MySQL)
```

## 프로필

| 프로필 | 용도 | DB |
| --- | --- | --- |
| local | 로컬 개발 (기본값) | Docker Compose MySQL, `.env` |
| test | 테스트 | Testcontainers MySQL |
| prod | 운영 | 환경 변수 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |

## 패키지 구조

```
com.moyeobom
├── guest      게스트 발급, 설정
├── sprint     스프린트, 회고, 이월
├── task       할 일
├── focus      집중 세션, 연결 끊김 처리
├── room       열람실, 자리, WebSocket 이벤트 발행
├── mate       가상 메이트 생성과 스케줄링
└── common     설정, 오류 처리, 시간(Clock), 게스트 인증 필터
```

각 기능 패키지 안은 `controller`, `service`, `domain`, `repository`, `dto`로 나눈다.
