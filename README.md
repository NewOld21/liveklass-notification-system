# Notification System

이 프로젝트는 다양한 도메인 이벤트에 대해 이메일과 인앱 알림을 안정적으로 처리하기 위한 백엔드 시스템이다. 핵심 목표는 단순 발송 기능이 아니라, **중복 없이 생성하고, 비동기로 처리하며, 실패를 추적하고, 서버 재시작 이후에도 복구 가능한 알림 처리 구조**를 만드는 데 있다.

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
- 인증 실패는 `토큰 누락`, `서명 오류`, `만료 토큰`, `존재하지 않는 사용자`로 단순 정의한다.

인가 역시 최소 범위만 다룬다.

- 사용자는 자신의 알림만 조회할 수 있다.
- 읽음 처리는 본인 소유 알림에 대해서만 허용한다.
- 알림 생성 API는 시스템 이벤트를 대신 등록하는 내부 API 성격으로 보고, 호출 주체는 JWT로 식별하되 실제 수신자는 `recipientId`로 분리한다.

이 방식을 선택한 이유는 인증 체계 자체보다 알림 도메인의 상태 관리, 멱등성, 복구 가능성 검증에 집중하기 위해서다.

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
- Redis는 서버가 요청 데이터로 생성한 멱등 판단 키를 TTL과 함께 저장해 짧은 시간 동안의 중복 요청을 빠르게 차단하는 데 적합하다.
- Docker Compose를 통해 로컬 실행 환경을 단순하게 맞출 수 있다.

## 실행 방법

### 1. PostgreSQL / Redis 실행

```bash
docker compose up -d
```

`docker compose up -d`를 실행하면 PostgreSQL과 Redis가 함께 실행된다.

기본 PostgreSQL 접속 정보는 다음과 같다.

- database: `notification`
- username: `notification`
- password: `notification`
- port: `5432`

기본 Redis 접속 정보는 다음과 같다.

- host: `localhost`
- port: `6379`
- password: 없음

Redis는 알림 생성 멱등 판단 키와 TTL 기반 중복 요청 제어를 위해 사용한다.

### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

Windows 환경에서는 다음 명령으로 실행할 수 있다.

```bash
gradlew.bat bootRun
```

