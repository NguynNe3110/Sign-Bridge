# Migrate Tag (Chip) Management to API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate quick tag (chip) loading, creation, deletion, and editing from local-only SQLite to the remote AI service backend (`/api/chips`), with multi-tenancy support via persistent device `userId` and local SQLite offline caching.

**Architecture:** Extend `BridgeApi` with general HTTP execution (`requestArray`, `delete`, custom headers) and introduce `UserSessionManager` for device-level multi-tenancy. Implement DTOs and mappers in `data/remote/dto` and `data/mapper/`. Enhance `ConversationDao` with atomic cache synchronization and update `ConversationRepositoryImpl` to query the remote API first with automatic fallback to local SQLite cache.

**Tech Stack:** Kotlin, Android Jetpack, OkHttp 4, SQLite / Android SQLiteOpenHelper, Robolectric, MockWebServer, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-14-migrate-chips-to-api-design.md`

## Global Constraints
- Follow package boundaries: `core`, `data`, `domain`, `feature`, `ui` (singular naming per `AGENTS.md`).
- Domain layer remains free of Android / OkHttp / SQLite imports.
- Maintain full backward compatibility for existing `ConversationRepository` interface and public methods.
- Offline resilience: If network or server fails, the app must fall back to cached chips without crashing.
- Multi-tenancy: Pass `X-User-Id` header and `user_id` parameter to isolate personal chips per device.

## Review Focus
1. Server returns empty list or network error: App falls back to local SQLite cache gracefully.
2. Server returns JSON array for `GET /api/chips`: Correctly parsed without JSONObject casting errors.
3. Editing personal chip: Server lacks PUT; app calls DELETE on old chip then POST for new chip.
4. Editing system/preset chip: System chip cannot be deleted; app creates a personal chip with the edited text.
5. Deleting chip returns `{success: false}`: Repository throws `BridgeException(BridgeError.INVALID_REQUEST)`.

---

### Task 1: DTOs and Mapper for Chip API

**Files:**
- Create: `app/src/main/java/com/uzuu/signbridge/data/remote/dto/request/CreateChipRequestDto.kt`
- Create: `app/src/main/java/com/uzuu/signbridge/data/remote/dto/response/ChipResponseDto.kt`
- Create: `app/src/main/java/com/uzuu/signbridge/data/mapper/ChipMapper.kt`
- Test: `app/src/test/java/com/uzuu/signbridge/data/mapper/ChipMapperTest.kt`

**Interfaces:**
- Produces:
  - `CreateChipRequestDto(text: String, context: String, userId: String?)`
  - `ChipResponseDto(id: String, text: String, context: String, isSystem: Boolean, userId: String?, usageCount: Int)`
  - `JSONObject.toChipResponseDto(): ChipResponseDto`
  - `ChipResponseDto.toDomain(): QuickChip`
  - `ChipResponseDto.toEntity(): QuickChipEntity`

- [ ] **Step 1: Write the failing unit tests for ChipMapper**

Write tests in `app/src/test/java/com/uzuu/signbridge/data/mapper/ChipMapperTest.kt` testing:
- Parsing JSONObject to `ChipResponseDto`
- Mapping `ChipResponseDto` to `QuickChip` (verifying `isPersonal = !isSystem`, `category = ChipCategory.DETAIL`)
- Mapping `ChipResponseDto` to `QuickChipEntity`

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.uzuu.signbridge.data.mapper.ChipMapperTest"`
Expected: FAIL with unresolved references.

- [ ] **Step 3: Implement DTOs and ChipMapper**

- Create `CreateChipRequestDto.kt` in `com.uzuu.signbridge.data.remote.dto.request`
- Create `ChipResponseDto.kt` in `com.uzuu.signbridge.data.remote.dto.response`
- Implement `toChipResponseDto()`, `toDomain()`, `toEntity()` in `com.uzuu.signbridge.data.mapper.ChipMapper.kt`

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.uzuu.signbridge.data.mapper.ChipMapperTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/uzuu/signbridge/data/remote/dto/ app/src/main/java/com/uzuu/signbridge/data/mapper/ChipMapper.kt app/src/test/java/com/uzuu/signbridge/data/mapper/ChipMapperTest.kt
git commit -m "feat: add chip DTOs and mapper"
```

---

### Task 2: UserSessionManager for Multi-Tenancy

**Files:**
- Create: `app/src/main/java/com/uzuu/signbridge/data/session/UserSessionManager.kt`
- Test: `app/src/test/java/com/uzuu/signbridge/data/session/UserSessionManagerTest.kt`

**Interfaces:**
- Produces:
  - `UserSessionManager(preferences: SharedPreferences)`
  - `UserSessionManager.userId: String` (returns existing UUID or generates & persists a new one)

- [ ] **Step 1: Write the failing test for UserSessionManager**

Write test in `app/src/test/java/com/uzuu/signbridge/data/session/UserSessionManagerTest.kt`:
- Generates a non-blank UUID when key does not exist.
- Persists and reuses the same UUID across multiple calls.
- Reuses UUID already stored in SharedPreferences.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.uzuu.signbridge.data.session.UserSessionManagerTest"`
Expected: FAIL with unresolved reference.

