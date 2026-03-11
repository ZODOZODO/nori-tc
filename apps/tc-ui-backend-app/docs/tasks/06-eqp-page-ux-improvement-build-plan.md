# 06. EQP 페이지 UX 개선 작업 계획

## 참조 문서

- 설계: `docs/design/06-eqp-page-ux-improvement-design.md`

---

## 작업 범위

| # | 작업 항목 | 대상 파일 |
|---|-----------|-----------|
| T1 | 선택 상태 모델(EqpSelection) 도입 | `eqp-ui.store.ts`, `eqp.types.ts` |
| T2 | 사이드바 항상 화면 하단까지 이어지도록 수정 | `EqpSidebar.tsx`, `EqpPage.tsx` |
| T3 | gateway_app 그룹 클릭 → 설비 목록 테이블 표시 | `EqpSidebar.tsx`, `GatewayGroupTable.tsx` (신규), `EqpPage.tsx` |
| T4 | 초기 진입 시 자동 설비 선택 제거 | `EqpPage.tsx` |
| T5 | 체크아웃 조건 수정 (버전 없어도 checkout 가능) | `EqpPage.tsx` |
| T6 | 레이아웃 크기 조절 (ResizableDivider) | `ResizableDivider.tsx` (신규), `EqpPage.tsx` |
| T7 | 데이터 최신성 전략 적용 | `useEqpDetail.ts`, `useEqpRuntimeState.ts`, `useEqpParamVersions.ts`, `useEqpParams.ts`, `useEqpList.ts`, `EqpPage.tsx` |

---

## T1. 선택 상태 모델(EqpSelection) 도입

### 목적

현재 `selectedEqpId: string | null` 단일 상태로는 "그룹 선택"과 "설비 선택"을 구분할 수 없다.
`EqpSelection` 유니온 타입으로 선택 유형을 명확히 표현한다.

### 작업 내용

#### 1. `eqp.types.ts` — EqpSelection 타입 추가

```ts
export type EqpSelection =
  | { type: 'none' }
  | { type: 'gateway_group'; groupIndex: number }
  | { type: 'eqp'; eqpId: string }
```

#### 2. `eqp-ui.store.ts` — 상태 및 액션 변경

**변경 전:**
```ts
selectedEqpId: string | null
setSelectedEqpId: (eqpId: string | null) => void
```

**변경 후:**
```ts
selection: EqpSelection   // 초기값: { type: 'none' }
selectEqp: (eqpId: string) => void
selectGatewayGroup: (groupIndex: number) => void
clearSelection: () => void
```

#### 3. `EqpPage.tsx` — 모든 `selectedEqpId` 참조를 새 상태 기준으로 교체

- `selectedEqpId` 파생: `selection.type === 'eqp' ? selection.eqpId : null`
- 모든 쿼리 훅의 `enabled` 조건도 위 파생값 기준

---

## T2. 사이드바 항상 화면 하단까지 이어지도록 수정

### 목적

현재 사이드바는 트리 콘텐츠 높이에 맞춰 잘린다. 항상 뷰포트 하단까지 이어져야 한다.

### 작업 내용

#### 1. `EqpPage.tsx` — `<main>` 레이아웃 수정

```tsx
// 현재
<main className="flex min-h-0 flex-1 overflow-hidden">

// 변경 후
<main className="flex flex-1 min-h-0">
```

#### 2. `EqpSidebar.tsx` — `<aside>` 높이 고정

```tsx
// 현재 (펼침 상태)
<aside className="flex h-full w-60 flex-col border-r ...">

// 변경 후: self-stretch 추가 또는 min-h-full 확인
// 부모 main이 stretch 레이아웃이면 h-full만으로 충분
// 실제 렌더링 확인 후 조정
```

#### 3. 사이드바 내부 스크롤 처리

- 트리 아이템이 많을 경우 스크롤: `overflow-y-auto`는 이미 적용되어 있으므로 유지
- 사이드바 자체는 스크롤 없이 하단까지 고정

---

## T3. gateway_app 그룹 클릭 → 설비 목록 테이블 표시

### 목적

