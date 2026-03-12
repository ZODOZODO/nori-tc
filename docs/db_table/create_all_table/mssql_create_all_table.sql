/* =====================================================================
   차세대 TC Version 5.0 (MSSQL) - DDL
   (1) 모든 테이블 DROP
   (2) 모든 테이블 CREATE + 제약/인덱스
   대상 스키마: dbo
   ===================================================================== */

-----------------------------------------------------------------------
-- (1) DROP: 의존성(FOREIGN KEY) 고려하여 자식 -> 부모 순서로 삭제
-----------------------------------------------------------------------
IF OBJECT_ID('dbo.tc_msg_send_log', 'U') IS NOT NULL DROP TABLE dbo.tc_msg_send_log;
IF OBJECT_ID('dbo.tc_msg_send_queue', 'U') IS NOT NULL DROP TABLE dbo.tc_msg_send_queue;

IF OBJECT_ID('dbo.tc_ui_auth_session', 'U') IS NOT NULL DROP TABLE dbo.tc_ui_auth_session;
IF OBJECT_ID('dbo.tc_user_group_permission', 'U') IS NOT NULL DROP TABLE dbo.tc_user_group_permission;
IF OBJECT_ID('dbo.tc_user_group_member', 'U') IS NOT NULL DROP TABLE dbo.tc_user_group_member;
IF OBJECT_ID('dbo.tc_user_group', 'U') IS NOT NULL DROP TABLE dbo.tc_user_group;
IF OBJECT_ID('dbo.tc_ui_permission', 'U') IS NOT NULL DROP TABLE dbo.tc_ui_permission;
IF OBJECT_ID('dbo.tc_user_info', 'U') IS NOT NULL DROP TABLE dbo.tc_user_info;

IF OBJECT_ID('dbo.tc_model_dcop_item', 'U') IS NOT NULL DROP TABLE dbo.tc_model_dcop_item;
IF OBJECT_ID('dbo.tc_model_mdf', 'U') IS NOT NULL DROP TABLE dbo.tc_model_mdf;
IF OBJECT_ID('dbo.tc_model_workflow', 'U') IS NOT NULL DROP TABLE dbo.tc_model_workflow;
IF OBJECT_ID('dbo.tc_model_eventid', 'U') IS NOT NULL DROP TABLE dbo.tc_model_eventid;
IF OBJECT_ID('dbo.tc_model_reportid', 'U') IS NOT NULL DROP TABLE dbo.tc_model_reportid;
IF OBJECT_ID('dbo.tc_model_variableid', 'U') IS NOT NULL DROP TABLE dbo.tc_model_variableid;
IF OBJECT_ID('dbo.tc_model_socket_message', 'U') IS NOT NULL DROP TABLE dbo.tc_model_socket_message;
IF OBJECT_ID('dbo.tc_model_secs_message', 'U') IS NOT NULL DROP TABLE dbo.tc_model_secs_message;
IF OBJECT_ID('dbo.tc_model_param', 'U') IS NOT NULL DROP TABLE dbo.tc_model_param;
IF OBJECT_ID('dbo.tc_model_version', 'U') IS NOT NULL DROP TABLE dbo.tc_model_version;
IF OBJECT_ID('dbo.tc_model', 'U') IS NOT NULL DROP TABLE dbo.tc_model;

IF OBJECT_ID('dbo.tc_work_processjob_lot_map', 'U') IS NOT NULL DROP TABLE dbo.tc_work_processjob_lot_map;
IF OBJECT_ID('dbo.tc_work_processjob', 'U') IS NOT NULL DROP TABLE dbo.tc_work_processjob;
IF OBJECT_ID('dbo.tc_work_controljob', 'U') IS NOT NULL DROP TABLE dbo.tc_work_controljob;
IF OBJECT_ID('dbo.tc_work_lot', 'U') IS NOT NULL DROP TABLE dbo.tc_work_lot;
IF OBJECT_ID('dbo.tc_work_carrier_slot', 'U') IS NOT NULL DROP TABLE dbo.tc_work_carrier_slot;
IF OBJECT_ID('dbo.tc_work_carrier', 'U') IS NOT NULL DROP TABLE dbo.tc_work_carrier;
IF OBJECT_ID('dbo.tc_work_param', 'U') IS NOT NULL DROP TABLE dbo.tc_work_param;
IF OBJECT_ID('dbo.tc_work', 'U') IS NOT NULL DROP TABLE dbo.tc_work;

IF OBJECT_ID('dbo.tc_eqp_global', 'U') IS NOT NULL DROP TABLE dbo.tc_eqp_global;
IF OBJECT_ID('dbo.tc_eqp_param_version', 'U') IS NOT NULL DROP TABLE dbo.tc_eqp_param_version;
IF OBJECT_ID('dbo.tc_eqp_param', 'U') IS NOT NULL DROP TABLE dbo.tc_eqp_param;
IF OBJECT_ID('dbo.tc_eqp_port_status', 'U') IS NOT NULL DROP TABLE dbo.tc_eqp_port_status;
IF OBJECT_ID('dbo.tc_eqp_log', 'U') IS NOT NULL DROP TABLE dbo.tc_eqp_log;
IF OBJECT_ID('dbo.tc_eqp_state_hist', 'U') IS NOT NULL DROP TABLE dbo.tc_eqp_state_hist;
IF OBJECT_ID('dbo.tc_eqp_state', 'U') IS NOT NULL DROP TABLE dbo.tc_eqp_state;
IF OBJECT_ID('dbo.tc_eqp_socket', 'U') IS NOT NULL DROP TABLE dbo.tc_eqp_socket;
IF OBJECT_ID('dbo.tc_eqp_hsms', 'U') IS NOT NULL DROP TABLE dbo.tc_eqp_hsms;
IF OBJECT_ID('dbo.tc_eqp', 'U') IS NOT NULL DROP TABLE dbo.tc_eqp;
IF OBJECT_ID('dbo.tc_eqp_socket_protocol_type', 'U') IS NOT NULL DROP TABLE dbo.tc_eqp_socket_protocol_type;
GO


-----------------------------------------------------------------------
-- (2) CREATE: 부모 -> 자식 순서로 생성
-----------------------------------------------------------------------

/* =========================
   2.1 tc_model
   ========================= */
CREATE TABLE dbo.tc_model (
  model_key      BIGINT IDENTITY(1,1) NOT NULL,
  model_name     NVARCHAR(128) NOT NULL,
  comm_interface NVARCHAR(16)  NOT NULL,
  maker          NVARCHAR(32)  NULL,
  created_at     DATETIME2(3)  NOT NULL CONSTRAINT df_tc_model_created_at DEFAULT (SYSUTCDATETIME()),
  updated_at     DATETIME2(3)  NOT NULL CONSTRAINT df_tc_model_updated_at DEFAULT (SYSUTCDATETIME()),
  created_by     NVARCHAR(50)  NOT NULL CONSTRAINT df_tc_model_created_by DEFAULT (N'SYSTEM'),
  updated_by     NVARCHAR(50)  NOT NULL CONSTRAINT df_tc_model_updated_by DEFAULT (N'SYSTEM'),

  CONSTRAINT pk_tc_model PRIMARY KEY (model_key),
  CONSTRAINT uk_tc_model_model_name UNIQUE (model_name),
  CONSTRAINT ck_tc_model_comm_interface CHECK (comm_interface IN (N'HSMS', N'SOCKET'))
);
GO

CREATE INDEX ix_tc_model_model_name     ON dbo.tc_model (model_name);
CREATE INDEX ix_tc_model_comm_interface ON dbo.tc_model (comm_interface);
CREATE INDEX ix_tc_model_maker          ON dbo.tc_model (maker);
GO

