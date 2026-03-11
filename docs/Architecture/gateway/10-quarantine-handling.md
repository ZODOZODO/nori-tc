# 10. Quarantine 처리 (Quarantine Handling)

## 개요

**Quarantine(격리)** 은 설비에서 수신했지만 정상적으로 처리할 수 없는 데이터를 별도 저장소에 격리하는 메커니즘입니다.

DLQ는 **명령 처리 실패** 시 사용하고,
Quarantine은 **설비에서 수신된 데이터가 처리 불가** 할 때 사용합니다.

---

## DLQ vs Quarantine 차이

| 항목 | DLQ | Quarantine |
|------|-----|-----------|
| 대상 | Kafka 명령 (Gateway → 설비 방향 실패) | 설비 수신 데이터 (설비 → Gateway 방향 실패) |
| 발생 조건 | 설비 미연결, 계약 위반, 처리 오류 | 미등록 설비, eqpId 매핑 실패, 프레임 오류 |
| 저장 TTL | 7일 | 14일 |
| Redis 키 | `tc:comm:gateway:dlq:{id}` | `tc:comm:gateway:quarantine:{id}` |
| 재처리 방법 | 명령 재발행 | 수동 분석 후 판단 |

---

## Quarantine 발생 조건

### 1. 알 수 없는 설비에서 연결 시도

```
PASSIVE 모드에서 등록되지 않은 설비가 접속:
  - IP 주소로 설비 조회 → 해당 IP 없음
  - eqpId를 특정할 수 없음
  - 수신된 데이터 전체를 Quarantine에 저장
```

### 2. HSMS SessionID 매핑 실패

```
HSMS 프레임의 SessionID가 DB에 등록된 설비와 매칭되지 않음:
  SessionID: 9999 → 해당 SessionID의 설비 없음
  → Quarantine 저장: reason="UNKNOWN_SESSION_ID:9999"
```

### 3. SOCKET 프레임 크기 초과

```
SOCKET 설비에서 max-frame-bytes(256KB)를 초과하는 데이터 수신:
  → 정상 처리 불가 (메모리 보호)
  → 해당 프레임 데이터를 Quarantine에 저장
```

### 4. SOCKET 디코딩 실패

```
플러그인 디코더가 null을 반환하거나 예외 발생:
  → 처리할 수 있는 이벤트로 변환 불가
  → Quarantine 저장: reason="DECODE_FAILED"
```

---

## 처리 흐름

```
설비에서 데이터 수신
        │
        ↓
eqpId 추출 시도
        │
        ├─ 성공 (등록된 설비) → 정상 파이프라인 처리
        │
        └─ 실패 (미등록 설비 또는 매핑 오류)
                │
                ↓
        Quarantine 저장
        {
          id: ULID,
          rawBytes: (base64 인코딩),
          reason: "UNKNOWN_SESSION_ID:9999",
          sourceIp: "192.168.1.200",
          receivedAt: "2026-03-11T10:00:00Z",
          ttl: 14일
        }
                │
                ↓
        채널 종료 (해당 연결 끊기)
                │
                ↓
        로그 출력
```

---

## Redis 저장 구조

```
Key:   tc:comm:gateway:quarantine:{ulid-id}
TTL:   1,209,600초 (14일)
Value: JSON
{
  "id": "01JNCMX7YB...",
  "rawBytesBase64": "SGVsbG8gV29ybGQ=",
  "reason": "UNKNOWN_SESSION_ID:9999",
  "sourceIp": "192.168.1.200",
  "sourcePort": 52341,
  "receivedAt": "2026-03-11T10:00:00Z",
  "protocol": "HSMS"
}
```

```properties
# tc-redis.properties
tc.comm.gateway.redis.quarantine-ttl-seconds=1209600   # 14일
```

---

## Quarantine 조회 (UI Backend)

운영자는 UI Backend의 REST API를 통해 Quarantine 데이터를 조회할 수 있습니다.

```
GET /api/quarantine?type=gateway     → Gateway Quarantine 목록
GET /api/quarantine/{id}             → 특정 항목 상세
DELETE /api/quarantine/{id}          → 항목 삭제 (처리 완료)
```

UI Backend는 Gateway Redis에 접근하여 `tc:comm:gateway:quarantine:*` 키를 조회합니다.

---

## 운영 절차

### Quarantine 데이터가 발생했을 때

```
1. Quarantine 목록 조회
   GET /api/quarantine?type=gateway

2. reason 코드 확인
   - UNKNOWN_SESSION_ID: HSMS SessionID 설정 오류
   - UNKNOWN_IP: 설비 IP 등록 누락
   - DECODE_FAILED: 플러그인 오류 또는 설비 포맷 변경
   - FRAME_TOO_LARGE: 설비 데이터 크기 이상

3. 원인 분석 및 조치
   - UNKNOWN_SESSION_ID → DB에 설비의 SessionID 등록
   - UNKNOWN_IP → DB에 설비 IP 등록
   - DECODE_FAILED → 플러그인 코드 수정 및 재업로드
   - FRAME_TOO_LARGE → 설비 설정 또는 max-frame-bytes 조정

4. 조치 완료 후 Quarantine 항목 삭제
   DELETE /api/quarantine/{id}

5. 설비 재연결 또는 데이터 재전송
   (자동 재처리 없음, 수동으로 재발행 필요)
```

---

## 주요 reason 코드

| reason 코드 | 원인 | 조치 방법 |
|------------|------|---------|
| `UNKNOWN_SESSION_ID:{id}` | HSMS SessionID가 DB에 없음 | 설비의 SessionID를 DB에 등록 |
| `UNKNOWN_IP:{ip}` | 설비 IP가 DB에 없음 (PASSIVE SOCKET) | DB에 설비 IP 등록 |
| `DECODE_FAILED` | SOCKET 플러그인 디코딩 실패 | 플러그인 코드 확인 및 수정 |
| `FRAME_TOO_LARGE:{size}` | 수신 프레임이 max-frame-bytes 초과 | 설비 설정 확인 또는 max-frame-bytes 증가 |
| `EMPTY_FRAME` | 빈 프레임 수신 (allow-empty=false 설정) | 설비 동작 확인 |

---

## 주의사항

| 항목 | 내용 |
|------|------|
| **14일 TTL** | 14일 이내에 Quarantine 데이터를 분석하고 조치하세요. 자동 삭제 후에는 복구 불가합니다 |
| **자동 재처리 없음** | Quarantine 데이터는 자동으로 재처리되지 않습니다. 원인 수정 후 설비에서 데이터를 다시 보내야 합니다 |
| **raw bytes 저장** | Quarantine에는 원본 bytes가 base64로 저장됩니다. 바이너리 데이터를 분석할 수 있는 도구가 필요할 수 있습니다 |
| **용량 모니터링** | Quarantine이 지속적으로 쌓이면 Redis 메모리를 소비합니다. 주기적으로 모니터링하고 조치하세요 |
| **보안** | Quarantine 데이터는 외부에서 보낸 원시 데이터입니다. 조회 시 주의하세요 |
