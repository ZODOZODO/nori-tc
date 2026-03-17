# 모델(Model) 테이블 명세

## 1. 설계 요약

- 모델 원장과 버전은 분리됩니다.
- `maker`, `comm_interface` 소유자는 `tc_model` 입니다.
- 하위 모델 설정/정의 테이블과 장비(`tc_eqp`)는 모두 `model_version_key` 기준으로 참조합니다.

## 2. 확정 관계

1. `tc_model(1) : tc_model_version(n)`
2. `tc_model_version(1) : tc_eqp(n)`
3. `tc_model_version(1) : tc_model_param(n)`
4. `tc_model_version(1) : tc_model_secs_message(n)` — UNIQUE(model_version_key, secs_msg_name)
5. `tc_model_version(1) : tc_model_socket_message(n)` — UNIQUE(model_version_key, socket_msg_name)
6. `tc_model_version(1) : tc_model_mes_message(n)` — UNIQUE(model_version_key, mes_msg_name)
7. `tc_model_version(1) : tc_model_variableid(n)`
8. `tc_model_version(1) : tc_model_reportid(n)`
9. `tc_model_version(1) : tc_model_eventid(n)`
10. `tc_model_version(1) : tc_model_workflow(n)`
11. `tc_model_version(1) : tc_model_mdf(1)`
12. `tc_model_version(1) : tc_model_dcop_item(n)`

## 3. 핵심 테이블

### 3-1. `tc_model`

- PK: `model_key`
- 주요 컬럼: `model_name`, `maker`, `comm_interface`, 감사 컬럼
- Unique: `uk_tc_model_model_name`

### 3-2. `tc_model_version`

- PK: `model_version_key`
- FK: `model_key -> tc_model.model_key` (`ON DELETE CASCADE`)
- 주요 컬럼: `model_version`, `status`, 감사 컬럼
- Unique: `uk_tc_model_version_model_key_model_version`

## 4. 하위 테이블 FK 기준

아래 테이블은 모두 `model_version_key` FK를 사용합니다.

- `tc_model_param`
- `tc_model_secs_message`
- `tc_model_socket_message`
- `tc_model_mes_message`
- `tc_model_variableid`
- `tc_model_reportid`
- `tc_model_eventid`
- `tc_model_workflow`
- `tc_model_mdf`
- `tc_model_dcop_item`

## 5. 1:1 강제 테이블

아래 테이블은 버전당 1건만 허용하도록 `UNIQUE(model_version_key)` 제약을 둡니다.

- `tc_model_mdf`

> 주의: `tc_model_secs_message`, `tc_model_socket_message`, `tc_model_mes_message`는 `UNIQUE(model_version_key, *_msg_name)` 복합 유니크이므로 1:N 관계입니다.

## 6. FK 관계 요약

```text
tc_model.model_key
  └─(CASCADE)→ tc_model_version.model_key

 tc_model_version.model_version_key
  ├─(CASCADE)→ tc_model_param.model_version_key
  ├─(CASCADE)→ tc_model_secs_message.model_version_key
  ├─(CASCADE)→ tc_model_socket_message.model_version_key
  ├─(CASCADE)→ tc_model_mes_message.model_version_key
  ├─(CASCADE)→ tc_model_variableid.model_version_key
  ├─(CASCADE)→ tc_model_reportid.model_version_key
  ├─(CASCADE)→ tc_model_eventid.model_version_key
  ├─(CASCADE)→ tc_model_workflow.model_version_key
  ├─(CASCADE)→ tc_model_mdf.model_version_key
  ├─(CASCADE)→ tc_model_dcop_item.model_version_key
  └─(RESTRICT)→ tc_eqp.model_version_key
```

## 7. 조회 예시

```sql
SELECT m.model_name,
       m.maker,
       m.comm_interface,
       mv.model_version_key,
       mv.model_version,
       mv.status
  FROM tc_model m
  JOIN tc_model_version mv ON mv.model_key = m.model_key
 ORDER BY m.model_name, mv.model_version;
```