gateway_app 그룹명을 클릭하면 해당 그룹에 속한 모든 설비를 테이블 형태로 표시한다.
이때 파라미터/Checkout/버전 섹션은 표시하지 않는다.

### 작업 내용

#### 1. `EqpSidebar.tsx` — gateway_app 그룹 클릭 핸들러 추가

```tsx
// props 추가
interface EqpSidebarProps {
  selectedGroupIndex: number | null         // 신규
  onSelectGatewayGroup: (groupIndex: number) => void  // 신규
  // ...기존 유지
}

// gateway_app 그룹명 버튼화
<button
  type="button"
  onClick={() => onSelectGatewayGroup(group.appIndex)}
  className={cn(
    'text-[11px] font-medium',
    selectedGroupIndex === group.appIndex
      ? 'text-[#1F2D26] font-bold'
      : 'text-[#5D6B65] hover:text-[#1F2D26]',
  )}
>
  {group.appName}
</button>
```

#### 2. `GatewayGroupTable.tsx` — 신규 컴포넌트 생성

**파일 경로:** `src/features/eqp/components/GatewayGroupTable.tsx`

```tsx
interface GatewayGroupTableProps {
  eqpItems: EqpInfo[]
  groupName: string
}

export function GatewayGroupTable({ eqpItems, groupName }: GatewayGroupTableProps) {
  // 컬럼: EQPID, Comm Interface, Comm Mode, Route Partition, IP, Port, Enabled
  // 각 행은 설비 정보 1건
  // Enabled: EquipmentStatusIndicator (enabled 필드 기준만 사용, runtimeState 없음)
}
```

#### 3. `EqpPage.tsx` — 선택 유형에 따른 가운데 영역 분기

```tsx
// selection.type === 'none'
<div className="flex flex-1 items-center justify-center text-sm text-[#8A8A8A]">
  설비 또는 게이트웨이 그룹을 선택해 주세요.
</div>

// selection.type === 'gateway_group'
<section className="flex flex-1 flex-col p-3 md:p-4">
  <GatewayGroupTable
    eqpItems={gatewayGroupItems}  // 해당 그룹의 EqpInfo[]
    groupName={`gateway_app${selection.groupIndex}`}
  />
</section>

// selection.type === 'eqp'
<section className="flex flex-1 flex-col p-3 md:p-4">
  {/* 기존 레이아웃 (설비 정보 + ResizableDivider + 파라미터/Checkout 섹션) */}
</section>
```

**gateway_group 선택 시 필요한 eqpItems 파생:**
```ts
const gatewayGroupItems = useMemo(() => {
  if (selection.type !== 'gateway_group') return []
  const group = gatewayGroups.find(g => g.appIndex === selection.groupIndex)
  return group?.items ?? []
}, [selection, gatewayGroups])
```

> `gatewayGroups`는 현재 `EqpSidebar` 내부에서 계산됨.
> `EqpPage`에서도 동일하게 계산하거나, EqpSidebar에서 콜백으로 전달받는 구조로 변경 필요.
> **권장:** `EqpPage`에서 동일한 grouping 로직을 갖고, Sidebar에 `gatewayGroups`를 prop으로 내려줌

---

## T4. 초기 진입 시 자동 설비 선택 제거

### 목적

페이지 최초 진입 시 첫 번째 설비가 자동 선택되어 콘텐츠가 표시되는 문제를 제거한다.

### 작업 내용

#### `EqpPage.tsx` — 자동 선택 useEffect 제거

```tsx
// 아래 블록 전체 제거
useEffect(() => {
  if (selectedEqpId || eqpItems.length === 0) {
    return
  }

  const firstEqpId = eqpItems[0]?.eqpId
  if (!firstEqpId) {
    return
  }

  setSelectedEqpId(firstEqpId)
}, [selectedEqpId, eqpItems, setSelectedEqpId])
```

**주의:** 이 useEffect 제거로 `selectedEqpId`가 null인 상태에서 동작하는 다른 useEffect도 점검 필요
- `appliedVersion` 초기화 useEffect: `!selectedEqpId`이면 빈 문자열로 초기화 → 정상
- `checkoutStatus` 복원 useEffect: `!selectedEqpId`이면 return → 정상

