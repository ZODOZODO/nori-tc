/* =====================================================================
   차세대 TC Version 5.0 (MySQL) - DDL
   (1) 모든 테이블 DROP
   (2) 모든 테이블 CREATE + 제약/인덱스
   Engine: InnoDB, Charset: utf8mb4
   ===================================================================== */

SET FOREIGN_KEY_CHECKS = 0;

-- (1) DROP (의존성 무시하고 일괄 드롭)
DROP TABLE IF EXISTS tc_msg_send_log;
DROP TABLE IF EXISTS tc_msg_send_queue;

DROP TABLE IF EXISTS tc_ui_auth_session;
DROP TABLE IF EXISTS tc_user_group_permission;
DROP TABLE IF EXISTS tc_user_group_member;
DROP TABLE IF EXISTS tc_user_group;
DROP TABLE IF EXISTS tc_ui_permission;
DROP TABLE IF EXISTS tc_user_info;

DROP TABLE IF EXISTS tc_model_dcop_item;
DROP TABLE IF EXISTS tc_model_mdf;
DROP TABLE IF EXISTS tc_model_workflow;
DROP TABLE IF EXISTS tc_model_eventid;
DROP TABLE IF EXISTS tc_model_reportid;
DROP TABLE IF EXISTS tc_model_variableid;
DROP TABLE IF EXISTS tc_model_socket_message;
DROP TABLE IF EXISTS tc_model_secs_message;
DROP TABLE IF EXISTS tc_model_param;
DROP TABLE IF EXISTS tc_model;

DROP TABLE IF EXISTS tc_work_processjob_lot_map;
DROP TABLE IF EXISTS tc_work_processjob;
DROP TABLE IF EXISTS tc_work_controljob;
DROP TABLE IF EXISTS tc_work_lot;
DROP TABLE IF EXISTS tc_work_carrier_slot;
DROP TABLE IF EXISTS tc_work_carrier;
DROP TABLE IF EXISTS tc_work_param;
DROP TABLE IF EXISTS tc_work;

DROP TABLE IF EXISTS tc_eqp_global;
DROP TABLE IF EXISTS tc_eqp_param;
DROP TABLE IF EXISTS tc_eqp_port_status;
DROP TABLE IF EXISTS tc_eqp_log;
DROP TABLE IF EXISTS tc_eqp_state_hist;
DROP TABLE IF EXISTS tc_eqp_state;
DROP TABLE IF EXISTS tc_eqp_socket;
DROP TABLE IF EXISTS tc_eqp_hsms;
DROP TABLE IF EXISTS tc_eqp;
DROP TABLE IF EXISTS tc_eqp_socket_protocol_type;

SET FOREIGN_KEY_CHECKS = 1;


-----------------------------------------------------------------------
-- (2) CREATE: 부모 -> 자식 순서
-----------------------------------------------------------------------

/* =========================
   2.1 tc_model
   ========================= */
