# Design Doc: Migrate Tag (Chip) Management to Remote API

## 1. Context and Motivation
SignBridge currently manages quick conversation tags (chips) purely in the device's local SQLite database (`ConversationDao`). Now, the SignBridge AI Service backend provides a dedicated Chip API at `/api/chips` supporting multi-tenant keyword tag storage, custom chips per user, and default system presets.

This design transitions tag loading, creation, deletion, and editing to use the remote backend API while preserving local SQLite caching for offline resilience.

## 2. API Contract Analysis
Backend endpoints:
- `GET /api/chips?context={CONTEXT}&user_id={USER_ID}`:
  - Header: `X-User-Id: {USER_ID}`
  - Returns: `[{"id":"...", "text":"...", "context":"...", "is_system":bool, "user_id":"...", "usage_count":int}, ...]`
- `POST /api/chips`:
  - Header: `X-User-Id: {USER_ID}`
  - Body: `{"text": string, "context": string, "user_id": string}`
  - Returns: Created chip JSON
- `DELETE /api/chips/{chip_id}?user_id={USER_ID}`:
  - Header: `X-User-Id: {USER_ID}`
  - Returns: `{"success": true}` (or `{"success": false}` if chip is system or not found)
- `POST /api/chips/reset?user_id={USER_ID}`:
  - Header: `X-User-Id: {USER_ID}`
  - Returns: Reset confirmation

Key Constraints:
- Backend has no `PUT/PATCH` endpoint for chips.
- System chips (`is_system = true`) cannot be deleted on the backend.
- Backend isolates personal chips per `user_id`.

## 3. Architecture & Approaches

### Approaches Considered:
1. **Direct API Only:** Only call API; fail immediately if offline.
   - *Tradeoff:* Users cannot communicate using chips in spots without internet connection.
2. **Online-first with Local SQLite Cache (Selected):**
   - When online, fetch chips from API and update local SQLite cache (`QuickChip` table).
   - When creating/editing/deleting, call API and update local cache.
   - When network is unavailable or server is unconfigured, fall back seamlessly to local SQLite cache.
   - For editing:
     - If personal chip: delete old chip on API (`DELETE /api/chips/{id}`) and post new chip (`POST /api/chips`).
     - If preset chip: create new personal chip with updated label (`POST /api/chips`).

## 4. Component Structure
- `data/session/UserSessionManager.kt`: Generates and persists a stable device `userId` (UUID) in SharedPreferences.
- `data/remote/dto/request/CreateChipRequestDto.kt`: DTO for chip creation.
- `data/remote/dto/response/ChipResponseDto.kt`: DTO for chip responses.
- `data/mapper/ChipMapper.kt`: Translates between DTO, JSONObject, domain `QuickChip`, and entity `QuickChipEntity`.
- `data/remote/api/BridgeApi.kt`: Extended to support `requestArray`, `delete`, and headers (`X-User-Id`, `ngrok-skip-browser-warning`).
- `data/local/dao/ConversationDao.kt`: Added `syncChips(context: String, chips: List<QuickChipEntity>)` to atomically refresh cache.
- `data/repository/ConversationRepositoryImpl.kt`: Implements API-first CRUD with local cache fallback and reactivity through `revision` StateFlow.
- `core/di/AppContainer.kt`: Wires `UserSessionManager` and `BridgeApi` into `ConversationRepositoryImpl`.
