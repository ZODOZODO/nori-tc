# Tasks: Action Modules (`tc-business-action`, `tc-gateway-action`)

설계 문서: [docs/design/02-action-modules.md](../design/02-action-modules.md)

---

## Phase 1: Gateway Action 모듈 (`tc-gateway-action`) ✅ 완료

### 1-1. 모듈 생성

- [x] `libs/action/tc-gateway-action/` 디렉토리 생성
- [x] `libs/action/tc-gateway-action/build.gradle.kts` 생성
  - `api(project(":libs:comm:tc-comm-core"))` 의존 추가 (`ReassemblyBuffer` 참조)
- [x] `settings.gradle.kts`에 `:libs:action:tc-gateway-action` include 추가

### 1-2. SPI 클래스 이동

신규 패키지: `com.nori.tc.comm.gateway.action`

- [x] `SocketTypeHandler.java` 이동 (기존: `com.nori.tc.comm.gateway.socket.socketType.core`)
- [x] `SocketTypeDecodeResult.java` 이동 (동일 패키지)
- [x] `SocketTypeEncodeResult.java` 이동 (동일 패키지)
- [x] `SocketFrame.java` 이동 (기존: `com.nori.tc.comm.gateway.socket.frame`)

### 1-3. 기존 파일 삭제

- [x] `libs/comm/tc-comm-socket/.../socketType/core/SocketTypeHandler.java` 삭제
- [x] `libs/comm/tc-comm-socket/.../socketType/core/SocketTypeDecodeResult.java` 삭제
- [x] `libs/comm/tc-comm-socket/.../socketType/core/SocketTypeEncodeResult.java` 삭제
- [x] `libs/comm/tc-comm-socket/.../frame/SocketFrame.java` 삭제

### 1-4. build.gradle.kts 수정

- [x] `libs/comm/tc-comm-socket/build.gradle.kts`에 `api(project(":libs:action:tc-gateway-action"))` 추가
- [x] `libs/comm/adapter/tc-comm-gateway-plugin-adapter/build.gradle.kts`에 `api(project(":libs:action:tc-gateway-action"))` 추가

### 1-5. import 수정

- [x] `SocketTypeRegistry.java`: `SocketTypeHandler` import 변경
- [x] `SocketInboundPipeline.java`: `SocketFrame`, `SocketTypeDecodeResult`, `SocketTypeHandler` import 변경
- [x] `LineDelimitedSocketTypeHandler.java`: 4개 import 변경
- [x] `RegexDelimitedSocketTypeHandler.java`: 4개 import 변경
- [x] `GatewaySocketPluginRuntimeProvider.java`: `SocketTypeHandler` import 변경
- [x] `GatewaySocketPluginRuntimeManager.java`: `SocketTypeHandler` import 변경

### 1-6. 빌드 검증

- [x] `./gradlew :libs:action:tc-gateway-action:build` — BUILD SUCCESSFUL
- [x] `./gradlew :libs:comm:tc-comm-socket:build` — BUILD SUCCESSFUL
- [x] `./gradlew :libs:comm:adapter:tc-comm-gateway-plugin-adapter:build` — BUILD SUCCESSFUL

---

## Phase 2: Business Action 모듈 (`tc-business-action`) ✅ 완료

### 2-1. 모듈 생성

- [x] `libs/action/tc-business-action/` 디렉토리 생성
- [x] `libs/action/tc-business-action/build.gradle.kts` 생성
  - 의존성 없음 (순수 Java)
- [x] `settings.gradle.kts`에 `:libs:action:tc-business-action` include 추가

### 2-2. SPI 클래스 이동

신규 패키지: `com.nori.tc.business.action`

- [x] `AbstractSecsActionExecutor.java` 이동 (기존: `com.nori.tc.business.core.workflow.api.spi.executor`)
- [x] `AbstractSocketActionExecutor.java` 이동 (동일 패키지)
- [x] `AbstractMesActionExecutor.java` 이동 (동일 패키지)
- [x] `TcAction.java` 이동 (기존: `com.nori.tc.business.core.workflow.api.annotation`)

