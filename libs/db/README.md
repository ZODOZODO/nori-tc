

## 📚 Database Schema Change Guide (DB 변경 가이드)

이 문서는 프로젝트의 **헥사고날 아키텍처(Hexagonal Architecture)** 구조 하에서 테이블이나 컬럼이 변경되었을 때, **Core(Domain)**와 **Adapter(JPA/MyBatis)** 계층을 어떻게 수정해야 하는지 설명합니다.

> **⚠️ 핵심 원칙 (Golden Rules)**
> 1.  **Core 먼저, 구현은 나중에:** 항상 `Domain`과 `Core(Port)`를 먼저 수정하고, 그에 맞춰 `JPA`나 `MyBatis` 구현체를 수정합니다.
> 2.  **JPA는 컴파일러를 믿으세요:** MapStruct를 사용하므로 **빌드(`gradlew build`)**를 통해 매핑 코드를 자동 생성합니다.
> 3.  **MyBatis는 XML을 확인하세요:** 자동화되지 않으므로 SQL 쿼리(ResultMap, Select 등) 수정에 주의해야 합니다.

---

### 1. ➕ 컬럼 추가 (Column Addition)
가장 빈번하게 발생하는 시나리오입니다. (예: `tc_eqp` 테이블에 `description` 컬럼 추가)

#### 1-1. Core 계층 수정 (공통)
모든 변경의 시작점입니다. DB 구현 기술(JPA/MyBatis)과 무관하게 수행합니다.

1.  **Domain 객체 수정**: `libs/db/tc-db-domain/.../TcEqp.java`에 필드를 추가합니다.
2.  **Command(DTO) 객체 수정**: `libs/db/tc-db-core/.../UpsertTcEqp.java` (저장용 DTO)에 필드를 추가합니다.
3.  **SearchCriteria 수정 (선택)**: 검색 조건에 포함된다면 `TcEqpSearchCriteria.java`도 수정합니다.

#### 1-2. JPA 구현체 수정 (`libs/db/jpa/...`)
MapStruct 덕분에 수정할 양이 매우 적습니다.

1.  **Entity 수정**: `.../entity/TcEqpEntity.java`에 필드를 추가합니다.
2.  **빌드 실행 (필수)**: 터미널에서 `gradlew clean compileJava`를 실행합니다.
    * *이유:* `TcEqpEntityMapperImpl` 클래스가 자동으로 재생성되어, 새로운 필드를 `set` 해주는 코드가 포함됩니다.
3.  **Store 확인**: `TcEqpJpaStore.java`를 확인합니다.
    * 대부분 `mapper.updateEntity()`를 사용하므로 **Store 코드는 수정할 필요가 없습니다.**
    * **예외:** `sinceAt`이나 `charset` 처럼 **별도 비즈니스 로직(기본값 처리 등)이 필요한 필드**라면, Store의 `upsert` 메서드 하단에 수동 로직을 추가해야 합니다.

#### 1-3. MyBatis 구현체 수정 (`libs/db/mybatis/...`)
XML은 컴파일 에러가 나지 않으므로 꼼꼼히 수정해야 합니다.

1.  **Mapper XML 수정**: `resources/mapper/TcEqpMapper.xml`을 엽니다.
    * `<resultMap>`: 새로운 컬럼 매핑 추가.
    * `<insert>` / `<update>`: SQL 구문에 새로운 컬럼 추가.
    * `<select>`: 조회 컬럼 목록에 추가.
2.  **Mapper 인터페이스**: 파라미터가 변경된 경우에만 수정합니다 (보통 Domain 객체를 통째로 넘기므로 수정 불필요).

---

### 2. 🆕 테이블 신규 추가 (New Table Creation)
새로운 테이블 `tc_new_feature`를 추가하는 경우입니다.

#### 2-1. Core 계층 (설계)
1.  **Domain**: `TcNewFeature` 클래스 생성 (`tc-db-domain`).
2.  **Port (Interface)**: `TcNewFeatureStore` 인터페이스 생성 (`tc-db-core`).
    * `upsert`, `findById`, `findAll`, `delete` 등 메서드 시그니처 정의.
3.  **DTO**: `UpsertTcNewFeature`, `NewFeatureSearchCriteria` 등 생성.

