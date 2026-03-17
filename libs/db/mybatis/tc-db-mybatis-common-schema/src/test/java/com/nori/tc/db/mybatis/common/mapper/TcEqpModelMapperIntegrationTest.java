package com.nori.tc.db.mybatis.common.mapper;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.Optional;

import javax.sql.DataSource;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nori.tc.db.domain.common.model.ModelStatus;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.db.mybatis.common.mapper.eqp.TcEqpMapper;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelMapper;
import com.nori.tc.db.mybatis.common.typehandler.OffsetDateTimeTypeHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EQP/Model MyBatis 매퍼의 실제 SQL/결과 매핑 경로를 검증합니다.
 */
class TcEqpModelMapperIntegrationTest {

    private static DataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;
    private static Connection anchorConnection;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        final JdbcDataSource jdbcDataSource = new JdbcDataSource();
        jdbcDataSource.setURL(
                "jdbc:h2:mem:tc_eqp_model_mapper;"
                        + "MODE=PostgreSQL;"
                        + "DB_CLOSE_DELAY=-1;"
                        + "DB_CLOSE_ON_EXIT=FALSE;"
                        + "DATABASE_TO_LOWER=TRUE"
        );
        jdbcDataSource.setUser("sa");
        jdbcDataSource.setPassword("");