- [ ] **Step 3: Implement UserSessionManager**

Create `app/src/main/java/com/uzuu/signbridge/data/session/UserSessionManager.kt` using `SharedPreferences`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.uzuu.signbridge.data.session.UserSessionManagerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/uzuu/signbridge/data/session/UserSessionManager.kt app/src/test/java/com/uzuu/signbridge/data/session/UserSessionManagerTest.kt
git commit -m "feat: add UserSessionManager for persistent device identity"
```

---

### Task 3: Extend BridgeApi for List, Delete, and Custom Headers

**Files:**
- Modify: `app/src/main/java/com/uzuu/signbridge/data/remote/api/BridgeApi.kt`
- Test: `app/src/test/java/com/uzuu/signbridge/data/BridgeApiTest.kt`

**Interfaces:**
- Consumes: `OkHttpClient`, `ApiLogger`
- Produces:
  - `BridgeApi.execute(path: String, method: String = "GET", body: RequestBody? = null, headers: Map<String, String> = emptyMap()): String`
  - `BridgeApi.request(path: String, body: RequestBody? = null): JSONObject`
  - `BridgeApi.requestArray(path: String, headers: Map<String, String> = emptyMap()): JSONArray`
  - `BridgeApi.delete(path: String, headers: Map<String, String> = emptyMap()): JSONObject`
  - Automatically adds header `ngrok-skip-browser-warning: true` and supports query parameters in path via `base.resolve("api/$path")`.

- [ ] **Step 1: Write the failing tests for BridgeApi extension**

Write tests in `app/src/test/java/com/uzuu/signbridge/data/BridgeApiTest.kt`:
- `requestArray` returns parsed JSONArray and sends custom headers + `ngrok-skip-browser-warning`.
- `delete` performs HTTP DELETE and parses JSONObject response.
- Query parameters in path (e.g. `chips?context=CAFE`) are correctly preserved in the resolved URL.
- HTTP errors (400, 404, 500) still map to appropriate `BridgeException`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.uzuu.signbridge.data.BridgeApiTest"`
Expected: FAIL

- [ ] **Step 3: Refactor BridgeApi with execute, requestArray, and delete**

In `app/src/main/java/com/uzuu/signbridge/data/remote/api/BridgeApi.kt`:
- Extract core HTTP call into `execute(path, method, body, headers): String`.
- Resolve relative URL with `base.resolve("api/$path") ?: base.newBuilder().addPathSegments("api/$path").build()`.
- Add `ngrok-skip-browser-warning` header by default.
- Implement `requestArray` and `delete`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.uzuu.signbridge.data.BridgeApiTest"`
Expected: PASS

- [ ] **Step 5: Verify existing TranslationClientTest still passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.uzuu.signbridge.data.TranslationClientTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/uzuu/signbridge/data/remote/api/BridgeApi.kt app/src/test/java/com/uzuu/signbridge/data/BridgeApiTest.kt
git commit -m "feat: extend BridgeApi with requestArray and delete"
```

---

### Task 4: Add syncChips Cache Method to ConversationDao and Expand Seed Chips

**Files:**
- Modify: `app/src/main/java/com/uzuu/signbridge/data/local/dao/ConversationDao.kt`
- Modify: `app/src/main/java/com/uzuu/signbridge/data/local/AppDatabase.kt`
- Test: `app/src/test/java/com/uzuu/signbridge/data/ConversationDaoTest.kt`

**Interfaces:**
- Produces:
  - `ConversationDao.syncChips(context: String, chips: List<QuickChipEntity>)`
  - Replaces cached chips for that context atomically in a single SQLite transaction, preserving chip order and usage counts.
  - `AppDatabase.seed`: Expands default preset chips from 5 to 15 chips per domain (`CAFE`, `PHARMACY`, `PUBLIC`, `CUSTOM`).

- [ ] **Step 1: Write the failing test for syncChips and expanded seed**

In `app/src/test/java/com/uzuu/signbridge/data/ConversationDaoTest.kt`:
- Seed a database with chips for CAFE and PHARMACY.
- Call `syncChips("CAFE", newChips)`.
- Assert CAFE chips match `newChips`, and PHARMACY chips remain intact.
- Verify AppDatabase creates 15 seed chips for each context.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.uzuu.signbridge.data.ConversationDaoTest"`
Expected: FAIL with unresolved method `syncChips`.

- [ ] **Step 3: Implement syncChips in ConversationDao and expand seed chips in AppDatabase**

