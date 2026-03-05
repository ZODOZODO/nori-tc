-- =====================================================================
-- seed_v5_fixed_pg.sql  (UTF-8)
-- 목적:
--  - Outbox(tc_msg_send_queue / tc_msg_send_log)는 건드리지 않는다.
--  - 모델/설비/워크 및 하위 테이블까지 "유니크 제약 위반 없이" 샘플 데이터 생성
-- =====================================================================

\set ON_ERROR_STOP on
\echo '=== SEED START (v5.0 / postgres) ==='

BEGIN;
SET TIME ZONE 'UTC';

-- =====================================================================
-- 0) 기존 데이터 삭제 (FK 자식 -> 부모 순)
--    * Outbox 2개 테이블은 삭제/삽입 모두 하지 않음
-- =====================================================================

-- Work 하위
DELETE FROM tc_work_processjob_lot_map;
DELETE FROM tc_work_processjob;
DELETE FROM tc_work_controljob;
DELETE FROM tc_work_carrier_slot;
DELETE FROM tc_work_carrier;
DELETE FROM tc_work_lot;
DELETE FROM tc_work_param;
DELETE FROM tc_work;

-- Eqp 하위
DELETE FROM tc_eqp_port_status;
DELETE FROM tc_eqp_log;
DELETE FROM tc_eqp_state_hist;
DELETE FROM tc_eqp_state;
DELETE FROM tc_eqp_param;
DELETE FROM tc_eqp_global;
DELETE FROM tc_eqp_hsms;
DELETE FROM tc_eqp_socket;
DELETE FROM tc_jar_business;
DELETE FROM tc_jar_gateway;
DELETE FROM tc_eqp;

-- Socket protocol type (eqp_socket FK 대상이므로 eqp_socket 삭제 후 삭제)
DELETE FROM tc_eqp_socket_protocol_type;

-- Model 하위
DELETE FROM tc_model_dcop_item;
DELETE FROM tc_model_mdf;
DELETE FROM tc_model_workflow;
DELETE FROM tc_model_eventid;
DELETE FROM tc_model_reportid;
DELETE FROM tc_model_variableid;
DELETE FROM tc_model_socket_message;
DELETE FROM tc_model_secs_message;
DELETE FROM tc_model_param;
DELETE FROM tc_model_version;
DELETE FROM tc_model;

-- =====================================================================
-- 1) 상수/매핑용 임시 테이블
-- =====================================================================

-- 슬롯맵(1:2:...:24) 한 번만 생성해서 재사용
CREATE TEMP TABLE seed_const (
  slot_map VARCHAR(255) NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_const(slot_map)
SELECT string_agg(gs::text, ':' ORDER BY gs)
FROM generate_series(1,24) gs;

-- 모델 매핑
CREATE TEMP TABLE seed_model_map (
  model_key          BIGINT       NOT NULL,
  model_version_key  BIGINT       NOT NULL,
  model_name         VARCHAR(128) NOT NULL,
  model_version      VARCHAR(32)  NOT NULL,
  comm_interface     VARCHAR(16)  NOT NULL
) ON COMMIT DROP;

-- 설비 매핑
CREATE TEMP TABLE seed_eqp_map (
  eqp_key        BIGINT       NOT NULL,
  eqp_id         VARCHAR(64)  NOT NULL,
  comm_interface VARCHAR(16)  NOT NULL
) ON COMMIT DROP;

-- Work 매핑
CREATE TEMP TABLE seed_work_map (
  work_key BIGINT      NOT NULL,
  eqp_key  BIGINT      NOT NULL,
  work_id  VARCHAR(64) NOT NULL
) ON COMMIT DROP;

-- Work Carrier 매핑
CREATE TEMP TABLE seed_carrier_map (
  work_carrier_key BIGINT      NOT NULL,
  work_key         BIGINT      NOT NULL,
  carrier_id       VARCHAR(64) NOT NULL
) ON COMMIT DROP;

-- Work Lot 매핑(슬롯에 lot_id 연결 위해 lot_seq 포함)
CREATE TEMP TABLE seed_lot_map (
  work_lot_key BIGINT      NOT NULL,
  work_key     BIGINT      NOT NULL,
  lot_id       VARCHAR(64) NOT NULL,
  lot_seq      INT         NOT NULL
) ON COMMIT DROP;

-- ControlJob 매핑
CREATE TEMP TABLE seed_cj_map (
  control_job_key BIGINT      NOT NULL,
  work_key        BIGINT      NOT NULL
) ON COMMIT DROP;

-- ProcessJob 매핑
CREATE TEMP TABLE seed_pj_map (
  process_job_key BIGINT NOT NULL,
  work_key        BIGINT NOT NULL,
  pj_seq          INT    NOT NULL
) ON COMMIT DROP;

-- =====================================================================
-- 2) tc_eqp_socket_protocol_type
-- =====================================================================

