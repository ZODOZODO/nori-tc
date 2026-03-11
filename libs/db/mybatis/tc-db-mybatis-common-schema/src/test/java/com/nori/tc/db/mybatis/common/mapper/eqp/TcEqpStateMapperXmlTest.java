package com.nori.tc.db.mybatis.common.mapper.eqp;

import java.io.InputStream;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nori.tc.db.mybatis.common.mapper.model.TcModelMapper;

/**
 * tc_eqp_state 계열 MyBatis XML이 enum 타입을 올바르게 해석하는지 검증합니다.
 */
class TcEqpStateMapperXmlTest {

    @Test
    @DisplayName("tc_eqp_state Mapper XML은 enum 타입을 정상 해석합니다")
    void parseEqpStateMapperXml() {
        assertDoesNotThrow(() -> assertMappedStatement(
                "mybatis/common/eqp/TcEqpStateMapper.xml",
                TcEqpStateMapper.class.getName() + ".findByEqpKey"
        ));
    }

    @Test
    @DisplayName("tc_eqp_state_hist Mapper XML은 enum 타입을 정상 해석합니다")
    void parseEqpStateHistMapperXml() {
        assertDoesNotThrow(() -> assertMappedStatement(
                "mybatis/common/eqp/TcEqpStateHistMapper.xml",
                TcEqpStateHistMapper.class.getName() + ".findAllByEqpKey"
        ));
    }

    @Test
    @DisplayName("tc_eqp Mapper XML은 인터페이스 namespace로 바인딩됩니다")
    void parseEqpMapperXml() {
        assertDoesNotThrow(() -> assertMappedStatement(
                "mybatis/common/eqp/TcEqpMapper.xml",
                TcEqpMapper.class.getName() + ".findByEqpId"
        ));
    }

    @Test
    @DisplayName("tc_model Mapper XML은 인터페이스 namespace로 바인딩됩니다")
    void parseModelMapperXml() {
        assertDoesNotThrow(() -> assertMappedStatement(
                "mybatis/common/model/TcModelMapper.xml",
                TcModelMapper.class.getName() + ".findByModelVersionKey"
        ));
    }

    private void assertMappedStatement(final String resourcePath, final String statementId) throws Exception {
        final Configuration configuration = new Configuration();

        try (InputStream inputStream = Resources.getResourceAsStream(resourcePath)) {
            final XMLMapperBuilder builder = new XMLMapperBuilder(
                    inputStream,
                    configuration,
                    resourcePath,
                    configuration.getSqlFragments()
            );
            builder.parse();
        }

        assertTrue(configuration.hasStatement(statementId), () -> "MyBatis statement가 등록되지 않았습니다. id=" + statementId);
    }
}
