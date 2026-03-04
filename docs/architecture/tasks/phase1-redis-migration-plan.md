# Phase 1 Redis 직렬화 마이그레이션 계획

## 1. 대상
- UI Backend Business Redis
  - `tc:ui:backend:session:*`
  - `tc:ui:backend:async:*`
  - `tc:ui:backend:dual:*`

## 2. 변경 내용
- 기존: JDK 직렬화 바이트 저장
- 변경: GenericJackson2JsonRedisSerializer 기반 JSON 저장

## 3. 배포 전 점검
1. UI Backend 배포 직전 Redis 키 백업 여부 확인
2. 운영 중 세션 유지 정책 확인(강제 재로그인 허용 여부)

## 4. 마이그레이션 절차(권장)
1. UI Backend 인스턴스 순차 중지
2. 아래 키 패턴만 선택 삭제
   - `SCAN 0 MATCH tc:ui:backend:session:* COUNT 1000`
   - `SCAN 0 MATCH tc:ui:backend:async:* COUNT 1000`
   - `SCAN 0 MATCH tc:ui:backend:dual:* COUNT 1000`
3. 신규 버전(UI JSON 직렬화) 배포
4. 로그인/비동기 조회/eqp create 시나리오 스모크 테스트 수행

## 5. 롤백 전략
- 긴급 롤백 시 이전 바이너리 배포 + 동일 키 패턴 재삭제 후 재기동
- 직렬화 포맷 혼재 상태는 운영 장애를 유발할 수 있으므로 혼재 상태로 롤백하지 않음

## 6. 운영 로그 확인 포인트
- `토큰 캐시 타입 불일치`
- `비동기 결과 타입 불일치`
- `DualResponse Redis 조회 실패`

위 로그가 지속 발생하면 키 정리 누락 또는 직렬화 포맷 혼재 가능성이 높습니다.