---

## T5. 체크아웃 조건 수정 (버전 없어도 Check Out 가능)

### 목적

현재 `appliedVersion`이 없으면 Check Out 버튼이 비활성화되고 요청도 보내지 않음.
버전이 없는 설비도 Check Out 가능하도록 수정한다.

### 작업 내용

#### `EqpPage.tsx`

**isCheckoutDisabled 수정:**

```ts
// 현재
const isCheckoutDisabled =
  !eqpDetailQuery.data ||
  !appliedVersion ||
  checkoutMutation.isPending ||
  (checkoutStatus.isCheckedOut && !isEditMode)

// 변경 후
const isCheckoutDisabled =
  !eqpDetailQuery.data ||
  checkoutMutation.isPending ||
  (checkoutStatus.isCheckedOut && !isEditMode)
```

**handleCheckOut 수정:**

```ts
// 현재
const handleCheckOut = async () => {
  if (!selectedEqpId || !appliedVersion) {  // !appliedVersion 조건 제거
    return
  }
  // ...
}

// 변경 후
const handleCheckOut = async () => {
  if (!selectedEqpId) {
    return
  }

  try {
    setCheckInErrorMessage(null)
    await checkoutMutation.mutateAsync({
      eqpId: selectedEqpId,
      request: { sourceVersion: appliedVersion },  // 빈 문자열이어도 전달
    })
    setEditMode(true)
  } catch (error) {
    setCheckInErrorMessage(resolveErrorMessage(error, '체크아웃에 실패했습니다.'))
  }
}
```

**백엔드 동작 확인:**
- `sourceVersion`이 빈 문자열로 전달 시 백엔드에서 `blank` 검증으로 400 발생 가능
- 백엔드 `EqpParamCommandPort.checkout()`에서 `sourceVersion` blank 검증 조건 확인 후 필요 시 백엔드도 함께 수정

---

## T6. 레이아웃 크기 조절 (ResizableDivider)

### 목적

개별 설비 선택 시 설비 정보 테이블과 파라미터/Checkout 섹션 사이에 드래그 핸들을 제공하여
사용자가 두 영역의 높이를 자유롭게 조절할 수 있도록 한다.

### 작업 내용

#### 1. `ResizableDivider.tsx` — 신규 컴포넌트 생성

**파일 경로:** `src/features/eqp/components/ResizableDivider.tsx`

```tsx
interface ResizableDividerProps {
  onDrag: (deltaY: number) => void
}

/**
 * 위아래 패널 사이의 드래그 핸들 컴포넌트입니다.
 * mousedown 이벤트로 드래그 시작, document의 mousemove로 deltaY를 계산해 onDrag 콜백으로 전달합니다.
 * touchmove도 지원합니다.
 */
export function ResizableDivider({ onDrag }: ResizableDividerProps) {
  // mousedown → document.addEventListener('mousemove', handleMouseMove)
  // mouseup → document.removeEventListener 정리
  // deltaY 계산 후 onDrag(deltaY) 호출
}
```

**UI 디자인:**
```
┌──────────────────────────────────────────┐
│         ⋯⋯⋯ (그립 점선 or 아이콘) ⋯⋯⋯        │ ← h-2 ~ h-3, cursor-row-resize
└──────────────────────────────────────────┘
```

#### 2. `EqpPage.tsx` — 크기 상태 및 분기 적용

