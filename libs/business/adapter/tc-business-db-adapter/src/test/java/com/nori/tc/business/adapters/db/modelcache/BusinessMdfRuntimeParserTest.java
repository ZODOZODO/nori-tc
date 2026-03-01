package com.nori.tc.business.adapters.db.modelcache;

import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

/**
 * {@link BusinessMdfRuntimeParser} 단위 테스트입니다.
 */
class BusinessMdfRuntimeParserTest {

    private final BusinessMdfRuntimeParser parser = new BusinessMdfRuntimeParser();

    @Test
    void shouldParseExplicitAndShorthandMessages() {
        final String xml = """
                <mdf>
                  <message name="TOOL_CONDITION_REQUEST_EQP" target="EQP" action="PUBLISH_EQP_COMMAND">
                    <template>CMD=REQ EQPID={EQPID}</template>
                    <field name="EQPID" var="eqpId" source="CTX" required="true"/>
                  </message>
                  <TOOL_CONDITION_REPLY_MES>EQPID={EQPID} STATUS={STATUS}</TOOL_CONDITION_REPLY_MES>
                </mdf>
                """;

        final MdfRuntimeDefinition parsed = parser.parse(
                100L,
                "MDF_MAIN",
                xml.getBytes(StandardCharsets.UTF_8)
        );

        Assertions.assertEquals(2, parsed.messagesByName().size());
        Assertions.assertTrue(parsed.findMessage("TOOL_CONDITION_REQUEST_EQP").isPresent());
        Assertions.assertTrue(parsed.findMessage("TOOL_CONDITION_REPLY_MES").isPresent());
    }

    @Test
    void shouldFailWhenXmlIsMalformed() {
        final String malformed = "<mdf><message name=\"A\"></mdf>";

        Assertions.assertThrows(
                IllegalStateException.class,
                () -> parser.parse(100L, "MDF_MAIN", malformed.getBytes(StandardCharsets.UTF_8))
        );
    }
}
