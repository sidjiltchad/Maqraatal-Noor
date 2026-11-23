# مخطط مقرأة النور — Supabase

خلفية التطبيق على PostgreSQL (Supabase): حلقة واحدة، عدة معلمات، طالبات كثيرات،
والدخول بدعوة فقط. كل عمليات الكتابة الحسّاسة تمر من دوال `RPC` تتحقق من الدور
داخل قاعدة البيانات، والعزل بين الطالبات مفروض بـ RLS لا بالكود.

## الملفات

```
supabase/
├── migrations/
│   ├── 20261123160000_schema.sql     الأنواع والجداول والفهارس والمحفزات
│   ├── 20261123160100_policies.sql   RLS + الصلاحيات + المزامنة اللحظية + التخزين
│   └── 20261123160200_rpc.sql        الدوال التنفيذية (دعوات، جلسات، دور، تقييم، رسائل)
└── tests/
    ├── 00_local_harness.sql          بيئة محلية تحاكي auth/storage (للاختبار فقط)
    └── 10_rls_tests.sql              اختبارات العزل والأدوار
```

## نموذج البيانات

| الجدول | الغرض |
|---|---|
| `profiles` | ملف كل مستخدم (يُنشأ تلقائيا مع `auth.users`): الاسم والدور |
| `halqa` | **صف واحد فقط** (عمود `singleton` فريد): اسم الحلقة وموعدها وسعتها |
| `halqa_members` | مَن في الحلقة: الدور، المستوى (`foundation/tajweed/hifz/ijaza`)، التقدم |
| `sessions` | جلسة الحلقة الحية: الحالة، موضع المصحف (صفحة/سورة/آية)، القارئة الآن |
| `session_attendance` | الحضور: حاضر/غائب/متأخر/معذور، ومن سجّله |
| `recitation_turns` | طابور التلاوة: الترتيب، الحالة، رفع اليد |
| `evaluations` | تقييم المعلمة للطالبة في جلسة (تجويد 1–5، حفظ 1–5، ملاحظة) |
| `messages` + `message_receipts` | رسائل الحلقة العامة، والخاصة بين معلمة وطالبة، وإيصالات القراءة |
| `invitations` | أكواد الدعوة: الدور، عدد الاستخدامات، الانتهاء |
| `mushaf_bookmarks` | علامة المصحف لكل مستخدمة (قراءة/تجويد) |
| `device_tokens` | رموز FCM للإشعارات لاحقا |

الأنواع (enums): `user_role`, `member_level`, `member_status`, `session_status`,
`attendance_status`, `turn_state`, `message_kind`, `thread_kind`.

## من يرى ماذا (RLS)

| البيانات | الطالبة | المعلمة/المشرفة |
|---|---|---|
| ملفات العضوات | نعم (الاسم فقط) | نعم |
| الحلقة والأعضاء | نعم | نعم + التعديل |
| الجلسات والطابور | قراءة | قراءة وتعديل |
| الحضور | **حضورها فقط** | الكل |
| التقييمات | **تقييماتها فقط** | الكل |
| الرسائل العامة | نعم | نعم |
| الرسائل الخاصة | **رسائلها فقط** | كل الرسائل |
| الدعوات | لا | إنشاء وقراءة |
| علامتها وإشعاراتها | نعم | لا |

غير العضوة (بلا دعوة مقبولة) لا ترى أي صف في أي جدول.

## الدوال (RPC)

| الدالة | لمن | ما تفعل |
|---|---|---|
| `bootstrap_supervisor()` | أول مستخدمة | تجعلها مشرفة عامة وتضمّها للحلقة (مرة واحدة) |
| `create_invitation(role, max_uses, days)` | الطاقم | تُنشئ كود دعوة |
| `redeem_invitation(code)` | المدعوة | تستهلك الكود وتضيفها للحلقة |
| `start_session(id)` / `end_session(id)` | الطاقم | بدء/إنهاء الجلسة (الإنهاء يُغلق الطابور) |
| `set_session_position(id, page, surah, ayah)` | الطاقم | نقل المصحف للجميع |
| `give_turn(session, student)` | الطاقم | تُنهي دور القارئة وتُسند الدور لطالبة |
| `raise_hand(session)` | الطالبة | ترفع يدها وتدخل الطابور |
| `mark_attendance(session, user, status)` | الطاقم | تسجيل حضور |
| `save_evaluation(session, student, tajweed, hifz, note)` | الطاقم | حفظ تقييم (upsert لمعلمة/جلسة) |
| `send_message(thread, body, student, kind, duration)` | العضوات | إرسال رسالة مع التحقق من الملكية |
| `mark_thread_read(ids[])` | العضوات | تعليم الرسائل كمقروءة |

