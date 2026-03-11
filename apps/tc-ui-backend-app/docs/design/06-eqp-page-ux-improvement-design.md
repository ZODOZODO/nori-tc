# 06. EQP 페이지 UX 개선 설계

## 1. 개요

### 1.1 배경

현재 EQP 페이지(`EqpPage.tsx`)는 다음과 같은 UX 문제점을 갖고 있다.

- 체크아웃/체크인 흐름이 사용자 의도와 다르게 동작
- 사이드바가 콘텐츠 높이에 맞춰 잘림
- gateway_app 그룹 클릭 시 해당 그룹 설비 목록을 테이블로 볼 수 없음
- 페이지 최초 진입 시 자동으로 첫 번째 설비가 선택되어 콘텐츠가 표시됨
- 설비 정보 영역과 파라미터 영역의 크기를 사용자가 조절할 수 없음
- 설비/그룹 선택 시 다른 사용자의 변경사항이 반영되지 않을 수 있음 (5분 캐시 staleTime)

### 1.2 목표

6가지 UX 문제를 해결하여 사용자가 의도한 흐름대로 동작하는 EQP 페이지를 설계한다.

---

## 2. 현재 상태 분석

### 2.1 선택 상태 구분 (Selection State)

| 선택 대상 | 현재 동작 | 원하는 동작 |
|-----------|-----------|-------------|
| 없음 (초기 진입) | 첫 번째 EQP 자동 선택 → 콘텐츠 표시 | 아무것도 선택되지 않은 상태 → 가운데 비어있음 |
| gateway_app 그룹 클릭 | 클릭 불가 (단순 텍스트) | 해당 그룹 설비 목록을 다중 행 테이블로 표시 |
| 개별 설비 클릭 | 설비 정보 + 파라미터 + Checkout 영역 표시 | 동일 (유지) |

### 2.2 선택 상태 모델 (신규 정의)

현재는 선택 상태가 `selectedEqpId: string | null` 하나뿐이다.
개선 후에는 **선택 유형(type)**과 **선택 값(value)**을 함께 추적해야 한다.

```
SelectionState =
  | { type: 'none' }                          // 초기 진입 / 선택 없음
  | { type: 'gateway_group', groupIndex: number }  // gateway_app 그룹 선택
  | { type: 'eqp', eqpId: string }            // 개별 설비 선택
```

### 2.3 체크아웃 / 체크인 흐름

**현재 문제:**
- `handleCheckOut`에서 `!appliedVersion`이면 요청을 보내지 않음
- 초기에 자동으로 첫 번째 설비가 선택되어, 원하지 않는 상태에서 체크아웃이 가능해짐

**백엔드 체크아웃 로직 (정상):**
```
POST /api/eqp/{eqpId}/checkout
  → tc_eqp_param에 해당 eqpId의 param_version='EDIT' 존재 여부 확인
  → 존재: 409 Conflict (EqpAlreadyCheckedOutException)
  → 미존재: sourceVersion 파라미터를 EDIT으로 복사 후 200 OK
```

**원하는 흐름:**
```
사용자가 설비 선택 → Check Out 클릭
  → 백엔드에 checkout 요청
  → EDIT 버전 있음: 실패 메시지 표시
  → EDIT 버전 없음: 성공 → 편집 모드 진입 → Check In 버튼 표시
```

### 2.4 사이드바 높이 문제

- 현재 `<aside>`는 `h-full`이지만, 부모 `<main>`이 실제 콘텐츠 높이에 맞춰 동작함
- 결과적으로 트리 항목이 적으면 사이드바가 화면 하단까지 채우지 못함

**원인:**
`main` 요소가 `flex-1`이지만 내부 컨텐츠(트리)가 짧으면 높이가 콘텐츠에 맞춰 결정됨

**해결 방향:**
사이드바에 `self-stretch` 또는 `min-h-full` 적용, 또는 `main` 영역이 항상 남은 화면 전체를 채우도록 레이아웃 조정

### 2.5 가운데 영역 레이아웃 크기 조절

현재 설비 정보 테이블과 파라미터/Checkout 섹션은 고정 레이아웃이다.

**원하는 동작:**
- 두 섹션 사이에 드래그 핸들(Resizer)을 배치
- 사용자가 위아래로 드래그하여 각 섹션의 높이를 조절할 수 있어야 함
- 두 섹션의 합산 높이는 항상 가운데 영역 전체를 채워야 함