INSERT INTO tc_eqp_socket_protocol_type (
  socket_protocol_type,
  socket_protocol_type_name,
  parse_start_rule,
  parse_end_rule,
  parse_regex,
  description
)
VALUES
  -- Gateway 기본 내장 핸들러 이름과 동일한 타입명을 사용해야 tc_eqp_socket FK 및 런타임 조회가 일관됩니다.
  ('LINE_DELIMITED',  'Line Delimited',  NULL, E'\n',     NULL,      'LF(\\n) 종료 기준으로 프레임 분리'),
  ('REGEX_DELIMITED', 'Regex Delimited', NULL, NULL,      E'END\\n', '정규식 종료 패턴(예: END\\n) 기준으로 프레임 분리');

-- =====================================================================
-- 3) tc_model 10개 + 하위 테이블
-- =====================================================================

WITH ins_model AS (
  INSERT INTO tc_model (
    model_name, comm_interface, maker,
    created_at, updated_at, created_by, updated_by
  )
  VALUES
    -- HSMS 5
    ('MODEL_HSMS_01','HSMS','ACME', now(), now(),'SEED','SEED'),
    ('MODEL_HSMS_02','HSMS','ACME', now(), now(),'SEED','SEED'),
    ('MODEL_HSMS_03','HSMS','ACME', now(), now(),'SEED','SEED'),
    ('MODEL_HSMS_04','HSMS','ACME', now(), now(),'SEED','SEED'),
    ('MODEL_HSMS_05','HSMS','ACME', now(), now(),'SEED','SEED'),
    -- SOCKET 5
    ('MODEL_SOCKET_01','SOCKET','BETA', now(), now(),'SEED','SEED'),
    ('MODEL_SOCKET_02','SOCKET','BETA', now(), now(),'SEED','SEED'),
    ('MODEL_SOCKET_03','SOCKET','BETA', now(), now(),'SEED','SEED'),
    ('MODEL_SOCKET_04','SOCKET','BETA', now(), now(),'SEED','SEED'),
    ('MODEL_SOCKET_05','SOCKET','BETA', now(), now(),'SEED','SEED')
  RETURNING model_key, model_name, comm_interface
),
ins_model_version AS (
  INSERT INTO tc_model_version (
    model_key, model_version, status,
    created_at, updated_at, created_by, updated_by
  )
  SELECT m.model_key,
         '1.0.0',
         CASE
           WHEN m.model_name LIKE '%_03' THEN 'DRAFT'
           WHEN m.model_name LIKE '%_04' THEN 'DEPRECATED'
           ELSE 'ACTIVE'
         END,
         now(), now(), 'SEED', 'SEED'
  FROM ins_model m
  RETURNING model_version_key, model_key, model_version
)
INSERT INTO seed_model_map(model_key, model_version_key, model_name, model_version, comm_interface)
SELECT m.model_key,
       v.model_version_key,
       m.model_name,
       v.model_version,
       m.comm_interface
FROM ins_model m
JOIN ins_model_version v ON v.model_key = m.model_key;

-- model_param (모든 모델 버전에 2개씩)
INSERT INTO tc_model_param(model_version_key, param_name, param_value, updated_at)
SELECT model_version_key, 'SITE', 'DEV', now() FROM seed_model_map
UNION ALL
SELECT model_version_key, 'REVISION', 'A', now() FROM seed_model_map;

-- HSMS 모델: 버전별 2개 이상 (1:N 관계 검증)
INSERT INTO tc_model_secs_message(model_version_key, secs_msg_name, description, data_index, updated_at)
SELECT m.model_version_key, 'S1F1', 'Are you there', NULL, now()
FROM seed_model_map m
WHERE m.comm_interface='HSMS'
UNION ALL
SELECT m.model_version_key, 'S2F41', 'Remote command', NULL, now()
FROM seed_model_map m
WHERE m.comm_interface='HSMS';

-- SOCKET 모델: 버전별 2개 이상 (1:N 관계 검증)
INSERT INTO tc_model_socket_message(model_version_key, socket_msg_name, description, data_index, updated_at)
SELECT m.model_version_key, 'CMD=PING', 'Health check', NULL, now()
FROM seed_model_map m
WHERE m.comm_interface='SOCKET'
UNION ALL
SELECT m.model_version_key, 'CMD=STATUS', 'Status request', NULL, now()
FROM seed_model_map m
WHERE m.comm_interface='SOCKET';

