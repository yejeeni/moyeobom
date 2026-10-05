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
