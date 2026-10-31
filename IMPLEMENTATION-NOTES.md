# IMPLEMENTATION-NOTES — Run Android v3.3.0 «Run Agent» (`versionCode = 6`)

## 1. ملخص تنفيذي صادق (Honest Executive Summary)

تم بناء النسخة **v3.3.0** (`versionCode = 6`, `versionName = "3.3.0"`) انطلاقاً من سورس **v3.2.0** المرفق مباشرةً، مع الالتزام الكامل بأن تكون الإضافات **من جهة التطبيق فقط (App-Side Only)** دون إنشاء أي خادم أو جسر أو مواضيع MQTT جديدة أو مكتبات إضافية:

- **الحفاظ الصارم على الهوية والتوقيع والبروتوكول**:
  - ملف التوقيع **`debug.keystore`** في جذر المشروع لم يُمسّ بايتاً واحداً (`2666` بايت، بصمة SHA-256 مطابقة: `0c260daf231bdcfdc6acbf65d0f5fcf00820e0bc20f3b202ed258c0a6f6ef048`).
  - جميع مواضيع البروتوكول (`runapp/v1/...`) وطبقة `RoomCryptoUtils` ومفاتيح التخزين المحلي (`run_native_prefs_v1`) وهوية الجهاز بالرقم التسلسلي (`#24`) وجميع الثيمات السبع (بما فيها `Liquid Glass` و`Liquid Glass Dark`) والتصميم المعتمد (تصميم A) محفوظة 100%.

---

## 2. ما تم بناؤه في النسخة 3.3.0 (النقاط الثماني في البرومبت)

### 1) غرفة الوكيل المثبتة (`agent_room_code`)
- **التخزين**: أُضيف المفتاح `"agent_room_code"` داخل `PrefsManager` (`run_native_prefs_v1`) مع دوال `getAgentRoomCode()`, `setAgentRoomCode()`, `hasAgentRoomCode()`, و`isAgentRoom(roomCode)`، وأُدرج أيضاً في `TransferBundle` حتى ينتقل إعداد غرفة الوكيل تلقائياً عند نقل الحساب بين هاتفين أو ربطه بالويب.
- **البطاقة المثبتة في الرئيسية (`MainActivity` + `activity_main.xml`)**:
  - أُضيفت بطاقة `cardPinnedAgentRoom` أعلى قائمة الغرف مباشرة بنفس روح تصميم A.
  - **عند عدم ضبط كود**: تظهر البطاقة كدعوة ضبط (`«Run Agent — موس»` + `«اربط غرفة الوكيل — الصق كود الغرفة أو أنشئ غرفة جديدة»` + شارة `«إعداد»`)، والضغط عليها يفتح ورقة إعداد غرفة الوكيل (`showAgentRoomSetupSheet()`).
  - **عند ضبط كود**: تظهر البطاقة مثبتة دائماً في أعلى القائمة بأفاتار الوكيل (`🤖` أو صورة أفاتار الوكيل من `roomData.avatars` إن وُجدت)، والاسم `«Run Agent — موس»`، وحالة حضوره الحقيقية، والضغط عليها يدخل غرفة الوكيل مباشرة، بينما الضغط على زر الإعداد الصغير (`⚙`) أو الضغط المطوّل يفتح حوار تعديل الكود/نسخه/إنشاء غرفة جديدة/إلغاء الربط.
  - كما أُضيف خيار `«🤖 غرفة Run Agent (موس)»` في قائمة `⋮` بالرئيسية، وزر في ورقة المظهر والإعدادات، وخيار داخل قائمة `⋮` في أي غرفة لتثبيتها أو إلغاء تثبيتها كغرفة وكيل.