환경 변수로 DB 및 Redis 연결 정보를 변경할 수 있다.

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATA_REDIS_HOST`
- `SPRING_DATA_REDIS_PORT`
- `SPRING_DATA_REDIS_PASSWORD`

## 요구사항 해석 및 가정

이 시스템은 알림을 즉시 보내는 API가 아니라, 발송 요청을 안정적으로 접수하고 비동기로 처리하는 시스템으로 해석했다.

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

### 2. 생성 단계 검증과 발송 단계 실패의 분리

생성 단계에서는 처리할 수 없는 요청을 미리 차단한다.

예를 들면 다음과 같다.

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

### 3. 알림 생성 멱등성 보장

생성 단계에서는 요청 멱등성과 비즈니스 중복 방지를 서로 다른 책임으로 분리했다.

- 요청 멱등성: 서버가 요청 데이터의 핵심 값으로 멱등 판단 키를 생성하고, Redis에 TTL과 함께 저장해 처리한다.
- 비즈니스 중복 방지: 같은 알림이 시스템 안에 두 번 생성되지 않도록 `dedupKey`를 DB에 저장한다.

이 분리가 필요한 이유는 두 장치가 해결하는 문제가 다르기 때문이다.

서버가 생성한 멱등 판단 키는 네트워크 재시도, 중복 클릭, 동일 요청 재전송처럼 짧은 시간 안에 반복되는 같은 요청을 빠르게 차단하는 데 적합하다.
반면 Redis 키는 TTL이 지나면 제거될 수 있으므로, 같은 비즈니스 이벤트에 대한 영속적인 중복 방지 수단으로는 충분하지 않다.

멱등 판단 키와 `dedupKey`는 모두 아래 기준을 바탕으로 생성한다.

```text
recipientId:type:channel:eventId
101:PAYMENT_CONFIRMED:EMAIL:payment-5001
```

즉, Redis는 요청 수준의 빠른 입구 차단을 담당하고, `dedupKey`는 비즈니스 수준의 최종 중복 방지를 담당한다.

### 4. DB Unique 제약과 중복 생성 방지

최종 정합성은 DB에서 보장한다. `dedupKey`에는 unique 제약을 둔다.

처리 순서는 다음과 같다.

1. 서버가 요청 데이터로 멱등 판단 키를 생성한다.
2. Redis에서 같은 요청인지 먼저 확인한다.
3. 이미 처리 완료된 키라면 기존 결과를 반환한다.
4. 이미 처리 중인 키라면 중복 요청으로 간주하고 추가 생성을 막는다.
5. 신규 요청이면 `dedupKey`로 비즈니스 중복 여부를 판단한다.
6. 최종적으로는 DB unique 제약이 동시 요청 상황의 마지막 방어선 역할을 한다.

### 5. 상태 기반 설계

알림 상태는 다음 다섯 가지로 정의한다.

<table>
  <tr><th>Value</th><th>의미</th></tr>
  <tr><td><code>PENDING</code></td><td>생성 완료, 아직 처리되지 않은 상태</td></tr>
  <tr><td><code>PROCESSING</code></td><td>현재 워커가 처리 중인 상태</td></tr>
  <tr><td><code>RETRY_WAITING</code></td><td>실패 후 다음 재시도를 기다리는 상태</td></tr>
  <tr><td><code>SENT</code></td><td>최종 발송 성공 상태</td></tr>
  <tr><td><code>FAILED</code></td><td>재시도 한도를 초과한 최종 실패 상태</td></tr>
</table>

상태 전이 규칙은 다음과 같다.

- 알림 생성 시 `PENDING`
- 워커 선점 성공 시 `PROCESSING`
- 발송 성공 시 `SENT`
- 발송 실패 후 재시도 가능 시 `RETRY_WAITING`
- 발송 실패 후 재시도 한도 초과 시 `FAILED`

### 6. 재시도 정책

재시도는 일시적 장애를 고려해 다음 정책으로 설계했다.

- 최초 발송 1회
- 실패 시 최대 3회 재시도
- 1차 재시도: 5분 후
- 2차 재시도: 10분 후
- 3차 재시도: 20분 후

즉, 총 시도 횟수는 최대 4회다.

### 7. 처리 중복 방지와 워커 선점 규칙

DB unique 제약은 중복 생성 방지에는 유효하지만, 이미 생성된 알림을 여러 워커가 동시에 처리하는 문제까지 해결하지는 못한다.

이를 방지하기 위해 워커는 다음 규칙을 따른다.

- 처리 가능한 알림을 조회한다.
- 해당 알림을 `PENDING` 또는 `RETRY_WAITING`에서 `PROCESSING`으로 원자적으로 전이하는 데 성공한 경우에만 실제 발송을 수행한다.

즉, 조회 자체가 아니라 상태 전이에 성공한 워커만 발송 권한을 가진다.

### 8. 읽음 처리 일관성과 멱등성

읽음 처리는 기기별 상태가 아니라 알림 자체의 상태로 관리한다.

- `readAt == null`: 안 읽음
- `readAt != null`: 읽음

또한 읽음 처리 API는 멱등하게 동작하도록 설계한다.

- 이미 읽은 알림에 대해 다시 읽음 요청이 들어와도 상태를 변경하지 않는다.
- 최초 읽음 시각은 보존한다.

### 9. 서버 재시작 이후 복구 가능 구조

알림 요청, 상태, 실패 정보, 재시도 정보가 모두 DB에 저장되므로 서버가 재시작되더라도 미처리 알림은 유실되지 않는다.

애플리케이션이 다시 실행되면 워커는 DB를 기준으로 다음 대상을 재조회할 수 있다.

- `PENDING`
- `RETRY_WAITING`
- 장시간 `PROCESSING` 상태로 남아 있는 stale 데이터

### 10. 장시간 PROCESSING 상태 복구

워커 처리 중 서버가 비정상 종료되면 알림이 `PROCESSING` 상태에 머무른 채 더 이상 진행되지 않을 수 있다. 이를 방지하기 위해 `PROCESSING` 상태가 일정 시간 이상 유지되면 stale 상태로 간주하고 복구 대상으로 본다.

예시 정책은 다음과 같다.

- `PROCESSING` 상태가 10분 이상 지속되면 비정상 처리 중단으로 판단
- 별도 복구 스케줄러가 이를 다시 처리 가능한 상태로 전환

### 11. 채널별 처리 추상화

알림 채널은 `EMAIL`, `IN_APP` 두 가지로 정의하고, 실제 발송은 채널별 sender 컴포넌트가 담당하도록 분리한다.

예시 구조:

```text
NotificationSender
EmailNotificationSender
InAppNotificationSender
```

### 12. 템플릿 존재 여부를 생성 단계에서 검증한 이유

알림 타입별 템플릿은 발송 시점이 아니라 생성 시점에 존재 여부를 검증한다.

이렇게 하면 템플릿이 없는 알림이 비동기 처리 대상에 적재되지 않고, `PENDING` 상태 자체가 처리 가능한 정상 요청이라는 의미를 유지할 수 있다.

## ERD

이 시스템의 영속 데이터 모델은 `users`, `notification`, `notification_dispatch_history`, `notification_template`를 중심으로 구성했다.
요청 수준의 멱등성 정보는 Redis에 저장하므로 ERD에는 포함하지 않았고, 대신 비즈니스 수준의 중복 방지는 `notification.dedup_key`와 DB unique 제약으로 표현했다.

<p align="center">
  <img src="./docs/images/erd.png" alt="Notification System ERD" width="900" />
</p>

## Sequence Diagram - Notification Creation

알림 생성 단계에서는 서버가 요청 데이터로 멱등 판단 키를 생성하고, Redis를 이용해 동일 요청 여부를 먼저 확인한다. 이후 템플릿과 요청 유효성을 검증하고, 비즈니스 기준 중복 여부를 확인한 뒤 `PENDING` 상태로 저장한다.

<p align="center">
  <img src="./docs/images/sequence-notification-create.png" alt="Notification Creation Flow" width="900" />
</p>

## Sequence Diagram - Async Notification Dispatch

저장된 알림은 스케줄러 기반 워커가 비동기로 처리한다. 워커는 `PENDING` 또는 재시도 시각이 지난 `RETRY_WAITING` 알림을 조회한 뒤, 상태 선점에 성공한 경우에만 실제 발송을 수행한다. 발송 성공 시 `SENT`, 실패 시 재시도 정책에 따라 `RETRY_WAITING` 또는 `FAILED`로 전이된다.

<p align="center">
  <img src="./docs/images/sequence-notification-dispatch.png" alt="Async Notification Dispatch Flow" width="900" />
</p>

## Enum 명세

### `user_status`

<table>
  <tr><th>Value</th><th>의미</th></tr>
  <tr><td><code>ACTIVE</code></td><td>정상 사용 가능한 상태</td></tr>
  <tr><td><code>INACTIVE</code></td><td>비활성 상태</td></tr>
  <tr><td><code>DELETED</code></td><td>삭제 처리된 상태</td></tr>
</table>

### `notification_channel`

<table>
  <tr><th>Value</th><th>의미</th></tr>
  <tr><td><code>EMAIL</code></td><td>이메일 발송 채널</td></tr>
  <tr><td><code>IN_APP</code></td><td>인앱 알림 채널</td></tr>
</table>

### `notification_type`

<table>
  <tr><th>Value</th><th>의미</th></tr>
  <tr><td><code>COURSE_APPLIED</code></td><td>수강 신청 완료 알림</td></tr>
  <tr><td><code>PAYMENT_CONFIRMED</code></td><td>결제 확정 알림</td></tr>
  <tr><td><code>COURSE_START_REMINDER</code></td><td>강의 시작 전 알림</td></tr>
  <tr><td><code>COURSE_CANCELLED</code></td><td>강의/신청 취소 알림</td></tr>
</table>

### `notification_status`

<table>
  <tr><th>Value</th><th>의미</th></tr>
  <tr><td><code>PENDING</code></td><td>생성 완료, 아직 처리되지 않은 상태</td></tr>
  <tr><td><code>PROCESSING</code></td><td>현재 워커가 처리 중인 상태</td></tr>
  <tr><td><code>RETRY_WAITING</code></td><td>실패 후 다음 재시도를 기다리는 상태</td></tr>
  <tr><td><code>SENT</code></td><td>최종 발송 성공 상태</td></tr>
  <tr><td><code>FAILED</code></td><td>재시도 한도를 초과한 최종 실패 상태</td></tr>
</table>

### `dispatch_status`

<table>
  <tr><th>Value</th><th>의미</th></tr>
  <tr><td><code>SUCCESS</code></td><td>발송 시도 성공</td></tr>
  <tr><td><code>FAILED</code></td><td>발송 시도 실패</td></tr>
</table>

## 테이블 명세서

### 1) `users`

테이블 개요

- 알림 수신 대상이 되는 회원 테이블
- 최소한의 사용자 식별과 수신자 유효성 검증을 위한 기준 데이터

#### 컬럼 명세

<table>
  <tr><th>컬럼명</th><th>타입</th><th>PK</th><th>NULL</th><th>기본값</th><th>설명</th></tr>
  <tr><td><code>id</code></td><td><code>bigint</code></td><td>Y</td><td>N</td><td><code>increment</code></td><td>사용자 ID</td></tr>
  <tr><td><code>email</code></td><td><code>varchar(255)</code></td><td></td><td>N</td><td></td><td>이메일(Unique)</td></tr>
  <tr><td><code>name</code></td><td><code>varchar(100)</code></td><td></td><td>N</td><td></td><td>사용자 이름</td></tr>
  <tr><td><code>status</code></td><td><code>user_status</code></td><td></td><td>N</td><td><code>ACTIVE</code></td><td>사용자 상태</td></tr>
  <tr><td><code>created_at</code></td><td><code>timestamp</code></td><td></td><td>N</td><td><code>now()</code></td><td>생성 시각</td></tr>
</table>

#### 역할

- 알림 생성 시 `recipient_id` 유효성 검증
- 이메일 채널 사용 시 기본 수신 주소 참조
- 알림 조회 및 읽음 처리 시 소유권 확인

### 2) `notification`

테이블 개요

- 알림 요청의 현재 상태를 저장하는 메인 테이블
- 비즈니스 중복 방지, 상태 관리, 재시도 정보를 담당

#### 컬럼 명세

<table>
  <tr><th>컬럼명</th><th>타입</th><th>PK</th><th>NULL</th><th>기본값</th><th>설명</th></tr>
  <tr><td><code>id</code></td><td><code>bigint</code></td><td>Y</td><td>N</td><td><code>increment</code></td><td>알림 ID</td></tr>
  <tr><td><code>recipient_id</code></td><td><code>bigint</code></td><td></td><td>N</td><td></td><td>수신자 ID (<code>users.id</code>)</td></tr>
  <tr><td><code>type</code></td><td><code>notification_type</code></td><td></td><td>N</td><td></td><td>알림 타입</td></tr>
  <tr><td><code>channel</code></td><td><code>notification_channel</code></td><td></td><td>N</td><td></td><td>알림 채널</td></tr>
  <tr><td><code>status</code></td><td><code>notification_status</code></td><td></td><td>N</td><td><code>PENDING</code></td><td>알림 상태</td></tr>
  <tr><td><code>dedup_key</code></td><td><code>varchar(255)</code></td><td></td><td>N</td><td></td><td>비즈니스 중복 방지 키(Unique)</td></tr>
  <tr><td><code>payload</code></td><td><code>json</code></td><td></td><td>N</td><td></td><td>참조 데이터(eventId, courseId 등)</td></tr>
  <tr><td><code>retry_count</code></td><td><code>int</code></td><td></td><td>N</td><td><code>0</code></td><td>현재까지 재시도 횟수</td></tr>
  <tr><td><code>max_retry_count</code></td><td><code>int</code></td><td></td><td>N</td><td><code>3</code></td><td>최대 재시도 횟수</td></tr>
  <tr><td><code>next_retry_at</code></td><td><code>timestamp</code></td><td></td><td>Y</td><td></td><td>다음 재시도 예정 시각</td></tr>
  <tr><td><code>last_error_code</code></td><td><code>varchar(100)</code></td><td></td><td>Y</td><td></td><td>마지막 실패 코드</td></tr>
  <tr><td><code>last_error_message</code></td><td><code>varchar(500)</code></td><td></td><td>Y</td><td></td><td>마지막 실패 메시지</td></tr>
  <tr><td><code>requested_at</code></td><td><code>timestamp</code></td><td></td><td>N</td><td><code>now()</code></td><td>요청 접수 시각</td></tr>
  <tr><td><code>processed_at</code></td><td><code>timestamp</code></td><td></td><td>Y</td><td></td><td>최종 성공/실패 처리 시각</td></tr>
  <tr><td><code>read_at</code></td><td><code>timestamp</code></td><td></td><td>Y</td><td></td><td>읽음 처리 시각</td></tr>
  <tr><td><code>created_at</code></td><td><code>timestamp</code></td><td></td><td>N</td><td><code>now()</code></td><td>생성 시각</td></tr>
  <tr><td><code>updated_at</code></td><td><code>timestamp</code></td><td></td><td>N</td><td><code>now()</code></td><td>수정 시각</td></tr>
</table>

#### 제약사항 / 인덱스

<table>
  <tr><th>항목</th><th>내용</th></tr>
  <tr><td><code>FK</code></td><td><code>recipient_id -> users.id</code></td></tr>
  <tr><td><code>UNIQUE</code></td><td><code>dedup_key</code></td></tr>
  <tr><td><code>INDEX</code></td><td><code>(status, next_retry_at)</code></td></tr>
  <tr><td><code>INDEX</code></td><td><code>(recipient_id, created_at desc)</code></td></tr>
</table>

#### 비고

- 요청 수준의 멱등성 정보는 DB에 저장하지 않고 Redis TTL 기반 저장소에서 관리한다.
- 이 테이블은 비즈니스 수준의 최종 중복 방지와 상태 관리를 담당한다.

### 3) `notification_dispatch_history`

테이블 개요

- 발송 시도 이력을 저장하는 테이블
- 특정 알림이 몇 번째 시도에서 어떤 이유로 실패/성공했는지 추적하기 위한 용도

#### 컬럼 명세

<table>
  <tr><th>컬럼명</th><th>타입</th><th>PK</th><th>NULL</th><th>기본값</th><th>설명</th></tr>
  <tr><td><code>id</code></td><td><code>bigint</code></td><td>Y</td><td>N</td><td><code>increment</code></td><td>이력 ID</td></tr>
  <tr><td><code>notification_id</code></td><td><code>bigint</code></td><td></td><td>N</td><td></td><td>알림 ID (<code>notification.id</code>)</td></tr>
  <tr><td><code>attempt</code></td><td><code>int</code></td><td></td><td>N</td><td></td><td>발송 시도 순번</td></tr>
  <tr><td><code>status</code></td><td><code>dispatch_status</code></td><td></td><td>N</td><td></td><td>발송 시도 결과</td></tr>
  <tr><td><code>error_code</code></td><td><code>varchar(100)</code></td><td></td><td>Y</td><td></td><td>실패 코드</td></tr>
  <tr><td><code>error_message</code></td><td><code>varchar(500)</code></td><td></td><td>Y</td><td></td><td>실패 메시지</td></tr>
  <tr><td><code>dispatched_at</code></td><td><code>timestamp</code></td><td></td><td>N</td><td><code>now()</code></td><td>실제 발송 시도 시각</td></tr>
  <tr><td><code>created_at</code></td><td><code>timestamp</code></td><td></td><td>N</td><td><code>now()</code></td><td>생성 시각</td></tr>
</table>

#### 제약사항 / 인덱스

<table>
  <tr><th>항목</th><th>내용</th></tr>
  <tr><td><code>FK</code></td><td><code>notification_id -> notification.id</code></td></tr>
  <tr><td><code>UNIQUE</code></td><td><code>(notification_id, attempt)</code></td></tr>
</table>

### 4) `notification_template`

테이블 개요

- 알림 타입과 채널별 메시지 포맷을 관리하는 기준 테이블
- 생성 단계에서 템플릿 존재 여부를 검증하고, 발송 시 메시지 렌더링 기준으로 사용

#### 컬럼 명세

<table>
  <tr><th>컬럼명</th><th>타입</th><th>PK</th><th>NULL</th><th>기본값</th><th>설명</th></tr>
  <tr><td><code>id</code></td><td><code>bigint</code></td><td>Y</td><td>N</td><td><code>increment</code></td><td>템플릿 ID</td></tr>
  <tr><td><code>type</code></td><td><code>notification_type</code></td><td></td><td>N</td><td></td><td>알림 타입</td></tr>
  <tr><td><code>channel</code></td><td><code>notification_channel</code></td><td></td><td>N</td><td></td><td>알림 채널</td></tr>
  <tr><td><code>title_template</code></td><td><code>varchar(255)</code></td><td></td><td>N</td><td></td><td>제목 템플릿</td></tr>
  <tr><td><code>body_template</code></td><td><code>text</code></td><td></td><td>N</td><td></td><td>본문 템플릿</td></tr>
  <tr><td><code>is_active</code></td><td><code>boolean</code></td><td></td><td>N</td><td><code>true</code></td><td>활성 여부</td></tr>
  <tr><td><code>created_at</code></td><td><code>timestamp</code></td><td></td><td>N</td><td><code>now()</code></td><td>생성 시각</td></tr>
  <tr><td><code>updated_at</code></td><td><code>timestamp</code></td><td></td><td>N</td><td><code>now()</code></td><td>수정 시각</td></tr>
</table>

#### 제약사항 / 인덱스

<table>
  <tr><th>항목</th><th>내용</th></tr>
  <tr><td><code>UNIQUE</code></td><td><code>(type, channel)</code></td></tr>
</table>

#### 비고

- `notification`과 직접적인 외래키 관계는 두지 않는다.
- 타입과 채널 조합을 기준으로 참조한다.

## 비동기 처리 구조 및 재시도 정책

### 1. 알림 생성 흐름

처리 흐름은 다음과 같다.

1. API가 알림 생성 요청을 수신한다.
2. 서버가 요청 데이터의 핵심 값으로 멱등 판단 키를 생성한다.
3. Redis에서 동일 요청 여부를 먼저 확인한다.
4. Redis에 이미 존재하고 `COMPLETED` 상태이면 기존 `notificationId`를 반환한다.
5. Redis에 이미 존재하고 `PROCESSING` 상태이면 처리 중인 중복 요청으로 응답한다.
6. Redis에 키가 없으면 템플릿 존재 여부와 요청 유효성을 검증한다.
7. `dedupKey`를 생성하고 비즈니스 기준 중복 여부를 확인한다.
8. Redis에 멱등 판단 키를 `PROCESSING` 상태와 TTL로 저장한다.
9. 신규 요청이면 `Notification`을 `PENDING` 상태로 저장한다.
10. DB 저장 성공 시 Redis 상태를 `COMPLETED`와 `notificationId`로 갱신한다.
11. DB 저장 실패 시 Redis 키를 제거하거나 짧은 만료 처리한다.

### 2. 스케줄러 기반 비동기 발송 흐름

처리 흐름은 다음과 같다.

1. 스케줄러가 `PENDING` 또는 재시도 시각이 지난 `RETRY_WAITING` 알림을 조회한다.
2. 각 알림마다 `PENDING` 또는 `RETRY_WAITING`에서 `PROCESSING`으로 원자적 상태 선점을 시도한다.
3. 다른 워커가 이미 선점한 경우 해당 알림은 건너뛴다.
4. 선점에 성공한 경우 템플릿을 조회하고 제목/본문을 렌더링한다.
5. 채널별 `NotificationSender`를 통해 실제 발송을 시도한다.
6. 발송 성공 시 `NotificationDispatchHistory`에 `SUCCESS` 이력을 저장한다.
7. 발송 성공 시 상태를 `SENT`로 변경하고 `processedAt`을 기록한다.
8. 발송 실패 시 `NotificationDispatchHistory`에 `FAILED` 이력을 저장한다.
9. 재시도 가능 횟수가 남아 있으면 `RETRY_WAITING`으로 전이하고 `retryCount`, `nextRetryAt`, `lastErrorCode`, `lastErrorMessage`를 갱신한다.
10. 재시도 한도를 모두 소진하면 `FAILED`로 전이하고 `processedAt`, `lastErrorCode`, `lastErrorMessage`를 저장한다.

### 재시도 정책

<table>
  <tr><th>단계</th><th>처리</th></tr>
  <tr><td>최초 발송</td><td>1회 시도</td></tr>
  <tr><td>1차 실패</td><td>5분 후 재시도</td></tr>
  <tr><td>2차 실패</td><td>10분 후 재시도</td></tr>
  <tr><td>3차 실패</td><td>20분 후 재시도</td></tr>
  <tr><td>최종 실패</td><td>재시도 3회 초과 시 <code>FAILED</code></td></tr>
</table>

### 복구 전략

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
