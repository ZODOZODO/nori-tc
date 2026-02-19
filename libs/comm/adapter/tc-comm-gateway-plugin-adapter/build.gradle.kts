plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

group = "com.nori.tc"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    /*
     * Gateway 肄붿뼱 + SOCKET ?뚮윭洹몄씤 ?ы듃 怨꾩빟 ?섏〈?깆엯?덈떎.
     * - ?고???留ㅻ땲?媛 肄붿뼱 ?ы듃/?꾨찓??怨꾩빟??援ы쁽?????꾩슂?⑸땲??
     */
    api(project(":libs:comm:tc-comm-gateway-core"))
    api(project(":libs:comm:tc-comm-socket"))

    /*
     * ?뚮윭洹몄씤 JAR 議고쉶瑜??꾪븳 DB ?ы듃/?꾨찓???섏〈?깆엯?덈떎.
     * - tc_eqp 議고쉶: TcEqpStore
     * - tc_jar_gateway 議고쉶: TcJarGatewayStore
     */
    implementation(project(":libs:db:tc-db-core"))
    implementation(project(":libs:db:tc-db-domain"))

    /*
     * Spring 而댄룷?뚰듃/?ㅼ젙 諛붿씤???쇱씠?꾩궗?댄겢 ?대끂?뚯씠??而댄뙆???섏〈?깆엯?덈떎.
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    compileOnly(libs.jakarta.annotation.api)
    annotationProcessor(libs.spring.boot.configuration.processor)

    /*
     * ?⑥쐞 ?뚯뒪???섏〈?깆엯?덈떎.
     */
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