-- variableid (각 모델 버전 3개)
INSERT INTO tc_model_variableid(model_version_key, variable_id, variable_id_type, description, updated_at)
SELECT model_version_key, 'VID_TEMP',  'SVID', 'Temperature', now() FROM seed_model_map
UNION ALL
SELECT model_version_key, 'VID_PRESS', 'SVID', 'Pressure',    now() FROM seed_model_map
UNION ALL
SELECT model_version_key, 'VID_STATE', 'CEID', 'State event', now() FROM seed_model_map;

-- reportid (각 모델 버전 2개)
INSERT INTO tc_model_reportid(model_version_key, report_id, variable_id, enabled, updated_at)
SELECT model_version_key, 'RPT_01', 'VID_TEMP,VID_PRESS', FALSE, now() FROM seed_model_map
UNION ALL
SELECT model_version_key, 'RPT_02', 'VID_STATE',          TRUE,  now() FROM seed_model_map;

-- eventid (각 모델 버전 2개)
INSERT INTO tc_model_eventid(model_version_key, event_id, report_id, enabled, updated_at)
SELECT model_version_key, 'EVT_100', 'RPT_01', TRUE,  now() FROM seed_model_map
UNION ALL
SELECT model_version_key, 'EVT_200', 'RPT_02', FALSE, now() FROM seed_model_map;

-- workflow (각 모델 버전 3개, (model_version_key, workflow_name, message_name) 유니크 보장)
INSERT INTO tc_model_workflow(
  model_version_key, workflow_name, message_name, event_id, transaction_id, workflow_filter,
  action_name, action_data_index, updated_at
)
SELECT m.model_version_key,
       w.workflow_name,
       w.message_name,
       w.event_id,
       w.transaction_id,
       w.workflow_filter,
       w.action_name,
       w.action_data_index,
       now()
FROM seed_model_map m
JOIN LATERAL (
  VALUES
    -- BASIC 2개 + ALARM 1개 (message_name만 다르게)
    ('BASIC',
      CASE WHEN m.comm_interface='HSMS' THEN 'S6F11' ELSE 'CMD=START' END,
      NULL, NULL, NULL,
      'ACTION_START', NULL
    ),
    ('BASIC',
      CASE WHEN m.comm_interface='HSMS' THEN 'S2F41' ELSE 'CMD=STOP' END,
      NULL, NULL, NULL,
      'ACTION_STOP', NULL
    ),
    ('ALARM',
      CASE WHEN m.comm_interface='HSMS' THEN 'S1F1' ELSE 'CMD=PING' END,
      NULL, NULL, NULL,
      'ACTION_PING', NULL
    )
) AS w(workflow_name, message_name, event_id, transaction_id, workflow_filter, action_name, action_data_index) ON TRUE;

-- mdf (각 모델 버전 1개)
INSERT INTO tc_model_mdf(model_version_key, mdf_name, mdf_file, updated_at)
SELECT model_version_key,
       'DEFAULT',
       convert_to('MDF:' || model_name, 'UTF8'),
       now()
FROM seed_model_map;

-- dcop_item (각 모델 버전 2개)
INSERT INTO tc_model_dcop_item(
  model_version_key, dcop_item_name, workflow_name, event_id, variable_id,
  collection_rule, calculation_rule, order_rule, updated_at
)
SELECT model_version_key, 'TEMP', NULL, 'EVT_100', 'VID_TEMP', 'LAST',  'NONE',  1, now() FROM seed_model_map
UNION ALL
SELECT model_version_key, 'PRESS',NULL, 'EVT_100', 'VID_PRESS','LAST',  'NONE',  2, now() FROM seed_model_map;

-- =====================================================================
-- 4) tc_eqp 5대 + 하위(HSMS/SOCKET/STATE/LOG/PORT/PARAM/GLOBAL/HIST)
-- =====================================================================