CREATE TABLE dbo.tc_model_version (
  model_version_key BIGINT IDENTITY(1,1) NOT NULL,
  model_key         BIGINT NOT NULL,
  model_version     NVARCHAR(32) NOT NULL,
  status            NVARCHAR(16) NOT NULL,
  created_at        DATETIME2(3) NOT NULL CONSTRAINT df_tc_model_version_created_at DEFAULT (SYSUTCDATETIME()),
  updated_at        DATETIME2(3) NOT NULL CONSTRAINT df_tc_model_version_updated_at DEFAULT (SYSUTCDATETIME()),
  created_by        NVARCHAR(50) NOT NULL CONSTRAINT df_tc_model_version_created_by DEFAULT (N'SYSTEM'),
  updated_by        NVARCHAR(50) NOT NULL CONSTRAINT df_tc_model_version_updated_by DEFAULT (N'SYSTEM'),

  CONSTRAINT pk_tc_model_version PRIMARY KEY (model_version_key),
  CONSTRAINT fk_tc_model_version_model_key__tc_model
    FOREIGN KEY (model_key) REFERENCES dbo.tc_model(model_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_model_version_model_key_model_version UNIQUE (model_key, model_version),
  CONSTRAINT ck_tc_model_version_status CHECK (status IN (N'DRAFT', N'ACTIVE', N'DEPRECATED'))
);
GO

CREATE INDEX ix_tc_model_version_model_key ON dbo.tc_model_version (model_key);
CREATE INDEX ix_tc_model_version_status    ON dbo.tc_model_version (status);
GO


CREATE TABLE dbo.tc_model_param (
  model_param_key BIGINT IDENTITY(1,1) NOT NULL,
  model_version_key BIGINT NOT NULL,
  param_name      NVARCHAR(128) NOT NULL,
  param_value     NVARCHAR(2000) NULL,
  updated_at      DATETIME2(3) NOT NULL CONSTRAINT df_tc_model_param_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_model_param PRIMARY KEY (model_param_key),
  CONSTRAINT fk_tc_model_param_model_version_key__tc_model_version
    FOREIGN KEY (model_version_key) REFERENCES dbo.tc_model_version(model_version_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_model_param_model_version_key_param_name UNIQUE (model_version_key, param_name)
);
GO

CREATE INDEX ix_tc_model_param_model_version_key ON dbo.tc_model_param (model_version_key);
GO


CREATE TABLE dbo.tc_model_secs_message (
  secs_msg_key   BIGINT IDENTITY(1,1) NOT NULL,
  model_version_key BIGINT NOT NULL,
  secs_msg_name  NVARCHAR(100) NOT NULL,
  description    NVARCHAR(2000) NULL,
  data_index     NVARCHAR(200) NULL,
  updated_at     DATETIME2(3) NOT NULL CONSTRAINT df_tc_model_secs_message_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_model_secs_message PRIMARY KEY (secs_msg_key),
  CONSTRAINT fk_tc_model_secs_message_model_version_key__tc_model_version
    FOREIGN KEY (model_version_key) REFERENCES dbo.tc_model_version(model_version_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_model_secs_message_model_version_key_secs_msg_name UNIQUE (model_version_key, secs_msg_name)
);
GO

CREATE INDEX ix_tc_model_secs_message_model_version_key ON dbo.tc_model_secs_message (model_version_key);
CREATE INDEX ix_tc_model_secs_message_secs_msg_name ON dbo.tc_model_secs_message (secs_msg_name);
GO


CREATE TABLE dbo.tc_model_socket_message (
  socket_msg_key  BIGINT IDENTITY(1,1) NOT NULL,
  model_version_key BIGINT NOT NULL,
  socket_msg_name NVARCHAR(100) NOT NULL,
  description     NVARCHAR(2000) NULL,
  data_index      NVARCHAR(200) NULL,
  updated_at      DATETIME2(3) NOT NULL CONSTRAINT df_tc_model_socket_message_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_model_socket_message PRIMARY KEY (socket_msg_key),
  CONSTRAINT fk_tc_model_socket_message_model_version_key__tc_model_version
    FOREIGN KEY (model_version_key) REFERENCES dbo.tc_model_version(model_version_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_model_socket_message_model_version_key_socket_msg_name UNIQUE (model_version_key, socket_msg_name)
);
GO

CREATE INDEX ix_tc_model_socket_message_model_version_key ON dbo.tc_model_socket_message (model_version_key);
CREATE INDEX ix_tc_model_socket_message_socket_msg_name ON dbo.tc_model_socket_message (socket_msg_name);
GO


CREATE TABLE dbo.tc_model_variableid (
  variable_key     BIGINT IDENTITY(1,1) NOT NULL,
  model_version_key BIGINT NOT NULL,
  variable_id      NVARCHAR(100) NOT NULL,
  variable_id_type NVARCHAR(10) NOT NULL CONSTRAINT df_tc_model_variableid_type DEFAULT (N'SVID'),
  description      NVARCHAR(2000) NULL,
  updated_at       DATETIME2(3) NOT NULL CONSTRAINT df_tc_model_variableid_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_model_variableid PRIMARY KEY (variable_key),
  CONSTRAINT fk_tc_model_variableid_model_version_key__tc_model_version
    FOREIGN KEY (model_version_key) REFERENCES dbo.tc_model_version(model_version_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_model_variableid_model_version_key_type_variable_id UNIQUE (model_version_key, variable_id_type, variable_id),
  CONSTRAINT ck_tc_model_variableid_type CHECK (variable_id_type IN (N'SVID', N'DVID', N'ECID', N'CEID'))
);
GO

CREATE INDEX ix_tc_model_variableid_model_version_key ON dbo.tc_model_variableid (model_version_key);
CREATE INDEX ix_tc_model_variableid_variable_id ON dbo.tc_model_variableid (variable_id);
GO


CREATE TABLE dbo.tc_model_reportid (
  report_key  BIGINT IDENTITY(1,1) NOT NULL,
  model_version_key BIGINT NOT NULL,
  report_id   NVARCHAR(100) NOT NULL,
  variable_id NVARCHAR(1000) NULL,
  enabled     BIT NOT NULL CONSTRAINT df_tc_model_reportid_enabled DEFAULT (0),
  updated_at  DATETIME2(3) NOT NULL CONSTRAINT df_tc_model_reportid_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_model_reportid PRIMARY KEY (report_key),
  CONSTRAINT fk_tc_model_reportid_model_version_key__tc_model_version
    FOREIGN KEY (model_version_key) REFERENCES dbo.tc_model_version(model_version_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_model_reportid_model_version_key_report_id UNIQUE (model_version_key, report_id),
  CONSTRAINT ck_tc_model_reportid_enabled CHECK (enabled IN (0,1))
);
GO

CREATE INDEX ix_tc_model_reportid_model_version_key ON dbo.tc_model_reportid (model_version_key);
CREATE INDEX ix_tc_model_reportid_enabled  ON dbo.tc_model_reportid (enabled);
GO


CREATE TABLE dbo.tc_model_eventid (
  event_key  BIGINT IDENTITY(1,1) NOT NULL,
  model_version_key BIGINT NOT NULL,
  event_id   NVARCHAR(100) NOT NULL,
  report_id  NVARCHAR(1000) NULL,
  enabled    BIT NOT NULL CONSTRAINT df_tc_model_eventid_enabled DEFAULT (0),
  updated_at DATETIME2(3) NOT NULL CONSTRAINT df_tc_model_eventid_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_model_eventid PRIMARY KEY (event_key),
  CONSTRAINT fk_tc_model_eventid_model_version_key__tc_model_version
    FOREIGN KEY (model_version_key) REFERENCES dbo.tc_model_version(model_version_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_model_eventid_model_version_key_event_id UNIQUE (model_version_key, event_id),
  CONSTRAINT ck_tc_model_eventid_enabled CHECK (enabled IN (0,1))
);
GO

CREATE INDEX ix_tc_model_eventid_model_version_key ON dbo.tc_model_eventid (model_version_key);
CREATE INDEX ix_tc_model_eventid_enabled  ON dbo.tc_model_eventid (enabled);
GO


CREATE TABLE dbo.tc_model_workflow (
  workflow_key      BIGINT IDENTITY(1,1) NOT NULL,
  model_version_key BIGINT NOT NULL,
  workflow_name     NVARCHAR(200) NOT NULL,
  message_name      NVARCHAR(200) NOT NULL,
  event_id          NVARCHAR(200) NULL,
  transaction_id    NVARCHAR(200) NULL,
  workflow_filter   NVARCHAR(200) NULL,
  action_name       NVARCHAR(200) NOT NULL,
  action_data_index NVARCHAR(4000) NULL,
  updated_at        DATETIME2(3) NOT NULL CONSTRAINT df_tc_model_workflow_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_model_workflow PRIMARY KEY (workflow_key),
  CONSTRAINT fk_tc_model_workflow_model_version_key__tc_model_version
    FOREIGN KEY (model_version_key) REFERENCES dbo.tc_model_version(model_version_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_model_workflow_model_version_key_workflow_name_message_name UNIQUE (model_version_key, workflow_name, message_name)
);
GO

CREATE INDEX ix_tc_model_workflow_model_version_key ON dbo.tc_model_workflow (model_version_key);
CREATE INDEX ix_tc_model_workflow_workflow_name ON dbo.tc_model_workflow (workflow_name);
GO


CREATE TABLE dbo.tc_model_mdf (
  mdf_key    BIGINT IDENTITY(1,1) NOT NULL,
  model_version_key BIGINT NOT NULL,
  mdf_name   NVARCHAR(100) NOT NULL,
  mdf_file   VARBINARY(MAX) NOT NULL,
  updated_at DATETIME2(3) NOT NULL CONSTRAINT df_tc_model_mdf_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_model_mdf PRIMARY KEY (mdf_key),
  CONSTRAINT fk_tc_model_mdf_model_version_key__tc_model_version
    FOREIGN KEY (model_version_key) REFERENCES dbo.tc_model_version(model_version_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_model_mdf_model_version_key UNIQUE (model_version_key)
);
GO

CREATE INDEX ix_tc_model_mdf_model_version_key ON dbo.tc_model_mdf (model_version_key);
GO


CREATE TABLE dbo.tc_model_dcop_item (
  dcop_item_key    BIGINT IDENTITY(1,1) NOT NULL,
  model_version_key BIGINT NOT NULL,
  dcop_item_name   NVARCHAR(200) NOT NULL,
  workflow_name    NVARCHAR(200) NULL,
  event_id         NVARCHAR(100) NULL,
  variable_id      NVARCHAR(100) NULL,
  collection_rule  NVARCHAR(10)  NULL,
  calculation_rule NVARCHAR(20)  NULL,
  order_rule       INT NULL,
  updated_at       DATETIME2(3) NOT NULL CONSTRAINT df_tc_model_dcop_item_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_model_dcop_item PRIMARY KEY (dcop_item_key),
  CONSTRAINT fk_tc_model_dcop_item_model_version_key__tc_model_version
    FOREIGN KEY (model_version_key) REFERENCES dbo.tc_model_version(model_version_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_model_dcop_item_model_version_key_dcop_item_name UNIQUE (model_version_key, dcop_item_name),
  CONSTRAINT ck_tc_model_dcop_item_collection_rule CHECK (collection_rule IS NULL OR collection_rule IN (N'FIRST', N'LAST')),
  CONSTRAINT ck_tc_model_dcop_item_calculation_rule CHECK (calculation_rule IS NULL OR calculation_rule IN (N'ADD', N'MULTIPLY', N'SUBTRACT', N'NONE')),
  CONSTRAINT ck_tc_model_dcop_item_order_rule CHECK (order_rule IS NULL OR order_rule >= 0)
);
GO

CREATE INDEX ix_tc_model_dcop_item_model_version_key ON dbo.tc_model_dcop_item (model_version_key);
CREATE INDEX ix_tc_model_dcop_item_event_id  ON dbo.tc_model_dcop_item (event_id);
GO



/* =========================
   2.2 tc_eqp + 프로토콜/상태/로그
   ========================= */

CREATE TABLE dbo.tc_eqp_socket_protocol_type (
  socket_protocol_type      NVARCHAR(32) NOT NULL,
  socket_protocol_type_name NVARCHAR(100) NOT NULL,
  parse_start_rule          NVARCHAR(1000) NULL,
  parse_end_rule            NVARCHAR(1000) NULL,
  parse_regex               NVARCHAR(1000) NULL,
  description               NVARCHAR(1000) NULL,

  CONSTRAINT pk_tc_eqp_socket_protocol_type PRIMARY KEY (socket_protocol_type)
);
GO


CREATE TABLE dbo.tc_eqp (
  eqp_key        BIGINT IDENTITY(1,1) NOT NULL,
  eqp_id         NVARCHAR(64) NOT NULL,
  comm_interface NVARCHAR(16) NOT NULL,
  eqp_ip         NVARCHAR(45) NOT NULL,
  eqp_port       INT NOT NULL,
  model_version_key BIGINT NOT NULL,
  enabled        BIT NOT NULL CONSTRAINT df_tc_eqp_enabled DEFAULT (1),
  created_at     DATETIME2(3) NOT NULL CONSTRAINT df_tc_eqp_created_at DEFAULT (SYSUTCDATETIME()),
  updated_at     DATETIME2(3) NOT NULL CONSTRAINT df_tc_eqp_updated_at DEFAULT (SYSUTCDATETIME()),
  created_by     NVARCHAR(50) NOT NULL CONSTRAINT df_tc_eqp_created_by DEFAULT (N'SYSTEM'),
  updated_by     NVARCHAR(50) NOT NULL CONSTRAINT df_tc_eqp_updated_by DEFAULT (N'SYSTEM'),

  CONSTRAINT pk_tc_eqp PRIMARY KEY (eqp_key),
  CONSTRAINT uk_tc_eqp_eqp_id UNIQUE (eqp_id),
  CONSTRAINT fk_tc_eqp_model_version_key__tc_model_version
    FOREIGN KEY (model_version_key) REFERENCES dbo.tc_model_version(model_version_key),
  CONSTRAINT ck_tc_eqp_comm_interface CHECK (comm_interface IN (N'HSMS', N'SOCKET')),
  CONSTRAINT ck_tc_eqp_eqp_port CHECK (eqp_port BETWEEN 1 AND 65535),
  CONSTRAINT ck_tc_eqp_enabled CHECK (enabled IN (0,1))
);
GO

CREATE INDEX ix_tc_eqp_enabled        ON dbo.tc_eqp (enabled);
CREATE INDEX ix_tc_eqp_model_version_key ON dbo.tc_eqp (model_version_key);
CREATE INDEX ix_tc_eqp_comm_interface ON dbo.tc_eqp (comm_interface);
CREATE INDEX ix_tc_eqp_eqp_ip_port    ON dbo.tc_eqp (eqp_ip, eqp_port);
GO


CREATE TABLE dbo.tc_eqp_hsms (
  eqp_key            BIGINT NOT NULL,
  device_id          INT NOT NULL,
  connection_mode    NVARCHAR(10) NOT NULL,
  t3_timeout         INT NOT NULL CONSTRAINT df_tc_eqp_hsms_t3_timeout DEFAULT (45),
  t5_timeout         INT NOT NULL CONSTRAINT df_tc_eqp_hsms_t5_timeout DEFAULT (10),
  t6_timeout         INT NOT NULL CONSTRAINT df_tc_eqp_hsms_t6_timeout DEFAULT (5),
  t7_timeout         INT NOT NULL CONSTRAINT df_tc_eqp_hsms_t7_timeout DEFAULT (10),
  t8_timeout         INT NOT NULL CONSTRAINT df_tc_eqp_hsms_t8_timeout DEFAULT (5),
  link_test_enabled  BIT NOT NULL CONSTRAINT df_tc_eqp_hsms_link_test_enabled DEFAULT (1),
  link_test_interval INT NOT NULL CONSTRAINT df_tc_eqp_hsms_link_test_interval DEFAULT (60),
  max_msg_bytes      BIGINT NOT NULL CONSTRAINT df_tc_eqp_hsms_max_msg_bytes DEFAULT (10485760),
  created_at         DATETIME2(3) NOT NULL CONSTRAINT df_tc_eqp_hsms_created_at DEFAULT (SYSUTCDATETIME()),
  updated_at         DATETIME2(3) NOT NULL CONSTRAINT df_tc_eqp_hsms_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_eqp_hsms PRIMARY KEY (eqp_key),
  CONSTRAINT fk_tc_eqp_hsms_eqp_key__tc_eqp
    FOREIGN KEY (eqp_key) REFERENCES dbo.tc_eqp(eqp_key) ON DELETE CASCADE,

  CONSTRAINT ck_tc_eqp_hsms_device_id CHECK (device_id BETWEEN 0 AND 32767),
  CONSTRAINT ck_tc_eqp_hsms_connection_mode CHECK (connection_mode IN (N'ACTIVE', N'PASSIVE')),
  CONSTRAINT ck_tc_eqp_hsms_t3_timeout CHECK (t3_timeout > 0),
  CONSTRAINT ck_tc_eqp_hsms_t5_timeout CHECK (t5_timeout > 0),
  CONSTRAINT ck_tc_eqp_hsms_t6_timeout CHECK (t6_timeout > 0),
  CONSTRAINT ck_tc_eqp_hsms_t7_timeout CHECK (t7_timeout > 0),
  CONSTRAINT ck_tc_eqp_hsms_t8_timeout CHECK (t8_timeout > 0),
  CONSTRAINT ck_tc_eqp_hsms_link_test_enabled CHECK (link_test_enabled IN (0,1)),
  CONSTRAINT ck_tc_eqp_hsms_link_test_interval CHECK (link_test_interval > 0),
  CONSTRAINT ck_tc_eqp_hsms_max_msg_bytes CHECK (max_msg_bytes > 0)
);
GO


CREATE TABLE dbo.tc_eqp_socket (
  eqp_key              BIGINT NOT NULL,
  socket_protocol_type NVARCHAR(32) NOT NULL,
  connection_mode      NVARCHAR(10) NOT NULL,
  charset              NVARCHAR(20) NOT NULL CONSTRAINT df_tc_eqp_socket_charset DEFAULT (N'UTF-8'),
  heartbeat_enabled    BIT NOT NULL CONSTRAINT df_tc_eqp_socket_heartbeat_enabled DEFAULT (1),
  heartbeat_interval   INT NOT NULL CONSTRAINT df_tc_eqp_socket_heartbeat_interval DEFAULT (30),
  read_timeout         INT NOT NULL CONSTRAINT df_tc_eqp_socket_read_timeout DEFAULT (0),
  write_timeout        INT NOT NULL CONSTRAINT df_tc_eqp_socket_write_timeout DEFAULT (0),
  max_frame_size_bytes INT NOT NULL CONSTRAINT df_tc_eqp_socket_max_frame_size_bytes DEFAULT (8192),
  keep_alive_enabled   BIT NOT NULL CONSTRAINT df_tc_eqp_socket_keep_alive_enabled DEFAULT (1),
  created_at           DATETIME2(3) NOT NULL CONSTRAINT df_tc_eqp_socket_created_at DEFAULT (SYSUTCDATETIME()),
  updated_at           DATETIME2(3) NOT NULL CONSTRAINT df_tc_eqp_socket_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_eqp_socket PRIMARY KEY (eqp_key),
  CONSTRAINT fk_tc_eqp_socket_eqp_key__tc_eqp
    FOREIGN KEY (eqp_key) REFERENCES dbo.tc_eqp(eqp_key) ON DELETE CASCADE,
  CONSTRAINT fk_tc_eqp_socket_socket_protocol_type__tc_eqp_socket_protocol_type
    FOREIGN KEY (socket_protocol_type) REFERENCES dbo.tc_eqp_socket_protocol_type(socket_protocol_type),

  CONSTRAINT ck_tc_eqp_socket_connection_mode CHECK (connection_mode IN (N'ACTIVE', N'PASSIVE')),
  CONSTRAINT ck_tc_eqp_socket_heartbeat_enabled CHECK (heartbeat_enabled IN (0,1)),
  CONSTRAINT ck_tc_eqp_socket_heartbeat_interval CHECK (heartbeat_interval >= 0),
  CONSTRAINT ck_tc_eqp_socket_read_timeout CHECK (read_timeout >= 0),
  CONSTRAINT ck_tc_eqp_socket_write_timeout CHECK (write_timeout >= 0),
  CONSTRAINT ck_tc_eqp_socket_max_frame_size_bytes CHECK (max_frame_size_bytes > 0),
  CONSTRAINT ck_tc_eqp_socket_keep_alive_enabled CHECK (keep_alive_enabled IN (0,1))
);
GO

CREATE INDEX ix_tc_eqp_socket_socket_protocol_type ON dbo.tc_eqp_socket (socket_protocol_type);
GO


CREATE TABLE dbo.tc_eqp_state (
  eqp_key       BIGINT NOT NULL,
  control_state NVARCHAR(20) NULL,
  eqp_state     NVARCHAR(20) NULL,
  since_at      DATETIME2(3) NULL,
  reason_code   NVARCHAR(50) NULL,
  reason_detail NVARCHAR(MAX) NULL,
  updated_at    DATETIME2(3) NOT NULL CONSTRAINT df_tc_eqp_state_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_eqp_state PRIMARY KEY (eqp_key),
  CONSTRAINT fk_tc_eqp_state_eqp_key__tc_eqp
    FOREIGN KEY (eqp_key) REFERENCES dbo.tc_eqp(eqp_key) ON DELETE CASCADE,

  CONSTRAINT ck_tc_eqp_state_control_state CHECK (control_state IS NULL OR control_state IN (N'OFFLINE', N'LOCAL', N'REMOTE')),
  CONSTRAINT ck_tc_eqp_state_eqp_state CHECK (eqp_state IS NULL OR eqp_state IN (N'IDLE', N'RUN', N'DOWN', N'MAINTENANCE', N'PAUSE'))
);
GO


CREATE TABLE dbo.tc_eqp_state_hist (
  state_hist_key BIGINT IDENTITY(1,1) NOT NULL,
  eqp_key        BIGINT NOT NULL,
  state_type     NVARCHAR(10) NOT NULL,
  from_state     NVARCHAR(50) NULL,
  to_state       NVARCHAR(50) NULL,
  changed_at     DATETIME2(3) NOT NULL CONSTRAINT df_tc_eqp_state_hist_changed_at DEFAULT (SYSUTCDATETIME()),
  reason_code    NVARCHAR(50) NULL,
  reason_detail  NVARCHAR(MAX) NULL,

  CONSTRAINT pk_tc_eqp_state_hist PRIMARY KEY (state_hist_key),
  CONSTRAINT fk_tc_eqp_state_hist_eqp_key__tc_eqp
    FOREIGN KEY (eqp_key) REFERENCES dbo.tc_eqp(eqp_key) ON DELETE CASCADE,
  CONSTRAINT ck_tc_eqp_state_hist_state_type CHECK (state_type IN (N'OPER', N'CONN'))
);
GO

CREATE INDEX ix_tc_eqp_state_hist_eqp_key_changed_at
  ON dbo.tc_eqp_state_hist (eqp_key, changed_at);
CREATE INDEX ix_tc_eqp_state_hist_state_type_changed_at
  ON dbo.tc_eqp_state_hist (state_type, changed_at);
GO


CREATE TABLE dbo.tc_eqp_log (
  eqp_key            BIGINT NOT NULL,
  log_level          NVARCHAR(10) NOT NULL CONSTRAINT df_tc_eqp_log_log_level DEFAULT (N'INFO'),
  log_retention_days INT NOT NULL CONSTRAINT df_tc_eqp_log_log_retention_days DEFAULT (30),
  log_path           NVARCHAR(1000) NULL,
  updated_at         DATETIME2(3) NOT NULL CONSTRAINT df_tc_eqp_log_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_eqp_log PRIMARY KEY (eqp_key),
  CONSTRAINT fk_tc_eqp_log_eqp_key__tc_eqp
    FOREIGN KEY (eqp_key) REFERENCES dbo.tc_eqp(eqp_key) ON DELETE CASCADE,
  CONSTRAINT ck_tc_eqp_log_log_level CHECK (log_level IN (N'TRACE', N'DEBUG', N'INFO', N'WARN', N'ERROR')),
  CONSTRAINT ck_tc_eqp_log_log_retention_days CHECK (log_retention_days >= 1)
);
GO


CREATE TABLE dbo.tc_eqp_port_status (
  eqp_port_status_key BIGINT IDENTITY(1,1) NOT NULL,
  eqp_key             BIGINT NOT NULL,
  port_id             NVARCHAR(20) NOT NULL,
  port_type           NVARCHAR(20) NULL,
  port_state          NVARCHAR(20) NULL,
  carrier_id          NVARCHAR(64) NULL,
  carrier_type        NVARCHAR(20) NULL,
  carrier_state       NVARCHAR(20) NULL,
  updated_at          DATETIME2(3) NOT NULL CONSTRAINT df_tc_eqp_port_status_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_eqp_port_status PRIMARY KEY (eqp_port_status_key),
  CONSTRAINT fk_tc_eqp_port_status_eqp_key__tc_eqp
    FOREIGN KEY (eqp_key) REFERENCES dbo.tc_eqp(eqp_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_eqp_port_status_eqp_key_port_id UNIQUE (eqp_key, port_id),

  CONSTRAINT ck_tc_eqp_port_status_port_type
    CHECK (port_type IS NULL OR port_type IN (N'LOAD_PORT', N'UNLOAD_PORT', N'INTERNAL_BUFFER', N'OTHER')),
  CONSTRAINT ck_tc_eqp_port_status_port_state
    CHECK (port_state IS NULL OR port_state IN (N'EMPTY', N'LOADED', N'READY_TO_LOAD', N'DOWN', N'IN_SERVICE', N'UNKNOWN')),
  CONSTRAINT ck_tc_eqp_port_status_carrier_type
    CHECK (carrier_type IS NULL OR carrier_type IN (N'FOUP', N'CASSETTE', N'WAFER_BOX', N'TRAY', N'OTHER')),
  CONSTRAINT ck_tc_eqp_port_status_carrier_state
    CHECK (carrier_state IS NULL OR carrier_state IN (N'CLAMPED', N'UNCLAMPED', N'OPENED', N'CLOSED', N'UNKNOWN'))
);
GO

CREATE INDEX ix_tc_eqp_port_status_eqp_key    ON dbo.tc_eqp_port_status (eqp_key);
CREATE INDEX ix_tc_eqp_port_status_port_id   ON dbo.tc_eqp_port_status (port_id);
CREATE INDEX ix_tc_eqp_port_status_carrier_id ON dbo.tc_eqp_port_status (carrier_id);
GO


CREATE TABLE dbo.tc_eqp_param (
  eqp_param_key BIGINT IDENTITY(1,1) NOT NULL,
  eqp_key       BIGINT NOT NULL,
  param_name    NVARCHAR(100) NOT NULL,
  param_version NVARCHAR(100) NOT NULL,
  param_value   NVARCHAR(MAX) NULL,
  updated_at    DATETIME2(3) NOT NULL CONSTRAINT df_tc_eqp_param_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_eqp_param PRIMARY KEY (eqp_param_key),
  CONSTRAINT fk_tc_eqp_param_eqp_key__tc_eqp
    FOREIGN KEY (eqp_key) REFERENCES dbo.tc_eqp(eqp_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_eqp_param_eqp_key_param_name_param_version UNIQUE (eqp_key, param_name, param_version)
);
GO

CREATE INDEX ix_tc_eqp_param_eqp_key    ON dbo.tc_eqp_param (eqp_key);
CREATE INDEX ix_tc_eqp_param_param_name ON dbo.tc_eqp_param (param_name);
GO

CREATE TABLE dbo.tc_eqp_param_version (
  eqp_param_version_key BIGINT IDENTITY(1,1) NOT NULL,
  eqp_key               BIGINT NOT NULL,
  param_version         NVARCHAR(100) NOT NULL,
  version_description   NVARCHAR(2000) NULL,
  created_at            DATETIME2(3) NOT NULL CONSTRAINT df_tc_eqp_param_version_created_at DEFAULT (SYSUTCDATETIME()),
  updated_at            DATETIME2(3) NOT NULL CONSTRAINT df_tc_eqp_param_version_updated_at DEFAULT (SYSUTCDATETIME()),
  created_by            NVARCHAR(50) NOT NULL CONSTRAINT df_tc_eqp_param_version_created_by DEFAULT (N'SYSTEM'),
  updated_by            NVARCHAR(50) NOT NULL CONSTRAINT df_tc_eqp_param_version_updated_by DEFAULT (N'SYSTEM'),

  CONSTRAINT pk_tc_eqp_param_version PRIMARY KEY (eqp_param_version_key),
  CONSTRAINT fk_tc_eqp_param_version_eqp_key__tc_eqp
    FOREIGN KEY (eqp_key) REFERENCES dbo.tc_eqp(eqp_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_eqp_param_version_eqp_key_param_version UNIQUE (eqp_key, param_version)
);
GO

CREATE INDEX ix_tc_eqp_param_version_eqp_key ON dbo.tc_eqp_param_version (eqp_key);
GO


CREATE TABLE dbo.tc_eqp_global (
  eqp_global_key BIGINT IDENTITY(1,1) NOT NULL,
  eqp_key        BIGINT NOT NULL,
  param_name     NVARCHAR(100) NOT NULL,
  param_value    NVARCHAR(MAX) NULL,
  updated_at     DATETIME2(3) NOT NULL CONSTRAINT df_tc_eqp_global_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_eqp_global PRIMARY KEY (eqp_global_key),
  CONSTRAINT fk_tc_eqp_global_eqp_key__tc_eqp
    FOREIGN KEY (eqp_key) REFERENCES dbo.tc_eqp(eqp_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_eqp_global_eqp_key_param_name UNIQUE (eqp_key, param_name)
);
GO

CREATE INDEX ix_tc_eqp_global_eqp_key ON dbo.tc_eqp_global (eqp_key);
GO



/* =========================
   2.3 tc_work
   ========================= */

CREATE TABLE dbo.tc_work (
  work_key    BIGINT IDENTITY(1,1) NOT NULL,
  eqp_key     BIGINT NOT NULL,
  work_id     NVARCHAR(64) NOT NULL,
  operator_id NVARCHAR(64) NULL,
  step_seq    INT NULL,
  work_state  NVARCHAR(20) NOT NULL,
  start_time  DATETIME2(3) NULL,
  end_time    DATETIME2(3) NULL,
  mes_message NVARCHAR(MAX) NULL,
  created_at  DATETIME2(3) NOT NULL CONSTRAINT df_tc_work_created_at DEFAULT (SYSUTCDATETIME()),
  updated_at  DATETIME2(3) NOT NULL CONSTRAINT df_tc_work_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_work PRIMARY KEY (work_key),
  CONSTRAINT fk_tc_work_eqp_key__tc_eqp FOREIGN KEY (eqp_key) REFERENCES dbo.tc_eqp(eqp_key),
  CONSTRAINT uk_tc_work_eqp_key_work_id UNIQUE (eqp_key, work_id),
  CONSTRAINT ck_tc_work_step_seq CHECK (step_seq IS NULL OR step_seq >= 0),
  CONSTRAINT ck_tc_work_work_state CHECK (work_state IN (N'QUEUED', N'RUNNING', N'COMPLETED', N'ABORTED'))
);
GO

CREATE INDEX ix_tc_work_eqp_key    ON dbo.tc_work (eqp_key);
CREATE INDEX ix_tc_work_work_state ON dbo.tc_work (work_state);
CREATE INDEX ix_tc_work_created_at ON dbo.tc_work (created_at);
GO


CREATE TABLE dbo.tc_work_param (
  work_param_key BIGINT IDENTITY(1,1) NOT NULL,
  work_key       BIGINT NOT NULL,
  param_name     NVARCHAR(100) NOT NULL,
  param_value    NVARCHAR(2000) NULL,
  updated_at     DATETIME2(3) NOT NULL CONSTRAINT df_tc_work_param_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_work_param PRIMARY KEY (work_param_key),
  CONSTRAINT fk_tc_work_param_work_key__tc_work
    FOREIGN KEY (work_key) REFERENCES dbo.tc_work(work_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_work_param_work_key_param_name UNIQUE (work_key, param_name)
);
GO

CREATE INDEX ix_tc_work_param_work_key ON dbo.tc_work_param (work_key);
GO


CREATE TABLE dbo.tc_work_carrier (
  work_carrier_key BIGINT IDENTITY(1,1) NOT NULL,
  work_key         BIGINT NOT NULL,
  carrier_id       NVARCHAR(64) NOT NULL,
  port_id          NVARCHAR(20) NULL,
  slot_map         NVARCHAR(255) NULL,
  total_qty        INT NULL,
  good_qty         INT NULL,
  scrap_qty        INT NULL,
  updated_at       DATETIME2(3) NOT NULL CONSTRAINT df_tc_work_carrier_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_work_carrier PRIMARY KEY (work_carrier_key),
  CONSTRAINT fk_tc_work_carrier_work_key__tc_work
    FOREIGN KEY (work_key) REFERENCES dbo.tc_work(work_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_work_carrier_work_key_carrier_id UNIQUE (work_key, carrier_id),
  CONSTRAINT ck_tc_work_carrier_total_qty CHECK (total_qty IS NULL OR total_qty >= 0),
  CONSTRAINT ck_tc_work_carrier_good_qty  CHECK (good_qty  IS NULL OR good_qty  >= 0),
  CONSTRAINT ck_tc_work_carrier_scrap_qty CHECK (scrap_qty IS NULL OR scrap_qty >= 0)
);
GO

CREATE INDEX ix_tc_work_carrier_work_key   ON dbo.tc_work_carrier (work_key);
CREATE INDEX ix_tc_work_carrier_carrier_id ON dbo.tc_work_carrier (carrier_id);
CREATE INDEX ix_tc_work_carrier_port_id    ON dbo.tc_work_carrier (port_id);
GO


CREATE TABLE dbo.tc_work_carrier_slot (
  carrier_slot_key BIGINT IDENTITY(1,1) NOT NULL,
  work_carrier_key BIGINT NOT NULL,
  slot_no          INT NOT NULL,
  slot_state       NVARCHAR(50) NOT NULL, -- (표 명세) CHECK 없음
  lot_id           NVARCHAR(64) NULL,
  updated_at       DATETIME2(3) NOT NULL CONSTRAINT df_tc_work_carrier_slot_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_work_carrier_slot PRIMARY KEY (carrier_slot_key),
  CONSTRAINT fk_tc_work_carrier_slot_work_carrier_key__tc_work_carrier
    FOREIGN KEY (work_carrier_key) REFERENCES dbo.tc_work_carrier(work_carrier_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_work_carrier_slot_work_carrier_key_slot_no UNIQUE (work_carrier_key, slot_no),
  CONSTRAINT ck_tc_work_carrier_slot_slot_no CHECK (slot_no >= 1)
);
GO

CREATE INDEX ix_tc_work_carrier_slot_work_carrier_key ON dbo.tc_work_carrier_slot (work_carrier_key);
CREATE INDEX ix_tc_work_carrier_slot_lot_id           ON dbo.tc_work_carrier_slot (lot_id);
GO


CREATE TABLE dbo.tc_work_lot (
  work_lot_key  BIGINT IDENTITY(1,1) NOT NULL,
  work_key      BIGINT NOT NULL,
  carrier_id    NVARCHAR(64) NULL,
  lot_id        NVARCHAR(64) NOT NULL,
  parent_lot_id NVARCHAR(64) NULL,
  chamber_id    NVARCHAR(64) NULL,
  updated_at    DATETIME2(3) NOT NULL CONSTRAINT df_tc_work_lot_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_work_lot PRIMARY KEY (work_lot_key),
  CONSTRAINT fk_tc_work_lot_work_key__tc_work
    FOREIGN KEY (work_key) REFERENCES dbo.tc_work(work_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_work_lot_work_key_lot_id UNIQUE (work_key, lot_id)
);
GO

CREATE INDEX ix_tc_work_lot_work_key   ON dbo.tc_work_lot (work_key);
CREATE INDEX ix_tc_work_lot_lot_id     ON dbo.tc_work_lot (lot_id);
CREATE INDEX ix_tc_work_lot_carrier_id ON dbo.tc_work_lot (carrier_id);
GO


CREATE TABLE dbo.tc_work_controljob (
  control_job_key  BIGINT IDENTITY(1,1) NOT NULL,
  work_key         BIGINT NOT NULL,
  controljob_id    NVARCHAR(64) NOT NULL,
  controljob_state NVARCHAR(20) NOT NULL,
  created_at       DATETIME2(3) NOT NULL CONSTRAINT df_tc_work_controljob_created_at DEFAULT (SYSUTCDATETIME()),
  updated_at       DATETIME2(3) NOT NULL CONSTRAINT df_tc_work_controljob_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_work_controljob PRIMARY KEY (control_job_key),
  CONSTRAINT fk_tc_work_controljob_work_key__tc_work
    FOREIGN KEY (work_key) REFERENCES dbo.tc_work(work_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_work_controljob_work_key_controljob_id UNIQUE (work_key, controljob_id),
  CONSTRAINT ck_tc_work_controljob_state
    CHECK (controljob_state IN (N'CREATED', N'QUEUED', N'RUNNING', N'PAUSED', N'COMPLETED', N'ABORTED', N'FAILED'))
);
GO

CREATE INDEX ix_tc_work_controljob_work_key      ON dbo.tc_work_controljob (work_key);
CREATE INDEX ix_tc_work_controljob_controljob_id ON dbo.tc_work_controljob (controljob_id);
GO


CREATE TABLE dbo.tc_work_processjob (
  process_job_key  BIGINT IDENTITY(1,1) NOT NULL,
  control_job_key  BIGINT NOT NULL,
  processjob_id    NVARCHAR(64) NOT NULL,
  processjob_state NVARCHAR(20) NOT NULL,
  recipe_id        NVARCHAR(128) NOT NULL,
  created_at       DATETIME2(3) NOT NULL CONSTRAINT df_tc_work_processjob_created_at DEFAULT (SYSUTCDATETIME()),
  updated_at       DATETIME2(3) NOT NULL CONSTRAINT df_tc_work_processjob_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_work_processjob PRIMARY KEY (process_job_key),
  CONSTRAINT fk_tc_work_processjob_control_job_key__tc_work_controljob
    FOREIGN KEY (control_job_key) REFERENCES dbo.tc_work_controljob(control_job_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_work_processjob_control_job_key_processjob_id UNIQUE (control_job_key, processjob_id),
  CONSTRAINT ck_tc_work_processjob_state
    CHECK (processjob_state IN (N'CREATED', N'QUEUED', N'RUNNING', N'PAUSED', N'COMPLETED', N'ABORTED', N'FAILED'))
);
GO

CREATE INDEX ix_tc_work_processjob_control_job_key ON dbo.tc_work_processjob (control_job_key);
CREATE INDEX ix_tc_work_processjob_processjob_id   ON dbo.tc_work_processjob (processjob_id);
GO


CREATE TABLE dbo.tc_work_processjob_lot_map (
  pj_lot_map_key  BIGINT IDENTITY(1,1) NOT NULL,
  process_job_key BIGINT NOT NULL,
  work_lot_key    BIGINT NOT NULL,
  map_role        NVARCHAR(20) NULL,
  map_order       NVARCHAR(20) NULL,
  created_at      DATETIME2(3) NOT NULL CONSTRAINT df_tc_work_pj_lot_map_created_at DEFAULT (SYSUTCDATETIME()),
  updated_at      DATETIME2(3) NOT NULL CONSTRAINT df_tc_work_pj_lot_map_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_work_processjob_lot_map PRIMARY KEY (pj_lot_map_key),
  CONSTRAINT fk_tc_work_pj_lot_map_process_job_key__tc_work_processjob
    FOREIGN KEY (process_job_key) REFERENCES dbo.tc_work_processjob(process_job_key) ON DELETE CASCADE,
  CONSTRAINT fk_tc_work_pj_lot_map_work_lot_key__tc_work_lot
    FOREIGN KEY (work_lot_key) REFERENCES dbo.tc_work_lot(work_lot_key) ON DELETE CASCADE,
  CONSTRAINT uk_tc_work_pj_lot_map_process_job_key_work_lot_key UNIQUE (process_job_key, work_lot_key),
  CONSTRAINT ck_tc_work_pj_lot_map_map_order CHECK (map_order IS NULL OR map_order IN (N'FORWARD', N'REVERSE'))
);
GO

CREATE INDEX ix_tc_work_pj_lot_map_process_job_key ON dbo.tc_work_processjob_lot_map (process_job_key);
CREATE INDEX ix_tc_work_pj_lot_map_work_lot_key    ON dbo.tc_work_processjob_lot_map (work_lot_key);
GO



/* =========================
   2.4 UI / 권한
   ========================= */

CREATE TABLE dbo.tc_user_info (
  user_pk       BIGINT IDENTITY(1,1) NOT NULL,
  company       NVARCHAR(100) NOT NULL,
  department    NVARCHAR(100) NOT NULL,
  user_name     NVARCHAR(100) NOT NULL,
  user_id       NVARCHAR(50)  NOT NULL,
  user_id_norm  NVARCHAR(50)  NOT NULL,
  password_hash NVARCHAR(255) NOT NULL,
  email         NVARCHAR(255) NOT NULL,
  status        NVARCHAR(20)  NOT NULL CONSTRAINT df_tc_user_info_status DEFAULT (N'ACTIVE'),
  created_at    DATETIME2(3)  NOT NULL CONSTRAINT df_tc_user_info_created_at DEFAULT (SYSUTCDATETIME()),
  updated_at    DATETIME2(3)  NOT NULL CONSTRAINT df_tc_user_info_updated_at DEFAULT (SYSUTCDATETIME()),
  created_by    NVARCHAR(50)  NOT NULL CONSTRAINT df_tc_user_info_created_by DEFAULT (N'SYSTEM'),
  updated_by    NVARCHAR(50)  NOT NULL CONSTRAINT df_tc_user_info_updated_by DEFAULT (N'SYSTEM'),

  CONSTRAINT pk_tc_user_info PRIMARY KEY (user_pk),
  CONSTRAINT uk_tc_user_info_user_id_norm UNIQUE (user_id_norm),
  CONSTRAINT uk_tc_user_info_email UNIQUE (email),
  CONSTRAINT ck_tc_user_info_status CHECK (status IN (N'ACTIVE', N'LOCKED', N'DISABLED', N'DELETED'))
);
GO

CREATE INDEX ix_tc_user_info_company_department ON dbo.tc_user_info (company, department);
CREATE INDEX ix_tc_user_info_status            ON dbo.tc_user_info (status);
CREATE INDEX ix_tc_user_info_email             ON dbo.tc_user_info (email);
GO


CREATE TABLE dbo.tc_ui_permission (
  perm_id       BIGINT IDENTITY(1,1) NOT NULL,
  perm_code     NVARCHAR(80)  NOT NULL,
  perm_name     NVARCHAR(120) NOT NULL,
  resource_type NVARCHAR(10)  NOT NULL,
  match_type    NVARCHAR(10)  NOT NULL CONSTRAINT df_tc_ui_permission_match_type DEFAULT (N'PREFIX'),
  resource      NVARCHAR(255) NOT NULL,
  http_method   NVARCHAR(10)  NULL,
  description   NVARCHAR(1000) NULL,
  is_active     BIT NOT NULL CONSTRAINT df_tc_ui_permission_is_active DEFAULT (1),
  created_at    DATETIME2(3) NOT NULL CONSTRAINT df_tc_ui_permission_created_at DEFAULT (SYSUTCDATETIME()),
  updated_at    DATETIME2(3) NOT NULL CONSTRAINT df_tc_ui_permission_updated_at DEFAULT (SYSUTCDATETIME()),
  created_by    NVARCHAR(50) NOT NULL CONSTRAINT df_tc_ui_permission_created_by DEFAULT (N'SYSTEM'),
  updated_by    NVARCHAR(50) NOT NULL CONSTRAINT df_tc_ui_permission_updated_by DEFAULT (N'SYSTEM'),

  CONSTRAINT pk_tc_ui_permission PRIMARY KEY (perm_id),
  CONSTRAINT uk_tc_ui_permission_perm_code UNIQUE (perm_code),
  CONSTRAINT ck_tc_ui_permission_resource_type CHECK (resource_type IN (N'PAGE', N'API')),
  CONSTRAINT ck_tc_ui_permission_match_type CHECK (match_type IN (N'EXACT', N'PREFIX', N'REGEX')),
  CONSTRAINT ck_tc_ui_permission_is_active CHECK (is_active IN (0,1))
);
GO

CREATE INDEX ix_tc_ui_permission_resource_type_resource ON dbo.tc_ui_permission (resource_type, resource);
CREATE INDEX ix_tc_ui_permission_is_active             ON dbo.tc_ui_permission (is_active);
GO


CREATE TABLE dbo.tc_user_group (
  group_id    BIGINT IDENTITY(1,1) NOT NULL,
  group_code  NVARCHAR(50)  NOT NULL,
  group_name  NVARCHAR(100) NOT NULL,
  description NVARCHAR(1000) NULL,
  is_active   BIT NOT NULL CONSTRAINT df_tc_user_group_is_active DEFAULT (1),
  created_at  DATETIME2(3) NOT NULL CONSTRAINT df_tc_user_group_created_at DEFAULT (SYSUTCDATETIME()),
  updated_at  DATETIME2(3) NOT NULL CONSTRAINT df_tc_user_group_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_user_group PRIMARY KEY (group_id),
  CONSTRAINT uk_tc_user_group_group_code UNIQUE (group_code),
  CONSTRAINT ck_tc_user_group_is_active CHECK (is_active IN (0,1))
);
GO

CREATE INDEX ix_tc_user_group_is_active ON dbo.tc_user_group (is_active);
GO


CREATE TABLE dbo.tc_user_group_member (
  ugm_key     BIGINT IDENTITY(1,1) NOT NULL,
  user_pk     BIGINT NOT NULL,
  group_id    BIGINT NOT NULL,
  granted_at  DATETIME2(3) NOT NULL CONSTRAINT df_tc_user_group_member_granted_at DEFAULT (SYSUTCDATETIME()),
  granted_by  NVARCHAR(50) NULL,

  CONSTRAINT pk_tc_user_group_member PRIMARY KEY (ugm_key),
  CONSTRAINT fk_tc_user_group_member_user_pk__tc_user_info
    FOREIGN KEY (user_pk) REFERENCES dbo.tc_user_info(user_pk) ON DELETE CASCADE,
  CONSTRAINT fk_tc_user_group_member_group_id__tc_user_group
    FOREIGN KEY (group_id) REFERENCES dbo.tc_user_group(group_id) ON DELETE CASCADE,
  CONSTRAINT uk_tc_user_group_member_user_pk_group_id UNIQUE (user_pk, group_id)
);
GO

CREATE INDEX ix_tc_user_group_member_group_id ON dbo.tc_user_group_member (group_id);
CREATE INDEX ix_tc_user_group_member_user_pk  ON dbo.tc_user_group_member (user_pk);
GO


CREATE TABLE dbo.tc_user_group_permission (
  ugp_key    BIGINT IDENTITY(1,1) NOT NULL,
  group_id   BIGINT NOT NULL,
  perm_id    BIGINT NOT NULL,
  granted_at DATETIME2(3) NOT NULL CONSTRAINT df_tc_user_group_permission_granted_at DEFAULT (SYSUTCDATETIME()),
  granted_by NVARCHAR(50) NULL,

  CONSTRAINT pk_tc_user_group_permission PRIMARY KEY (ugp_key),
  CONSTRAINT fk_tc_user_group_permission_group_id__tc_user_group
    FOREIGN KEY (group_id) REFERENCES dbo.tc_user_group(group_id) ON DELETE CASCADE,
  CONSTRAINT fk_tc_user_group_permission_perm_id__tc_ui_permission
    FOREIGN KEY (perm_id) REFERENCES dbo.tc_ui_permission(perm_id) ON DELETE CASCADE,
  CONSTRAINT uk_tc_user_group_permission_group_id_perm_id UNIQUE (group_id, perm_id)
);
GO

CREATE INDEX ix_tc_user_group_permission_perm_id  ON dbo.tc_user_group_permission (perm_id);
CREATE INDEX ix_tc_user_group_permission_group_id ON dbo.tc_user_group_permission (group_id);
GO


CREATE TABLE dbo.tc_ui_auth_session (
  token        NVARCHAR(64) NOT NULL,
  user_pk      BIGINT NOT NULL,
  issued_at    DATETIME2(3) NOT NULL CONSTRAINT df_tc_ui_auth_session_issued_at DEFAULT (SYSUTCDATETIME()),
  expires_at   DATETIME2(3) NOT NULL,
  last_seen_at DATETIME2(3) NULL,
  revoked      BIT NOT NULL CONSTRAINT df_tc_ui_auth_session_revoked DEFAULT (0),

  CONSTRAINT pk_tc_ui_auth_session PRIMARY KEY (token),
  CONSTRAINT fk_tc_ui_auth_session_user_pk__tc_user_info
    FOREIGN KEY (user_pk) REFERENCES dbo.tc_user_info(user_pk),
  CONSTRAINT ck_tc_ui_auth_session_revoked CHECK (revoked IN (0,1))
);
GO

CREATE INDEX ix_tc_ui_auth_session_user_pk            ON dbo.tc_ui_auth_session (user_pk);
CREATE INDEX ix_tc_ui_auth_session_expires_at         ON dbo.tc_ui_auth_session (expires_at);
CREATE INDEX ix_tc_ui_auth_session_revoked_expires_at ON dbo.tc_ui_auth_session (revoked, expires_at);
GO



/* =========================
   2.5 Outbox (tc_msg_send_queue / tc_msg_send_log)
   ========================= */

CREATE TABLE dbo.tc_msg_send_queue (
  msg_key         BIGINT IDENTITY(1,1) NOT NULL,
  idempotency_key NVARCHAR(128) NOT NULL,
  topic           NVARCHAR(200) NOT NULL,
  message_key     NVARCHAR(200) NULL,
  headers_json    NVARCHAR(MAX) NULL,
  payload_json    NVARCHAR(MAX) NOT NULL,
  status          NVARCHAR(16) NOT NULL,
  retry_count     INT NOT NULL CONSTRAINT df_tc_msg_send_queue_retry_count DEFAULT (0),
  next_retry_at   DATETIME2(3) NULL,
  locked_by       NVARCHAR(64) NULL,
  locked_until    DATETIME2(3) NULL,
  created_at      DATETIME2(3) NOT NULL CONSTRAINT df_tc_msg_send_queue_created_at DEFAULT (SYSUTCDATETIME()),
  updated_at      DATETIME2(3) NOT NULL CONSTRAINT df_tc_msg_send_queue_updated_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_msg_send_queue PRIMARY KEY (msg_key),
  CONSTRAINT uk_tc_msg_send_queue_topic_idempotency_key UNIQUE (topic, idempotency_key),
  CONSTRAINT ck_tc_msg_send_queue_status CHECK (status IN (N'PENDING', N'SENDING', N'SENT', N'FAILED', N'DEAD')),
  CONSTRAINT ck_tc_msg_send_queue_retry_count CHECK (retry_count >= 0)
);
GO

CREATE INDEX ix_tc_msg_send_queue_status_next_retry_at ON dbo.tc_msg_send_queue (status, next_retry_at);
CREATE INDEX ix_tc_msg_send_queue_locked_until         ON dbo.tc_msg_send_queue (locked_until);
CREATE INDEX ix_tc_msg_send_queue_topic_status         ON dbo.tc_msg_send_queue (topic, status);
GO


CREATE TABLE dbo.tc_msg_send_log (
  send_log_key    BIGINT IDENTITY(1,1) NOT NULL,
  msg_key         BIGINT NOT NULL,
  attempt_no      INT NOT NULL,
  result          NVARCHAR(16) NOT NULL,
  kafka_partition INT NULL,
  kafka_offset    BIGINT NULL,
  error_code      NVARCHAR(64) NULL,
  error_message   NVARCHAR(MAX) NULL,
  sent_at         DATETIME2(3) NOT NULL CONSTRAINT df_tc_msg_send_log_sent_at DEFAULT (SYSUTCDATETIME()),

  CONSTRAINT pk_tc_msg_send_log PRIMARY KEY (send_log_key),
  CONSTRAINT fk_tc_msg_send_log_msg_key__tc_msg_send_queue
    FOREIGN KEY (msg_key) REFERENCES dbo.tc_msg_send_queue(msg_key) ON DELETE CASCADE,
  CONSTRAINT ck_tc_msg_send_log_attempt_no CHECK (attempt_no >= 1),
  CONSTRAINT ck_tc_msg_send_log_result CHECK (result IN (N'SUCCESS', N'FAIL'))
);
GO

CREATE INDEX ix_tc_msg_send_log_msg_key_attempt_no ON dbo.tc_msg_send_log (msg_key, attempt_no);
CREATE INDEX ix_tc_msg_send_log_sent_at           ON dbo.tc_msg_send_log (sent_at);
GO

COMMIT;