### 2) حضور صادق للوكيل (`agent-` prefix + نبضة الحضور الحقيقية)
- أُنشئت أداة `AgentHelper.java` التي تعرّف بادئة معرّف الوكيل `AGENT_ID_PREFIX = "agent-"` وقائمة المعرّفات المعروفة (`agent-mous`, `agent-moss`, `agent-run`, `agent-main`, `run-agent`, `mous-agent`).
- في `MqttRoomManager.java`:
  - تشترك خدمة الشبكة تلقائياً في مواضيع غرفة الوكيل المثبتة (`runapp/v1/room/<agent_room_code>/state` و`.../presence/+`) فور الاتصال حتى تظهر حالة حضور موس الحقيقية في الشاشة الرئيسية دون الحاجة لدخول الغرفة أولاً.
  - الدالة `isAgentOnlineInRoom(roomCode)` تتحقق بصدق من وجود عضو يحمل بادئة `agent-` (أو معرّف وكيل معروف) بنبضة حضور حديثة (`<= 60_000ms`، مع تطبيع الطوابع الزمنية سواء أُرسلت بالثواني أو بالملي ثانية).
  - إذا كانت النبضة حديثة تُعرض الحالة `«متصل»`؛ وإذا كانت غائبة أو قديمة تُعرض `«غير متاح حالياً»` بنص رمادي هادئ (`palette.muted`) دون أي نقطة خضراء تجميلية كاذبة، سواء في بطاقة الرئيسية أو في العنوان الفرعي داخل `RoomActivity` (`«موس: متصل · X رسالة»` أو `«موس: غير متاح حالياً · X رسالة»`).

### 3) مذكرة الغرفة المشتركة (سطح بارز وسريع في غرفة الوكيل)
- داخل `activity_room.xml` و`RoomActivity.java`، أُضيفت بطاقة علوية بارزة `cardAgentSharedNoteBar` تظهر تلقائياً داخل غرفة الوكيل تحت شريط التبويبات مباشرة، وتعرض معاينة حية لأول سطر من `RoomData.note` مع زر `«المذكرة المشتركة — فتح وتحرير»`.
- الضغط على البطاقة أو الزر يفتح ورقة `showAgentSharedNoteEditorSheet()` لقراءة وتحرير وحفظ حقل `RoomData.note` مباشرة عبر `mqttManager.updateRoomNote(updated)` (الذي يعيد نشر حالة الغرفة Retained على `runapp/v1/room/<code>/state` كالمعتاد) بضغطة واحدة دون مغادرة المحادثة.

### 4) الرسائل الصوتية من الوكيل (`type=voice` + `caption`)
- في `Message.java`: أُضيفت حقول احتياطية (`audioUrl`, `mediaUrl`, `url`, `dataUrl`, `duration`, `caption`, `mime`, `fileName`) ودوال `getEffectiveMediaUrl()` و`getEffectiveVoiceCaption()` حتى تعمل الرسائل الصوتية سواء أرسلها الجسر داخل كائن `file` متداخل أو كحقول مباشرة في جذر JSON الرسالة.
- في `VoiceRecorderHelper.java`: تم تحديث `playVoiceMessage` لاكتشاف صيغ الصوت الشائعة التي قد يولّدها الجسر (`audio/ogg`, `audio/mpeg` / `.mp3`, `audio/wav`, `audio/webm`, `audio/mp4` / `.m4a`) عند فك `data:` URI إلى ملف مؤقت قبل تشغيله عبر `MediaPlayer`.
- في `MessagesAdapter.java` و`item_message.xml`:
  - في نسخة 3.2.0 كان `tvMessageText` يُخفى دائماً في الرسائل الصوتية. في 3.3.0 تم إصلاح ذلك: المشغّل الصوتي الكامل (`voiceContainer`) يظهر أولاً، وتحته مباشرة يظهر `tvMessageText` حاملاً النص المرفق (`caption` أو نص الرسالة الفعلي غير التلقائي) دون كسر صوتيات الغرف العادية.

### 5) إيصالات صادقة داخل غرفة الوكيل فقط (`deliveredTo` / `readBy`)
- في `MqttRoomManager.java`: عند وصول إيصال على موضوع `/acks` (`status = "delivered"` أو `"read"`)، يتم إضافة `ack.fromUserId` إلى قائمتي `deliveredTo` و`readBy` للرسائل المطابقة (مع الحفاظ على القوائم القادمة من تحديثات `/state`).
- في `MessagesAdapter.java`:
  - داخل غرفة الوكيل فقط (`isAgentRoom == true`)، تفحص رسائل المستخدم الصادرة قائمتي `msg.readBy` و`msg.deliveredTo` بحثاً عن أي معرّف وكيل (`AgentHelper.containsAgentId(...)`):
    - عند وجود معرّف الوكيل في `readBy` (أو `status == "read"`): تعرض `«✓✓ قرأها موس»`.
    - عند وجود معرّف الوكيل في `deliveredTo` (أو `status == "delivered"`): تعرض `«✓ وصلت إلى الجسر»`.
    - قبل ذلك تعرض `«✓ أُرسلت»`.
  - في الغرف العادية (`isAgentRoom == false`) يبقى سلوك الإيصالات السابق (`✓` / `✓✓`) كما هو تماماً دون تغيير.

