

# tc-db-mybatis-common-schema (Layer 2 / MyBatis) - FIX

## 목적
- 공통 테이블(현재 7개)의 MyBatis Mapper 인터페이스 + XML(SQL/ResultMap)을 제공한다.

## 포함
- Mapper 인터페이스
- mapper XML (CRUD)
- 최소 TypeHandler(시간 타입)

## 제외
- DataSource/SqlSessionFactory/MapperScan/Transaction 등 "조립"
  → starter 모듈에서 처리

## 테이블(7)
- tc_model
- tc_eqp
- tc_eqp_conn_state
- tc_eqp_hsms
- tc_eqp_log
- tc_eqp_oper_state
- tc_eqp_socket

## 중요 운영 메모
- 4개 DB를 완전하게 지원하려면,
  - 페이징 SQL
  - 업서트 문법
  - 불리언 타입(특히 Oracle)
  같은 방언 차이를 starter/adapter에서 흡수해야 한다.
- common-schema는 가장 보수적인 CRUD(insert/update/select/delete)만 기본 제공한다.
