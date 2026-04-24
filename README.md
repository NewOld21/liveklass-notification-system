# Notification System

이 프로젝트는 다양한 도메인 이벤트에 대해 이메일과 인앱 알림을 안정적으로 처리하기 위한 백엔드 시스템이다. 핵심 목표는 단순 발송 기능이 아니라, 중복 없이 생성하고, 비동기로 처리하며, 실패를 추적하고, 서버 재시작 이후에도 복구 가능한 알림 처리 구조를 만드는 데 있다.

현재 구현 방향은 Spring Boot, JPA, PostgreSQL, Redis 기반으로 구성되어 있으며, 실제 메시지 브로커 없이도 운영 관점에서 확장 가능한 구조를 우선 설계했다.

## 프로젝트 개요

다음과 같은 이벤트가 발생했을 때 사용자에게 알림을 전달하는 시스템을 가정한다.

- 수강 신청 완료
- 결제 확정
- 강의 시작 D-1
- 취소 처리

알림 발송은 요청 스레드와 분리되어야 하며, 다음 조건을 만족해야 한다.

- 동일 이벤트에 대한 중복 발송 방지
- 일시적 장애에 대한 재시도 가능
- 실패 이력과 실패 사유 추적 가능
- 서버 재시작 이후에도 유실 없이 복구 가능
- 다중 인스턴스 환경에서도 동일 알림의 중복 처리 방지

## 인증 / 인가

이 시스템에서 인증과 인가는 핵심 도메인이 아니므로 최소 수준으로 단순화했다. 별도 OAuth 서버나 복잡한 권한 체계는 두지 않고, JWT 기반 인증만 적용하는 것을 전제로 한다.

- 요청 헤더: `Authorization: Bearer <jwt>`
- JWT에는 최소한 `userId` 클레임을 포함한다고 가정한다.
- 서버는 토큰 검증 후 `userId`를 기준으로 요청 주체를 식별한다.
- 인증 실패는 "토큰 누락, 서명 오류, 만료 토큰, 존재하지 않는 사용자"로 단순 정의한다.

인가 역시 최소 범위만 다룬다.

- 사용자는 자신의 알림만 조회할 수 있다.
- 읽음 처리는 본인 소유 알림에 대해서만 허용한다.
- 알림 생성 API는 시스템 이벤트를 대신 등록하는 내부 API 성격으로 보고, 호출 주체는 JWT로 식별하되 실제 수신자는 `recipientId`로 분리한다.

이 방식을 선택한 이유는 인증 체계 자체보다 알림 도메인의 상태 관리, 멱등성, 복구 가능성 검증에 집중하기 위해서다. 인증 수단은 간단하게 유지하되, 실제 서비스에서 가장 일반적인 방식인 JWT를 사용해 API 경계에서 사용자 식별과 소유권 판단이 가능하도록 한다.

## 기술 스택

- Java 17
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Redis
- Docker Compose
- JUnit 5
- H2

선택 이유는 다음과 같다.

- Spring Boot는 API, 스케줄링, 트랜잭션 처리를 한 프레임워크 안에서 일관되게 구성하기 좋다.
- JPA는 상태 기반 엔티티 모델링과 트랜잭션 경계 관리에 적합하다.
- PostgreSQL은 unique 제약, 인덱스, 락 전략 등 중복 방지와 동시성 제어를 다루기에 안정적이다.
- Redis는 생성 요청의 `Idempotency-Key`를 TTL과 함께 저장해 짧은 시간 동안의 중복 요청을 빠르게 차단하는 데 적합하다.
- Docker Compose를 통해 로컬 실행 환경을 단순하게 맞출 수 있다.

## 실행 방법

### 1. PostgreSQL 실행

```bash
docker compose up -d
```

기본 DB 접속 정보는 다음과 같다.

- database: `notification`
- username: `notification`
- password: `notification`
- port: `5432`

### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

Windows 환경에서는 다음 명령으로 실행할 수 있다.

```bash
gradlew.bat bootRun
```

환경 변수로 DB 연결 정보를 변경할 수 있다.

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

## 요구사항 해석 및 가정

이 시스템은 알림을 "즉시 보내는 API"가 아니라 "발송 요청을 안정적으로 접수하고 비동기로 처리하는 시스템"으로 해석했다.

핵심 가정은 다음과 같다.