### 2.6 데이터 최신성 현황

**현재 React Query 전역 설정 (`AppProvider.tsx`):**
- staleTime: 5분 (QueryClient 기본값) → 같은 EQP를 5분 내 재선택 시 캐시 반환
- refetchOnWindowFocus: true (기본값)
- refetchInterval: 없음 (폴링 없음)

**현재 훅별 staleTime:**

| 훅 | staleTime | 문제 |
|----|-----------|------|
| useEqpList | 5분 | 다른 사용자의 EQP 추가/수정이 반영 안 됨 |
| useEqpDetail | 5분 | 재선택 시 오래된 IP/포트/모델 표시 가능 |
| useEqpRuntimeState | 5분 | 실시간 상태가 5분 지연될 수 있음 |
| useEqpParamVersions | 5분 | 다른 사용자 체크인으로 추가된 버전 미반영 |
| useEqpCheckoutStatus | 0 | 이미 항상 최신 (정상) |
| useEqpParams | 5분 | EDIT 버전 파라미터가 오래된 데이터일 수 있음 |

---

## 3. 개선 항목 상세 설계

---

## 3. 개선 항목 상세 설계

### 3.1 [개선 1] 선택 상태 모델 도입

#### 3.1.1 Store 변경

**파일:** `src/features/eqp/stores/eqp-ui.store.ts`

현재 상태:
```ts
selectedEqpId: string | null
```

변경 후:
```ts
type EqpSelection =
  | { type: 'none' }
  | { type: 'gateway_group'; groupIndex: number }
  | { type: 'eqp'; eqpId: string }

selection: EqpSelection  // 초기값: { type: 'none' }
```

**연관 변경:**
- `setSelectedEqpId(eqpId)` → `selectEqp(eqpId: string)` (type: 'eqp')
- `selectGatewayGroup(groupIndex: number)` 신규 추가 (type: 'gateway_group')
- `clearSelection()` 신규 추가 (type: 'none')

#### 3.1.2 초기 진입 시 자동 선택 제거

**파일:** `src/features/eqp/components/EqpPage.tsx`

제거 대상:
```ts
// 아래 useEffect 제거
useEffect(() => {
  if (selectedEqpId || eqpItems.length === 0) return
  const firstEqpId = eqpItems[0]?.eqpId
  if (!firstEqpId) return
  setSelectedEqpId(firstEqpId)
}, [selectedEqpId, eqpItems, setSelectedEqpId])
```

**결과:** 초기 진입 시 selection = `{ type: 'none' }`, 가운데 영역 비어있음

---

### 3.2 [개선 2] 사이드바 항상 화면 하단까지 이어지도록 수정

#### 3.2.1 레이아웃 구조

```
<div class="flex min-h-screen w-screen flex-col">
  <header />                         ← 52px 고정
  <div class="h-px" />              ← 구분선
  <main class="flex flex-1">        ← 남은 높이 전체 차지
    <EqpSidebar />                  ← h-full (부모 높이 전체)
    <section class="flex-1" />      ← 나머지 너비 차지
  </main>
</div>
```

**수정 포인트:**
- `main` 요소: `overflow-hidden` 제거, `flex-1` 유지, `min-h-0` 추가
- `EqpSidebar`의 `<aside>`: `h-full` 유지, `min-h-full` 추가

---

### 3.3 [개선 3] gateway_app 그룹 클릭 → 설비 목록 테이블 표시

#### 3.3.1 사이드바 변경

**파일:** `src/features/eqp/components/EqpSidebar.tsx`

- `gateway_app{N}` 텍스트를 클릭 가능한 `<button>`으로 변경
- 클릭 시 `onSelectGatewayGroup(groupIndex)` 콜백 호출
- 선택된 그룹은 강조 표시

```tsx
interface EqpSidebarProps {
  // 기존
  onSelectEqp: (eqpId: string) => void
  // 신규 추가
  onSelectGatewayGroup: (groupIndex: number) => void
  selectedGroupIndex: number | null
}
```

#### 3.3.2 가운데 영역 콘텐츠 분기

**파일:** `src/features/eqp/components/EqpPage.tsx`