WITH ins AS (
  INSERT INTO tc_eqp(
    eqp_id, comm_interface, comm_mode, route_partition, eqp_ip, eqp_port, model_version_key,
    enabled, created_at, updated_at, created_by, updated_by
  )
  VALUES
    -- HSMS 3대 (A/P/A)
    ('EQP_HSMS_A01','HSMS','ACTIVE', 0,'10.10.0.11',5000,(SELECT model_version_key FROM seed_model_map WHERE model_name='MODEL_HSMS_01' AND model_version='1.0.0'), FALSE, now(), now(),'SEED','SEED'),
    ('EQP_HSMS_P01','HSMS','PASSIVE',1,'10.10.0.12',5000,(SELECT model_version_key FROM seed_model_map WHERE model_name='MODEL_HSMS_02' AND model_version='1.0.0'), FALSE, now(), now(),'SEED','SEED'),
    ('EQP_HSMS_A02','HSMS','ACTIVE', 2,'10.10.0.13',5001,(SELECT model_version_key FROM seed_model_map WHERE model_name='MODEL_HSMS_03' AND model_version='1.0.0'), FALSE, now(), now(),'SEED','SEED'),

    -- SOCKET 3대 (A/P/P)
    ('EQP_SOCKET_A01','SOCKET','ACTIVE', 3,'10.10.0.21',6000,(SELECT model_version_key FROM seed_model_map WHERE model_name='MODEL_SOCKET_01' AND model_version='1.0.0'), FALSE, now(), now(),'SEED','SEED'),
    ('EQP_SOCKET_P01','SOCKET','PASSIVE',4,'192.168.0.7',6000,(SELECT model_version_key FROM seed_model_map WHERE model_name='MODEL_SOCKET_02' AND model_version='1.0.0'), TRUE, now(), now(),'SEED','SEED'),
    ('DG_SOCKET_P01','SOCKET','PASSIVE',5,'192.168.0.14',6000,(SELECT model_version_key FROM seed_model_map WHERE model_name='MODEL_SOCKET_02' AND model_version='1.0.0'), TRUE, now(), now(),'SEED','SEED')
  RETURNING eqp_key, eqp_id, comm_interface
)
INSERT INTO seed_eqp_map(eqp_key, eqp_id, comm_interface)
SELECT eqp_key, eqp_id, comm_interface FROM ins;

-- HSMS 설정(HSMS 설비만)
INSERT INTO tc_eqp_hsms(
  eqp_key, device_id,
  t3_timeout, t5_timeout, t6_timeout, t7_timeout, t8_timeout,
  link_test_enabled, link_test_interval, max_msg_bytes,
  created_at, updated_at
)
SELECT e.eqp_key,
       CASE e.eqp_id
         WHEN 'EQP_HSMS_A01' THEN 1001
         WHEN 'EQP_HSMS_P01' THEN 1002
         WHEN 'EQP_HSMS_A02' THEN 1003
         ELSE 1000
       END AS device_id,
       45, 10, 5, 10, 5,
       TRUE, 60, 10485760,
       now(), now()
FROM seed_eqp_map e
WHERE e.comm_interface='HSMS';

-- SOCKET 설정(SOCKET 설비만)
INSERT INTO tc_eqp_socket(
  eqp_key, socket_protocol_type,
  charset, heartbeat_enabled, heartbeat_interval,
  read_timeout, write_timeout, max_frame_size_bytes, keep_alive_enabled,
  created_at, updated_at
)
SELECT e.eqp_key,
       CASE
         WHEN e.eqp_id LIKE '%A01' THEN 'LINE_DELIMITED'
         ELSE 'LINE_DELIMITED'
       END AS socket_protocol_type,
       'UTF-8',
       TRUE, 30,
       0, 0, 8192, TRUE,
       now(), now()
FROM seed_eqp_map e
WHERE e.comm_interface='SOCKET';

-- 설비 상태(1:1)
INSERT INTO tc_eqp_state(
  eqp_key, control_state, eqp_state, since_at,
  reason_code, reason_detail, updated_at
)
SELECT eqp_key, 'REMOTE', 'IDLE', now(), NULL, NULL, now()
FROM seed_eqp_map;

-- 설비 로그(1:1)
INSERT INTO tc_eqp_log(eqp_key, log_level, log_retention_days, log_path, updated_at)
SELECT eqp_key, 'INFO', 30, '/var/log/tc/' || lower(eqp_id), now()
FROM seed_eqp_map;

-- 포트 상태(설비당 2포트)
INSERT INTO tc_eqp_port_status(
  eqp_key, port_id, port_type, port_state,
  carrier_id, carrier_type, carrier_state,
  updated_at
)
SELECT e.eqp_key,
       p.port_id,
       'LOAD_PORT',
       'EMPTY',
       NULL, NULL, NULL,
       now()
FROM seed_eqp_map e
JOIN LATERAL (VALUES ('LP1'), ('LP2')) AS p(port_id) ON TRUE;

-- 설비 파라미터(설비당 2개)
INSERT INTO tc_eqp_param(eqp_key, param_name, param_version, param_value, updated_at)
SELECT eqp_key, 'CFG', 'v1', '{"seed":true}', now() FROM seed_eqp_map
UNION ALL
SELECT eqp_key, 'LIMIT', 'v1', '{"max":100}',   now() FROM seed_eqp_map;

