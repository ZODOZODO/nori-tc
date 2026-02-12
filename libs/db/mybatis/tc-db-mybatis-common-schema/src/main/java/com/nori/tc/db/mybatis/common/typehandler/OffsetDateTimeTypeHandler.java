package com.nori.tc.db.mybatis.common.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * OffsetDateTime TypeHandler (FIX)
 *
 * 목적
 * - timestamptz(또는 timestamp 계열) 컬럼을 OffsetDateTime으로 안정적으로 매핑한다.
 *
 * 구현 포인트
 * - setObject/getObject(Class) 기반으로 최대한 JDBC 드라이버에 위임한다.
 *
 * 주의
 * - DB/드라이버 별로 TIMESTAMP WITH TIME ZONE 지원 정도가 다르다.
 * - Oracle/MySQL 등에서 타입 호환 이슈가 나오면,
 *   vendor/starter에서 별도 TypeHandler로 치환할 수 있도록 구조를 유지한다.
 */
public class OffsetDateTimeTypeHandler extends BaseTypeHandler<OffsetDateTime> {

    
    /**
     * DB MyBatis 계층 설정 값을 반영합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param ps DB MyBatis 계층 처리에 사용하는 입력 값
     * @param i DB MyBatis 계층 처리에 사용하는 입력 값
     * @param parameter DB MyBatis 계층 처리에 사용하는 입력 값
     * @param jdbcType DB MyBatis 계층 처리에 사용하는 입력 값
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, OffsetDateTime parameter, JdbcType jdbcType)
            throws SQLException {
        // jdbcType을 강제하기보다, 드라이버가 OffsetDateTime을 처리할 수 있도록 setObject로 위임
        ps.setObject(i, parameter);
    }

    
    /**
     * DB MyBatis 계층의 현재 값을 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param rs DB MyBatis 계층 처리에 사용하는 입력 값
     * @param columnName DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    @Override
    public OffsetDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getObject(columnName, OffsetDateTime.class);
    }

    
    /**
     * DB MyBatis 계층의 현재 값을 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param rs DB MyBatis 계층 처리에 사용하는 입력 값
     * @param columnIndex DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    @Override
    public OffsetDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getObject(columnIndex, OffsetDateTime.class);
    }

    
    /**
     * DB MyBatis 계층의 현재 값을 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param cs DB MyBatis 계층 처리에 사용하는 입력 값
     * @param columnIndex DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    @Override
    public OffsetDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getObject(columnIndex, OffsetDateTime.class);
    }
}