- API는 발송 완료를 보장하지 않고, 요청 접수만 보장한다.
- DB에 저장된 알림은 생성 단계 검증을 통과한 정상 요청만 의미한다.
- `FAILED` 상태는 잘못된 요청이 아니라, 정상 요청이었지만 재시도 이후에도 끝내 발송에 성공하지 못한 경우만 의미한다.
- 읽음 상태는 기기별 상태가 아니라 알림 자체의 상태로 관리한다.
- 사용자 식별은 JWT 기반 인증으로 단순화하고, 토큰의 `userId` 클레임을 사용한다.
- 실제 이메일 발송은 외부 연동 대신 Mock 또는 로그 출력으로 대체하되, 채널별 구현은 분리 가능한 구조로 둔다.
- 실제 메시지 브로커는 사용하지 않지만, 운영 환경에서는 DB 큐 대신 외부 브로커로 전환 가능한 구조를 목표로 한다.

## 설계 결정과 이유

### 1. 전체 구조: DB 기반 큐 + Scheduler 기반 비동기 워커

알림 발송 요청 API는 실제 발송을 동기적으로 처리하지 않고, 먼저 알림 요청을 DB에 저장한 뒤 접수 완료를 반환한다. 이후 별도 워커가 처리 가능한 알림을 조회해 비동기로 발송을 수행한다.

이 구조를 선택한 이유는 다음과 같다.

- API 요청 스레드와 발송 처리를 분리해 응답 지연과 장애 전파를 줄일 수 있다.
- 서버 재시작 이후에도 DB에 저장된 미처리 알림을 다시 조회해 유실 없이 재처리할 수 있다.
- 재시도 횟수, 다음 재시도 시각, 실패 사유를 DB에 저장함으로써 운영 추적성과 복구 가능성을 확보할 수 있다.
- 실제 메시지 브로커 없이도 요구사항을 만족할 수 있고, 이후 Kafka 또는 RabbitMQ 기반 구조로 전환할 수 있다.

### 2. 도메인 모델 분리

#### Notification

`Notification`은 알림 요청의 현재 상태를 관리하는 핵심 엔티티다. 누구에게, 어떤 타입의 알림을, 어떤 채널로 보내는지와 함께 현재 상태, 재시도 정보, 실패 정보, 읽음 여부를 저장한다.

주요 필드는 다음과 같다.

- `recipientId`
- `type`
- `channel`
- `status`
- `dedupKey`
- `idempotencyKey`
- `payload`
- `retryCount`
- `maxRetryCount`
- `nextRetryAt`
- `lastErrorCode`
- `lastErrorMessage`
- `readAt`
- `requestedAt`
- `processedAt`

알림 시스템의 핵심은 보내기 자체보다 알림 요청을 상태 있게 관리하는 데 있다고 보고, 이 엔티티를 중심으로 설계했다.

#### NotificationDispatchHistory

`NotificationDispatchHistory`는 발송 시도 이력을 저장하는 엔티티다. 현재 상태는 `Notification`에서 관리하고, 각 시도별 성공/실패 내역은 이력 테이블에 기록한다.

이렇게 분리한 이유는 다음과 같다.

- 현재 상태와 시도 이력을 분리해 책임을 명확히 할 수 있다.
- 몇 번째 시도에서 어떤 이유로 실패했는지 운영 관점에서 추적하기 쉽다.
- 최종 상태만으로는 알 수 없는 발송 과정을 보존할 수 있다.

#### User

`User`는 알림 수신 주체를 나타내는 최소 엔티티다. 이번 범위에서는 회원가입, 비밀번호, 권한 그룹 같은 계정 관리 기능은 다루지 않고, 알림 시스템이 필요로 하는 수준의 사용자 식별 정보만 유지한다.

주요 필드는 다음과 같다.

- `id`
- `email`
- `name`
- `status`
- `createdAt`

회원 테이블을 단순하게 둔 이유는 다음과 같다.

- `recipientId`가 실제 존재하는 사용자임을 검증할 수 있다.
- 이메일 채널에서 수신 주소를 참조할 수 있다.
- 인증/인가를 단순화하더라도 최소한의 사용자 유효성 검증과 소유권 판단 기준은 유지할 수 있다.

### 3. 상태 기반 설계

알림 상태는 다음 다섯 가지로 정의한다.