```tsx
// 상단 패널 높이 퍼센트 (15% ~ 85%)
const [topPanelHeightPercent, setTopPanelHeightPercent] = useState(35)
const PANEL_MIN_PERCENT = 15
const PANEL_MAX_PERCENT = 85

// 드래그 핸들러
const handleDividerDrag = useCallback((deltaY: number) => {
  setTopPanelHeightPercent((prev) => {
    const containerHeight = /* 컨테이너 ref.current.clientHeight */ 0
    const deltaPct = (deltaY / containerHeight) * 100
    return Math.min(PANEL_MAX_PERCENT, Math.max(PANEL_MIN_PERCENT, prev + deltaPct))
  })
}, [])

// JSX (selection.type === 'eqp' 분기 내부)
<div ref={containerRef} className="flex flex-1 flex-col overflow-hidden">
  {/* 상단: 설비 정보 */}
  <div style={{ height: `${topPanelHeightPercent}%` }} className="overflow-auto">
    <EqpInfoTable ... />
  </div>

  {/* 드래그 핸들 */}
  <ResizableDivider onDrag={handleDividerDrag} />

  {/* 하단: 파라미터/Checkout/버전 */}
  <div style={{ height: `${100 - topPanelHeightPercent}%` }} className="overflow-auto">
    {/* 기존 파라미터 섹션 */}
  </div>
</div>
```

---

## T7. 데이터 최신성 전략 적용

### 목적

설비 또는 gateway_app 그룹 선택 시 다른 사용자의 변경사항을 반드시 반영한다.
선택할 때마다 항상 최신 데이터를 가져오되, 불필요한 서버 부하는 최소화한다.

### 작업 내용

#### 1. 훅별 staleTime 조정

**`useEqpDetail.ts`**
```ts
return useQuery({
  queryKey: ['eqp', 'detail', eqpId],
  queryFn: () => eqpApi.getEqpDetail(eqpId as string),
  enabled: Boolean(eqpId),
  staleTime: 0,  // 추가: 선택 시 항상 최신 데이터
})
```

**`useEqpRuntimeState.ts`**
```ts
return useQuery({
  queryKey: ['eqp', 'runtimeState', eqpId],
  queryFn: () => eqpApi.getEqpRuntimeState(eqpId as string),
  enabled: Boolean(eqpId),
  staleTime: 0,  // 추가: 실시간 상태값
})
```

**`useEqpParamVersions.ts`**
```ts
return useQuery({
  queryKey: ['eqp', 'paramVersions', eqpId],
  queryFn: () => eqpApi.getEqpParamVersions(eqpId as string),
  enabled: Boolean(eqpId),
  staleTime: 0,  // 추가: 다른 사용자 체크인 버전 즉시 반영
})
```

**`useEqpParams.ts`**
```ts
return useQuery({
  queryKey: ['eqp', 'params', eqpId, version],
  queryFn: () => eqpApi.getEqpParams(eqpId as string, version as string),
  enabled: Boolean(eqpId) && Boolean(version),
  staleTime: version === 'EDIT' ? 0 : undefined,  // EDIT만 항상 최신, 특정 버전은 캐시 유지
})
```

**`useEqpList.ts`**
```ts
return useQuery({
  queryKey: ['eqp', 'list'],
  queryFn: () => eqpApi.getEqpList(0, 500),
  staleTime: 60_000,  // 변경: 5분 → 60초
})
```

#### 2. `EqpPage.tsx` — handleSelectEqp에 invalidateQueries 추가

```ts
const handleSelectEqp = (eqpId: string) => {
  // 선택 시 해당 EQP 관련 캐시 즉시 무효화 → 백그라운드 리페치 트리거
  void queryClient.invalidateQueries({ queryKey: ['eqp', 'detail', eqpId] })
  void queryClient.invalidateQueries({ queryKey: ['eqp', 'runtimeState', eqpId] })
  void queryClient.invalidateQueries({ queryKey: ['eqp', 'paramVersions', eqpId] })
  void queryClient.invalidateQueries({ queryKey: ['eqp', 'checkoutStatus', eqpId] })

  // 기존 상태 초기화
  selectEqp(eqpId)
  setAppliedVersion(EMPTY_VERSION_VALUE)
  setEditMode(false)
  setLocalEditRows([])
  setIsCheckInModalOpen(false)
  setCheckInErrorMessage(null)
}
```

#### 3. `EqpPage.tsx` — handleSelectGatewayGroup에 invalidateQueries 추가

```ts
const handleSelectGatewayGroup = (groupIndex: number) => {
  // 그룹 선택 시 eqpList 캐시 무효화 → 최신 설비 목록 반영
  void queryClient.invalidateQueries({ queryKey: ['eqp', 'list'] })
  selectGatewayGroup(groupIndex)
}
```