-- 설비 글로벌(설비당 1개)
INSERT INTO tc_eqp_global(eqp_key, param_name, param_value, updated_at)
SELECT eqp_key, 'SITE_MODE', '{"mode":"DEV"}', now() FROM seed_eqp_map;

-- 상태 이력(설비당 OPER/CONN 2건씩)
INSERT INTO tc_eqp_state_hist(
  eqp_key, state_type, from_state, to_state,
  changed_at, reason_code, reason_detail
)
SELECT eqp_key, 'CONN', 'DISCONNECTED', 'CONNECTED', now() - interval '30 minutes', NULL, NULL
FROM seed_eqp_map
UNION ALL
SELECT eqp_key, 'OPER', 'OFFLINE', 'IDLE', now() - interval '20 minutes', 'SEED', 'seed init'
FROM seed_eqp_map;

-- =====================================================================
-- 5) tc_work (설비당 5개) + 하위 테이블 전체
-- =====================================================================

-- Work 25개 생성 + 매핑 적재
WITH ins AS (
  INSERT INTO tc_work(
    eqp_key, work_id, operator_id, step_seq, work_state,
    start_time, end_time, mes_message,
    created_at, updated_at
  )
  SELECT e.eqp_key,
         e.eqp_id || '-W' || lpad(gs::text, 2, '0') AS work_id,
         CASE WHEN gs=1 THEN 'OP001' ELSE NULL END AS operator_id,
         gs AS step_seq,
         CASE
           WHEN gs=1 THEN 'RUNNING'
           WHEN gs=2 THEN 'QUEUED'
           WHEN gs=3 THEN 'COMPLETED'
           WHEN gs=4 THEN 'ABORTED'
           ELSE 'COMPLETED'
         END AS work_state,
         CASE WHEN gs IN (1,3,5) THEN now() - (gs * interval '10 minutes') ELSE NULL END AS start_time,
         CASE WHEN gs IN (3,5) THEN now() - (gs * interval '5 minutes')  ELSE NULL END AS end_time,
         CASE WHEN gs=1 THEN '{"seed":"mes","type":"start"}' ELSE NULL END AS mes_message,
         now(), now()
  FROM seed_eqp_map e
  CROSS JOIN generate_series(1,5) gs
  RETURNING work_key, eqp_key, work_id
)
INSERT INTO seed_work_map(work_key, eqp_key, work_id)
SELECT work_key, eqp_key, work_id FROM ins;

-- Work Param (work당 2개)
INSERT INTO tc_work_param(work_key, param_name, param_value, updated_at)
SELECT work_key, 'PRIORITY',
       CASE WHEN work_id LIKE '%W01' THEN 'HIGH' ELSE 'NORMAL' END,
       now()
FROM seed_work_map
UNION ALL
SELECT work_key, 'BATCH_ID', substring(md5(work_id),1,8), now()
FROM seed_work_map;

-- Work Carrier (work당 1개) + 매핑
WITH ins AS (
  INSERT INTO tc_work_carrier(
    work_key, carrier_id, port_id, slot_map,
    total_qty, good_qty, scrap_qty,
    updated_at
  )
  SELECT w.work_key,
         w.work_id || '-C1' AS carrier_id,
         'LP1' AS port_id,
         (SELECT slot_map FROM seed_const) AS slot_map,
         24, 24, 0,
         now()
  FROM seed_work_map w
  RETURNING work_carrier_key, work_key, carrier_id
)
INSERT INTO seed_carrier_map(work_carrier_key, work_key, carrier_id)
SELECT work_carrier_key, work_key, carrier_id FROM ins;

-- Work Lot (work당 3개) + 매핑(lot_seq=1..3)
WITH src AS (
  SELECT w.work_key, w.work_id, gs AS lot_seq
  FROM seed_work_map w
  CROSS JOIN generate_series(1,3) gs
),
ins AS (
  INSERT INTO tc_work_lot(
    work_key, carrier_id, lot_id, parent_lot_id, chamber_id, updated_at
  )
  SELECT s.work_key,
         s.work_id || '-C1' AS carrier_id,
         s.work_id || '-L' || lpad(s.lot_seq::text,2,'0') AS lot_id,
         NULL,
         'CH' || s.lot_seq::text AS chamber_id,
         now()
  FROM src s
  RETURNING work_lot_key, work_key, lot_id
)
INSERT INTO seed_lot_map(work_lot_key, work_key, lot_id, lot_seq)
SELECT i.work_lot_key,
       i.work_key,
       i.lot_id,
       s.lot_seq
