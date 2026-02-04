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

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, OffsetDateTime parameter, JdbcType jdbcType)
            throws SQLException {
        // jdbcType을 강제하기보다, 드라이버가 OffsetDateTime을 처리할 수 있도록 setObject로 위임
        ps.setObject(i, parameter);
    }

    @Override
    public OffsetDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getObject(columnName, OffsetDateTime.class);
    }

    @Override
    public OffsetDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getObject(columnIndex, OffsetDateTime.class);
    }

    @Override
    public OffsetDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getObject(columnIndex, OffsetDateTime.class);
    }
}
