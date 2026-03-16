package com.nori.tc.business.adapters.db.modelcache;

import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfFieldDefinition;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfMessageDefinition;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfOutputType;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfTargetType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

/**
 * {@link BusinessMdfRuntimeParser} 단위 테스트입니다.
 */
class BusinessMdfRuntimeParserTest {

    private final BusinessMdfRuntimeParser parser = new BusinessMdfRuntimeParser();

    @Test
    void shouldParseRawMessageExplicitMessage() {
        final String xml = """
                <mdf>
                  <message name="TOOL_CONDITION_REQUEST" target="EQP" output="RAW_MESSAGE">
                    <template>CMD=TOOL_CONDITION_REQUEST EQPID={EQPID} CARID={CARID}</template>
                    <field name="EQPID" var="EQPID" required="true"/>
                    <field name="CARID" var="CARID" required="false"/>
                  </message>
                </mdf>
                """;

        final MdfRuntimeDefinition parsed = parser.parse(100L, "MDF_MAIN", xml.getBytes(StandardCharsets.UTF_8));

        final MdfMessageDefinition message = parsed.findMessage("TOOL_CONDITION_REQUEST").orElseThrow();
        Assertions.assertEquals(MdfTargetType.EQP, message.targetType());
        Assertions.assertEquals(MdfOutputType.RAW_MESSAGE, message.outputType());
        Assertions.assertNotNull(message.template());

        // EQPID field 검증
        final MdfFieldDefinition eqpidField = message.findField("EQPID").orElseThrow();
        Assertions.assertEquals("EQPID", eqpidField.variablePath());
        Assertions.assertTrue(eqpidField.required());

        // CARID field 검증
        final MdfFieldDefinition caridField = message.findField("CARID").orElseThrow();
        Assertions.assertEquals("CARID", caridField.variablePath());
        Assertions.assertFalse(caridField.required());
    }

    @Test
    void shouldParseKafkaMessageWithoutTemplate() {
        final String xml = """
                <mdf>
                  <message name="TOOL_CONDITION_REPLY" target="MES" output="KAFKA">
                    <field name="EQPID" var="EQPID" required="true"/>
                    <field name="STATUS" var="STATUS" required="true"/>
                    <field name="CARID" var="CARID" required="false"/>
                  </message>
                </mdf>
                """;

        final MdfRuntimeDefinition parsed = parser.parse(100L, "MDF_MAIN", xml.getBytes(StandardCharsets.UTF_8));

        final MdfMessageDefinition message = parsed.findMessage("TOOL_CONDITION_REPLY").orElseThrow();
        Assertions.assertEquals(MdfTargetType.MES, message.targetType());
        Assertions.assertEquals(MdfOutputType.KAFKA, message.outputType());
        // KAFKA output은 template 없음
        Assertions.assertNull(message.template());
        Assertions.assertEquals(3, message.fields().size());
    }

    @Test
    void shouldParseMultipleMessagesInOneMdf() {
        final String xml = """
                <mdf>
                  <message name="TOOL_CONDITION_REQUEST" target="EQP" output="RAW_MESSAGE">
                    <template>CMD=TOOL_CONDITION_REQUEST EQPID={EQPID}</template>
                    <field name="EQPID" var="EQPID" required="true"/>
                  </message>
                  <message name="TOOL_CONDITION_REPLY" target="MES" output="KAFKA">
                    <field name="EQPID" var="EQPID" required="true"/>
                    <field name="STATUS" var="STATUS" required="true"/>
                  </message>
                </mdf>
                """;

        final MdfRuntimeDefinition parsed = parser.parse(100L, "MDF_MAIN", xml.getBytes(StandardCharsets.UTF_8));

        Assertions.assertEquals(2, parsed.messagesByName().size());
        Assertions.assertTrue(parsed.findMessage("TOOL_CONDITION_REQUEST").isPresent());
        Assertions.assertTrue(parsed.findMessage("TOOL_CONDITION_REPLY").isPresent());
    }

    @Test
    void shouldAutoFillFieldFromTemplatePlaceholderWhenNotExplicitlyDeclared() {
        // 명시적 field 선언 없이 template placeholder만 있는 경우, var=name으로 자동 보강됩니다.
        final String xml = """
                <mdf>
                  <message name="TOOL_CONDITION_REQUEST" target="EQP" output="RAW_MESSAGE">
                    <template>CMD=REQ EQPID={EQPID} CARID={CARID}</template>
                  </message>
                </mdf>
                """;

        final MdfRuntimeDefinition parsed = parser.parse(100L, "MDF_MAIN", xml.getBytes(StandardCharsets.UTF_8));

        final MdfMessageDefinition message = parsed.findMessage("TOOL_CONDITION_REQUEST").orElseThrow();
        // EQPID와 CARID는 placeholder에서 자동 보강 (required=false 기본값)
        Assertions.assertEquals("EQPID", message.findField("EQPID").orElseThrow().variablePath());
        Assertions.assertEquals("CARID", message.findField("CARID").orElseThrow().variablePath());
    }

    @Test
    void shouldParseShorthandMessageWithTargetSuffix() {
        // 축약 포맷: 이름 suffix(_EQP/_MES)로 target 추론, RAW_MESSAGE output
        final String xml = """
                <mdf>
                  <TOOL_CONDITION_REQUEST_EQP>CMD=REQ EQPID={EQPID} STATUS={STATUS}</TOOL_CONDITION_REQUEST_EQP>
                </mdf>
                """;

        final MdfRuntimeDefinition parsed = parser.parse(100L, "MDF_MAIN", xml.getBytes(StandardCharsets.UTF_8));

        final MdfMessageDefinition message = parsed.findMessage("TOOL_CONDITION_REQUEST_EQP").orElseThrow();
        Assertions.assertEquals(MdfTargetType.EQP, message.targetType());
        Assertions.assertEquals(MdfOutputType.RAW_MESSAGE, message.outputType());
    }

    @Test
    void shouldUseVarAsVariablePathWhenExplicitlyDeclared() {
        // var 속성이 name과 다른 경우 var를 variablePath로 사용합니다.
        final String xml = """
                <mdf>
                  <message name="TOOL_CONDITION_REQUEST" target="EQP" output="RAW_MESSAGE">
                    <template>CMD=REQ EQPID={EQPID}</template>
                    <field name="EQPID" var="equipmentId" required="true"/>
                  </message>
                </mdf>
                """;

        final MdfRuntimeDefinition parsed = parser.parse(100L, "MDF_MAIN", xml.getBytes(StandardCharsets.UTF_8));

        final MdfFieldDefinition field = parsed.findMessage("TOOL_CONDITION_REQUEST")
                .orElseThrow()
                .findField("EQPID")
                .orElseThrow();
        // var="equipmentId"이면 action_data_index["equipmentId"]로 lookup합니다.
        Assertions.assertEquals("equipmentId", field.variablePath());
    }

    @Test
    void shouldFailWhenXmlIsMalformed() {
        final String malformed = "<mdf><message name=\"A\"></mdf>";

        Assertions.assertThrows(
                IllegalStateException.class,
                () -> parser.parse(100L, "MDF_MAIN", malformed.getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void shouldFailWhenRawMessageHasNoTemplate() {
        final String xml = """
                <mdf>
                  <message name="TOOL_CONDITION_REQUEST" target="EQP" output="RAW_MESSAGE">
                    <field name="EQPID" var="EQPID" required="true"/>
                  </message>
                </mdf>
                """;

        // RAW_MESSAGE는 template이 필수
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> parser.parse(100L, "MDF_MAIN", xml.getBytes(StandardCharsets.UTF_8))
        );
    }
}