- `PENDING`: 생성 완료, 아직 처리되지 않은 상태
- `PROCESSING`: 현재 워커가 처리 중인 상태
- `RETRY_WAITING`: 실패 후 다음 재시도를 기다리는 상태
- `SENT`: 최종 발송 성공 상태
- `FAILED`: 재시도 한도를 초과해 최종 실패한 상태

상태 전이 규칙은 다음과 같다.

- 알림 생성 시 `PENDING`
- 워커 선점 성공 시 `PROCESSING`
- 발송 성공 시 `SENT`
- 발송 실패 후 재시도 가능 시 `RETRY_WAITING`
- 발송 실패 후 재시도 한도 초과 시 `FAILED`

생성됨, 처리 중, 재시도 대기, 최종 성공, 최종 실패를 명확히 구분하기 위해 상태를 세분화했다.

### 4. 생성 단계 검증과 발송 단계 실패의 분리

생성 단계에서는 처리할 수 없는 요청을 미리 차단한다. 예를 들면 다음과 같다.

- 잘못된 수신자 정보
- 지원하지 않는 채널
- 필수 payload 누락
- 해당 타입과 채널에 대한 템플릿 없음

이러한 요청은 발송 실패가 아니라 생성 요청 자체를 거절해야 하는 잘못된 요청으로 본다.

반면 발송 단계에서는 다음과 같은 일시적 오류를 재시도 대상으로 본다.

- 외부 이메일 서버 일시 장애
- 네트워크 타임아웃
- 일시적인 내부 처리 오류

이 구분을 통해 DB에 저장된 알림은 모두 기본 검증을 통과한 정상 요청이라는 의미를 가지게 되고, `FAILED` 상태의 의미도 선명해진다.

### 5. 재시도 정책

재시도는 일시적 장애를 고려해 다음 정책으로 설계했다.

- 최초 발송 1회
- 실패 시 최대 3회 재시도
- 1차 재시도: 5분 후
- 2차 재시도: 10분 후
- 3차 재시도: 20분 후

즉, 총 시도 횟수는 최대 4회다.

재시도 정보는 `retryCount`, `maxRetryCount`, `nextRetryAt`으로 관리하고, 각 실패 시점마다 `lastErrorCode`, `lastErrorMessage`를 함께 기록한다.

이 정책은 즉시 최종 실패로 처리하지 않고 복구 기회를 제공하면서도, 재시도 간격을 점진적으로 늘려 불필요한 반복 호출을 줄이기 위한 선택이다.

### 6. 알림 생성 멱등성 보장

생성 단계에서는 요청 멱등성과 비즈니스 중복 방지를 서로 다른 책임으로 분리했다.

- 요청 멱등성: `POST /api/notifications` 요청의 `Idempotency-Key`를 Redis에 TTL과 함께 저장해 처리한다.
- 비즈니스 중복 방지: 같은 알림이 시스템 안에 두 번 생성되지 않도록 `dedupKey`를 DB에 저장한다.

이 분리가 필요한 이유는 두 장치가 해결하는 문제가 다르기 때문이다. `Idempotency-Key`는 네트워크 타임아웃, 중복 클릭, 동일 요청 재전송처럼 짧은 시간 안에 반복되는 같은 HTTP 요청을 빠르게 차단하는 데 적합하다. 반면 Redis 키는 TTL이 지나면 제거될 수 있으므로, 같은 비즈니스 이벤트에 대한 영속적인 중복 방지 수단으로는 충분하지 않다.

`dedupKey`는 다음 기준으로 생성한다.

- 같은 사용자
- 같은 알림 타입
- 같은 채널
- 같은 비즈니스 이벤트

예시:

```text
recipientId:type:channel:eventId
101:PAYMENT_CONFIRMED:EMAIL:payment-5001
```

즉, Redis는 요청 수준의 빠른 입구 차단을 담당하고, `dedupKey`는 비즈니스 수준의 최종 중복 방지를 담당한다.

### 7. DB Unique 제약과 중복 생성 방지

최종 정합성은 DB에서 보장한다. `dedupKey`에는 unique 제약을 두고, `idempotencyKey`는 생성 요청에서만 저장 대상으로 사용한다.

처리 순서는 다음과 같다.

