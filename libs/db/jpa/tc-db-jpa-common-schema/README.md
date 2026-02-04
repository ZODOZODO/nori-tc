# tc-db-jpa-common-schema (Layer 2 / JPA) - FIX

## 목적
- 공통 테이블의 JPA Entity/Repository를 제공한다.
- 현재 범위(차세대 TC v4.5, 7개 테이블)는 전부 common에 들어간다.

## 포함 대상
- `jakarta.persistence` Entity
- Spring Data JPA Repository
- 스캔 기준용 Marker 클래스

## 제외 대상
- Vendor(DB별) 설정/드라이버
- AutoConfiguration(조립)  → starter 모듈 책임
- Port(Store) 구현         → 다음 단계에서 추가(또는 별도 adapter 모듈로 분리 가능)

## 테이블 범위(7)
- tc_model
- tc_eqp
- tc_eqp_conn_state
- tc_eqp_hsms
- tc_eqp_log
- tc_eqp_oper_state
- tc_eqp_socket