```
selection.type === 'none'
  → 가운데 영역 비어있음 (안내 문구만 표시)

selection.type === 'gateway_group'
  → 설비 정보 테이블만 표시 (해당 그룹의 모든 설비 행)
  → 파라미터/Checkout/버전 섹션 없음
  → 테이블이 가운데 영역 전체를 채움

selection.type === 'eqp'
  → 기존 레이아웃: 설비 정보 테이블 + 파라미터/Checkout/버전 섹션
  → 두 섹션은 ResizablePanelGroup으로 크기 조절 가능
```

#### 3.3.3 EqpInfoTable 다중 행 지원

**파일:** `src/features/eqp/components/EqpInfoTable.tsx`

현재 단건(eqp: EqpInfo | null) 지원 → 다건(eqps: EqpInfo[]) 지원 추가

```tsx
// 단건 모드 (개별 설비 선택)
<EqpInfoTable eqp={...} modelInfo={...} runtimeState={...} />

// 다건 모드 (gateway_group 선택)
<EqpInfoTable eqps={[...]} />  // 복수 설비, modelInfo/runtimeState 없음
```

**OR** 별도 컴포넌트 분리 방식:

```
EqpInfoTable      → 단건 표시 (eqp 선택 시)
GatewayGroupTable → 다건 표시 (gateway_group 선택 시, 간소화된 컬럼)
```

> **권장:** 별도 컴포넌트(`GatewayGroupTable`) 분리 방식
> 이유: 컬럼 구성이 다르고 (runtimeState 없음), 역할이 명확히 다름

#### 3.3.4 GatewayGroupTable 컬럼 구성

| 컬럼 | 설명 |
|------|------|
| EQPID | 설비 ID |
| Comm Interface | 통신 인터페이스 |
| Comm Mode | 통신 모드 |
| Route Partition | 라우트 파티션 번호 |
| IP | 설비 IP |
| Port | 설비 포트 |
| Enabled | 활성 여부 |

> runtimeState, modelInfo는 다건 조회 시 비용이 크므로 제외

---

### 3.4 [개선 4] 체크아웃 / 체크인 흐름 정비

#### 3.4.1 현재 문제

- `handleCheckOut`에서 `!appliedVersion`이면 early return → 버전 없는 설비 checkout 불가
- `isCheckoutDisabled` 조건에 `!appliedVersion` 포함 → 버전 없으면 버튼 비활성화

#### 3.4.2 개선 방향

사용자가 설비를 선택한 후 Check Out 버튼을 클릭하면:

1. `appliedVersion`이 있으면 해당 버전을 `sourceVersion`으로 전달
2. `appliedVersion`이 없으면 `sourceVersion`을 빈 문자열 또는 null로 전달
   → 백엔드에서 파라미터 없이 빈 EDIT 버전 생성 허용

```ts
const handleCheckOut = async () => {
  if (!selectedEqpId) return  // eqpId만 필수

  try {
    await checkoutMutation.mutateAsync({
      eqpId: selectedEqpId,
      request: { sourceVersion: appliedVersion ?? '' },  // 버전 없으면 빈 문자열
    })
    setEditMode(true)
  } catch (error) {
    setCheckInErrorMessage(resolveErrorMessage(error, '체크아웃에 실패했습니다.'))
  }
}
```

**isCheckoutDisabled 조건 변경:**

```ts
// 현재
const isCheckoutDisabled =
  !eqpDetailQuery.data ||
  !appliedVersion ||          // ← 제거
  checkoutMutation.isPending ||
  (checkoutStatus.isCheckedOut && !isEditMode)

// 변경 후
const isCheckoutDisabled =
  !eqpDetailQuery.data ||
  checkoutMutation.isPending ||
  (checkoutStatus.isCheckedOut && !isEditMode)
```

#### 3.4.3 체크인 흐름 (변경 없음)

체크인 로직은 현재와 동일하게 유지:
- Check In 버튼 클릭 → CheckInModal 표시
- 버전명/설명 입력 후 Save → `POST /api/eqp/{eqpId}/checkin`
- 성공: EDIT 버전 삭제 → 편집 모드 해제

---

### 3.5 [개선 5] 레이아웃 크기 조절 (Resizable Panel)

#### 3.5.1 설계

`개별 설비(eqp)` 선택 시, 가운데 영역은 두 섹션으로 나뉜다:

```
┌─────────────────────────┐  ← 가운데 영역 전체
│  설비 정보 테이블         │  ← Panel A (초기 높이: 40%)
├────────── ⟷ ───────────┤  ← Resizer (드래그 핸들)
│  설비 파라미터           │
│  Check Out / Version    │  ← Panel B (초기 높이: 60%)
└─────────────────────────┘
```