---

## 작업 순서 (권장)

```
T4 (자동 선택 제거)
  → T1 (선택 상태 모델 도입)
    → T2 (사이드바 높이)
    → T3 (gateway_group 클릭)
    → T5 (체크아웃 조건)
    → T6 (ResizableDivider)
    → T7 (데이터 최신성 전략)  ← T1과 독립적으로 병렬 수행 가능
```

**이유:**
- T4와 T1은 기반 상태 변경이므로 먼저 수행
- T2, T3, T5, T6, T7은 T1 완료 후 독립적으로 병렬 수행 가능
- T7은 훅 파일과 EqpPage.tsx만 영향을 받으므로 다른 작업과 충돌 없음

---

## 검증 체크리스트

> **범례**
> - [x] 구현 완료 (코드 반영됨)
> - [ ] 브라우저/런타임 확인 필요 (실제 실행 후 검증 필요)

### T1 검증
- [x] `eqp-ui.store.ts`: `selection: EqpSelection` 상태 모델 도입, 초기값 `{ type: 'none' }`
- [x] `EqpPage.tsx`: `selectEqp()` 호출 시 `selection.type === 'eqp'` 전환
- [x] `EqpPage.tsx`: `selectGatewayGroup()` 호출 시 `selection.type === 'gateway_group'` 전환
- [ ] 초기 진입 시 `selection.type === 'none'` 상태 확인 (브라우저 확인 필요)
- [ ] 설비 클릭 시 `selection.type === 'eqp'` 전환 확인 (브라우저 확인 필요)
- [ ] gateway_app 클릭 시 `selection.type === 'gateway_group'` 전환 확인 (브라우저 확인 필요)

### T2 검증
- [x] `EqpSidebar.tsx`: `<aside>` 클래스에 `self-stretch` 적용 — 부모 flex 높이 전체 채움
- [x] `EqpPage.tsx`: `<main>` 클래스 `flex flex-1 min-h-0` — 남은 화면 전체 차지
- [ ] 설비 목록이 적을 때 사이드바가 화면 하단까지 이어지는지 확인 (브라우저 확인 필요)
- [ ] 설비 목록이 많을 때 사이드바 내부만 스크롤되는지 확인 (브라우저 확인 필요)

### T3 검증
- [x] `EqpSidebar.tsx`: gateway_app 그룹명을 `<button>`으로 변경, `onSelectGatewayGroup` 콜백 호출
- [x] `GatewayGroupTable.tsx`: 신규 컴포넌트 — 다건 설비 목록 테이블 (runtimeState 제외)
- [x] `EqpPage.tsx`: `selection.type === 'gateway_group'` 분기 → `GatewayGroupTable` 렌더링
- [ ] gateway_app1 클릭 시 해당 그룹 설비 목록 테이블 표시 확인 (브라우저 확인 필요)
- [ ] 파라미터/Checkout 섹션 표시되지 않음 확인 (브라우저 확인 필요)
- [ ] 테이블이 가운데 영역 전체를 채우는지 확인 (브라우저 확인 필요)

### T4 검증
- [x] `EqpPage.tsx`: 자동 선택 `useEffect` (첫 번째 EQP 자동 선택) 제거
- [x] `EqpPage.tsx`: `selection.type === 'none'` 분기 → 안내 문구만 표시
- [ ] 페이지 최초 진입 시 가운데 영역 비어있음 확인 (브라우저 확인 필요)
- [ ] 설비 클릭 후 콘텐츠 정상 표시 확인 (브라우저 확인 필요)

### T5 검증
- [x] `EqpPage.tsx`: `isCheckoutDisabled`에서 `!appliedVersion` 조건 제거
- [x] `EqpPage.tsx`: `handleCheckOut`에서 `!appliedVersion` early return 제거
- [x] 백엔드 `EqpCheckoutRequest.java`: `@NotBlank` 제거 — sourceVersion null/blank 허용
- [x] 백엔드 `JpaEqpParamCommandPort.java`: sourceVersion blank 검증 제거, blank이면 빈 EDIT 버전 생성
- [ ] 버전이 없는 설비에서 Check Out 버튼 활성화 확인 (브라우저 확인 필요)
- [ ] Check Out 성공 시 편집 모드 전환 확인 (브라우저 확인 필요)
- [ ] EDIT 버전이 이미 있을 때 실패 메시지 표시 확인 (브라우저 확인 필요)
- [ ] sourceVersion 빈 문자열 전달 시 백엔드 동작 확인 (런타임 확인 필요)

