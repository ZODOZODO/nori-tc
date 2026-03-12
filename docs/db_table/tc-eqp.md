# 장비(EQP) 테이블 명세

## 1. 설계 요약

- 장비의 모델 연결 기준 키는 `model_version_key` 입니다.
- 즉, 장비는 모델 원장(`tc_model`)이 아니라 모델 버전(`tc_model_version`)을 참조합니다.
- 장비별 통신 상세(`tc_eqp_hsms`, `tc_eqp_socket`)는 기존과 동일하게 `eqp_key` 기준 1:1 구조를 유지합니다.

## 2. 핵심 테이블

### 2-1. `tc_eqp`

- PK: `eqp_key`
- Unique: `uk_tc_eqp_eqp_id`
- FK: `model_version_key -> tc_model_version.model_version_key`
- 주요 컬럼: `eqp_id`, `comm_interface`, `comm_mode`, `route_partition`, `eqp_ip`, `eqp_port`, `enabled`

### 2-2. `tc_eqp_hsms`

- PK/FK: `eqp_key -> tc_eqp.eqp_key` (`ON DELETE CASCADE`)
- HSMS 타이머/링크 테스트/최대 프레임 설정 관리

### 2-3. `tc_eqp_socket`

- PK/FK: `eqp_key -> tc_eqp.eqp_key` (`ON DELETE CASCADE`)
- FK: `socket_protocol_type -> tc_eqp_socket_protocol_type.socket_protocol_type`

### 2-4. `tc_eqp_param_version`

- PK: `eqp_param_version_key`
- FK: `eqp_key -> tc_eqp.eqp_key` (`ON DELETE CASCADE`)
- Unique: `(eqp_key, param_version)`
- 주요 컬럼: `param_version`, `version_description`, `created_at`, `updated_at`, `created_by`, `updated_by`
- 비고: `tc_eqp_param.description`은 개별 파라미터 설명이고, 버전 전체 설명은 `tc_eqp_param_version.version_description`에 저장합니다.

## 3. 관계 요약

```text
tc_model_version.model_version_key
  └─(RESTRICT)→ tc_eqp.model_version_key

 tc_eqp.eqp_key
  ├─(CASCADE)→ tc_eqp_global.eqp_key
  ├─(CASCADE)→ tc_eqp_hsms.eqp_key
  ├─(CASCADE)→ tc_eqp_socket.eqp_key
  ├─(CASCADE)→ tc_eqp_log.eqp_key
  ├─(CASCADE)→ tc_eqp_param.eqp_key
  ├─(CASCADE)→ tc_eqp_param_version.eqp_key
  ├─(CASCADE)→ tc_eqp_port_status.eqp_key
  ├─(CASCADE)→ tc_eqp_state.eqp_key
  ├─(CASCADE)→ tc_eqp_state_hist.eqp_key
  ├─(CASCADE)→ tc_jar_business.eqp_key
  ├─(CASCADE)→ tc_jar_gateway.eqp_key
  └─(RESTRICT)→ tc_work.eqp_key

tc_eqp_param_version.(eqp_key, param_version)
  └─(논리 연동)→ tc_eqp_param.(eqp_key, param_version)

NOTE:
- `tc_eqp_param`에는 체크아웃 중간 상태인 `EDIT` 버전이 존재할 수 있으므로
  `tc_eqp_param_version`과의 물리 FK는 두지 않고 애플리케이션 레이어에서 정합성을 유지합니다.
```

## 4. 조회 예시

```sql
SELECT e.eqp_id,
       e.comm_interface,
       e.eqp_ip,
       e.eqp_port,
       mv.model_version_key,
       mv.model_version,
       m.model_name
  FROM tc_eqp e
  JOIN tc_model_version mv ON mv.model_version_key = e.model_version_key
  JOIN tc_model m ON m.model_key = mv.model_key
 WHERE e.enabled = TRUE
 ORDER BY e.eqp_id;
```