#### 3.5.2 구현 방식

**옵션 A: 직접 구현 (mouse/touch drag)**
- `onMouseDown` → `onMouseMove` → `onMouseUp` 이벤트로 높이 비율 조절
- 의존성 추가 없음

**옵션 B: `react-resizable-panels` 라이브러리 사용**
- `Panel`, `PanelGroup`, `PanelResizeHandle` 컴포넌트 사용
- 선언적이고 접근성(keyboard) 지원

> **권장: 옵션 A (직접 구현)**
> 이유: 현재 프로젝트에 외부 패널 라이브러리 없음, 요구사항이 단순(상하 분할만), 불필요한 의존성 추가 지양

#### 3.5.3 Resizer 컴포넌트 설계

```tsx
// src/features/eqp/components/ResizableDivider.tsx

interface ResizableDividerProps {
  onDrag: (deltaY: number) => void
}

// 드래그 핸들 UI: 수평 점선 또는 그립 아이콘
// mousedown → document mousemove/mouseup 등록 → deltaY 계산 → onDrag 콜백
```

#### 3.5.4 크기 상태 관리

```ts
// EqpPage.tsx 내부 로컬 상태
const [topPanelHeightPercent, setTopPanelHeightPercent] = useState(40)

// 상단 패널: height = `${topPanelHeightPercent}%`
// 하단 패널: height = `${100 - topPanelHeightPercent}%`
// 최소/최대 제한: 15% ~ 85%
```

---

### 3.6 [개선 6] 데이터 최신성 전략

#### 3.6.1 전략 개요

"선택 행위 = 신선도 트리거"로 취급한다. 두 가지 방식을 결합한다.

- **전략 A: 선택 시 명시적 캐시 무효화** — 선택 콜백에서 관련 캐시를 즉시 invalidate
- **전략 B: 데이터 유형별 staleTime 조정** — 변경 빈도와 중요도에 따라 staleTime 재설정

#### 3.6.2 전략 A: 선택 시 명시적 캐시 무효화

`handleSelectEqp`, `handleSelectGatewayGroup` 콜백에서 관련 캐시를 즉시 무효화한다.
React Query는 invalidate된 캐시를 stale로 표시하고, 구독 컴포넌트가 있으면 백그라운드 리페치를 자동 트리거한다.
기존 캐시 데이터를 먼저 보여주다가 새 데이터로 교체하므로 UI가 블로킹되지 않는다.

```
handleSelectEqp(eqpId) 실행 시:
  queryClient.invalidateQueries({ queryKey: ['eqp', 'detail', eqpId] })
  queryClient.invalidateQueries({ queryKey: ['eqp', 'runtimeState', eqpId] })
  queryClient.invalidateQueries({ queryKey: ['eqp', 'paramVersions', eqpId] })
  queryClient.invalidateQueries({ queryKey: ['eqp', 'checkoutStatus', eqpId] })

handleSelectGatewayGroup(groupIndex) 실행 시:
  queryClient.invalidateQueries({ queryKey: ['eqp', 'list'] })
```

**부하 분석:**
- EQP 선택 행위는 고빈도가 아니므로 허용 가능
- 기존에도 eqpId 변경 시 `enabled` 조건 변경으로 새 요청 발생
- 실질적 추가 부하는 "같은 EQP 재선택" 케이스만 해당 (의도된 동작: 사용자가 명시적으로 새로고침을 원하는 행위로 해석)

#### 3.6.3 전략 B: 데이터 유형별 staleTime 조정

| 훅 | 현재 | 변경 후 | 이유 |
|----|------|---------|------|
| useEqpList | 5분 | 60초 | 다른 유저의 EQP 추가/수정 반영 |
| useEqpDetail | 5분 | 0 | 선택 시 항상 최신 (IP/포트/모델 변경 가능) |
| useEqpRuntimeState | 5분 | 0 | 실시간 연결/제어 상태 |
| useEqpParamVersions | 5분 | 0 | 다른 사용자 체크인으로 버전 추가 가능 |
| useEqpCheckoutStatus | 0 | 0 유지 | 이미 항상 최신 |
| useEqpParams (EDIT) | 5분 | 0 | 편집 데이터는 항상 최신 필요 |
| useEqpParams (특정 버전) | 5분 | 5분 유지 | 특정 버전 파라미터는 불변 데이터 |
| useEqpModelInfo | 5분 | 5분 유지 | 변경 빈도 낮음 |