1. `POST /api/notifications` 요청에 `Idempotency-Key`가 있으면 Redis에서 먼저 같은 요청인지 확인한다.
2. 이미 처리된 키라면 기존 결과를 반환한다.
3. 같은 키에 다른 요청 본문이 들어오면 잘못된 재사용으로 보고 충돌 오류를 반환한다.
4. 신규 요청이면 `dedupKey`로 비즈니스 중복 여부를 판단한다.
5. 최종적으로는 DB unique 제약이 동시 요청 상황의 마지막 방어선 역할을 한다.

이 구조를 통해 요청 수준의 중복은 Redis에서 빠르게 차단하고, 비즈니스 수준의 최종 정합성은 DB에서 보장한다.

### 8. 처리 중복 방지와 워커 선점 규칙

DB unique 제약은 중복 생성 방지에는 유효하지만, 이미 생성된 알림을 여러 워커가 동시에 처리하는 문제까지 해결하지는 못한다.

이를 방지하기 위해 워커는 다음 규칙을 따른다.

1. 처리 가능한 알림을 조회한다.
2. 해당 알림을 `PENDING` 또는 `RETRY_WAITING`에서 `PROCESSING`으로 원자적으로 전이하는 데 성공한 경우에만 실제 발송을 수행한다.

즉, 조회 자체가 아니라 상태 전이에 성공한 워커만 발송 권한을 가진다.

이 방식은 상태 기반 원자적 갱신 또는 DB 락 전략으로 구현할 수 있으며, 다중 인스턴스 환경에서도 동일 알림이 동시에 여러 번 발송되지 않도록 하기 위한 설계다.

### 9. 읽음 처리 일관성과 멱등성

읽음 처리는 기기별 상태가 아니라 알림 자체의 상태로 관리한다.

- `readAt == null`: 안 읽음
- `readAt != null`: 읽음

한 기기에서 읽은 결과가 다른 기기에도 동일하게 반영되어야 한다는 점을 기준으로 삼았다. 또한 읽음 처리 API는 멱등하게 동작하도록 설계한다.

- 이미 읽은 알림에 대해 다시 읽음 요청이 들어와도 상태를 변경하지 않는다.
- 최초 읽음 시각은 보존한다.

이렇게 하면 여러 기기에서 동시에 읽음 요청이 들어오더라도 상태 일관성을 유지할 수 있다.

### 10. 처리 대상 조회 기준

워커는 다음 조건을 만족하는 알림만 처리 대상으로 본다.

- `PENDING`
- `RETRY_WAITING` 이면서 `nextRetryAt <= now`

아직 한 번도 처리되지 않은 알림과, 재시도 대기 시간이 지난 알림만 발송 대상으로 삼기 위한 기준이다.

### 11. 서버 재시작 이후 복구 가능 구조

알림 요청, 상태, 실패 정보, 재시도 정보가 모두 DB에 저장되므로 서버가 재시작되더라도 미처리 알림은 유실되지 않는다.

애플리케이션이 다시 실행되면 워커는 DB를 기준으로 다음 대상을 재조회할 수 있다.

- `PENDING`
- `RETRY_WAITING`
- 장시간 `PROCESSING` 상태로 남아 있는 stale 데이터

DB를 source of truth로 사용해 복구 가능한 비동기 처리 구조를 만드는 데 초점을 맞췄다.

### 12. 장시간 PROCESSING 상태 복구

워커 처리 중 서버가 비정상 종료되면 알림이 `PROCESSING` 상태에 머무른 채 더 이상 진행되지 않을 수 있다. 이를 방지하기 위해 `PROCESSING` 상태가 일정 시간 이상 유지되면 stale 상태로 간주하고 복구 대상으로 본다.

예시 정책은 다음과 같다.

- `PROCESSING` 상태가 10분 이상 지속되면 비정상 처리 중단으로 판단
- 별도 복구 스케줄러가 이를 다시 처리 가능한 상태로 전환

이 규칙은 처리 도중 장애가 발생하더라도 알림이 영구 정체되지 않도록 하기 위한 복구 장치다.

### 13. 채널별 처리 추상화

알림 채널은 `EMAIL`, `IN_APP` 두 가지로 정의하고, 실제 발송은 채널별 sender 컴포넌트가 담당하도록 분리한다.

예시 구조:

- `NotificationSender`
- `EmailNotificationSender`
- `InAppNotificationSender`

