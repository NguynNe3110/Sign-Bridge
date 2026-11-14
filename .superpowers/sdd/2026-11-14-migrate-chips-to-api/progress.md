# SDD ledger — plan: docs/superpowers/plans/2026-11-14-migrate-chips-to-api.md
Pre-flight: no conflicts detected between task interfaces.
Ruling: Expand seed chips from 5 to 15 per context in AppDatabase as requested by user.
Task 1: complete (DTOs & ChipMapper implemented, tests: ChipMapperTest -> 3 passed)
Task 2: complete (UserSessionManager implemented, tests: UserSessionManagerTest -> passed)
Task 3: complete (BridgeApi extended with execute, requestArray, delete; tests: BridgeApiTest, TranslationClientTest -> passed)
Task 4: complete (ConversationDao.syncChips and expanded 15 seed chips implemented; tests: ConversationDaoTest -> passed)
Task 5: complete (ConversationRepositoryImpl remote API CRUD and local cache fallback implemented; tests: ConversationRepositoryApiTest, ConversationStorageTest, Tv1Tv2IntegrationTest -> passed)
Task 6: complete (AppContainer wired with BridgeApi and UserSessionManager; strings.xml updated; tests: full suite 89/89 passed)
Final review: fixed Critical (prevent SQLite cache wipe on empty server list) + Important issues (safe updateChip delete check, DAO direct chip lookup, synchronized UserSessionManager); full suite green (90 tests passed).
Task 7 (Mock Fallback): implemented MockTranslationFallback.kt, enabled enableMockFallback = true in AppContainer and TranslationRepositoryImpl, added 404 NOT_CONFIGURED handling for AI endpoints in BridgeApi, verified full test suite (91/91 passed).