        dataSource = jdbcDataSource;
        /*
         * H2 메모리 DB는 CHECK 제약 평가 시 마지막 커넥션 종료 타이밍에 영향을 받는 경우가 있어,
         * 테스트 전체 동안 anchor 커넥션을 유지해 스키마/제약 조건을 안정적으로 재사용합니다.
         */
        anchorConnection = jdbcDataSource.getConnection();
        createSchema(anchorConnection);
        sqlSessionFactory = createSqlSessionFactory(jdbcDataSource);
    }

    @AfterAll
    static void tearDownDatabase() throws Exception {
        if (anchorConnection != null && !anchorConnection.isClosed()) {
            anchorConnection.close();
        }
    }

    @AfterEach
    void clearTables() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM tc_eqp");
            statement.execute("DELETE FROM tc_model_version");
            statement.execute("DELETE FROM tc_model");
        }
    }

    @Test
    @DisplayName("tc_model parent_model self FK는 root/branch 구조에서 cascade delete로 동작합니다")
    void parentModelSelfForeignKeySupportsRootBranchCascade() throws Exception {
        final long rootModelKey = insertModel("ROOT_MODEL", "SECS", null, "NORI");
        insertModelVersion(rootModelKey, "ROOT_V1", "OPERATE");

        final long branchModelKey = insertModel("ROOT_MODEL_BRANCH", "SECS", "ROOT_MODEL", "NORI");
        insertModelVersion(branchModelKey, "BRANCH_V1", "DEVELOP");

        try (Connection connection = dataSource.getConnection()) {
            assertEquals(2L, countRows(connection, "SELECT COUNT(*) FROM tc_model"));
            assertEquals(1L, countRows(connection, "SELECT COUNT(*) FROM tc_model WHERE parent_model IS NOT NULL"));

            try (PreparedStatement deleteRoot = connection.prepareStatement("DELETE FROM tc_model WHERE model_name = ?")) {
                deleteRoot.setString(1, "ROOT_MODEL");
                deleteRoot.executeUpdate();
            }

            assertEquals(0L, countRows(connection, "SELECT COUNT(*) FROM tc_model"));
            assertEquals(0L, countRows(connection, "SELECT COUNT(*) FROM tc_model_version"));
        }
    }

    @Test
    @DisplayName("tc_model parent_model self FK는 1000자 model_name 확장 후에도 동일하게 동작합니다")
    void parentModelSelfForeignKeySupportsExtendedLengthNames() throws Exception {
        final String rootModelName = "R".repeat(999) + "0";
        final String branchModelName = "R".repeat(999) + "1";
        final String modelVersion = "V".repeat(100);
        final long rootModelKey = insertModel(rootModelName, "SECS", null, "NORI");
        insertModelVersion(rootModelKey, modelVersion, "OPERATE");

        final long branchModelKey = insertModel(branchModelName, "SECS", rootModelName, "NORI");
        insertModelVersion(branchModelKey, "EDIT", "DEVELOP");

        try (Connection connection = dataSource.getConnection()) {
            assertEquals(2L, countRows(connection, "SELECT COUNT(*) FROM tc_model"));
            assertEquals(1L, countRows(connection, "SELECT COUNT(*) FROM tc_model WHERE parent_model IS NOT NULL"));

            try (PreparedStatement deleteRoot = connection.prepareStatement("DELETE FROM tc_model WHERE model_name = ?")) {
                deleteRoot.setString(1, rootModelName);
                deleteRoot.executeUpdate();
            }

            assertEquals(0L, countRows(connection, "SELECT COUNT(*) FROM tc_model"));
            assertEquals(0L, countRows(connection, "SELECT COUNT(*) FROM tc_model_version"));
        }
    }

    @Test
    @DisplayName("tc_model 조회 경로는 SECS/DEVELOP 저장값을 enum으로 복원합니다")
    void findByNameVersionMapsProtocolTypeAndModelStatusEnums() throws Exception {
        final long rootModelKey = insertModel("ROOT_MODEL", "SECS", null, "NORI");
        insertModelVersion(rootModelKey, "ROOT_V1", "OPERATE");

        final long branchModelKey = insertModel("ROOT_MODEL_BRANCH", "SECS", "ROOT_MODEL", "NORI");
        final long branchModelVersionKey = insertModelVersion(branchModelKey, "BRANCH_V1", "DEVELOP");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            final TcModelMapper mapper = session.getMapper(TcModelMapper.class);
            final Optional<TcModel> optionalModel = mapper.findByNameVersion("ROOT_MODEL_BRANCH", "BRANCH_V1");

            assertTrue(optionalModel.isPresent());

            final TcModel model = optionalModel.orElseThrow();
            assertEquals(branchModelVersionKey, model.modelVersionKey());
            assertEquals(branchModelKey, model.modelKey());
            assertEquals("ROOT_MODEL_BRANCH", model.modelName());
            assertEquals("ROOT_MODEL", model.parentModel());
            assertEquals(ProtocolType.SECS, model.commInterface());
            assertEquals(ModelStatus.DEVELOP, model.status());
        }
    }

    @Test
    @DisplayName("tc_eqp 저장/조회 경로는 SECS 저장값과 is_dev를 유지합니다")
    void eqpMapperInsertAndFindPreservesProtocolTypeAndIsDev() throws Exception {
        final long modelKey = insertModel("EQP_MODEL", "SECS", null, "NORI");
        final long modelVersionKey = insertModelVersion(modelKey, "EQP_MODEL_V1", "OPERATE");

        final OffsetDateTime now = OffsetDateTime.parse("2026-03-11T10:30:00+09:00");
        final TcEqp eqp = new TcEqp(
                null,
                "EQP-SECS-001",
                ProtocolType.SECS,
                "ACTIVE",
                true,
                3,
                "127.0.0.1",
                5000,
                modelVersionKey,
                "v1",
                true,
                now,
                now,
                "SYSTEM",
                "SYSTEM"
        );

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            final TcEqpMapper mapper = session.getMapper(TcEqpMapper.class);
            mapper.insert(eqp);
        }

        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT comm_interface, is_dev, applied_param_version FROM tc_eqp WHERE eqp_id = ?"
             )) {
            query.setString(1, "EQP-SECS-001");
            try (ResultSet resultSet = query.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("SECS", resultSet.getString("comm_interface"));
                assertTrue(resultSet.getBoolean("is_dev"));
                assertEquals("v1", resultSet.getString("applied_param_version"));
            }
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            final TcEqpMapper mapper = session.getMapper(TcEqpMapper.class);
            final Optional<TcEqp> optionalEqp = mapper.findByEqpId("EQP-SECS-001");

            assertTrue(optionalEqp.isPresent());

            final TcEqp savedEqp = optionalEqp.orElseThrow();
            assertNotNull(savedEqp.eqpKey());
            assertEquals(ProtocolType.SECS, savedEqp.commInterface());
            assertTrue(savedEqp.isDev());
            assertEquals(modelVersionKey, savedEqp.modelVersionKey());
            assertEquals("v1", savedEqp.appliedParamVersion());
        }
    }

    private static SqlSessionFactory createSqlSessionFactory(final DataSource source) throws Exception {
        final Environment environment = new Environment("test", new JdbcTransactionFactory(), source);
        final Configuration configuration = new Configuration(environment);
        configuration.getTypeHandlerRegistry().register(OffsetDateTimeTypeHandler.class);
        configuration.addMapper(TcModelMapper.class);
        configuration.addMapper(TcEqpMapper.class);

        parseMapper(configuration, "mybatis/common/model/TcModelMapper.xml");
        parseMapper(configuration, "mybatis/common/eqp/TcEqpMapper.xml");

        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static void parseMapper(final Configuration configuration, final String resourcePath) throws Exception {
        try (InputStream inputStream = TcEqpModelMapperIntegrationTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("MyBatis mapper resource를 찾을 수 없습니다. path=" + resourcePath);
            }
            final XMLMapperBuilder builder = new XMLMapperBuilder(
                    inputStream,
                    configuration,
                    resourcePath,
                    configuration.getSqlFragments()
            );
            builder.parse();
        }
    }

    private static void createSchema(final Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE tc_model (
                        model_key BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        model_name VARCHAR(1000) NOT NULL,
                        comm_interface VARCHAR(16) NOT NULL,
                        maker VARCHAR(32) NULL,
                        parent_model VARCHAR(1000) NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        created_by VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
                        updated_by VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
                        CONSTRAINT uk_tc_model_model_name UNIQUE (model_name),
                        CONSTRAINT fk_tc_model_parent_model__tc_model
                            FOREIGN KEY (parent_model) REFERENCES tc_model(model_name) ON DELETE CASCADE,
                        CONSTRAINT ck_tc_model_comm_interface CHECK (comm_interface IN ('SECS','SOCKET'))
                    )
                    """);
            statement.execute("""
                    CREATE TABLE tc_model_version (
                        model_version_key BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        model_key BIGINT NOT NULL,
                        model_version VARCHAR(100) NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        description VARCHAR(2000) NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        created_by VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
                        updated_by VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
                        CONSTRAINT fk_tc_model_version_model_key__tc_model
                            FOREIGN KEY (model_key) REFERENCES tc_model(model_key) ON DELETE CASCADE,
                        CONSTRAINT uk_tc_model_version_model_key_model_version UNIQUE (model_key, model_version),
                        CONSTRAINT ck_tc_model_version_status CHECK (status IN ('DEVELOP','OPERATE','DEPRECATED'))
                    )
                    """);
            statement.execute("""
                    CREATE TABLE tc_eqp (
                        eqp_key BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        eqp_id VARCHAR(64) NOT NULL,
                        comm_interface VARCHAR(16) NOT NULL,
                        comm_mode VARCHAR(10) NOT NULL,
                        is_dev BOOLEAN NOT NULL DEFAULT FALSE,
                        route_partition INTEGER NULL,
                        eqp_ip VARCHAR(45) NOT NULL,
                        eqp_port INT NOT NULL,
                        model_version_key BIGINT NOT NULL,
                        applied_param_version VARCHAR(100) NULL,
                        enabled BOOLEAN NOT NULL DEFAULT TRUE,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        created_by VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
                        updated_by VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
                        CONSTRAINT uk_tc_eqp_eqp_id UNIQUE (eqp_id),
                        CONSTRAINT fk_tc_eqp_model_version_key__tc_model_version
                            FOREIGN KEY (model_version_key) REFERENCES tc_model_version(model_version_key),
                        CONSTRAINT ck_tc_eqp_comm_interface CHECK (comm_interface IN ('SECS','SOCKET')),
                        CONSTRAINT ck_tc_eqp_comm_mode CHECK (comm_mode IN ('ACTIVE','PASSIVE')),
                        CONSTRAINT ck_tc_eqp_is_dev CHECK (is_dev IN (TRUE, FALSE)),
                        CONSTRAINT ck_tc_eqp_eqp_port CHECK (eqp_port BETWEEN 1 AND 65535),
                        CONSTRAINT ck_tc_eqp_enabled CHECK (enabled IN (TRUE, FALSE))
                    )
                    """);
        }
    }

    private long insertModel(
            final String modelName,
            final String commInterface,
            final String parentModel,
            final String maker
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO tc_model (
                                 model_name, comm_interface, maker, parent_model, created_by, updated_by
                             ) VALUES (?, ?, ?, ?, 'TEST', 'TEST')
                             """,
                     Statement.RETURN_GENERATED_KEYS
             )) {
            statement.setString(1, modelName);
            statement.setString(2, commInterface);
            statement.setString(3, maker);
            statement.setString(4, parentModel);
            statement.executeUpdate();

            try (ResultSet resultSet = statement.getGeneratedKeys()) {
                assertTrue(resultSet.next());
                return resultSet.getLong(1);
            }
        }
    }

    private long insertModelVersion(
            final long modelKey,
            final String modelVersion,
            final String status
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO tc_model_version (
                                 model_key, model_version, status, description, created_by, updated_by
                             ) VALUES (?, ?, ?, 'test', 'TEST', 'TEST')
                             """,
                     Statement.RETURN_GENERATED_KEYS
             )) {
            statement.setLong(1, modelKey);
            statement.setString(2, modelVersion);
            statement.setString(3, status);
            statement.executeUpdate();

            try (ResultSet resultSet = statement.getGeneratedKeys()) {
                assertTrue(resultSet.next());
                return resultSet.getLong(1);
            }
        }
    }

    private long countRows(final Connection connection, final String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getLong(1);
        }
    }
}