> **staleTime 0의 의미:** 매번 즉시 요청이 아니라, 컴포넌트 마운트·window focus·invalidation 시에만 리페치. "항상 최신"이지 "항상 즉시 요청"은 아님

---

## 4. 최종 레이아웃 구조

### 4.1 초기 진입 (selection: none)

```
┌───────────────────────────────────────────────────┐
│  Header                                           │
├──────────┬────────────────────────────────────────┤
│          │                                        │
│ Sidebar  │  (비어있음)                             │
│ (항상    │  "설비를 선택해 주세요." 안내 문구       │
│  하단    │                                        │
│  까지)   │                                        │
│          │                                        │
└──────────┴────────────────────────────────────────┘
```

### 4.2 gateway_app 그룹 선택 (selection: gateway_group)

```
┌───────────────────────────────────────────────────┐
│  Header                                           │
├──────────┬────────────────────────────────────────┤
│          │                                        │
│ Sidebar  │  설비 정보 테이블 (다중 행)              │
│ (항상    │  ┌────────────────────────────────────┐│
│  하단    │  │ EQPID │ Comm │ IP │ Port │ Enabled ││
│  까지)   │  ├────────────────────────────────────┤│
│          │  │ eqp01 │ ...  │ .. │ ...  │  ●      ││
│          │  │ eqp02 │ ...  │ .. │ ...  │  ●      ││
│          │  └────────────────────────────────────┘│
│          │  (가운데 전체 영역을 채움)               │
└──────────┴────────────────────────────────────────┘
```

### 4.3 개별 설비 선택 (selection: eqp)

```
┌───────────────────────────────────────────────────┐
│  Header                                           │
├──────────┬────────────────────────────────────────┤
│          │  설비 정보 테이블 (단건)    ← Panel A   │
│ Sidebar  │  ─────────────── ⟷ ───────────────────│ ← Resizer
│ (항상    │  [버전 선택] [Check Out]               │
│  하단    │  설비 파라미터 테이블       ← Panel B   │
│  까지)   │                                        │
│          │                                        │
└──────────┴────────────────────────────────────────┘
```

---

## 5. 영향 범위 요약

| 파일 | 변경 유형 | 변경 내용 |
|------|-----------|-----------|
| `EqpPage.tsx` | 수정 | 선택 상태 분기 로직, 자동 선택 제거, 체크아웃 조건 수정, ResizableDivider 적용, invalidateQueries 추가 |
| `EqpSidebar.tsx` | 수정 | gateway_app 클릭 핸들러 추가, 선택 상태 표시 |
| `EqpInfoTable.tsx` | 수정 또는 유지 | 단건 모드 유지 |
| `GatewayGroupTable.tsx` | 신규 | 다건 설비 목록 테이블 |
| `ResizableDivider.tsx` | 신규 | 드래그 핸들 컴포넌트 |
| `eqp-ui.store.ts` | 수정 | 선택 상태 모델(EqpSelection) 도입 |
| `eqp.types.ts` | 수정 (가능) | EqpSelection 타입 추가 |
| `useEqpDetail.ts` | 수정 | staleTime: 0 추가 |
| `useEqpRuntimeState.ts` | 수정 | staleTime: 0 추가 |
| `useEqpParamVersions.ts` | 수정 | staleTime: 0 추가 |
| `useEqpParams.ts` | 수정 | EDIT 버전만 staleTime: 0 조건부 설정 |
| `useEqpList.ts` | 수정 | staleTime: 60_000 (5분 → 60초) |

---

## 6. 미결 사항 / 추가 확인 필요

1. **gateway_group 선택 시 runtimeState 표시 여부**: 다건 조회는 N+1 비용이 크므로 현재 설계에서는 제외. 추후 배치 조회 API 추가 가능
2. **Resizer 초기 높이 퍼센트 저장**: 현재 설계는 세션 내 유지. localStorage 저장은 추후 검토
3. **설비 파라미터 버전 선택 영역**: `gateway_group` 선택 시 표시하지 않음 (파라미터는 개별 설비 단위이므로)
4. **체크아웃 흐름**: `sourceVersion`이 빈 문자열로 전달될 때 백엔드 동작 확인 필요 (현재 빈 EDIT 버전 허용)