이렇게 분리하면 채널별 발송 방식 차이를 감추고 공통 처리 흐름을 유지할 수 있고, 신규 채널 추가 시 확장 비용도 줄일 수 있다. 테스트 시에는 실제 외부 시스템 대신 Mock Sender로 대체하기 쉽다.

### 14. 템플릿 존재 여부를 생성 단계에서 검증한 이유

알림 타입별 템플릿은 발송 시점이 아니라 생성 시점에 존재 여부를 검증한다.

이렇게 하면 템플릿이 없는 알림이 비동기 처리 대상에 적재되지 않고, `PENDING` 상태 자체가 처리 가능한 정상 요청이라는 의미를 유지할 수 있다.

### 15. 인덱스 및 조회 성능 고려

워커 조회와 사용자 알림 목록 조회가 자주 발생하므로 다음 인덱스를 우선 고려한다.

- `(status, next_retry_at)`
- `(recipient_id, created_at desc)`

첫 번째는 워커가 처리 가능한 대상을 빠르게 찾기 위한 인덱스고, 두 번째는 사용자 기준 알림 목록을 최신순으로 조회하기 위한 인덱스다.

### 16. 설계 요약

이 시스템은 다음 네 가지 원칙을 중심으로 설계했다.

1. 알림 생성은 멱등해야 한다.
2. 알림 발송은 중복 처리되면 안 된다.
3. 읽음 상태는 알림 기준으로 일관되게 관리되어야 한다.
4. 비동기 처리 구조는 복구 가능해야 한다.

결과적으로 이 프로젝트는 단순 발송 기능이 아니라, 중복 없이 생성하고, 실패를 관리하며, 복구 가능한 알림 시스템을 만드는 데 초점을 맞춘다.

## API 목록 및 예시

### 1. 알림 발송 요청 등록

`POST /api/notifications`

요청 헤더:

```http
Authorization: Bearer <jwt>
Idempotency-Key: notification-create-001
```

`Idempotency-Key`는 생성 요청 중복 재전송 방지를 위해 `POST /api/notifications`에서만 사용한다.

요청 예시:

```json
{
  "recipientId": 101,
  "type": "PAYMENT_CONFIRMED",
  "channel": "EMAIL",
  "payload": {
    "eventId": "payment-5001",
    "courseId": 301,
    "paymentId": 5001
  }
}
```

응답 예시:

```json
{
  "notificationId": 1,
  "status": "PENDING",
  "deduplicated": false,
  "requestedAt": "2026-04-24T10:00:00"
}
```

### 2. 알림 상태 조회

`GET /api/notifications/{notificationId}`

요청 헤더:

```http
Authorization: Bearer <jwt>
```

응답 예시:

```json
{
  "notificationId": 1,
  "recipientId": 101,
  "type": "PAYMENT_CONFIRMED",
  "channel": "EMAIL",
  "status": "RETRY_WAITING",
  "retryCount": 1,
  "nextRetryAt": "2026-04-24T10:05:00",
  "lastErrorCode": "EMAIL_TEMPORARY_FAILURE",
  "lastErrorMessage": "mock smtp timeout"
}
```

### 3. 사용자 알림 목록 조회

`GET /api/users/{recipientId}/notifications?read=false`

요청 헤더:

```http
Authorization: Bearer <jwt>
```

응답 예시:

```json
[
  {
    "notificationId": 10,
    "type": "COURSE_START_REMINDER",
    "channel": "IN_APP",
    "status": "SENT",
    "read": false,
    "requestedAt": "2026-04-24T09:30:00"
  },
  {
    "notificationId": 9,
    "type": "PAYMENT_CONFIRMED",
    "channel": "EMAIL",
    "status": "SENT",
    "read": true,
    "requestedAt": "2026-04-24T08:10:00"
  }
]
```

### 4. 읽음 처리

`PATCH /api/notifications/{notificationId}/read`

요청 헤더:

```http
Authorization: Bearer <jwt>
```

응답 예시:

```json
{
  "notificationId": 10,
  "read": true,
  "readAt": "2026-04-24T11:00:00"
}
```

## 데이터 모델 설명

### User

알림 수신 대상이 되는 회원 테이블이다. 인증/인가를 단순화하는 대신, 최소한의 사용자 식별과 수신자 유효성 검증은 가능하도록 별도 테이블로 둔다.

