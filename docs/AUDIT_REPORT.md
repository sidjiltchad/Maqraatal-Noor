# تقرير تدقيق مقرأة النور — استعادة من جلسة DeepSeek

- المصدر: جلسة DeepSeek Harness المحفوظة، معرّف الجلسة `e4b5ba6a-6002-47b8-83bf-73de48a6dace`.
- المسار: `deepseek-default`، صلاحية `read-only`، رمز الخروج `0`، المدة المسجلة 436,089 ms (نحو 7 دقائق و16 ثانية).
- هذا تدقيق ساكن فقط؛ لم يُنفّذ بناء أو اختبارات في تلك المهمة. تقرير DeepSeek أدناه بانتظار مراجعة الأدلة قبل اعتماد كل نتيجة.
- أزيلت الأنماط المعروفة للاعتمادات قبل الحفظ إن وُجدت؛ عدد الاستبدالات: `0`.

---
# Phase 0 Audit — Maqraat Al Noor (`D:\YAMII\miqraatalnoor`)

**Scope/method.** Read-only inspection of `main` with a dirty worktree (19 modified tracked files, ~35 untracked entries, including 5 new Supabase migrations). I ran only read-only commands (`git status/diff`, file reads, `tar -tf` on the existing APK). I did not build, test, format, install, or migrate anything, and I did not read `docs/HANDOVER.md`, `supabase/.temp/`, `android/supabase.properties`, `.work/`, `.env*`, or credential files. No secrets are reproduced below.

## 1. Actual stack and architecture

- **Android:** single-module Kotlin app, Compose Material 3, Arabic-only/RTL. `MainActivity` is the only activity (`MainActivity.kt:64`). Toolchain: Gradle 8.10.2, AGP 8.7.3, Kotlin 2.0.21, Java 17, compile/target SDK 35, minSdk 29 (`android/build.gradle.kts`, `android/app/build.gradle.kts:15-24`).
- **Architecture:** MVVM with `StateFlow`, manual construction (no Hilt/Koin), singleton `SessionHolder` for the current session/profile (`data/remote/SessionHolder.kt:7-39`). Navigation is Compose Navigation with 10 route patterns (`ui/navigation/Routes.kt`).
- **Entry/auth gate:** `AuthState.Loading → SignedOut → SignedIn`; signed-in users are then gated by membership (`null` → join screen, non-`active` → pending/banned screen, otherwise `MainApp`) (`MainActivity.kt:86-122`).
- **Network/data:** custom `OkHttp` + `org.json` client for Supabase Auth/PostgREST/RPC (`data/remote/SupabaseApi.kt:16-152`); custom Phoenix/WebSocket Realtime client (`RealtimeClient.kt`); DataStore for settings. No Room/SQLite, no supabase-kt, no offline cache.
- **Backend:** Supabase Postgres with RLS and RPCs, plus one Deno edge function for LiveKit tokens (`supabase/functions/livekit-token/index.ts`). No web admin app; supervision is in-app.
- **Calls:** real LiveKit Android SDK 2.4.2 (`CallViewModel.kt`, `CallScreen.kt`), plus a legacy local simulation (`call/CallSession.kt`) still used only by demo code/tests.
- **Quran assets:** two bundled image mushafs, rendered from owner-supplied PDFs: `assets/mushaf_reading` (627 pages) and `assets/mushaf_tajweed` (689 pages), each with `index.json` (114 surahs, 60 hizb, 30 juz). Reader: `MushafScreen.kt` with page pager, index drawer, pinch/double-tap zoom.
- **Important reality:** the tree is a hybrid. Real Supabase/LiveKit code exists, but demo classes (`DemoRepository`, `HomeViewModel`, `ClassesViewModel`, `TeacherViewModel`, `PrivateSessionViewModel`, `LocalCallSession`) remain, and several screens are no longer routed (`ClassesScreen`, `PrivateSessionScreen`). Documentation is stale: `android/README.md`/`REPORT.md` still say “no server, no calls, one sample mushaf”, while the code has all three.

## 2. Supabase schema, migrations, functions

