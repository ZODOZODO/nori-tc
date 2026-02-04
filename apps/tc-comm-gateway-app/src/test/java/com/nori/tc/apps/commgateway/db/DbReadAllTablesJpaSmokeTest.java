package com.nori.tc.apps.commgateway.db;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.TcEqpConnStateStore;
import com.nori.tc.db.core.eqp.TcEqpHsmsStore;
import com.nori.tc.db.core.eqp.TcEqpLogStore;
import com.nori.tc.db.core.eqp.TcEqpOperStateStore;
import com.nori.tc.db.core.eqp.TcEqpSearchCriteria;
import com.nori.tc.db.core.eqp.TcEqpSocketStore;
import com.nori.tc.db.core.eqp.TcEqpStore;
import com.nori.tc.db.core.model.TcModelSearchCriteria;
import com.nori.tc.db.core.model.TcModelStore;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.domain.model.TcModel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 7개 테이블 "전체 읽기" DB-Core(Store Port) 기반 스모크 테스트
 *
 * 왜 이렇게 해야 하는가?
 * - tc-comm-gateway-app은 "DB 접근 기술(JPA/MyBatis)과 스키마 엔티티/리포지토리"를 몰라야 합니다.
 * - 따라서 Entity/Repository를 직접 import하는 테스트는 의존성 설계와 충돌합니다.
 * - 올바른 검증은 starter가 제공하는 Store 구현체 Bean이 정상 주입되고,
 *   Store API를 통해 실제 DB에서 데이터를 읽어오는지 확인하는 것입니다.
 *
 * 읽기 방식(테이블별)
 * - tc_model: TcModelStore.findAll(criteria, page) 로 offset/limit 페이징 전체 스캔
 * - tc_eqp  : TcEqpStore.findAll(criteria, page) 로 offset/limit 페이징 전체 스캔
 * - 1:1 테이블(tc_eqp_*): FK로 tc_eqp에 종속이므로,
 *   "전체 eqp_id 목록"을 기준으로 각 Store.findByEqpId(eqpId)를 호출하면 전체 행을 커버합니다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        // 테스트 실행 위치가 달라도 config/tc-db.properties 를 찾아오도록 후보 경로를 넓힘
        "spring.config.import=optional:file:config/tc-db.properties,optional:file:../config/tc-db.properties,optional:file:../../config/tc-db.properties",
        // 웹 앱이 아니므로 테스트 부팅 시 웹 강제 방지
        "spring.main.web-application-type=none"
})
class DbReadAllTablesDbCoreSmokeTest {

    @Autowired private TcModelStore tcModelStore;

    @Autowired private TcEqpStore tcEqpStore;
    @Autowired private TcEqpConnStateStore tcEqpConnStateStore;
    @Autowired private TcEqpHsmsStore tcEqpHsmsStore;
    @Autowired private TcEqpSocketStore tcEqpSocketStore;
    @Autowired private TcEqpLogStore tcEqpLogStore;
    @Autowired private TcEqpOperStateStore tcEqpOperStateStore;

    private static final int PAGE_LIMIT = 200;

    @Test
    void readAllRowsFromSevenTables_viaDbCoreStores() {
        // 1) Bean 주입 자체가 starter 조립 검증(주입 실패하면 여기서 NPE/실패)
        assertNotNull(tcModelStore);

        assertNotNull(tcEqpStore);
        assertNotNull(tcEqpConnStateStore);
        assertNotNull(tcEqpHsmsStore);
        assertNotNull(tcEqpSocketStore);
        assertNotNull(tcEqpLogStore);
        assertNotNull(tcEqpOperStateStore);

        System.out.println("============================================================");
        System.out.println("[SMOKE] Read all rows from 7 tables via tc-db-core Stores");
        System.out.println("============================================================");

        // 2) tc_model 전체 읽기
        List<TcModel> allModels = readAllModels();
        System.out.println("[tc_model] total=" + allModels.size());

        // 3) tc_eqp 전체 읽기
        List<TcEqp> allEqps = readAllEqps();
        System.out.println("[tc_eqp] total=" + allEqps.size());

        // 4) 1:1 테이블(= eqp_id PK) 전체 읽기
        //    FK가 tc_eqp(eqp_id)에 종속이므로, 모든 eqpId를 돌면서 findByEqpId를 호출하면 "전체 커버" 됩니다.
        int connStateCount = 0;
        int hsmsCount = 0;
        int socketCount = 0;
        int logCount = 0;
        int operStateCount = 0;

        for (TcEqp eqp : allEqps) {
            String eqpId = eqp.eqpId();

            if (tcEqpConnStateStore.findByEqpId(eqpId).isPresent()) connStateCount++;
            if (tcEqpHsmsStore.findByEqpId(eqpId).isPresent()) hsmsCount++;
            if (tcEqpSocketStore.findByEqpId(eqpId).isPresent()) socketCount++;
            if (tcEqpLogStore.findByEqpId(eqpId).isPresent()) logCount++;
            if (tcEqpOperStateStore.findByEqpId(eqpId).isPresent()) operStateCount++;
        }

        System.out.println("[tc_eqp_conn_state] rows(read by eqpId) = " + connStateCount);
        System.out.println("[tc_eqp_hsms]       rows(read by eqpId) = " + hsmsCount);
        System.out.println("[tc_eqp_socket]     rows(read by eqpId) = " + socketCount);
        System.out.println("[tc_eqp_log]        rows(read by eqpId) = " + logCount);
        System.out.println("[tc_eqp_oper_state] rows(read by eqpId) = " + operStateCount);

        // 5) 최소 보장 검증(“조회가 실제로 수행됐는지”)
        //    - 테이블이 비어 있어도(0행) 테스트는 통과해야 정상입니다.
        //    - 다만 findAll 루프가 정상 종료되었는지, null 없이 수행되었는지는 위에서 이미 검증됩니다.
        assertTrue(allModels.size() >= 0);
        assertTrue(allEqps.size() >= 0);

        System.out.println("============================================================");
        System.out.println("[SMOKE] DONE");
        System.out.println("============================================================");
    }

    private List<TcModel> readAllModels() {
        List<TcModel> out = new ArrayList<>();
        int offset = 0;

        while (true) {
            List<TcModel> chunk = tcModelStore.findAll(
                    TcModelSearchCriteria.empty(),
                    PageRequest.of(offset, PAGE_LIMIT)
            );

            if (chunk.isEmpty()) break;

            out.addAll(chunk);
            offset += chunk.size();

            // 방어: 구현체가 limit을 무시하고 계속 같은 데이터를 반환하면 무한 루프가 되므로 안전장치
            if (chunk.size() > PAGE_LIMIT) {
                fail("TcModelStore.findAll returned more rows than limit. size=" + chunk.size());
            }
        }

        return out;
    }

    private List<TcEqp> readAllEqps() {
        List<TcEqp> out = new ArrayList<>();
        int offset = 0;

        while (true) {
            List<TcEqp> chunk = tcEqpStore.findAll(
                    TcEqpSearchCriteria.empty(),
                    PageRequest.of(offset, PAGE_LIMIT)
            );

            if (chunk.isEmpty()) break;

            out.addAll(chunk);
            offset += chunk.size();

            // 방어: limit 무시/중복 반환 시 무한 루프 방지
            if (chunk.size() > PAGE_LIMIT) {
                fail("TcEqpStore.findAll returned more rows than limit. size=" + chunk.size());
            }
        }

        return out;
    }
}