주요 컬럼 예시는 다음과 같다.

- `id`
- `email`
- `name`
- `status`
- `created_at`

역할은 다음과 같다.

- 알림 생성 시 `recipient_id` 유효성 검증
- 이메일 채널 사용 시 기본 수신 주소 참조
- 알림 조회 및 읽음 처리 시 소유권 확인

### Notification

알림 요청의 현재 상태를 저장하는 메인 테이블이다.

주요 컬럼 예시는 다음과 같다.

- `id`
- `recipient_id`
- `type`
- `channel`
- `status`
- `dedup_key`
- `idempotency_key`
- `payload`
- `retry_count`
- `max_retry_count`
- `next_retry_at`
- `last_error_code`
- `last_error_message`
- `read_at`
- `requested_at`
- `processed_at`
- `created_at`
- `updated_at`

핵심 제약:

- `recipient_id` foreign key -> `users.id`
- `dedup_key` unique
- `idempotency_key` unique nullable
- `status`, `next_retry_at` 인덱스
- `recipient_id`, `created_at` 인덱스

`idempotency_key`는 생성 요청인 `POST /api/notifications`에서만 저장 대상으로 사용하며, 요청 수준의 빠른 중복 차단은 Redis TTL 기반 키 저장소를 기준으로 처리한다.

### NotificationDispatchHistory

발송 시도 이력을 저장하는 테이블이다.

주요 컬럼 예시는 다음과 같다.

- `id`
- `notification_id`
- `attempt_no`
- `status`
- `error_code`
- `error_message`
- `dispatched_at`

이 테이블은 운영 중 실패 패턴을 분석하거나, 특정 알림이 어떤 과정으로 최종 상태에 도달했는지 추적하는 데 사용한다.

## 비동기 처리 구조 및 재시도 정책

처리 흐름은 다음과 같다.

1. API가 알림 요청을 수신한다.
2. 생성 단계 검증을 수행한다.
3. `POST /api/notifications` 요청에 `Idempotency-Key`가 있으면 Redis에서 동일 요청 여부를 먼저 확인한다.
4. `dedupKey`를 생성하고 비즈니스 기준 중복 여부를 확인한다.
5. 신규 요청이면 `PENDING` 상태로 저장한다.
6. 스케줄러 기반 워커가 처리 가능한 알림을 주기적으로 조회한다.
7. 워커가 상태를 `PROCESSING`으로 선점한 뒤 발송을 시도한다.
8. 성공하면 `SENT`, 실패하면 `RETRY_WAITING` 또는 `FAILED`로 전이한다.
9. 각 시도 결과는 `NotificationDispatchHistory`에 기록한다.

복구 전략은 다음과 같다.

- 서버 재시작 이후 DB에 남아 있는 `PENDING`, `RETRY_WAITING` 데이터를 다시 처리한다.
- 장시간 `PROCESSING` 상태에 머문 알림은 stale 데이터로 판단하고 복구 스케줄러가 재처리 가능 상태로 되돌린다.
- 다중 인스턴스 환경에서는 상태 전이에 성공한 워커만 실제 발송을 수행한다.

## 미구현 / 제약사항

- 현재 저장소는 기본 스캐폴드 상태이며, 도메인 모델과 API 구현은 진행 중이다.
- 실제 이메일 서버 연동은 포함하지 않고 Mock 또는 로그 출력 방식으로 대체할 예정이다.
- 실제 메시지 브로커는 사용하지 않으며, DB 기반 큐와 스케줄러 기반 워커로 동작하도록 설계했다.
- 읽음 처리, 예약 발송, 수동 재시도 등 선택 기능은 우선순위에 따라 순차적으로 확장할 계획이다.

## 테스트 실행 방법

```bash
./gradlew test
```

Windows 환경:

```bash
gradlew.bat test
```

테스트는 H2 기반으로 실행하는 것을 기준으로 한다.

## AI 활용 범위

AI 도구는 다음 범위에서 활용했다.

- 요구사항 해석 초안 정리
- 설계 문서 구조화
- README 문장 다듬기

최종 설계 판단, 구조 선택, 상태 모델 정의, 멱등성 및 재시도 정책 정리는 직접 검토하고 수정했다. 문서에 작성된 내용은 그대로 복사한 결과가 아니라 현재 프로젝트 방향에 맞게 재구성한 내용이다.