### 6) مؤشر كتابة الوكيل (`/typing`)
- في `MqttRoomManager.java`: يدعم `handleTypingPayload` استقبال إشارة الكتابة سواء أرسلها الجسر بالحقل `userId` أو `id` أو `fromUserId`، وبالحقل `name` أو `author`، أو بالحقل المنطقي `typing: true/false` أو `isTyping: true/false`. وعندما يكون الكاتب وكيلاً (`AgentHelper.isAgentUserId` أو `isAgentDisplayName`) يُطبّع اسمه إلى `«موس»`.
- في `RoomActivity.java` (`onTypingUsersChanged`): يعرض المؤشر `«موس يكتب…»` (أو `Mous is typing…` بالإنجليزية) داخل غرفة الوكيل فور وصول إشارة الكتابة.

### 7) أزرار أوامر سريعة في غرفة الوكيل فقط (Quick Command Chips)
- في `activity_room.xml` و`RoomActivity.java`: أُضيف شريط الرقائق الأفقي `scrollAgentQuickCommands` (`layoutAgentQuickChips`) فوق صندوق إدخال الرسالة مباشرة، يظهر **فقط** داخل غرفة الوكيل (`View.GONE` في جميع الغرف العادية)، ويتلوّن ديناميكياً بلوحة الثيم الحالي ولون هوية الغرفة في الثيمات السبع كلها وبالعربية RTL:
  - `«الحالة»` ← يدرج `/حالة`
  - `«كتم ساعة»` ← يدرج `/كتم 1h`
  - `«رد بصوتك»` ← يدرج `رد بصوتك: `
  - `«طلب إصلاح»` ← يدرج `طلب إصلاح: `
- الضغط على أي رقاقة يضع النص في `etMessageInput` ويضع المؤشر في نهاية النص ويفتح لوحة المفاتيح دون إرسال الرسالة مباشرة.

### 8) روابط وملفات APK داخل الغرفة
- في `MessagesAdapter.java` و`AgentHelper.java`:
  - أي مرفق ملف من نوع `.apk` (أو `application/vnd.android.package-archive`) أو أي رسالة نصية تحتوي على رابط مباشر لملف `.apk` (`extractFirstApkUrl`) تعرض بطاقة ملف واضحة بشارة `[APK]` مع تلميح `«اضغط لفتح أو تثبيت حزمة APK»`.
  - الضغط عليها يستدعي مسار التعامل مع الملفات الحالي (`Intent.ACTION_VIEW` مع `FLAG_ACTIVITY_NEW_TASK | FLAG_GRANT_READ_URI_PERMISSION` ومحاولة احتياطية عامة إذا لزم) لفتح/تشفير/تثبيت الرابط عبر النظام دون بناء مثبّت مخصص.

---

## 3. قائمة الملفات المعدّلة أو المضافة ملفاً بملف (File-by-File Changelog)

1. **`app/build.gradle.kts`**
   - رفع `versionCode` من `5` إلى `6`، و`versionName` من `"3.2.0"` إلى `"3.3.0"`.
2. **`app/src/main/java/com/takidz/run/utils/AgentHelper.java`** *(ملف جديد)*
   - ثوابت ودوال مساعدة لتمييز الوكيل (`AGENT_ID_PREFIX = "agent-"`, `KNOWN_AGENT_IDS`, `isAgentUserId`, `isAgentDisplayName`, `containsAgentId`)، وتعريف الأوامر السريعة الأربعة (`QUICK_COMMANDS`)، واكتشاف روابط/ملفات `.apk` (`extractFirstApkUrl`, `isApkResource`).
3. **`app/src/main/java/com/takidz/run/utils/PrefsManager.java`**
   - إضافة مفتاح `"agent_room_code"` ودوال `getAgentRoomCode()`, `setAgentRoomCode()`, `hasAgentRoomCode()`, `isAgentRoom(roomCode)`، ودعم تضمينه واستعادته في حزم النقل والنسخ الاحتياطي.
4. **`app/src/main/java/com/takidz/run/models/TransferBundle.java`**
   - إضافة الحقل `public String agentRoomCode;` لمزامنة غرفة الوكيل عند نقل البيانات بجهاز جديد.