FROM ins i
JOIN src s
  ON s.work_key = i.work_key
 AND i.lot_id = s.work_id || '-L' || lpad(s.lot_seq::text,2,'0');

-- Work Carrier Slot (캐리어당 24 슬롯)
-- 슬롯 1~3은 lot_id를 연결해서 "샘플 의미"를 갖게 함
INSERT INTO tc_work_carrier_slot(
  work_carrier_key, slot_no, slot_state, lot_id, updated_at
)
SELECT c.work_carrier_key,
       gs AS slot_no,
       CASE WHEN gs<=3 THEN 'LOADED' ELSE 'EMPTY' END AS slot_state,
       l.lot_id,
       now()
FROM seed_carrier_map c
CROSS JOIN generate_series(1,24) gs
LEFT JOIN seed_lot_map l
  ON l.work_key = c.work_key
 AND l.lot_seq = gs
 AND gs <= 3;

-- ControlJob (work당 1개) + 매핑
WITH ins AS (
  INSERT INTO tc_work_controljob(
    work_key, controljob_id, controljob_state, created_at, updated_at
  )
  SELECT w.work_key,
         w.work_id || '-CJ1' AS controljob_id,
         CASE WHEN w.work_id LIKE '%W01' THEN 'RUNNING' ELSE 'QUEUED' END AS controljob_state,
         now(), now()
  FROM seed_work_map w
  RETURNING control_job_key, work_key
)
INSERT INTO seed_cj_map(control_job_key, work_key)
SELECT control_job_key, work_key FROM ins;

-- ProcessJob (ControlJob당 2개) + 매핑(pj_seq=1..2)
WITH src AS (
  SELECT cj.control_job_key, cj.work_key, w.work_id, gs AS pj_seq
  FROM seed_cj_map cj
  JOIN seed_work_map w ON w.work_key = cj.work_key
  CROSS JOIN generate_series(1,2) gs
),
ins AS (
  INSERT INTO tc_work_processjob(
    control_job_key, processjob_id, processjob_state, recipe_id, created_at, updated_at
  )
  SELECT s.control_job_key,
         s.work_id || '-PJ' || s.pj_seq::text AS processjob_id,
         CASE WHEN s.pj_seq=1 THEN 'RUNNING' ELSE 'QUEUED' END AS processjob_state,
         'RECIPE_' || s.pj_seq::text AS recipe_id,
         now(), now()
  FROM src s
  RETURNING process_job_key, control_job_key, processjob_id
)
INSERT INTO seed_pj_map(process_job_key, work_key, pj_seq)
SELECT i.process_job_key,
       s.work_key,
       s.pj_seq
FROM ins i
JOIN src s
  ON s.control_job_key = i.control_job_key
 AND i.processjob_id = (s.work_id || '-PJ' || s.pj_seq::text);

-- ProcessJob <-> Lot Map
-- pj_seq=1: INPUT/FORWARD, pj_seq=2: OUTPUT/REVERSE
INSERT INTO tc_work_processjob_lot_map(
  process_job_key, work_lot_key, map_role, map_order, created_at, updated_at
)
SELECT pj.process_job_key,
       l.work_lot_key,
       CASE WHEN pj.pj_seq=1 THEN 'INPUT' ELSE 'OUTPUT' END AS map_role,
       CASE WHEN pj.pj_seq=1 THEN 'FORWARD' ELSE 'REVERSE' END AS map_order,
       now(), now()
FROM seed_pj_map pj
JOIN seed_lot_map l
  ON l.work_key = pj.work_key;

-- =====================================================================
-- 6) RBAC + 사용자 샘플 데이터 (Phase 1 반영)
-- ---------------------------------------------------------------------
-- 목적:
--  - ADMIN / DEVELOPER / OPERATOR 그룹 기준을 표준화
--  - UI API 권한 기준(tc_ui_permission)을 upsert로 고정
--  - 그룹별 권한 매핑을 요구사항 기준으로 재구성
--  - tc_user_info 에 그룹별 사용자 1명(admin/developer/operator) 보장
-- ---------------------------------------------------------------------
-- 참고:
--  - 본 구간은 idempotent 성격으로 작성하여 재실행 시에도 동일 상태를 유지합니다.
--  - sample 계정 비밀번호 평문: ChangeMe123!
--    bcrypt 해시: $2b$10$NEGPGKiIoW.D3MGsubv24ubovk8uyxQIu3AKMoala6v9ILc59ub3G
-- =====================================================================

