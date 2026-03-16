package com.nori.tc.business.adapters.db.modelcache;

import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfFieldDefinition;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfMessageDefinition;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfOutputType;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfTargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DB의 MDF(byte[])를 런타임 메시지 정의로 파싱/검증하는 컴포넌트입니다.
 *
 * <p>
 * MDF는 메시지 구조와 field 목록만 선언합니다.
 * source/xform/action 등 workflow-specific 정보는 MDF에 포함되지 않습니다.
 * </p>
 *
 * <p>
 * 지원 포맷:
 * 1) 명시적 포맷: {@code <message name="..." target="EQP|MES" output="RAW_MESSAGE|KAFKA">}
 * 2) 축약 포맷: {@code <TOOL_CONDITION_REQUEST_EQP>...</TOOL_CONDITION_REQUEST_EQP>} (이름에서 target 추론)
 * </p>
 */
@Component
public class BusinessMdfRuntimeParser {

    private static final Logger log = LoggerFactory.getLogger(BusinessMdfRuntimeParser.class);

    private static final Pattern TEMPLATE_FIELD_PATTERN = Pattern.compile("\\{([A-Za-z0-9_.-]+)}");

    /**
     * MDF 바이너리를 UTF-8 XML로 파싱합니다.
     *
     * @param modelVersionKey 모델 버전 키(로그/예외 식별용)
     * @param mdfName         MDF 이름
     * @param mdfBytes        MDF 원본 바이너리
     * @return 런타임 MDF 정의
     */
    public MdfRuntimeDefinition parse(long modelVersionKey, String mdfName, byte[] mdfBytes) {
        if (modelVersionKey <= 0L) {
            throw new IllegalArgumentException("modelVersionKey must be > 0");
        }
        if (mdfName == null || mdfName.isBlank()) {
            throw new IllegalArgumentException("mdfName must not be null/blank");
        }
        if (mdfBytes == null || mdfBytes.length == 0) {
            throw new IllegalArgumentException("mdfBytes must not be null/empty");
        }

        try {
            final String xml = new String(mdfBytes, StandardCharsets.UTF_8);
            final Document document = parseXmlDocument(xml);
            final Element root = document.getDocumentElement();
            if (root == null) {
                throw new IllegalArgumentException("MDF XML root element is missing");
            }

            final Map<String, MdfMessageDefinition> messages = new LinkedHashMap<>();
            final NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                final Node child = children.item(i);
                if (!(child instanceof Element element)) {
                    continue;
                }

                final MdfMessageDefinition message = parseMessageElement(element);
                if (messages.containsKey(message.name())) {
                    throw new IllegalArgumentException("Duplicate MDF message name: " + message.name());
                }
                messages.put(message.name(), message);
            }

            if (messages.isEmpty()) {
                throw new IllegalArgumentException("MDF XML does not contain any message definitions");
            }

            if (log.isDebugEnabled()) {
                log.debug("MDF parsed. modelVersionKey={}, mdfName={}, messageCount={}",
                        modelVersionKey,
                        mdfName,
                        messages.size());
            }
            return new MdfRuntimeDefinition(messages);
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "MDF parsing failed. modelVersionKey=" + modelVersionKey + ", mdfName=" + mdfName,
                    ex
            );
        }
    }

    /**
     * XML 문자열을 보안 옵션과 함께 DOM 문서로 변환합니다.
     */
    private static Document parseXmlDocument(String xml) {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid MDF XML format", ex);
        }
    }

    /**
     * 단일 메시지 노드를 파싱합니다.
     */
    private MdfMessageDefinition parseMessageElement(Element element) {
        final String nodeName = element.getTagName();
        if ("message".equalsIgnoreCase(nodeName)) {
            return parseExplicitMessage(element);
        }
        return parseShorthandMessage(element);
    }

    /**
     * 명시적 포맷({@code <message name="..." target="..." output="...">})을 파싱합니다.
     *
     * <p>
     * output이 RAW_MESSAGE이면 template이 필수입니다.
     * output이 KAFKA이면 template 없이 field 목록만 정의합니다.
     * </p>
     */
    private MdfMessageDefinition parseExplicitMessage(Element element) {
        final String name = requiredAttribute(element, "name");

        final MdfTargetType targetType = MdfTargetType.fromText(attribute(element, "target"))
                .orElseGet(() -> inferTargetFromName(name)
                        .orElseThrow(() -> new IllegalArgumentException("target is required. message=" + name)));

        final MdfOutputType outputType = MdfOutputType.fromText(attribute(element, "output"))
                .orElse(defaultOutputType(targetType));

        // RAW_MESSAGE는 template 필수, KAFKA는 template 불필요
        final String template = resolveTemplate(element, outputType);

        final List<MdfFieldDefinition> fieldDefinitions = new ArrayList<>();
        final NodeList fieldNodes = element.getElementsByTagName("field");
        for (int i = 0; i < fieldNodes.getLength(); i++) {
            if (fieldNodes.item(i) instanceof Element fieldElement) {
                fieldDefinitions.add(parseField(fieldElement, name));
            }
        }

        final List<MdfFieldDefinition> normalizedFields = normalizeFields(name, template, fieldDefinitions);
        return new MdfMessageDefinition(name, targetType, outputType, template, normalizedFields);
    }

    /**
     * 축약 포맷({@code <XXX_EQP>...</XXX_EQP>})을 파싱합니다.
     *
     * <p>축약 포맷은 이름 suffix(_EQP/_MES)로 target을 추론하며, 항상 RAW_MESSAGE output을 사용합니다.</p>
     */
    private MdfMessageDefinition parseShorthandMessage(Element element) {
        final String name = normalize(element.getTagName());
        if (name == null) {
            throw new IllegalArgumentException("MDF shorthand message name is empty");
        }

        final MdfTargetType targetType = inferTargetFromName(name)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cannot infer target(EQP/MES) from shorthand message name: " + name
                ));

        final String template = normalize(element.getTextContent());
        if (template == null) {
            throw new IllegalArgumentException("Shorthand MDF template is empty. message=" + name);
        }

        final List<MdfFieldDefinition> fields = normalizeFields(name, template, List.of());
        return new MdfMessageDefinition(name, targetType, MdfOutputType.RAW_MESSAGE, template, fields);
    }

    /**
     * 메시지 템플릿 본문을 조회합니다.
     *
     * <p>output이 KAFKA이면 template이 없어도 됩니다(null 반환). RAW_MESSAGE이면 필수입니다.</p>
     */
    private String resolveTemplate(Element messageElement, MdfOutputType outputType) {
        final NodeList templateNodes = messageElement.getElementsByTagName("template");
        if (templateNodes.getLength() > 0 && templateNodes.item(0) != null) {
            final String templateText = normalize(templateNodes.item(0).getTextContent());
            if (templateText != null) {
                return templateText;
            }
        }

        final String fallback = normalize(messageElement.getTextContent());
        if (fallback != null) {
            return fallback;
        }

        // KAFKA output은 template 없이 field 목록만으로 직렬화하므로 null 반환
        // RAW_MESSAGE는 MdfMessageDefinition 생성자에서 필수 검증
        return null;
    }

    /**
     * 필드 노드를 파싱합니다.
     *
     * <p>
     * {@code name}은 template의 치환 위치 식별자이고,
     * {@code var}는 action_data_index에서 값을 가져올 lookup key 이름입니다.
     * </p>
     */
    private MdfFieldDefinition parseField(Element fieldElement, String messageName) {
        final String fieldName = requiredAttribute(fieldElement, "name");

        // var가 없으면 name과 동일한 이름으로 action_data_index를 lookup합니다.
        final String varAttr = normalize(attribute(fieldElement, "var"));
        final String variablePath = varAttr != null ? varAttr : fieldName;

        final boolean required = parseBoolean(attribute(fieldElement, "required"), true);

        try {
            return new MdfFieldDefinition(fieldName, variablePath, required);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "Invalid field definition. message=" + messageName + ", field=" + fieldName,
                    ex
            );
        }
    }

    /**
     * 템플릿 플레이스홀더를 기준으로 필드 정의를 정규화합니다.
     *
     * <p>
     * template이 null(KAFKA output)이면 explicit fields만 사용합니다.
     * template이 있으면 플레이스홀더 중 explicit fields에 없는 항목을 var=name으로 자동 보강합니다.
     * </p>
     */
    private List<MdfFieldDefinition> normalizeFields(
            String messageName,
            String template,
            List<MdfFieldDefinition> explicitFields
    ) {
        final Map<String, MdfFieldDefinition> byName = new LinkedHashMap<>();

        for (MdfFieldDefinition field : explicitFields) {
            if (byName.put(field.name(), field) != null) {
                throw new IllegalArgumentException(
                        "Duplicate field definition. message=" + messageName + ", field=" + field.name()
                );
            }
        }

        // 템플릿 플레이스홀더 중 explicit fields에 없는 항목은 var=name으로 자동 보강합니다.
        if (template != null) {
            for (String placeholder : extractTemplatePlaceholders(template)) {
                byName.computeIfAbsent(
                        placeholder,
                        ignored -> new MdfFieldDefinition(placeholder, placeholder, false)
                );
            }
        }

        return List.copyOf(byName.values());
    }

    /**
     * 템플릿에서 {FIELD} 패턴 필드명을 추출합니다.
     */
    private static Set<String> extractTemplatePlaceholders(String template) {
        final Set<String> placeholders = new LinkedHashSet<>();
        final Matcher matcher = TEMPLATE_FIELD_PATTERN.matcher(template);
        while (matcher.find()) {
            final String name = normalize(matcher.group(1));
            if (name != null) {
                placeholders.add(name);
            }
        }
        return placeholders;
    }

    /**
     * 메시지 이름 suffix를 기반으로 target 타입을 추론합니다.
     */
    private static Optional<MdfTargetType> inferTargetFromName(String messageName) {
        final String normalized = normalize(messageName);
        if (normalized == null) {
            return Optional.empty();
        }
        if (normalized.endsWith("_EQP")) {
            return Optional.of(MdfTargetType.EQP);
        }
        if (normalized.endsWith("_MES")) {
            return Optional.of(MdfTargetType.MES);
        }
        return Optional.empty();
    }

    /**
     * target별 기본 output 타입을 반환합니다.
     */
    private static MdfOutputType defaultOutputType(MdfTargetType targetType) {
        return switch (targetType) {
            case EQP -> MdfOutputType.RAW_MESSAGE;
            case MES -> MdfOutputType.KAFKA;
        };
    }

    /**
     * 필수 속성값을 조회합니다.
     */
    private static String requiredAttribute(Element element, String name) {
        final String value = normalize(attribute(element, name));
        if (value == null) {
            throw new IllegalArgumentException("Required attribute is missing: " + name);
        }
        return value;
    }

    /**
     * 속성값을 안전하게 조회합니다.
     */
    private static String attribute(Element element, String name) {
        if (!element.hasAttribute(name)) {
            return null;
        }
        return element.getAttribute(name);
    }

    /**
     * 문자열 boolean을 파싱합니다.
     */
    private static boolean parseBoolean(String value, boolean defaultValue) {
        final String normalized = normalize(value);
        if (normalized == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(normalized);
    }

    /**
     * 문자열을 trim하고 빈 값이면 null을 반환합니다.
     */
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }
}