- Add `syncChips(context: String, chips: List<QuickChipEntity>)` in `app/src/main/java/com/uzuu/signbridge/data/local/dao/ConversationDao.kt` using `beginTransaction()` and batch insert.
- In `app/src/main/java/com/uzuu/signbridge/data/local/AppDatabase.kt`, expand `seed` map to include 15 chips for each domain (`CAFE`, `PHARMACY`, `PUBLIC`, `CUSTOM`).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.uzuu.signbridge.data.ConversationDaoTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/uzuu/signbridge/data/local/dao/ConversationDao.kt app/src/main/java/com/uzuu/signbridge/data/local/AppDatabase.kt app/src/test/java/com/uzuu/signbridge/data/ConversationDaoTest.kt
git commit -m "feat: add syncChips cache method and expand seed chips to 15 per domain"
```

---

### Task 5: ConversationRepositoryImpl Remote API Integration with Offline Fallback

**Files:**
- Modify: `app/src/main/java/com/uzuu/signbridge/data/repository/ConversationRepositoryImpl.kt`
- Test: `app/src/test/java/com/uzuu/signbridge/data/ConversationRepositoryApiTest.kt`

**Interfaces:**
- Consumes: `ConversationDao`, `BridgeApi?`, `UserSessionManager?`, `CoroutineDispatcher`
- Produces:
  - `getChips(context)`: Calls `GET /api/chips`, syncs to local SQLite cache, returns chips; on error falls back to cache.
  - `addChip(context, label, category)`: Calls `POST /api/chips`, caches result, triggers revision.
  - `deletePersonalChip(id)`: Calls `DELETE /api/chips/{id}`, deletes from cache, triggers revision. Throws if `{success: false}`.
  - `updateChip(id, label)`:
    - If personal chip: calls `DELETE /api/chips/{id}` then `POST /api/chips`.
    - If preset chip: calls `POST /api/chips` to add personal customization.
    - Caches result, triggers revision.

- [ ] **Step 1: Write the failing unit tests for API integration in ConversationRepository**

Write tests in `app/src/test/java/com/uzuu/signbridge/data/ConversationRepositoryApiTest.kt` using `MockWebServer`:
- `getChips` queries server with context and `X-User-Id`, syncs cache, and returns chips.
- `getChips` falls back to SQLite cache when server is unreachable.
- `addChip` sends POST with text and context, updates cache and revision.
- `deletePersonalChip` sends DELETE, updates cache and revision. Throws if server returns `{success: false}`.
- `updateChip` on personal chip sends DELETE + POST.
- `updateChip` on preset chip creates personal chip without deleting preset on server.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.uzuu.signbridge.data.ConversationRepositoryApiTest"`
Expected: FAIL

- [ ] **Step 3: Implement API integration and cache fallback in ConversationRepositoryImpl**

Update `app/src/main/java/com/uzuu/signbridge/data/repository/ConversationRepositoryImpl.kt`:
- Accept optional `api: BridgeApi? = null` and `session: UserSessionManager? = null`.
- Implement remote-first logic for `getChips`, `addChip`, `deletePersonalChip`, and `updateChip`.
- Maintain full compatibility with existing `storage { }` and `revision` flow.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.uzuu.signbridge.data.ConversationRepositoryApiTest"`
Expected: PASS

- [ ] **Step 5: Verify existing storage and integration tests still pass**

Run: `./gradlew.bat testDebugUnitTest --tests "com.uzuu.signbridge.data.ConversationStorageTest"`
Run: `./gradlew.bat testDebugUnitTest --tests "com.uzuu.signbridge.integration.Tv1Tv2IntegrationTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/uzuu/signbridge/data/repository/ConversationRepositoryImpl.kt app/src/test/java/com/uzuu/signbridge/data/ConversationRepositoryApiTest.kt
git commit -m "feat: implement API chip operations with offline cache fallback"
```

---

### Task 6: Wire Dependencies in AppContainer and Update UI Copy

**Files:**
- Modify: `app/src/main/java/com/uzuu/signbridge/core/di/AppContainer.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Update AppContainer to instantiate UserSessionManager and pass BridgeApi to ConversationRepository**

In `app/src/main/java/com/uzuu/signbridge/core/di/AppContainer.kt`:
- Instantiate `userSession = UserSessionManager(appContext.getSharedPreferences("user_session", Context.MODE_PRIVATE))`
- Pass `bridgeApi` and `userSession` to `ConversationRepositoryImpl`.

- [ ] **Step 2: Update string resources for chip management**

In `app/src/main/res/values/strings.xml`:
- Update `chip_manage_local_hint` to reflect cloud sync: `"Thẻ được đồng bộ với máy chủ. Nhấn nút sửa để đổi nội dung."`

- [ ] **Step 3: Run full unit test suite**

Run: `./gradlew.bat testDebugUnitTest`
Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/uzuu/signbridge/core/di/AppContainer.kt app/src/main/res/values/strings.xml
git commit -m "feat: wire chip API in AppContainer and update UI copy"
```