#### 2-2. JPA 구현 (`tc-db-jpa-common-schema`)
1.  **Entity**: `TcNewFeatureEntity` 생성.
    * `@Entity`, `@Table` 선언.
    * **중요:** MapStruct 호환을 위해 **`public` 기본 생성자**와 **전체 인자 생성자**를 반드시 포함해야 합니다.
2.  **Repository**: `TcNewFeatureJpaRepository` 인터페이스 생성 (`extends JpaRepository`).
3.  **Mapper**: `TcNewFeatureEntityMapper` 인터페이스 생성.
    * `@Mapper(componentModel = "spring")` 적용.
    * `toDomain()`, `updateEntity()` 메서드 정의.
4.  **Store Impl**: `TcNewFeatureJpaStore` 클래스 생성 (`implements TcNewFeatureStore`).
    * Repository와 Mapper를 주입받아 구현.

#### 2-3. MyBatis 구현 (`tc-db-mybatis-common-schema`)
1.  **Mapper Interface**: `TcNewFeatureMapper` 자바 인터페이스 생성.
2.  **Mapper XML**: `TcNewFeatureMapper.xml` 작성 (`<mapper namespace="...">`).
    * `resultMap`, `insert`, `update`, `select`, `delete` SQL 작성.
    * **주의:** `findAll` 등 리스트 조회 시 반드시 **`LIMIT / OFFSET`** 구문을 사용하여 메모리 이슈를 방지해야 합니다.
3.  **Store Impl**: `TcNewFeatureMybatisStore` 클래스 생성.
    * Mapper를 주입받아 구현.

---

### 3. 🛠 컬럼 수정/삭제 (Modification / Deletion)

#### 3-1. 컬럼 타입/이름 변경
1.  **Core**: Domain 및 DTO의 필드 타입/이름 변경.
2.  **JPA**: Entity 필드 변경 -> **Build 실행** -> 컴파일 에러가 발생하는 지점(Store 수동 로직 등)을 찾아 수정.
3.  **MyBatis**: XML 파일에서 해당 컬럼을 사용하는 모든 SQL(`SELECT`, `INSERT`, `UPDATE`, `WHERE`)을 찾아 **수동으로 수정**.

#### 3-2. 컬럼 삭제
1.  **Core**: Domain 및 DTO에서 필드 제거.
2.  **JPA**: Entity에서 필드 제거 -> **Build 실행**. (MapStruct가 해당 필드 매핑 코드를 자동 삭제함)
3.  **MyBatis**: XML 파일에서 해당 컬럼과 관련된 모든 SQL 구문 제거.

---

### 4. ✅ 체크리스트 (Commit 전 확인)

작업을 마치고 PR(Pull Request)을 올리기 전에 확인하세요.

- [ ] **Core**: Domain 객체와 Store 인터페이스가 특정 DB 기술(JPA annotations 등)에 의존하지 않는 순수한 자바 코드인가?
- [ ] **JPA**: Entity에 **`public` 기본 생성자**가 있는가? (MapStruct 필수 조건)
- [ ] **JPA**: `gradlew clean build` 실행 시 MapStruct 관련 에러 없이 빌드가 성공하는가?
- [ ] **MyBatis**: `findAll` 조회 시 **`LIMIT / OFFSET`** 처리가 되어 있는가? (OOM 방지)
- [ ] **Test**: `tc-comm-gateway-app`의 스모크 테스트(`DbReadAllTables...`)가 통과하는가?

---

### 📂 디렉토리 구조 참고

```text
nori-tc/
├── libs/
│   ├── db/
│   │   ├── tc-db-core/          # [Port] Store 인터페이스, DTO
│   │   ├── tc-db-domain/        # [Domain] 순수 도메인 객체
│   │   ├── jpa/
│   │   │   └── tc-db-jpa-common-schema/
│   │   │       ├── entity/      # [JPA] Entity (public constructor 필수)
│   │   │       ├── mapper/      # [JPA] MapStruct Interface
│   │   │       ├── repository/  # [JPA] Spring Data Repository
│   │   │       └── store/       # [Adapter] JPA Store 구현체
│   │   └── mybatis/
│   │       └── tc-db-mybatis-common-schema/
│   │           ├── mapper/      # [MyBatis] Mapper Interface
│   │           ├── store/       # [Adapter] MyBatis Store 구현체
│   │           └── resources/   # [MyBatis] Mapper XML (Limit/Offset 필수)