5. **`app/src/main/java/com/takidz/run/models/Message.java`**
   - دعم حقول الوسائط/الصوت الاحتياطية في جذر الرسالة ودوال `getEffectiveMediaUrl()`, `getEffectiveVoiceDuration()`, `getEffectiveVoiceCaption()`, و`hasNonVoiceFileOrApk()`.
6. **`app/src/main/java/com/takidz/run/utils/VoiceRecorderHelper.java`**
   - دعم امتدادات `.ogg`, `.mp3`, `.wav`, `.webm`, `.m4a` عند تشغيل الرسائل الصوتية المشفرة بصيغة `data:` URI من الوكيل.
7. **`app/src/main/java/com/takidz/run/network/MqttRoomManager.java`**
   - اشتراك قراءة تلقائي في حالة وحضور غرفة الوكيل المثبتة (`refreshAgentRoomSubscription`)، ودالة `isAgentOnlineInRoom(roomCode)` و`getAgentAvatarUrl(roomCode)`، وتحديث `deliveredTo`/`readBy` عند وصول `/acks` أو دمج `/state`، وتطبيع مؤشر كتابة `«موس»`.
8. **`app/src/main/res/layout/activity_main.xml`**
   - إضافة بطاقة `cardPinnedAgentRoom` أعلى قائمة الغرف في الشاشة الرئيسية.
9. **`app/src/main/res/layout/dialog_theme_live_preview.xml`**
   - إضافة زر إعداد غرفة Run Agent داخل ورقة المظهر والإعدادات.
10. **`app/src/main/res/layout/activity_room.xml`**
    - إضافة شريط `cardAgentSharedNoteBar` في أعلى الغرفة، وشريط رقائق الأوامر السريعة `scrollAgentQuickCommands` (`layoutAgentQuickChips`) فوق صندوق الإدخال.
11. **`app/src/main/res/layout/item_message.xml`**
    - ترتيب `voiceContainer` فوق `tvMessageText` ليظهر النص المرفق (`caption`) تحت مشغّل الصوت مباشرة.
12. **`app/src/main/java/com/takidz/run/ui/adapters/MessagesAdapter.java`**
    - عرض النص المرفق (`caption`) تحت المشغّل الصوتي، وعرض الإيصالات الصادقة (`«✓ وصلت إلى الجسر»` / `«✓✓ قرأها موس»`) داخل غرفة الوكيل فقط، ودعم بطاقات وروابط APK عبر `Intent.ACTION_VIEW`.
13. **`app/src/main/java/com/takidz/run/ui/MainActivity.java`**
    - ربط بطاقة غرفة الوكيل المثبتة وحالتها الحقيقية وحوار اللصق/الإنشاء/الإدارة (`showAgentRoomSetupSheet()`) وخيار قائمة `⋮`.
14. **`app/src/main/java/com/takidz/run/ui/RoomActivity.java`**
    - ربط حضور الوكيل الصادق في ترويسة الغرفة، وشريط ومحرر المذكرة المشتركة السريع، ورقائق الأوامر السريعة الأربعة، ومؤشر `«موس يكتب…»`، وتشغيل الصوتيات.

---

## 4. كيف تم التحقق منطقياً وبنيوياً (Verification & Limitations)

- **الفحص البنيوي والمرجعي (Static Verification)**:
  - تم التحقق آلياً من سلامة صياغة جميع ملفات XML في `app/src/main/res/` وعدم وجود أي مرجع `R.id.*` أو `R.drawable.*` أو `R.layout.*` مفقود في جميع ملفات Java الـ36.
  - تم التحقق من تطابق الأقواس والواردات (`imports`) في جميع ملفات الكود، ومن بقاء `debug.keystore` بايتاً ببايت (`2666` بايت).
- **ملاحظة صادقة حول الاختبار**:
  - التزاماً بتعليمات البرومبت («لا تبنِ APK ولا تدّعِ اختباراً على جهاز»)، لم يتم بناء ملف APK داخل هذه البيئة؛ تمت مراجعة ومحاكاة مسارات الكود منطقياً (تدفق رسائل MQTT، انتهاء صلاحية نبضة الحضور بعد 60 ثانية، تحديثات `deliveredTo`/`readBy`، وإدراج الرقائق النصية) ليكون السورس جاهزاً للبناء والتدقيق عند موس عبر GitHub Actions.