-- 6-1) 사용자 그룹 upsert
INSERT INTO tc_user_group (
  group_code, group_name, description, is_active, created_at, updated_at
)
VALUES
  ('ADMIN',     '관리자', '모든 권한 보유',                 TRUE, now(), now()),
  ('DEVELOPER', '개발자', 'USER/GROUP 관리 권한 제외',      TRUE, now(), now()),
  ('OPERATOR',  '운영자', 'EQP/DLQ/MODEL 조회 중심 권한',   TRUE, now(), now())
ON CONFLICT (group_code) DO UPDATE
SET
  group_name  = EXCLUDED.group_name,
  description = EXCLUDED.description,
  is_active   = EXCLUDED.is_active,
  updated_at  = now();

-- 6-2) API 권한 upsert
INSERT INTO tc_ui_permission (
  perm_code, perm_name, resource_type, match_type, resource, http_method,
  description, is_active, created_at, updated_at, created_by, updated_by
)
VALUES
  ('AUTH_ME_PERM',     '내 정보 조회',        'API', 'EXACT',  '/auth/me',    'GET',    'GET /auth/me',                    TRUE, now(), now(), 'SEED', 'SEED'),
  ('AUTH_LOGOUT_PERM', '로그아웃',            'API', 'EXACT',  '/auth/logout','POST',   'POST /auth/logout',               TRUE, now(), now(), 'SEED', 'SEED'),
  ('EQP_MANAGE',       '설비 관리',           'API', 'PREFIX', '/api/eqp',    NULL,     'POST/PUT/DELETE/start/end 포함', TRUE, now(), now(), 'SEED', 'SEED'),
  ('ASYNC_READ',       '비동기 결과 조회',     'API', 'PREFIX', '/api/async',  'GET',    'GET /api/async/{traceId}',       TRUE, now(), now(), 'SEED', 'SEED'),
  ('DLQ_READ',         'DLQ 조회',            'API', 'PREFIX', '/api/dlq',    'GET',    'GET /api/dlq/**',                TRUE, now(), now(), 'SEED', 'SEED'),
  ('DLQ_DELETE',       'DLQ 삭제',            'API', 'PREFIX', '/api/dlq',    'DELETE', 'DELETE /api/dlq/**',             TRUE, now(), now(), 'SEED', 'SEED'),
  ('MODEL_READ',       '모델 조회',           'API', 'PREFIX', '/api/model',  'GET',    '향후 모델 조회 API 대비',         TRUE, now(), now(), 'SEED', 'SEED'),
  ('USER_INFO_WRITE',  '사용자 관리',         'API', 'PREFIX', '/api/user',   NULL,     '향후 사용자 생성/수정/삭제 대비',  TRUE, now(), now(), 'SEED', 'SEED'),
  ('GROUP_WRITE',      '그룹 관리',           'API', 'PREFIX', '/api/group',  NULL,     '향후 그룹 생성/수정/삭제 대비',    TRUE, now(), now(), 'SEED', 'SEED')
ON CONFLICT (perm_code) DO UPDATE
SET
  perm_name     = EXCLUDED.perm_name,
  resource_type = EXCLUDED.resource_type,
  match_type    = EXCLUDED.match_type,
  resource      = EXCLUDED.resource,
  http_method   = EXCLUDED.http_method,
  description   = EXCLUDED.description,
  is_active     = EXCLUDED.is_active,
  updated_at    = now(),
  updated_by    = 'SEED';

-- 6-3) 그룹별 권한 매핑 재구성
-- ADMIN/DEVELOPER/OPERATOR 대상 매핑을 먼저 삭제한 뒤 정책 기준으로 재삽입합니다.
DELETE FROM tc_user_group_permission
WHERE group_id IN (
  SELECT group_id
  FROM tc_user_group
  WHERE group_code IN ('ADMIN', 'DEVELOPER', 'OPERATOR')
);

-- ADMIN: 활성 API 권한 전체
INSERT INTO tc_user_group_permission (
  group_id, perm_id, granted_at, granted_by
)
SELECT
  g.group_id, p.perm_id, now(), 'SEED'
FROM tc_user_group g
JOIN tc_ui_permission p
  ON p.resource_type = 'API'
 AND p.is_active = TRUE
WHERE g.group_code = 'ADMIN'
ON CONFLICT (group_id, perm_id) DO NOTHING;

-- DEVELOPER: USER/GROUP 관리 권한 제외
INSERT INTO tc_user_group_permission (
  group_id, perm_id, granted_at, granted_by
)
SELECT
  g.group_id, p.perm_id, now(), 'SEED'
FROM tc_user_group g
JOIN tc_ui_permission p
  ON p.resource_type = 'API'
 AND p.is_active = TRUE