## التركيب على Supabase

1. أنشئ مشروعا جديدا على [supabase.com](https://supabase.com).
2. من **SQL Editor** نفّذ الملفات بالترتيب: `schema` ثم `policies` ثم `rpc`.
   (أو بـ CLI: `supabase db push` بعد نسخ المجلد إلى مشروعك.)
3. أنشئ حسابك من **Authentication → Users → Add user**، ثم نفّذ مرة واحدة:

   ```sql
   select public.bootstrap_supervisor();
   ```

   بشرط أن يكون `request.jwt.claim.sub` لحسابك (نفّذها من تطبيق مسجّل دخوله، أو
   استخدم Service Role في سكربت إداري).
4. أنشئ دعوة أولى:

   ```sql
   select public.create_invitation('teacher', 3, 30);
   ```

5. انسخ `Project URL` و`anon key` إلى التطبيق (الخطوة التالية في الكود).

المزامنة اللحظية مفعّلة على: `sessions`, `recitation_turns`, `session_attendance`,
`messages`, `evaluations`، وخزان `voice-notes` للرسائل الصوتية.

## الاختبار محليا

الهجرات مختبرة على PostgreSQL حقيقي لا على الورق:

```powershell
# 1) قاعدة اختبار
initdb -D .\pgdata -U postgres -E UTF8
pg_ctl -D .\pgdata -o "-p 55432" start
createdb -p 55432 -U postgres maqraa_test

# 2) ترويسة تحاكي Supabase
psql -p 55432 -U postgres -d maqraa_test -f supabase/tests/00_local_harness.sql

# 3) الهجرات
psql -p 55432 -U postgres -d maqraa_test -f supabase/migrations/20261123160000_schema.sql
psql -p 55432 -U postgres -d maqraa_test -f supabase/migrations/20261123160100_policies.sql
psql -p 55432 -U postgres -d maqraa_test -f supabase/migrations/20261123160200_rpc.sql

# 4) الاختبارات: تطبع RLS TESTS PASSED
psql -p 55432 -U postgres -d maqraa_test -f supabase/tests/10_rls_tests.sql
```

الاختبارات تتحقق من: عزل الحضور والتقييمات والرسائل الخاصة بين الطالبات، منع
الطالبة من إعطاء الدور أو التقييم أو إنشاء دعوة أو رفع دورها، عمل المعلمة
والمشرفة، والدعوة، ومنع غير العضوة من رؤية أي شيء.

**النتيجة الفعلية (2026-11-23):** نُفِّذت الهجرات الثلاث والاختباران على
PostgreSQL 16.4 محليا وخرجت
`RLS TESTS PASSED` و`FLOW TESTS PASSED` بلا فشل، إضافة إلى اختبارات
المسارات: دعوة لمستخدمة جديدة، منع إعادة استخدام الكود، التهيئة مرة واحدة،
إيصالات القراءة، إغلاق الطابور عند إنهاء الجلسة، وقيود البيانات (تقييم خارج
1–5، حلقة ثانية، رسالة خاصة بلا طالبة).

ملاحظة عند التنفيذ على Supabase: المحفز على `auth.users` وخزان `voice-notes`
وسياسات `storage.objects` تُنفَّذ بحساب `postgres` في SQL Editor، وهي الطريقة
المعتادة هناك.

## الخطوة التالية (الكود)

1. إضافة عميل Supabase إلى الأندرويد (`supabase-kt`) وقراءة `URL` و`anon key`.
2. استبدال `DemoRepository` بمستودعات حقيقية تقرأ من الجداول وتشترك في Realtime.
3. شاشة دخول بدعوة: بريد/كلمة مرور + `redeem_invitation`.
4. الدردشة على `messages` مع Realtime، والرفع الصوتي على `voice-notes`.
5. المكالمات: LiveKit (SFU) لاحقا؛ الجلسة والطابور جاهزان الآن لربطها.