### 2-3. 신규 파일 작성

- [x] `TcActionContext.java` 신규 작성 (패키지: `com.nori.tc.business.action`)
  - 순수 Java 인터페이스: eqpId, messageName, payload, traceId, messageVariables, contextVariables, workflowName, actionName

### 2-4. 기존 파일 삭제

- [x] `tc-business-core/.../workflow/api/spi/executor/AbstractSecsActionExecutor.java` 삭제
- [x] `tc-business-core/.../workflow/api/spi/executor/AbstractSocketActionExecutor.java` 삭제
- [x] `tc-business-core/.../workflow/api/spi/executor/AbstractMesActionExecutor.java` 삭제
- [x] `tc-business-core/.../workflow/api/annotation/TcAction.java` 삭제

### 2-5. build.gradle.kts 수정

- [x] `libs/business/tc-business-core/build.gradle.kts`에 `api(project(":libs:action:tc-business-action"))` 추가

### 2-6. `BusinessWorkflowActionContext` 수정

- [x] `implements TcActionContext` 추가
- [x] `TcActionContext` 메서드 6개 구현 (`eqpId`, `messageName`, `payload`, `traceId`, `workflowName`, `actionName`)

### 2-7. `BusinessWorkflowActionRegistryBuilder` 수정

- [x] `validateActionMethod()`에서 파라미터 타입 검사를 `TcActionContext.class.isAssignableFrom`으로 변경
  - 이유: 플러그인 메서드(`TcActionContext` 파라미터)와 Core 메서드(`BusinessWorkflowActionContext` 파라미터) 모두 허용

### 2-8. import 수정 (메인 소스)

- [x] `BusinessWorkflowCoreActionRegistry.java`: AbstractXxx import 변경
- [x] `BusinessCoreSecsActionExecutor.java`: import 변경
- [x] `BusinessCoreSocketActionExecutor.java`: import 변경
- [x] `BusinessCoreMesActionExecutor.java`: import 변경
- [x] `DatacollTcAction.java`: import 변경
- [x] `CollectDcdataTcAction.java`: import 변경
- [x] `DcspecreqRepTcAction.java`: import 변경
- [x] `BusinessWorkflowPluginRuntimeManager.java`: AbstractXxx import 변경

### 2-9. import 수정 (테스트 소스)

- [x] `BusinessWorkflowActionRegistryBuilderTest.java`: import 변경
- [x] `ActionResolutionPolicyTest.java`: import 변경
- [x] `BusinessWorkflowDispatchingActionExecutorTest.java`: import 변경
- [x] `BusinessWorkflowPluginRuntimeManagerTest.java`: import 변경

### 2-10. 빌드 검증

- [x] `./gradlew :libs:action:tc-business-action:build` — BUILD SUCCESSFUL
- [x] `./gradlew :libs:business:tc-business-core:build` — BUILD SUCCESSFUL
- [x] `./gradlew :libs:business:adapter:tc-business-plugin-adapter:build` — BUILD SUCCESSFUL

---

## Phase 3: 문서화 ✅ 완료

- [x] `docs/Architecture/gateway/13-gateway-plugin-jar.md` 작성
- [x] `docs/Architecture/business/10-business-plugin-jar.md` 작성
- [x] `docs/design/02-action-modules.md` 작성
- [x] `docs/tasks/02-action-modules-tasks.md` 작성 (본 파일)

---

## 검증 체크리스트

- [x] `./gradlew :libs:action:tc-business-action:dependencies` — Spring/Kafka 의존 없음 확인
- [x] `./gradlew :libs:action:tc-gateway-action:dependencies` — `tc-comm-core`만 있음 확인
- [x] 기존 테스트 (`BusinessWorkflowPluginRuntimeManagerTest`) 통과 확인
- [ ] 플러그인 JAR 샘플 빌드 테스트 (`compileOnly` 의존으로 정상 컴파일 확인) — 향후 검증