CREATE TABLE tc_model (
  model_key      BIGINT AUTO_INCREMENT NOT NULL,
  model_name     VARCHAR(128) NOT NULL,
  model_version  VARCHAR(32)  NOT NULL,
  comm_interface VARCHAR(16)  NOT NULL,
  status         VARCHAR(16)  NOT NULL,
  maker          VARCHAR(32)  NULL,
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by     VARCHAR(50)  NOT NULL DEFAULT 'SYSTEM',
  updated_by     VARCHAR(50)  NOT NULL DEFAULT 'SYSTEM',

  CONSTRAINT pk_tc_model PRIMARY KEY (model_key),
  CONSTRAINT uk_tc_model_model_name_model_version UNIQUE (model_name, model_version),
  CONSTRAINT ck_tc_model_comm_interface CHECK (comm_interface IN ('HSMS', 'SOCKET')),
  CONSTRAINT ck_tc_model_status CHECK (status IN ('DRAFT', 'ACTIVE', 'DEPRECATED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX ix_tc_model_model_name     ON tc_model (model_name);
CREATE INDEX ix_tc_model_comm_interface ON tc_model (comm_interface);
CREATE INDEX ix_tc_model_status         ON tc_model (status);
CREATE INDEX ix_tc_model_maker          ON tc_model (maker);


CREATE TABLE tc_model_param (
  model_param_key BIGINT AUTO_INCREMENT NOT NULL,
  model_key       BIGINT NOT NULL,
  param_name      VARCHAR(128) NOT NULL,
  param_value     VARCHAR(2000) NULL,
  updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_model_param PRIMARY KEY (model_param_key),
  CONSTRAINT fk_tc_model_param_model_key__tc_model
    FOREIGN KEY (model_key) REFERENCES tc_model(model_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_model_param_model_key_param_name UNIQUE (model_key, param_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX ix_tc_model_param_model_key ON tc_model_param (model_key);


CREATE TABLE tc_model_secs_message (
  secs_msg_key  BIGINT AUTO_INCREMENT NOT NULL,
  model_key     BIGINT NOT NULL,
  secs_msg_name VARCHAR(100) NOT NULL,
  description   VARCHAR(2000) NULL,
  data_index    VARCHAR(200) NULL,
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_model_secs_message PRIMARY KEY (secs_msg_key),
  CONSTRAINT fk_tc_model_secs_message_model_key__tc_model
    FOREIGN KEY (model_key) REFERENCES tc_model(model_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_model_secs_message_model_key_secs_msg_name UNIQUE (model_key, secs_msg_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX ix_tc_model_secs_message_model_key     ON tc_model_secs_message (model_key);
CREATE INDEX ix_tc_model_secs_message_secs_msg_name ON tc_model_secs_message (secs_msg_name);


CREATE TABLE tc_model_socket_message (
  socket_msg_key  BIGINT AUTO_INCREMENT NOT NULL,
  model_key       BIGINT NOT NULL,
  socket_msg_name VARCHAR(100) NOT NULL,
  description     VARCHAR(2000) NULL,
  data_index      VARCHAR(200) NULL,
  updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_model_socket_message PRIMARY KEY (socket_msg_key),
  CONSTRAINT fk_tc_model_socket_message_model_key__tc_model
    FOREIGN KEY (model_key) REFERENCES tc_model(model_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_model_socket_message_model_key_socket_msg_name UNIQUE (model_key, socket_msg_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX ix_tc_model_socket_message_model_key       ON tc_model_socket_message (model_key);
CREATE INDEX ix_tc_model_socket_message_socket_msg_name ON tc_model_socket_message (socket_msg_name);


CREATE TABLE tc_model_variableid (
  variable_key     BIGINT AUTO_INCREMENT NOT NULL,
  model_key        BIGINT NOT NULL,
  variable_id      VARCHAR(100) NOT NULL,
  variable_id_type VARCHAR(10) NOT NULL DEFAULT 'SVID',
  description      VARCHAR(2000) NULL,
  updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_model_variableid PRIMARY KEY (variable_key),
  CONSTRAINT fk_tc_model_variableid_model_key__tc_model
    FOREIGN KEY (model_key) REFERENCES tc_model(model_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_model_variableid_model_key_type_variable_id UNIQUE (model_key, variable_id_type, variable_id),
  CONSTRAINT ck_tc_model_variableid_type CHECK (variable_id_type IN ('SVID', 'DVID', 'ECID', 'CEID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX ix_tc_model_variableid_model_key   ON tc_model_variableid (model_key);
CREATE INDEX ix_tc_model_variableid_variable_id ON tc_model_variableid (variable_id);


CREATE TABLE tc_model_reportid (
  report_key  BIGINT AUTO_INCREMENT NOT NULL,
  model_key   BIGINT NOT NULL,
  report_id   VARCHAR(100) NOT NULL,
  variable_id VARCHAR(1000) NULL,
  enabled     TINYINT(1) NOT NULL DEFAULT 0,
  updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_model_reportid PRIMARY KEY (report_key),
  CONSTRAINT fk_tc_model_reportid_model_key__tc_model
    FOREIGN KEY (model_key) REFERENCES tc_model(model_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_model_reportid_model_key_report_id UNIQUE (model_key, report_id),
  CONSTRAINT ck_tc_model_reportid_enabled CHECK (enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX ix_tc_model_reportid_model_key ON tc_model_reportid (model_key);
CREATE INDEX ix_tc_model_reportid_enabled  ON tc_model_reportid (enabled);


CREATE TABLE tc_model_eventid (
  event_key  BIGINT AUTO_INCREMENT NOT NULL,
  model_key  BIGINT NOT NULL,
  event_id   VARCHAR(100) NOT NULL,
  report_id  VARCHAR(1000) NULL,
  enabled    TINYINT(1) NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_model_eventid PRIMARY KEY (event_key),
  CONSTRAINT fk_tc_model_eventid_model_key__tc_model
    FOREIGN KEY (model_key) REFERENCES tc_model(model_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_model_eventid_model_key_event_id UNIQUE (model_key, event_id),
  CONSTRAINT ck_tc_model_eventid_enabled CHECK (enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX ix_tc_model_eventid_model_key ON tc_model_eventid (model_key);
CREATE INDEX ix_tc_model_eventid_enabled  ON tc_model_eventid (enabled);


CREATE TABLE tc_model_workflow (
  workflow_key      BIGINT AUTO_INCREMENT NOT NULL,
  model_key         BIGINT NOT NULL,
  workflow_name     VARCHAR(200) NOT NULL,
  message_name      VARCHAR(200) NOT NULL,
  event_id          VARCHAR(200) NULL,
  transaction_id    VARCHAR(200) NULL,
  workflow_filter   VARCHAR(200) NULL,
  action_name       VARCHAR(200) NOT NULL,
  action_data_index VARCHAR(1000) NULL,
  updated_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_model_workflow PRIMARY KEY (workflow_key),
  CONSTRAINT fk_tc_model_workflow_model_key__tc_model
    FOREIGN KEY (model_key) REFERENCES tc_model(model_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_model_workflow_model_key_workflow_name_message_name
    UNIQUE (model_key, workflow_name, message_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX ix_tc_model_workflow_model_key     ON tc_model_workflow (model_key);
CREATE INDEX ix_tc_model_workflow_workflow_name ON tc_model_workflow (workflow_name);


CREATE TABLE tc_model_mdf (
  mdf_key    BIGINT AUTO_INCREMENT NOT NULL,
  model_key  BIGINT NOT NULL,
  mdf_name   VARCHAR(100) NOT NULL,
  mdf_file   LONGBLOB NOT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_model_mdf PRIMARY KEY (mdf_key),
  CONSTRAINT fk_tc_model_mdf_model_key__tc_model
    FOREIGN KEY (model_key) REFERENCES tc_model(model_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_model_mdf_model_key_mdf_name UNIQUE (model_key, mdf_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX ix_tc_model_mdf_model_key ON tc_model_mdf (model_key);


CREATE TABLE tc_model_dcop_item (
  dcop_item_key    BIGINT AUTO_INCREMENT NOT NULL,
  model_key        BIGINT NOT NULL,
  dcop_item_name   VARCHAR(200) NOT NULL,
  workflow_name    VARCHAR(200) NULL,
  event_id         VARCHAR(100) NULL,
  variable_id      VARCHAR(100) NULL,
  collection_rule  VARCHAR(10)  NULL,
  calculation_rule VARCHAR(20)  NULL,
  order_rule       INT NULL,
  updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_model_dcop_item PRIMARY KEY (dcop_item_key),
  CONSTRAINT fk_tc_model_dcop_item_model_key__tc_model
    FOREIGN KEY (model_key) REFERENCES tc_model(model_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_model_dcop_item_model_key_dcop_item_name UNIQUE (model_key, dcop_item_name),
  CONSTRAINT ck_tc_model_dcop_item_collection_rule
    CHECK (collection_rule IS NULL OR collection_rule IN ('FIRST','LAST')),
  CONSTRAINT ck_tc_model_dcop_item_calculation_rule
    CHECK (calculation_rule IS NULL OR calculation_rule IN ('ADD','MULTIPLY','SUBTRACT','NONE')),
  CONSTRAINT ck_tc_model_dcop_item_order_rule
    CHECK (order_rule IS NULL OR order_rule >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX ix_tc_model_dcop_item_model_key ON tc_model_dcop_item (model_key);
CREATE INDEX ix_tc_model_dcop_item_event_id  ON tc_model_dcop_item (event_id);



/* =========================
   2.2 tc_eqp + 프로토콜/상태/로그
   ========================= */

CREATE TABLE tc_eqp_socket_protocol_type (
  socket_protocol_type      VARCHAR(32) NOT NULL,
  socket_protocol_type_name VARCHAR(100) NOT NULL,
  parse_start_rule          VARCHAR(1000) NULL,
  parse_end_rule            VARCHAR(1000) NULL,
  parse_regex               VARCHAR(1000) NULL,
  description               VARCHAR(1000) NULL,

  CONSTRAINT pk_tc_eqp_socket_protocol_type PRIMARY KEY (socket_protocol_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE tc_eqp (
  eqp_key        BIGINT AUTO_INCREMENT NOT NULL,
  eqp_id         VARCHAR(64) NOT NULL,
  comm_interface VARCHAR(16) NOT NULL,
  eqp_ip         VARCHAR(45) NOT NULL,
  eqp_port       INT NOT NULL,
  model_key      BIGINT NOT NULL,
  enabled        TINYINT(1) NOT NULL DEFAULT 1,
  created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by     VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
  updated_by     VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',

  CONSTRAINT pk_tc_eqp PRIMARY KEY (eqp_key),
  CONSTRAINT uk_tc_eqp_eqp_id UNIQUE (eqp_id),
  CONSTRAINT fk_tc_eqp_model_key__tc_model FOREIGN KEY (model_key) REFERENCES tc_model(model_key),
  CONSTRAINT ck_tc_eqp_comm_interface CHECK (comm_interface IN ('HSMS','SOCKET')),
  CONSTRAINT ck_tc_eqp_eqp_port CHECK (eqp_port BETWEEN 1 AND 65535),
  CONSTRAINT ck_tc_eqp_enabled CHECK (enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_eqp_enabled        ON tc_eqp (enabled);
CREATE INDEX ix_tc_eqp_model_key      ON tc_eqp (model_key);
CREATE INDEX ix_tc_eqp_comm_interface ON tc_eqp (comm_interface);
CREATE INDEX ix_tc_eqp_eqp_ip_port    ON tc_eqp (eqp_ip, eqp_port);


CREATE TABLE tc_eqp_hsms (
  eqp_key            BIGINT NOT NULL,
  device_id          INT NOT NULL,
  connection_mode    VARCHAR(10) NOT NULL,
  t3_timeout         INT NOT NULL DEFAULT 45,
  t5_timeout         INT NOT NULL DEFAULT 10,
  t6_timeout         INT NOT NULL DEFAULT 5,
  t7_timeout         INT NOT NULL DEFAULT 10,
  t8_timeout         INT NOT NULL DEFAULT 5,
  link_test_enabled  TINYINT(1) NOT NULL DEFAULT 1,
  link_test_interval INT NOT NULL DEFAULT 60,
  max_msg_bytes      BIGINT NOT NULL DEFAULT 10485760,
  created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_eqp_hsms PRIMARY KEY (eqp_key),
  CONSTRAINT fk_tc_eqp_hsms_eqp_key__tc_eqp
    FOREIGN KEY (eqp_key) REFERENCES tc_eqp(eqp_key) ON DELETE CASCADE,

  CONSTRAINT ck_tc_eqp_hsms_device_id CHECK (device_id BETWEEN 0 AND 32767),
  CONSTRAINT ck_tc_eqp_hsms_connection_mode CHECK (connection_mode IN ('ACTIVE','PASSIVE')),
  CONSTRAINT ck_tc_eqp_hsms_t3_timeout CHECK (t3_timeout > 0),
  CONSTRAINT ck_tc_eqp_hsms_t5_timeout CHECK (t5_timeout > 0),
  CONSTRAINT ck_tc_eqp_hsms_t6_timeout CHECK (t6_timeout > 0),
  CONSTRAINT ck_tc_eqp_hsms_t7_timeout CHECK (t7_timeout > 0),
  CONSTRAINT ck_tc_eqp_hsms_t8_timeout CHECK (t8_timeout > 0),
  CONSTRAINT ck_tc_eqp_hsms_link_test_enabled CHECK (link_test_enabled IN (0,1)),
  CONSTRAINT ck_tc_eqp_hsms_link_test_interval CHECK (link_test_interval > 0),
  CONSTRAINT ck_tc_eqp_hsms_max_msg_bytes CHECK (max_msg_bytes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE tc_eqp_socket (
  eqp_key              BIGINT NOT NULL,
  socket_protocol_type VARCHAR(32) NOT NULL,
  connection_mode      VARCHAR(10) NOT NULL,
  charset              VARCHAR(20) NOT NULL DEFAULT 'UTF-8',
  heartbeat_enabled    TINYINT(1) NOT NULL DEFAULT 1,
  heartbeat_interval   INT NOT NULL DEFAULT 30,
  read_timeout         INT NOT NULL DEFAULT 0,
  write_timeout        INT NOT NULL DEFAULT 0,
  max_frame_size_bytes INT NOT NULL DEFAULT 8192,
  keep_alive_enabled   TINYINT(1) NOT NULL DEFAULT 1,
  created_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_eqp_socket PRIMARY KEY (eqp_key),
  CONSTRAINT fk_tc_eqp_socket_eqp_key__tc_eqp
    FOREIGN KEY (eqp_key) REFERENCES tc_eqp(eqp_key) ON DELETE CASCADE,
  CONSTRAINT fk_tc_eqp_socket_socket_protocol_type__tc_eqp_socket_protocol_type
    FOREIGN KEY (socket_protocol_type) REFERENCES tc_eqp_socket_protocol_type(socket_protocol_type),

  CONSTRAINT ck_tc_eqp_socket_connection_mode CHECK (connection_mode IN ('ACTIVE','PASSIVE')),
  CONSTRAINT ck_tc_eqp_socket_heartbeat_enabled CHECK (heartbeat_enabled IN (0,1)),
  CONSTRAINT ck_tc_eqp_socket_heartbeat_interval CHECK (heartbeat_interval >= 0),
  CONSTRAINT ck_tc_eqp_socket_read_timeout CHECK (read_timeout >= 0),
  CONSTRAINT ck_tc_eqp_socket_write_timeout CHECK (write_timeout >= 0),
  CONSTRAINT ck_tc_eqp_socket_max_frame_size_bytes CHECK (max_frame_size_bytes > 0),
  CONSTRAINT ck_tc_eqp_socket_keep_alive_enabled CHECK (keep_alive_enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_eqp_socket_socket_protocol_type ON tc_eqp_socket (socket_protocol_type);


CREATE TABLE tc_eqp_state (
  eqp_key       BIGINT NOT NULL,
  control_state VARCHAR(20) NULL,
  eqp_state     VARCHAR(20) NULL,
  since_at      DATETIME(3) NULL,
  reason_code   VARCHAR(50) NULL,
  reason_detail TEXT NULL,
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_eqp_state PRIMARY KEY (eqp_key),
  CONSTRAINT fk_tc_eqp_state_eqp_key__tc_eqp
    FOREIGN KEY (eqp_key) REFERENCES tc_eqp(eqp_key) ON DELETE CASCADE,

  CONSTRAINT ck_tc_eqp_state_control_state CHECK (control_state IS NULL OR control_state IN ('OFFLINE','LOCAL','REMOTE')),
  CONSTRAINT ck_tc_eqp_state_eqp_state CHECK (eqp_state IS NULL OR eqp_state IN ('IDLE','RUN','DOWN','MAINTENANCE','PAUSE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE tc_eqp_state_hist (
  state_hist_key BIGINT AUTO_INCREMENT NOT NULL,
  eqp_key        BIGINT NOT NULL,
  state_type     VARCHAR(10) NOT NULL,
  from_state     VARCHAR(50) NULL,
  to_state       VARCHAR(50) NULL,
  changed_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reason_code    VARCHAR(50) NULL,
  reason_detail  TEXT NULL,

  CONSTRAINT pk_tc_eqp_state_hist PRIMARY KEY (state_hist_key),
  CONSTRAINT fk_tc_eqp_state_hist_eqp_key__tc_eqp
    FOREIGN KEY (eqp_key) REFERENCES tc_eqp(eqp_key) ON DELETE CASCADE,
  CONSTRAINT ck_tc_eqp_state_hist_state_type CHECK (state_type IN ('OPER','CONN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_eqp_state_hist_eqp_key_changed_at
  ON tc_eqp_state_hist (eqp_key, changed_at);
CREATE INDEX ix_tc_eqp_state_hist_state_type_changed_at
  ON tc_eqp_state_hist (state_type, changed_at);


CREATE TABLE tc_eqp_log (
  eqp_key            BIGINT NOT NULL,
  log_level          VARCHAR(10) NOT NULL DEFAULT 'INFO',
  log_retention_days INT NOT NULL DEFAULT 30,
  log_path           VARCHAR(1000) NULL,
  updated_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_eqp_log PRIMARY KEY (eqp_key),
  CONSTRAINT fk_tc_eqp_log_eqp_key__tc_eqp
    FOREIGN KEY (eqp_key) REFERENCES tc_eqp(eqp_key) ON DELETE CASCADE,
  CONSTRAINT ck_tc_eqp_log_log_level CHECK (log_level IN ('TRACE','DEBUG','INFO','WARN','ERROR')),
  CONSTRAINT ck_tc_eqp_log_log_retention_days CHECK (log_retention_days >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE tc_eqp_port_status (
  eqp_port_status_key BIGINT AUTO_INCREMENT NOT NULL,
  eqp_key             BIGINT NOT NULL,
  port_id             VARCHAR(20) NOT NULL,
  port_type           VARCHAR(20) NULL,
  port_state          VARCHAR(20) NULL,
  carrier_id          VARCHAR(64) NULL,
  carrier_type        VARCHAR(20) NULL,
  carrier_state       VARCHAR(20) NULL,
  updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_eqp_port_status PRIMARY KEY (eqp_port_status_key),
  CONSTRAINT fk_tc_eqp_port_status_eqp_key__tc_eqp
    FOREIGN KEY (eqp_key) REFERENCES tc_eqp(eqp_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_eqp_port_status_eqp_key_port_id UNIQUE (eqp_key, port_id),

  CONSTRAINT ck_tc_eqp_port_status_port_type
    CHECK (port_type IS NULL OR port_type IN ('LOAD_PORT','UNLOAD_PORT','INTERNAL_BUFFER','OTHER')),
  CONSTRAINT ck_tc_eqp_port_status_port_state
    CHECK (port_state IS NULL OR port_state IN ('EMPTY','LOADED','READY_TO_LOAD','DOWN','IN_SERVICE','UNKNOWN')),
  CONSTRAINT ck_tc_eqp_port_status_carrier_type
    CHECK (carrier_type IS NULL OR carrier_type IN ('FOUP','CASSETTE','WAFER_BOX','TRAY','OTHER')),
  CONSTRAINT ck_tc_eqp_port_status_carrier_state
    CHECK (carrier_state IS NULL OR carrier_state IN ('CLAMPED','UNCLAMPED','OPENED','CLOSED','UNKNOWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_eqp_port_status_eqp_key     ON tc_eqp_port_status (eqp_key);
CREATE INDEX ix_tc_eqp_port_status_port_id    ON tc_eqp_port_status (port_id);
CREATE INDEX ix_tc_eqp_port_status_carrier_id ON tc_eqp_port_status (carrier_id);


CREATE TABLE tc_eqp_param (
  eqp_param_key BIGINT AUTO_INCREMENT NOT NULL,
  eqp_key       BIGINT NOT NULL,
  param_name    VARCHAR(100) NOT NULL,
  param_version VARCHAR(100) NOT NULL,
  param_value   TEXT NULL,
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_eqp_param PRIMARY KEY (eqp_param_key),
  CONSTRAINT fk_tc_eqp_param_eqp_key__tc_eqp
    FOREIGN KEY (eqp_key) REFERENCES tc_eqp(eqp_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_eqp_param_eqp_key_param_name_param_version UNIQUE (eqp_key, param_name, param_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_eqp_param_eqp_key    ON tc_eqp_param (eqp_key);
CREATE INDEX ix_tc_eqp_param_param_name ON tc_eqp_param (param_name);


CREATE TABLE tc_eqp_global (
  eqp_global_key BIGINT AUTO_INCREMENT NOT NULL,
  eqp_key        BIGINT NOT NULL,
  param_name     VARCHAR(100) NOT NULL,
  param_value    TEXT NULL,
  updated_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_eqp_global PRIMARY KEY (eqp_global_key),
  CONSTRAINT fk_tc_eqp_global_eqp_key__tc_eqp
    FOREIGN KEY (eqp_key) REFERENCES tc_eqp(eqp_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_eqp_global_eqp_key_param_name UNIQUE (eqp_key, param_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_eqp_global_eqp_key ON tc_eqp_global (eqp_key);



/* =========================
   2.3 tc_work
   ========================= */

CREATE TABLE tc_work (
  work_key    BIGINT AUTO_INCREMENT NOT NULL,
  eqp_key     BIGINT NOT NULL,
  work_id     VARCHAR(64) NOT NULL,
  operator_id VARCHAR(64) NULL,
  step_seq    INT NULL,
  work_state  VARCHAR(20) NOT NULL,
  start_time  DATETIME(3) NULL,
  end_time    DATETIME(3) NULL,
  mes_message TEXT NULL,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_work PRIMARY KEY (work_key),
  CONSTRAINT fk_tc_work_eqp_key__tc_eqp FOREIGN KEY (eqp_key) REFERENCES tc_eqp(eqp_key),
  CONSTRAINT uk_tc_work_eqp_key_work_id UNIQUE (eqp_key, work_id),
  CONSTRAINT ck_tc_work_step_seq CHECK (step_seq IS NULL OR step_seq >= 0),
  CONSTRAINT ck_tc_work_work_state CHECK (work_state IN ('QUEUED','RUNNING','COMPLETED','ABORTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_work_eqp_key    ON tc_work (eqp_key);
CREATE INDEX ix_tc_work_work_state ON tc_work (work_state);
CREATE INDEX ix_tc_work_created_at ON tc_work (created_at);


CREATE TABLE tc_work_param (
  work_param_key BIGINT AUTO_INCREMENT NOT NULL,
  work_key       BIGINT NOT NULL,
  param_name     VARCHAR(100) NOT NULL,
  param_value    VARCHAR(2000) NULL,
  updated_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_work_param PRIMARY KEY (work_param_key),
  CONSTRAINT fk_tc_work_param_work_key__tc_work
    FOREIGN KEY (work_key) REFERENCES tc_work(work_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_work_param_work_key_param_name UNIQUE (work_key, param_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_work_param_work_key ON tc_work_param (work_key);


CREATE TABLE tc_work_carrier (
  work_carrier_key BIGINT AUTO_INCREMENT NOT NULL,
  work_key         BIGINT NOT NULL,
  carrier_id       VARCHAR(64) NOT NULL,
  port_id          VARCHAR(20) NULL,
  slot_map         VARCHAR(255) NULL,
  total_qty        INT NULL,
  good_qty         INT NULL,
  scrap_qty        INT NULL,
  updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_work_carrier PRIMARY KEY (work_carrier_key),
  CONSTRAINT fk_tc_work_carrier_work_key__tc_work
    FOREIGN KEY (work_key) REFERENCES tc_work(work_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_work_carrier_work_key_carrier_id UNIQUE (work_key, carrier_id),
  CONSTRAINT ck_tc_work_carrier_total_qty CHECK (total_qty IS NULL OR total_qty >= 0),
  CONSTRAINT ck_tc_work_carrier_good_qty  CHECK (good_qty  IS NULL OR good_qty  >= 0),
  CONSTRAINT ck_tc_work_carrier_scrap_qty CHECK (scrap_qty IS NULL OR scrap_qty >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_work_carrier_work_key   ON tc_work_carrier (work_key);
CREATE INDEX ix_tc_work_carrier_carrier_id ON tc_work_carrier (carrier_id);
CREATE INDEX ix_tc_work_carrier_port_id    ON tc_work_carrier (port_id);


CREATE TABLE tc_work_carrier_slot (
  carrier_slot_key BIGINT AUTO_INCREMENT NOT NULL,
  work_carrier_key BIGINT NOT NULL,
  slot_no          INT NOT NULL,
  slot_state       VARCHAR(50) NOT NULL, -- (표 명세) CHECK 없음
  lot_id           VARCHAR(64) NULL,
  updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_work_carrier_slot PRIMARY KEY (carrier_slot_key),
  CONSTRAINT fk_tc_work_carrier_slot_work_carrier_key__tc_work_carrier
    FOREIGN KEY (work_carrier_key) REFERENCES tc_work_carrier(work_carrier_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_work_carrier_slot_work_carrier_key_slot_no UNIQUE (work_carrier_key, slot_no),
  CONSTRAINT ck_tc_work_carrier_slot_slot_no CHECK (slot_no >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_work_carrier_slot_work_carrier_key ON tc_work_carrier_slot (work_carrier_key);
CREATE INDEX ix_tc_work_carrier_slot_lot_id           ON tc_work_carrier_slot (lot_id);


CREATE TABLE tc_work_lot (
  work_lot_key  BIGINT AUTO_INCREMENT NOT NULL,
  work_key      BIGINT NOT NULL,
  carrier_id    VARCHAR(64) NULL,
  lot_id        VARCHAR(64) NOT NULL,
  parent_lot_id VARCHAR(64) NULL,
  chamber_id    VARCHAR(64) NULL,
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_work_lot PRIMARY KEY (work_lot_key),
  CONSTRAINT fk_tc_work_lot_work_key__tc_work
    FOREIGN KEY (work_key) REFERENCES tc_work(work_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_work_lot_work_key_lot_id UNIQUE (work_key, lot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_work_lot_work_key   ON tc_work_lot (work_key);
CREATE INDEX ix_tc_work_lot_lot_id     ON tc_work_lot (lot_id);
CREATE INDEX ix_tc_work_lot_carrier_id ON tc_work_lot (carrier_id);


CREATE TABLE tc_work_controljob (
  control_job_key  BIGINT AUTO_INCREMENT NOT NULL,
  work_key         BIGINT NOT NULL,
  controljob_id    VARCHAR(64) NOT NULL,
  controljob_state VARCHAR(20) NOT NULL,
  created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_work_controljob PRIMARY KEY (control_job_key),
  CONSTRAINT fk_tc_work_controljob_work_key__tc_work
    FOREIGN KEY (work_key) REFERENCES tc_work(work_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_work_controljob_work_key_controljob_id UNIQUE (work_key, controljob_id),
  CONSTRAINT ck_tc_work_controljob_state
    CHECK (controljob_state IN ('CREATED','QUEUED','RUNNING','PAUSED','COMPLETED','ABORTED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_work_controljob_work_key      ON tc_work_controljob (work_key);
CREATE INDEX ix_tc_work_controljob_controljob_id ON tc_work_controljob (controljob_id);


CREATE TABLE tc_work_processjob (
  process_job_key  BIGINT AUTO_INCREMENT NOT NULL,
  control_job_key  BIGINT NOT NULL,
  processjob_id    VARCHAR(64) NOT NULL,
  processjob_state VARCHAR(20) NOT NULL,
  recipe_id        VARCHAR(128) NOT NULL,
  created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_work_processjob PRIMARY KEY (process_job_key),
  CONSTRAINT fk_tc_work_processjob_control_job_key__tc_work_controljob
    FOREIGN KEY (control_job_key) REFERENCES tc_work_controljob(control_job_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_work_processjob_control_job_key_processjob_id UNIQUE (control_job_key, processjob_id),
  CONSTRAINT ck_tc_work_processjob_state
    CHECK (processjob_state IN ('CREATED','QUEUED','RUNNING','PAUSED','COMPLETED','ABORTED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_work_processjob_control_job_key ON tc_work_processjob (control_job_key);
CREATE INDEX ix_tc_work_processjob_processjob_id   ON tc_work_processjob (processjob_id);


CREATE TABLE tc_work_processjob_lot_map (
  pj_lot_map_key  BIGINT AUTO_INCREMENT NOT NULL,
  process_job_key BIGINT NOT NULL,
  work_lot_key    BIGINT NOT NULL,
  map_role        VARCHAR(20) NULL,
  map_order       VARCHAR(20) NULL,
  created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_work_processjob_lot_map PRIMARY KEY (pj_lot_map_key),
  CONSTRAINT fk_tc_work_pj_lot_map_process_job_key__tc_work_processjob
    FOREIGN KEY (process_job_key) REFERENCES tc_work_processjob(process_job_key) ON DELETE CASCADE,
  CONSTRAINT fk_tc_work_pj_lot_map_work_lot_key__tc_work_lot
    FOREIGN KEY (work_lot_key) REFERENCES tc_work_lot(work_lot_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_work_pj_lot_map_process_job_key_work_lot_key UNIQUE (process_job_key, work_lot_key),
  CONSTRAINT ck_tc_work_pj_lot_map_map_order CHECK (map_order IS NULL OR map_order IN ('FORWARD','REVERSE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_work_pj_lot_map_process_job_key ON tc_work_processjob_lot_map (process_job_key);
CREATE INDEX ix_tc_work_pj_lot_map_work_lot_key    ON tc_work_processjob_lot_map (work_lot_key);



/* =========================
   2.4 UI / 권한
   ========================= */

CREATE TABLE tc_user_info (
  user_pk       BIGINT AUTO_INCREMENT NOT NULL,
  company       VARCHAR(100) NOT NULL,
  department    VARCHAR(100) NOT NULL,
  user_name     VARCHAR(100) NOT NULL,
  user_id       VARCHAR(50)  NOT NULL,
  user_id_norm  VARCHAR(50)  NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  email         VARCHAR(255) NOT NULL,
  status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    VARCHAR(50)  NOT NULL DEFAULT 'SYSTEM',
  updated_by    VARCHAR(50)  NOT NULL DEFAULT 'SYSTEM',

  CONSTRAINT pk_tc_user_info PRIMARY KEY (user_pk),
  CONSTRAINT uk_tc_user_info_user_id_norm UNIQUE (user_id_norm),
  CONSTRAINT uk_tc_user_info_email UNIQUE (email),
  CONSTRAINT ck_tc_user_info_status CHECK (status IN ('ACTIVE','LOCKED','DISABLED','DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_user_info_company_department ON tc_user_info (company, department);
CREATE INDEX ix_tc_user_info_status            ON tc_user_info (status);
CREATE INDEX ix_tc_user_info_email             ON tc_user_info (email);


CREATE TABLE tc_ui_permission (
  perm_id       BIGINT AUTO_INCREMENT NOT NULL,
  perm_code     VARCHAR(80)  NOT NULL,
  perm_name     VARCHAR(120) NOT NULL,
  resource_type VARCHAR(10)  NOT NULL,
  match_type    VARCHAR(10)  NOT NULL DEFAULT 'PREFIX',
  resource      VARCHAR(255) NOT NULL,
  http_method   VARCHAR(10)  NULL,
  description   VARCHAR(1000) NULL,
  is_active     TINYINT(1) NOT NULL DEFAULT 1,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
  updated_by    VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',

  CONSTRAINT pk_tc_ui_permission PRIMARY KEY (perm_id),
  CONSTRAINT uk_tc_ui_permission_perm_code UNIQUE (perm_code),
  CONSTRAINT ck_tc_ui_permission_resource_type CHECK (resource_type IN ('PAGE','API')),
  CONSTRAINT ck_tc_ui_permission_match_type CHECK (match_type IN ('EXACT','PREFIX','REGEX')),
  CONSTRAINT ck_tc_ui_permission_is_active CHECK (is_active IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_ui_permission_resource_type_resource ON tc_ui_permission (resource_type, resource);
CREATE INDEX ix_tc_ui_permission_is_active             ON tc_ui_permission (is_active);


CREATE TABLE tc_user_group (
  group_id    BIGINT AUTO_INCREMENT NOT NULL,
  group_code  VARCHAR(50)  NOT NULL,
  group_name  VARCHAR(100) NOT NULL,
  description VARCHAR(1000) NULL,
  is_active   TINYINT(1) NOT NULL DEFAULT 1,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_user_group PRIMARY KEY (group_id),
  CONSTRAINT uk_tc_user_group_group_code UNIQUE (group_code),
  CONSTRAINT ck_tc_user_group_is_active CHECK (is_active IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_user_group_is_active ON tc_user_group (is_active);


CREATE TABLE tc_user_group_member (
  ugm_key     BIGINT AUTO_INCREMENT NOT NULL,
  user_pk     BIGINT NOT NULL,
  group_id    BIGINT NOT NULL,
  granted_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  granted_by  VARCHAR(50) NULL,

  CONSTRAINT pk_tc_user_group_member PRIMARY KEY (ugm_key),
  CONSTRAINT fk_tc_user_group_member_user_pk__tc_user_info
    FOREIGN KEY (user_pk) REFERENCES tc_user_info(user_pk) ON DELETE CASCADE,
  CONSTRAINT fk_tc_user_group_member_group_id__tc_user_group
    FOREIGN KEY (group_id) REFERENCES tc_user_group(group_id) ON DELETE CASCADE,
  CONSTRAINT uk_tc_user_group_member_user_pk_group_id UNIQUE (user_pk, group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_user_group_member_group_id ON tc_user_group_member (group_id);
CREATE INDEX ix_tc_user_group_member_user_pk  ON tc_user_group_member (user_pk);


CREATE TABLE tc_user_group_permission (
  ugp_key    BIGINT AUTO_INCREMENT NOT NULL,
  group_id   BIGINT NOT NULL,
  perm_id    BIGINT NOT NULL,
  granted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  granted_by VARCHAR(50) NULL,

  CONSTRAINT pk_tc_user_group_permission PRIMARY KEY (ugp_key),
  CONSTRAINT fk_tc_user_group_permission_group_id__tc_user_group
    FOREIGN KEY (group_id) REFERENCES tc_user_group(group_id) ON DELETE CASCADE,
  CONSTRAINT fk_tc_user_group_permission_perm_id__tc_ui_permission
    FOREIGN KEY (perm_id) REFERENCES tc_ui_permission(perm_id) ON DELETE CASCADE,
  CONSTRAINT uk_tc_user_group_permission_group_id_perm_id UNIQUE (group_id, perm_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_user_group_permission_perm_id  ON tc_user_group_permission (perm_id);
CREATE INDEX ix_tc_user_group_permission_group_id ON tc_user_group_permission (group_id);


CREATE TABLE tc_ui_auth_session (
  token        VARCHAR(64) NOT NULL,
  user_pk      BIGINT NOT NULL,
  issued_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  expires_at   DATETIME(3) NOT NULL,
  last_seen_at DATETIME(3) NULL,
  revoked      TINYINT(1) NOT NULL DEFAULT 0,

  CONSTRAINT pk_tc_ui_auth_session PRIMARY KEY (token),
  CONSTRAINT fk_tc_ui_auth_session_user_pk__tc_user_info
    FOREIGN KEY (user_pk) REFERENCES tc_user_info(user_pk),
  CONSTRAINT ck_tc_ui_auth_session_revoked CHECK (revoked IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_ui_auth_session_user_pk            ON tc_ui_auth_session (user_pk);
CREATE INDEX ix_tc_ui_auth_session_expires_at         ON tc_ui_auth_session (expires_at);
CREATE INDEX ix_tc_ui_auth_session_revoked_expires_at ON tc_ui_auth_session (revoked, expires_at);



/* =========================
   2.5 Outbox (tc_msg_send_queue / tc_msg_send_log)
   ========================= */

CREATE TABLE tc_msg_send_queue (
  msg_key         BIGINT AUTO_INCREMENT NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  topic           VARCHAR(200) NOT NULL,
  message_key     VARCHAR(200) NULL,
  headers_json    TEXT NULL,
  payload_json    TEXT NOT NULL,
  status          VARCHAR(16) NOT NULL,
  retry_count     INT NOT NULL DEFAULT 0,
  next_retry_at   DATETIME(3) NULL,
  locked_by       VARCHAR(64) NULL,
  locked_until    DATETIME(3) NULL,
  created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_msg_send_queue PRIMARY KEY (msg_key),
  CONSTRAINT uk_tc_msg_send_queue_topic_idempotency_key UNIQUE (topic, idempotency_key),
  CONSTRAINT ck_tc_msg_send_queue_status CHECK (status IN ('PENDING','SENDING','SENT','FAILED','DEAD')),
  CONSTRAINT ck_tc_msg_send_queue_retry_count CHECK (retry_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_msg_send_queue_status_next_retry_at ON tc_msg_send_queue (status, next_retry_at);
CREATE INDEX ix_tc_msg_send_queue_locked_until         ON tc_msg_send_queue (locked_until);
CREATE INDEX ix_tc_msg_send_queue_topic_status         ON tc_msg_send_queue (topic, status);


CREATE TABLE tc_msg_send_log (
  send_log_key    BIGINT AUTO_INCREMENT NOT NULL,
  msg_key         BIGINT NOT NULL,
  attempt_no      INT NOT NULL,
  result          VARCHAR(16) NOT NULL,
  kafka_partition INT NULL,
  kafka_offset    BIGINT NULL,
  error_code      VARCHAR(64) NULL,
  error_message   TEXT NULL,
  sent_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_tc_msg_send_log PRIMARY KEY (send_log_key),
  CONSTRAINT fk_tc_msg_send_log_msg_key__tc_msg_send_queue
    FOREIGN KEY (msg_key) REFERENCES tc_msg_send_queue(msg_key) ON DELETE CASCADE,
  CONSTRAINT ck_tc_msg_send_log_attempt_no CHECK (attempt_no >= 1),
  CONSTRAINT ck_tc_msg_send_log_result CHECK (result IN ('SUCCESS','FAIL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_tc_msg_send_log_msg_key_attempt_no ON tc_msg_send_log (msg_key, attempt_no);
CREATE INDEX ix_tc_msg_send_log_sent_at           ON tc_msg_send_log (sent_at);