- **Schema:** 16 tables (`profiles`, `halqa`, `halqa_members`, `sessions`, `session_attendance`, `recitation_turns`, `evaluations`, `messages`, `message_receipts`, `invitations`, `mushaf_bookmarks`, `device_tokens`, plus `groups`, `member_groups`, `group_proposals`, `join_requests`), 9 enums, 31 functions, RLS enabled on every application table, 5 tables in `supabase_realtime`, and a private `voice-notes` bucket.
- **Migration inventory:** 3 committed migrations (schema/policies/RPC) + 5 untracked WIP migrations (`groups_and_membership`, `admin_rpc`, `group_levels_hierarchy`, `join_requests`, `pending_join_requests`). No `supabase/config.toml`; no seed; no CI.
- **Fresh-environment blockers:**
  - `20261124090000_groups_and_membership.sql:49-69` creates policies calling `public.is_supervisor()`, but that function is first defined in the *next* file, `20261124090100_admin_rpc.sql:6`. A fresh `db push` fails at policy creation.
  - Two migrations share version `20261124100000` (`group_levels_hierarchy.sql`, `join_requests.sql`) — ambiguous/duplicate version handling.
  - `20261124100000_join_requests.sql:103` inserts `halqa_id` into `invitations`, but `20261123160000_schema.sql:190-199` has no such column → `decide_join_request` fails at runtime.
  - `join_requests` has no `email`, `city`, or `level_pref` columns (`join_requests.sql:10-22`), while `AdminViewModel.kt:72` selects exactly those → admin panel load fails with 400.
  - `submit_join_request(p_name,p_phone,p_note)` (`join_requests.sql:35-39`) does not match the client call with `p_email,p_city,p_level` (`SessionViewModel.kt:104-114`) → student join form always fails.
  - `halqa_members` has no `graduated` column, but `HalqaRemote.kt:104` selects it and `Groups.kt:24` patches it → `members()` fails, which fails `SessionViewModel.load` (`SessionViewModel.kt:158`) and blocks sign-in; “grant ijaza” is broken.
- **Migration/documentation drift:** `supabase/README.md` describes 12 tables/19 functions and a three-file install; the tree has 16/31 and five extra migrations. The README’s local test instructions cover only the original three migrations.

## 3. Roles and features

Legend: **P** = implemented in code; **T** = test exists (historical results only); **Part** = partial; **A** = absent; **NV** = not verifiable without device/credentials/environment.

| Area | Status | Evidence / gap |
|---|---|---|
| Student sign-in, persisted session, refresh | **P, NV** | `SupabaseApi.signIn/refresh`, `SessionStore`, `SessionViewModel`; refresh only at startup, no 401 interceptor |
| Invite-only onboarding | **Part** | `redeem_invitation` + `JoinHalqaScreen` exist; no in-app sign-up; join-request flow is contract-broken (above); no email delivery |
| Student role | **Part** | Home/groups/chat/mushaf available; no route to live halqa or teacher control; attendance/evaluation self-read via RLS |
| Teacher role | **Part** | Staff RPCs/UI exist; teacher-control route unreachable; group proposals exist; role escalation risk (below) |
| Supervisor role | **Part** | `AdminScreen` + RPCs exist; panel load is broken by missing `join_requests` columns; ijaza broken |
| RBAC server-side | **Part** | RLS/RPC present but flawed (Section 5); UI gating only cosmetic |
| Live halqa session | **A/Part** | DB + `LiveSessionViewModel` + `LiveHalqaScreen` exist, but **no code navigates to `Routes.LIVE`/`Routes.TEACHER`** (only declarations in `Routes.kt:7,9`); sessions are created by direct POST with `status='live'`, `current_page=5`, never ended (`HalqaRemote.kt:141-154`); `start_session`/`end_session` unused |
| Attendance | **Part** | RPC/UI present; only reachable through unreachable teacher screen; unmarked students count as present (`TeacherRemoteViewModel.kt:29`) |
| Recitation queue/raise hand | **Part** | RPC/UI present; unreachable screen; student can update any turn column (Section 5); concurrent `max(position)+1` can violate unique constraint |
| Evaluations | **Part** | `save_evaluation` + UI present; unreachable screen; bounds enforced in DB |
| Group/private chat | **P, NV** | `ChatHubViewModel`/`ChatHubScreen`, 3-second polling (no Realtime); private thread is per-student, visible to all staff by RLS |
| Voice notes | **A** | `messages.kind='voice'` and bucket exist; no recording/upload/playback code; no ownership-scoped storage policy |
| Real LiveKit calls | **P, NV** | Token function + `CallViewModel`/`CallScreen`; permission requests present; no token/auth tests; single shared room (below) |
| Mushaf reader | **P** | Two image editions, index, zoom, pan; no bookmark wiring, no teacher-page sync |
| Synced mushaf / follow-teacher | **A** | `SettingsStore` stores `followTeacher`/bookmark but nothing reads them (`ViewModels.kt:203-216`; `saveBookmark` never called); no client call to `set_session_position` |
| Groups/levels | **Part** | `GroupsScreen`/`GroupsViewModel`; admin create/assign RPCs; depends on broken admin/member queries; no tests |
| Admin (requests, ban, proposals, ijaza) | **Part** | `AdminViewModel`/`AdminScreen`; missing columns break load; ijaza column absent; ban/accept RPCs present |
| Notifications/FCM | **A** | Only `device_tokens` table; no client registration, no sender |
| Settings/font import | **P** | SAF import, header check, local-only; no Thmanyah bundled (license-conscious) |
| Realtime | **Part** | `RealtimeClient` used only by live view model; no reconnection/backoff; token in URL; chat still polls |