### T6 검증
- [x] `ResizableDivider.tsx`: 신규 컴포넌트 — mousedown/mousemove/mouseup, touch 이벤트 처리, cleanup 포함
- [x] `EqpPage.tsx`: `centerContainerRef` + `handleDividerDrag` + `topPanelHeightPercent` 상태 적용
- [x] `EqpPage.tsx`: 상단 패널 `height: topPanelHeightPercent%`, 하단 패널 `height: (100 - topPanelHeightPercent)%`
- [x] 최소/최대 크기 `PANEL_MIN_PERCENT = 15`, `PANEL_MAX_PERCENT = 85` 상수 적용
- [ ] 드래그 핸들 표시 확인 (브라우저 확인 필요)
- [ ] 위아래 드래그로 패널 크기 조절 확인 (브라우저 확인 필요)
- [ ] 최소/최대 크기 제한(15%/85%) 동작 확인 (브라우저 확인 필요)
- [ ] 두 패널 합산이 항상 전체 영역을 채우는지 확인 (브라우저 확인 필요)

### T7 검증
- [x] `useEqpDetail.ts`: `staleTime: 0` 적용
- [x] `useEqpRuntimeState.ts`: `staleTime: 0` 적용
- [x] `useEqpParamVersions.ts`: `staleTime: 0` 적용
- [x] `useEqpParams.ts`: EDIT 버전만 `staleTime: 0`, 특정 버전은 기본값(5분) 유지
- [x] `useEqpList.ts`: `staleTime: 60_000` (5분 → 60초)
- [x] `EqpPage.tsx`: `handleSelectEqp` — detail/runtimeState/paramVersions/checkoutStatus `invalidateQueries` 추가
- [x] `EqpPage.tsx`: `handleSelectGatewayGroup` — eqp/list `invalidateQueries` 추가
- [ ] 설비 A 선택 → 네트워크 탭에서 detail/runtimeState/paramVersions/checkoutStatus API 호출 확인 (브라우저 확인 필요)
- [ ] 설비 A 재선택(클릭) → 동일 API 재호출 확인 (5분 캐시 사용 안 함) (브라우저 확인 필요)
- [ ] gateway_app1 선택 → eqp/list API 재호출 확인 (브라우저 확인 필요)
- [ ] 설비 B 선택 후 5초 내 설비 A 재선택 → API 재호출 확인 (브라우저 확인 필요)
- [ ] EDIT 버전 파라미터: staleTime 0으로 항상 리페치 확인 (브라우저 확인 필요)
- [ ] 특정 버전 파라미터: 동일 버전 재조회 시 캐시 사용 확인 (staleTime 5분) (브라우저 확인 필요)
- [ ] eqpList: 60초 이내 재조회 시 캐시 사용 확인, 60초 이후 리페치 확인 (브라우저 확인 필요)

---

## 주의사항

1. **grouping 로직 중복 제거**: `EqpSidebar` 내부의 `gatewayGroups` 계산 로직을 `EqpPage`로 이동하고 `EqpSidebar`에 prop으로 전달. 동일 로직이 두 곳에 존재하면 안 됨.
2. **체크아웃 상태 복원 useEffect**: `selection.type === 'eqp'`일 때만 동작하도록 조건 추가
3. **버전 버전 선택 useEffect**: `selection.type === 'eqp'`일 때만 동작하도록 조건 추가
4. **ResizableDivider 이벤트 정리**: 컴포넌트 언마운트 시 `document.removeEventListener` 반드시 호출
5. **백엔드 sourceVersion 빈 문자열 허용 여부**: T5 작업 전 백엔드 코드(`JpaEqpParamCommandPort.checkout()`) 확인 필요