WHERE g.group_code = 'DEVELOPER'
  AND p.perm_code NOT IN ('USER_INFO_WRITE', 'GROUP_WRITE')
ON CONFLICT (group_id, perm_id) DO NOTHING;

-- OPERATOR: EQP/DLQ/MODEL 조회 중심 + 계정 기본 동작
INSERT INTO tc_user_group_permission (
  group_id, perm_id, granted_at, granted_by
)
SELECT
  g.group_id, p.perm_id, now(), 'SEED'
FROM tc_user_group g
JOIN tc_ui_permission p
  ON p.perm_code IN (
    'AUTH_ME_PERM',
    'AUTH_LOGOUT_PERM',
    'ASYNC_READ',
    'DLQ_READ',
    'MODEL_READ'
  )
WHERE g.group_code = 'OPERATOR'
ON CONFLICT (group_id, perm_id) DO NOTHING;

-- 6-4) 그룹별 샘플 사용자 1명 upsert
-- ChangeMe123!
INSERT INTO tc_user_info (
  company, department, user_name, user_id, user_id_norm, password_hash, email, status,
  created_at, updated_at, created_by, updated_by
)
VALUES
  ('NORI', 'Platform',    '관리자 계정', 'admin',     'admin',     '$2b$10$NEGPGKiIoW.D3MGsubv24ubovk8uyxQIu3AKMoala6v9ILc59ub3G', 'admin@nori.local',     'ACTIVE', now(), now(), 'SEED', 'SEED'),
  ('NORI', 'Development', '개발자 계정', 'developer', 'developer', '$2b$10$NEGPGKiIoW.D3MGsubv24ubovk8uyxQIu3AKMoala6v9ILc59ub3G', 'developer@nori.local', 'ACTIVE', now(), now(), 'SEED', 'SEED'),
  ('NORI', 'Operations',  '운영자 계정', 'operator',  'operator',  '$2b$10$NEGPGKiIoW.D3MGsubv24ubovk8uyxQIu3AKMoala6v9ILc59ub3G', 'operator@nori.local',  'ACTIVE', now(), now(), 'SEED', 'SEED')
ON CONFLICT (user_id_norm) DO UPDATE
SET
  company       = EXCLUDED.company,
  department    = EXCLUDED.department,
  user_name     = EXCLUDED.user_name,
  password_hash = EXCLUDED.password_hash,
  email         = EXCLUDED.email,
  status        = EXCLUDED.status,
  updated_at    = now(),
  updated_by    = 'SEED';

-- 6-5) 사용자-그룹 멤버십 재정렬
-- 샘플 3계정에 대한 기존 그룹 멤버십을 제거한 뒤, 정책대로 1:1 재매핑합니다.
DELETE FROM tc_user_group_member
WHERE user_pk IN (
  SELECT user_pk
  FROM tc_user_info
  WHERE user_id_norm IN ('admin', 'developer', 'operator')
);

INSERT INTO tc_user_group_member (
  user_pk, group_id, granted_at, granted_by
)
SELECT
  u.user_pk,
  g.group_id,
  now(),
  'SEED'
FROM tc_user_info u
JOIN tc_user_group g
  ON (
    (u.user_id_norm = 'admin' AND g.group_code = 'ADMIN')
    OR (u.user_id_norm = 'developer' AND g.group_code = 'DEVELOPER')
    OR (u.user_id_norm = 'operator' AND g.group_code = 'OPERATOR')
  )
ON CONFLICT (user_pk, group_id) DO NOTHING;

-- =====================================================================
-- 7) 결과 확인
-- =====================================================================

\echo '--- SEED RESULT CHECK ---'
SELECT 'tc_model'                 AS table_name, COUNT(*) AS cnt FROM tc_model;
SELECT 'tc_eqp'                   AS table_name, COUNT(*) AS cnt FROM tc_eqp;
SELECT 'tc_work'                  AS table_name, COUNT(*) AS cnt FROM tc_work;
SELECT 'tc_user_group'            AS table_name, COUNT(*) AS cnt FROM tc_user_group;
SELECT 'tc_ui_permission'         AS table_name, COUNT(*) AS cnt FROM tc_ui_permission;
SELECT 'tc_user_info'             AS table_name, COUNT(*) AS cnt FROM tc_user_info;
SELECT 'tc_user_group_permission' AS table_name, COUNT(*) AS cnt FROM tc_user_group_permission;
SELECT 'tc_user_group_member'     AS table_name, COUNT(*) AS cnt FROM tc_user_group_member;

COMMIT;

\echo '=== SEED DONE ==='