## 4. Build, tests, release readiness

- **Build config:** `release { isMinifyEnabled = false }`, no `signingConfig`, no `bundle`/ABI splits/Play Asset Delivery (`app/build.gradle.kts:41-52`). Version `1.1.0` / code `2`. Repos include JitPack for LiveKit (`settings.gradle.kts:16`).
- **Existing artifacts:** debug APK at `android/app/build/outputs/apk/debug/app-debug.apk` is **462,768,413 bytes (~441 MiB)**, built after the current sources (11/25). No AAB, no release APK, no lint report.
- **Size:** bundled mushaf assets alone are **330.5 MB** (`mushaf_reading` 182.8 MB, `mushaf_tajweed` 147.7 MB). The existing debug APK is 462,768,413 bytes and carries 4 ABIs (arm64-v8a, armeabi-v7a, x86, x86_64), including `liblkjingle_peerconnection_so.so`. This is a major download/install cost, but the APK size does not determine compressed AAB download size. Google Play currently allows up to 500 MB for an AAB base module; downloads above 200 MB on mobile data show a non-blocking warning. No release AAB was built, so publishability by size remains unverified. [Google Play size limits](https://support.google.com/googleplay/android-developer/answer/9859372?hl=en), [Android App Bundle FAQ](https://developer.android.com/guide/app-bundle/faq?hl=en).
- **Tests:** six JVM files with **36 `@Test` methods** currently; stored results from 2026-11-23 report **41 tests, 0 failures** (MushafLogicTest had 10 then, now 5). Docs claim 37. The stored results predate the current code, so they are historical evidence only. No instrumentation tests (`src/androidTest` absent), no Compose UI tests, no LiveKit/edge-function tests, no tests for the five new migrations.
- **SDK/permissions:** merged manifest has INTERNET, ACCESS_NETWORK_STATE, RECORD_AUDIO, CAMERA, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PROJECTION, BLUETOOTH, MODIFY_AUDIO_SETTINGS. Missing `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_MICROPHONE`/`_CAMERA` (Android 14+ background-call risk), `BLUETOOTH_CONNECT`. `allowBackup="true"` with no extraction rules (`AndroidManifest.xml:17`).
- **Performance risks:** full-page WebP decode with no `inSampleSize` (`MushafScreen.kt:495-499`), ~10 MB bitmap per page; pager keeps neighbors; no baseline profile; chat polls every 3 s, live every 20 s; `material-icons-extended` adds bulk.
- **Licensing:** there is no repo LICENSE, attribution, or provenance file. The tajweed edition’s own final page (`assets/mushaf_tajweed/p0689.webp`) is a publisher colophon for **دار الوسيلة**, 2001, with an explicit all-rights-reserved/no-copying notice. Shipping all 689 scans is a verified legal risk; the reading mushaf is described only as owner-supplied “مصحف قطر”. No script reproduces either asset set.

## 5. Security and privacy

**Verified from code/DDL:**

1. **Teacher → supervisor privilege escalation.** Any teacher can call `create_invitation(p_role='supervisor')` (`rpc.sql:49-58` checks only `is_staff()`), then redeem her own code; `redeem_invitation` writes `profiles.role = invitation.role` (`pending_join_requests.sql:44-53`). Also `profiles_update_staff` and `members_write_staff` allow staff to change any profile/member role (`policies.sql:33-37,61-65`). No policy restricts the `role` column.
2. **`is_staff()` ignores membership.** It checks only `profiles.role` (`schema.sql:243-247`), while `is_supervisor()` requires an active membership (`admin_rpc.sql:6-13`). A banned/left teacher keeps staff read/write access via RLS.
3. **Phone exposure.** `profiles_select` lets any active member read every column of every profile, including `phone` (`policies.sql:22-25`, `schema.sql:46-53`). This contradicts the privacy statement that students see names only.
4. **Queue tampering.** `turns_raise_own` lets a student update *any* column of her own `recitation_turns` row (`policies.sql:110-114`), not only `hand_raised` — she can change `position`/`state` outside the RPC.
5. **Message RPC bypasses membership.** `send_message` is `SECURITY DEFINER` and only checks `auth.uid()` and private-thread ownership (`rpc.sql:314-330`); an authenticated non-member/pending account can post to the halqa thread. `body`/`note`/group names are unbounded.
6. **Private voice notes are member-wide.** `voice_notes_read` allows any member to read the whole `voice-notes` bucket (`policies.sql:220-223`); no per-message/owner path policy. Upload is not implemented yet, so this is a latent design gap.
7. **Read receipts are unscoped.** `mark_thread_read` inserts receipts for arbitrary message IDs without verifying the caller can see them (`rpc.sql:337-351`).
8. **Token storage.** Access and refresh tokens are stored in plain `SharedPreferences` (`SessionStore.kt:9-18`) and included in Android Auto Backup because `allowBackup=true`.
9. **LiveKit room isolation.** All halqa calls share one room (`index.ts:96`); room names are the env prefix plus `-p-<student-id>`; any staff token gets `roomAdmin` in every room (`index.ts:120-128`), so any teacher can join another teacher’s private call; tokens are valid 2 h with no revocation. Student-to-student private joining is correctly blocked (`index.ts:108-110`), and membership is checked (`index.ts:69-77`).
10. **Unauthenticated write surface.** `submit_join_request` is granted to `anon` and has no rate limiting beyond a 24 h name+phone duplicate check (`join_requests.sql:55-68,120`). It currently fails from the app due to the signature mismatch, which incidentally limits abuse.
11. **Realtime token in URL.** `RealtimeClient.kt:40-41` puts `access_token` in the WebSocket query string.
12. **Release hygiene.** No upload signing, no R8, no lint gate; `supabase.properties` is gitignored and injected into `BuildConfig` (`app/build.gradle.kts:29-38`) — anon key is public by design, but a service-role key placed there would ship in the APK. Only path/type noted.

**Runtime risks (not provable here):** Realtime `postgres_changes` RLS enforcement for private `messages` (needs a two-account test); expired access tokens (1 h) with no refresh interceptor; LiveKit background/audio focus on Android 14+; 441 MiB install/low-RAM bitmap pressure; 32-bit invite codes (`gen_random_uuid`/`md5` truncated to 8 hex chars) with no redeem throttling; migration behavior under the CLI; and actual Supabase schema drift.

**Input validation:** client-side login checks only non-empty; join form has no format/min-length checks; server validates full name (3–80), evaluation 1–5, attendance/status enums, page/surah/ayah ranges. Message bodies, notes, and group names have no length cap.

## 6. Maintainability and highest-impact defects

1. **Three competing schema truths** (repo migrations, live project, Kotlin expectations) — the `graduated`/join-request/invitation mismatches are the top defect cluster.
2. **Core screens unreachable** — no `navigate(Routes.live/teacher)` call exists anywhere; live halqa, attendance, queue and teacher control are dead-ends.
3. **Fresh provisioning impossible** — policy-before-function ordering and duplicate migration version.
4. **Authorization model internally inconsistent** — `is_staff` vs `is_supervisor`, role mutation paths.
5. **Release/asset strategy absent** — 330 MB images, copyrighted scans, no signing/PAD/compression.
6. **Stale docs and dead code** — root `README.md`, `android/REPORT.md`, `supabase/README.md`, demo repositories, unused bookmark/follow settings; three different test counts (37/36/41).
7. **Duplicated PostgREST query logic** across `AdminViewModel`, `GroupsViewModel`, `HomeRemoteViewModel`; raw server error strings surfaced to users.
8. **Best-preserved path:** keep single-activity MVVM, OkHttp/PostgREST/RPC, RLS as the enforcement boundary, and the existing image-reader design. Add missing migrations/policies and wire routes; do not rewrite or replace the dirty worktree.

## 7. Testing capability and exact safe commands

Currently possible without a device: 36 pure-JVM tests; historical results show they passed on an older tree. SQL tests require a disposable PostgreSQL (`supabase/tests/00`, `10`, `20`, `30`), and they cover only the original three migrations. LiveKit, permissions, camera/mic, install size, and RLS under real JWTs require a device/project.

Commands below are the exact ones I would use, but **I did not run them** because they write build/report files:

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat :app:lintDebug --console=plain
.\gradlew.bat :app:bundleRelease --console=plain
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath
```

```powershell
# read-only inspection of the existing APK
& "$env:ANDROID_HOME\build-tools\35.0.0\aapt2.exe" dump badging android\app\build\outputs\apk\debug\app-debug.apk
```

```sql
-- only against a disposable local database, never the live project
psql -p 55432 -U postgres -d maqraa_test -f supabase/tests/00_local_harness.sql
psql -p 55432 -U postgres -d maqraa_test -f supabase/migrations/20261123160000_schema.sql
-- ... policies, rpc, then new migrations in unique-version order
psql -p 55432 -U postgres -d maqraa_test -f supabase/tests/10_rls_tests.sql
```

Read-only Supabase checks (if CLI is authenticated): `supabase migration list --linked`, `supabase db lint --linked`; do not run `db push` during an audit.

## 8. Unknowns requiring environment/device/credential access

- Whether the live Supabase project already has `graduated`, `join_requests.email/city/level_pref`, and `invitations.halqa_id` added manually (schema drift). This determines whether the app currently signs in at all.
- Whether the five WIP migrations were ever applied, and in what order.
- Deployment state and secrets of `livekit-token`, actual LiveKit room names, audio/video quality, Bluetooth/background behavior, and token-expiry handling.
- Real device results for the 441 MiB APK (install, cold start, low-RAM paging, Android 14/15 microphone foreground rules).
- Play/signing assets and account status; copyright permission for both mushaf editions.
- Whether Supabase Auth sign-up is disabled, email confirmation required, and whether any teacher/student accounts exist.

## 9. Phased implementation outline with acceptance criteria

**Phase A — Make schema and code agree (P0).**
Add one reconciliation migration: `halqa_members.graduated boolean`, `join_requests.email/city/level_pref`, `invitations.halqa_id`, unique migration versions, and move/create `is_supervisor()` before dependent policies. Align `submit_join_request` with the client (or change the client to the SQL signature). Add SQL tests for every new RPC.
*Accept:* empty DB → all migrations apply in one run; `members()` returns 200; sign-in reaches `MainApp`; admin panel loads; a join request can be submitted and accepted; `decide_join_request` returns a code.

**Phase B — Restore reachability and close RBAC holes (P0/P1).**
Add explicit home entry points for `Routes.live(halwa.id)` and `Routes.teacher(halwa.id)` gated by `isStaff`; make `is_staff()` require active membership; restrict role changes and `create_invitation(role)` to supervisor; replace `turns_raise_own` with a column-scoped `hand_raised`-only path; add `is_member()` to `send_message`; scope `voice-notes` and receipts.
*Accept:* student cannot change `profiles.role` or another turn column; teacher cannot create/redeem a supervisor invite; non-member `send_message` returns 403; staff can open live/teacher screens; existing teacher/student RLS tests still pass.

**Phase C — Release and asset strategy (P1).**
Add signing/`bundleRelease`, R8 with keep rules, ABI splits or Play Asset Delivery, WebP recompression, and a licensing decision (permission or replace the tajweed scans). Add backup exclusion/encrypted token storage.
*Accept:* signed AAB builds reproducibly; measured compressed base-module download is ≤500 MB; target ≤200 MB to avoid the mobile-data warning, or move optional Quran assets to Play Asset Delivery; `allowBackup` no longer exports session tokens; a clean install passes a device smoke matrix. [Google Play size limits](https://support.google.com/googleplay/android-developer/answer/9859372?hl=en).

**Phase D — Media/privacy hardening (P1/P2).**
Use per-session/per-group LiveKit rooms; add room binding to session and teacher identity; shorten/rotate tokens; implement or remove voice-note UI; add a two-account Realtime test; add refresh-on-401.
*Accept:* two simultaneous halqas do not share a room; a teacher cannot enter another teacher’s 1:1; expired-token recovery works without sign-out; private chat/attendance isolation is proven by test.

**Phase E — Verification and documentation (P2).**
Add instrumentation tests for the auth gate and admin actions, current SQL tests for the new schema, and update `android/REPORT.md`/`supabase/README.md` to the real table/function/test counts.
*Accept:* `testDebugUnitTest`, `lintDebug`, and the SQL suite run clean on the current tree; docs match code; no stale “no server/no calls” claims.

## 10. Prioritized findings

| Priority | Finding |
|---|---|
| **P0** | `members()` selects non-existent `graduated`; against a migration-built DB, sign-in fails (`HalqaRemote.kt:104`, `SessionViewModel.kt:158`). |
| **P0** | `Routes.LIVE`/`ROUTES.TEACHER` have no navigation caller; live halqa and teacher control are unreachable. |
| **P0** | Fresh migrations cannot apply: `is_supervisor()` used before definition; duplicate `20261124100000` version. |
| **P0** | Join-request flow is broken in three places (columns, RPC signature, `invitations.halqa_id`); admin panel cannot load. |
| **P0** | Teacher can self-promote to supervisor via invitation/RPC/policy paths; `is_staff` ignores membership. |
| **P1** | 441 MiB debug APK / 330 MB assets; high download cost and 4 ABIs; no release AAB, signing, PAD, or compression baseline. AAB base-module size is unverified; Play's current cap is 500 MB, with a mobile-data warning above 200 MB. |
| **P1** | Bundled tajweed scans are a copyrighted commercial print; no license/provenance. |
| **P1** | Any member can read all profiles including phone; voice-notes bucket is member-wide. |
| **P1** | Tokens stored plaintext and included in Auto Backup. |
| **P1** | LiveKit uses one shared room; staff are admin in every room; 2 h tokens, no revocation. |
| **P2** | Student can rewrite queue columns; non-member can post via `send_message`; receipts unscoped. |
| **P2** | No tests for new migrations, ViewModels, RPC contracts, LiveKit, or UI; stored test results are stale. |
| **P2** | Dead demo/dead-end code, stale docs, duplicate PostgREST queries, no CI/lint gate. |

**Bottom line:** the architecture is viable and should be preserved, but the current tree is not a functioning end-to-end product against its own migrations. The highest-value work is not new features; it is (1) one reconciling migration plus contract-aligned clients, (2) wiring the two orphaned routes, and (3) tightening three authorization paths — before any release or asset work.

## مراجعة المعالجة — 2026-11-25

أنشأ DeepSeek Flash الهجرة الأمامية `supabase/migrations/20261124130000_p0_authorization_privacy.sql` واختبار القبول `supabase/tests/50_p0_authorization_privacy.sql`، لكن انتهت مهلة التنفيذ بعد 900 ثانية؛ لم يصل تقرير نهائي (`stdout` فارغ). لم تُعد المهمة. روجع التغيير محليًا وأعيد تشغيل كامل الاختبارات على قاعدة جديدة.

تغطي الهجرة: منع منح دور المعلمة/المشرفة خارج المشرفة، اشتراط عضوية فعالة للصلاحيات، حجب هواتف العضوات عن غير المشرفة، تقييد تعديلات دور القراءة، اشتراط العضوية للرسائل والتحقق من رؤية الرسائل قبل إيصالات القراءة، وتقليل نطاق قراءة الملاحظات الصوتية إلى المالك والموظفات الفعالات. أضيف `NOTIFY pgrst, 'reload schema'` ليُرسل عند commit كي يتعرف PostgREST على العرضين الآمنين.

النتيجة: ملفات SQL الخمسة (RLS، التدفقات، الدخان، عقود Phase 1.1، واختبارات P0) نجحت بعد تطبيق 10 migrations على PostgreSQL محلي فارغ. لا تثبت هذه الاختبارات سلوك PostgREST أو الخدمة البعيدة، ولم تُطبق الهجرة على Supabase البعيد. معاينة الهاتف السابقة أظهرت غياب `halqa_member_directory` من الـschema cache عندما استعيدت جلسة النسخة القديمة؛ لذلك يتطلب استخدام الأدوار المسجلة ترحيلًا مصرحًا للخلفية. بقية مخاطر الأمان، ومنها عزل غرف LiveKit وتخزين رموز الجلسة، ما زالت مفتوحة.
