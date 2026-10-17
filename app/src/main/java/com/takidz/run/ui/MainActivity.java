package com.takidz.run.ui;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.takidz.run.R;
import com.takidz.run.models.DirectoryData;
import com.takidz.run.models.RecentRoom;
import com.takidz.run.network.CatboxUploader;
import com.takidz.run.network.MqttRoomManager;
import com.takidz.run.utils.PrefsManager;
import com.takidz.run.utils.RoomCryptoUtils;
import com.takidz.run.utils.ThemeHelper;
import java.util.List;
import java.util.Locale;
/**

* Redesigned Native Android Home Screen for `run`:
* - Includes Audit Fixes:
* - #1 & F1: Requests POST_NOTIFICATIONS permission on Android 13+ (API 33+).
* - #10: Displays honest online/offline status without fake counts.
* - #11: Updates MqttRoomManager foreground state.
* - #16 & F5: Profile Avatar picker & uploader in Settings dialog.
*/
public class MainActivity extends AppCompatActivity implements
MqttRoomManager.GlobalListener {
private static final int REQ_POST_NOTIFICATIONS = 301;
private PrefsManager prefs;
private MqttRoomManager mqttManager;
private CatboxUploader catboxUploader;
private ThemeHelper.Palette palette;
private ScrollView rootScroll;
private MaterialCardView mainCard;
private MaterialCardView recentRoomsCard;
private TextView tvAppTitle;
private TextView tvOnlineStatus;
private MaterialButton btnThemeSettings;
private EditText etUserName;
private EditText etRoomName;

private CheckBox cbPublicGroup;
private EditText etPublicPassword;
private MaterialButton btnCreateRoom;
private MaterialButton btnJoinByCode;
private MaterialButton btnBrowsePublicGroups;
private ChipGroup chipGroupRecentRooms;
private TextView tvRecentTitle;
private ActivityResultLauncher<String> avatarPickerLauncher;
@Override
protected void onCreate(Bundle savedInstanceState) {
super.onCreate(savedInstanceState);
setContentView(R.layout.activity_main);
prefs = new PrefsManager(this);
mqttManager = MqttRoomManager.getInstance(this);
catboxUploader = new CatboxUploader();
setupAvatarPicker();
initViews();
applyCurrentTheme();
populateSavedUser();
refreshRecentRoomsUI();
requestNotificationPermissionIfNeeded();
mqttManager.ensureConnected();

handleIncomingDeepLink(getIntent());
}
private void setupAvatarPicker() {
avatarPickerLauncher = registerForActivityResult(
new ActivityResultContracts.GetContent(),
uri -> {
if (uri != null) {
Toast.makeText(this, "جاري تحديث صورة البروفايل...", Toast.LENGTH_SHORT).show();
catboxUploader.processAndUploadUri(this, uri, new
CatboxUploader.UploadCallback() {
@Override
public void onSuccess(String fileUrlOrDataUrl, String fileName, long fileSize,
String mimeType) {
prefs.setUserAvatarUrl(fileUrlOrDataUrl);
mqttManager.syncLocalProfileToActiveRoom();

            Toast.makeText(MainActivity.this, "تم حفظ صورة البروفايل بنجاح!", Toast.LENGTH_SHORT).show();
Toast.LENGTH_SHORT).show();
}
@Override

public void onError(String errorMessage) {
Toast.makeText(MainActivity.this, errorMessage,
Toast.LENGTH_LONG).show();
}

});
}
}
);
}
private void requestNotificationPermissionIfNeeded() {
if (Build.VERSION.SDK_INT >= 33 && prefs.isNotificationsEnabled()) {
if (ContextCompat.checkSelfPermission(this,
Manifest.permission.POST_NOTIFICATIONS)
!= PackageManager.PERMISSION_GRANTED) {
ActivityCompat.requestPermissions(
this,
new String[]{Manifest.permission.POST_NOTIFICATIONS},
REQ_POST_NOTIFICATIONS
);
}
}
}
@Override
protected void onNewIntent(Intent intent) {
super.onNewIntent(intent);
setIntent(intent);

handleIncomingDeepLink(intent);
}
@Override
protected void onResume() {
super.onResume();
mqttManager.setAppForeground(true);
mqttManager.addGlobalListener(this);
refreshRecentRoomsUI();
}
@Override
protected void onPause() {
super.onPause();
mqttManager.removeGlobalListener(this);
}
private void initViews() {
rootScroll = findViewById(R.id.rootScroll);
mainCard = findViewById(R.id.mainCard);
recentRoomsCard = findViewById(R.id.recentRoomsCard);
tvAppTitle = findViewById(R.id.tvAppTitle);
tvOnlineStatus = findViewById(R.id.tvOnlineStatus);
btnThemeSettings = findViewById(R.id.btnThemeSettings);
etUserName = findViewById(R.id.etUserName);

etRoomName = findViewById(R.id.etRoomName);
cbPublicGroup = findViewById(R.id.cbPublicGroup);
etPublicPassword = findViewById(R.id.etPublicPassword);
btnCreateRoom = findViewById(R.id.btnCreateRoom);
btnJoinByCode = findViewById(R.id.btnJoinByCode);
btnBrowsePublicGroups = findViewById(R.id.btnBrowsePublicGroups);
chipGroupRecentRooms = findViewById(R.id.chipGroupRecentRooms);
tvRecentTitle = findViewById(R.id.tvRecentTitle);
cbPublicGroup.setOnCheckedChangeListener((buttonView, isChecked) -> {
etPublicPassword.setVisibility(isChecked ? View.VISIBLE : View.GONE);
});
btnCreateRoom.setOnClickListener(v -> onCreateRoomClicked());
btnJoinByCode.setOnClickListener(v -> showJoinRoomDialog());
btnBrowsePublicGroups.setOnClickListener(v -> showPublicGroupsDialog());
btnThemeSettings.setOnClickListener(v -> showSettingsDialog());
}
private void populateSavedUser() {
etUserName.setText(prefs.getUserName());
}
private void saveUserNameFromInput() {
String name = etUserName.getText().toString().trim();

if (name.isEmpty()) name = "Guest";
prefs.setUserName(name);
}
private void onCreateRoomClicked() {
saveUserNameFromInput();
String roomName = etRoomName.getText().toString().trim();
boolean isPublic = cbPublicGroup.isChecked();
String optionalPw = isPublic ? etPublicPassword.getText().toString().trim() : null;
String code = mqttManager.createNewRoom(roomName, isPublic, optionalPw);
openRoomActivity(code, roomName.isEmpty() ? ("Room " + code) : roomName);
}
private void showJoinRoomDialog() {
saveUserNameFromInput();
EditText input = new EditText(this);
input.setHint("\u0623\u062F\u062E\u0644 \u0643\u0648\u062F \u0627\u0644\u063A\u0631\u0641\u0629 (6 \u0623\u0648 12 \u062D\u0631\u0641\u064B\u0627) \u0623\u0648 \u0627\u0644\u0635\u0642 \u0627\u0644\u0631\u0627\u0628\u0637");
input.setSingleLine(true);
input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});
int pad = (int) (18 * getResources().getDisplayMetrics().density);
LinearLayout container = new LinearLayout(this);
container.setOrientation(LinearLayout.VERTICAL);
container.setPadding(pad, pad / 2, pad, 0);
container.addView(input);

                new MaterialAlertDialogBuilder(this)
                        .setTitle("\uD83D\uDD12 \u0645\u062C\u0645\u0648\u0639\u0629 \u0645\u062D\u0645\u064A\u0629: " + group.name)
                        .setMessage("\u0623\u062F\u062E\u0644 \u0643\u0644\u0645\u0629 \u0627\u0644\u0645\u0631\u0648\u0631 \u0644\u0644\u062A\u062D\u0642\u0642 \u0648\u0627\u0634\u062A\u0642\u0627\u0642 \u0645\u0641\u062A\u0627\u062D \u0627\u0644\u063A\u0631\u0641\u0629 \u0627\u0644\u0645\u0634\u0641\u0631:")
                        .setView(container)
                        .setPositiveButton("\u062F\u062E\u0648\u0644", (d, w) -> {
.setTitle("\u0627\u0644\u0627\u0646\u0636\u0645\u0627\u0645 \u0625\u0644\u0649 \u063A\u0631\u0641\u0629")
        .setMessage("\u0623\u062F\u062E\u0644 \u0643\u0648\u062F \u0627\u0644\u063A\u0631\u0641\u0629 \u0627\u0644\u0645\u0643\u0648\u0646 \u0645\u0646 6 \u0623\u062D\u0631\u0641 (\u0623\u0648 \u0627\u0644\u0635\u0642 \u0631\u0627\u0628\u0637 \u0627\u0644\u062F\u0639\u0648\u0629):")
.setView(container)
.setPositiveButton(""انضمام, (d, w) -> {
String raw = input.getText().toString().trim();
String extracted = extractCodeFromInputOrLink(raw);
if (!RoomCryptoUtils.isValidRoomCode(extracted)) {
Toast.makeText(this, "ً  حرفا12  أو6( )كود الغرفة غير صالح! تأكد من الكود.",
Toast.LENGTH_LONG).show();
return;
}
mqttManager.joinRoom(extracted, "Room " + extracted, null);
openRoomActivity(extracted, "Room " + extracted);
})
.setNegativeButton(""إلغاء, null)
.show();
}
private String extractCodeFromInputOrLink(String raw) {
if (raw == null) return "";
String trimmed = raw.trim();
if (trimmed.contains("room=")) {

try {
Uri uri = Uri.parse(trimmed);
String q = uri.getQueryParameter("room");
if (q != null) return q.trim().toUpperCase(Locale.US);
} catch (Exception ignored) {}
}
return trimmed.toUpperCase(Locale.US);
}
private void showPublicGroupsDialog() {
saveUserNameFromInput();
DirectoryData dir = mqttManager.getDirectoryData();
List<DirectoryData.PublicGroupEntry> groups = (dir != null && dir.groups != null) ?
dir.groups : null;
if (groups == null || groups.isEmpty()) {
new MaterialAlertDialogBuilder(this)
.setTitle(")"المجموعات العامة النشطة
.setMessage("لا توجد مجموعات عامة مدرجة حاليًا. يمكنك إنشاء أول مجموعة عامة بتفعيل خيار 'إدراج كمجموعة عامة'!")
.setPositiveButton("حسنًا", null)
.show();
return;
}

String[] labels = new String[groups.size()];
for (int i = 0; i < groups.size(); i++) {
DirectoryData.PublicGroupEntry g = groups.get(i);

String prefix = g.isLocked ? "\uD83D\uDD12 " : "\uD83C\uDF10 ";

String lockIcon = g.hasPassword ? "\uD83D\uDD12 " : "";

labels[i] = lockIcon + (g.name != null ? g.name : "Public Room");
}
new MaterialAlertDialogBuilder(this)
.setTitle(" "( تصفح المجموعات العامة+ groups.size() + ")")
.setItems(labels, (dialog, which) -> {
DirectoryData.PublicGroupEntry selected = groups.get(which);
if (selected.hasPassword) {
promptPasswordForPublicGroup(selected);
} else if (selected.key != null && !selected.key.isEmpty()) {
mqttManager.joinRoom(selected.key, selected.name, null);
openRoomActivity(selected.key, selected.name);
}
})
.setNegativeButton(""إغالق, null)
.show();
}
private void promptPasswordForPublicGroup(DirectoryData.PublicGroupEntry group) {
EditText pwInput = new EditText(this);

pwInput.setHint("كلمة مرور المجموعة");
pwInput.setSingleLine(true);
int pad = (int) (18 * getResources().getDisplayMetrics().density);
LinearLayout container = new LinearLayout(this);
container.setOrientation(LinearLayout.VERTICAL);
container.setPadding(pad, pad / 2, pad, 0);
container.addView(pwInput);
new MaterialAlertDialogBuilder(this)
        .setTitle("\uD83D\uDD12 \u0645\u062C\u0645\u0648\u0639\u0629 \u0645\u062D\u0645\u064A\u0629: " + group.name)

.setMessage("أدخل كلمة المرور للتحقق واشتقاق مفتاح الغرفة المشفر:")
.setView(container)
.setPositiveButton(""دخول, (d, w) -> {
String pw = pwInput.getText().toString().trim();
String computedVerifier = RoomCryptoUtils.computePasswordVerifier(group.seed,
pw);
if (group.verifier != null && group.verifier.equalsIgnoreCase(computedVerifier)) {
String derivedTopicKey =
RoomCryptoUtils.deriveProtectedGroupTopicKey(group.seed, pw);
mqttManager.joinRoom(derivedTopicKey, group.name, null);
openRoomActivity(derivedTopicKey, group.name);
} else {
Toast.makeText(this, ""!كلمة المرور غير صحيحة, Toast.LENGTH_LONG).show();

}
})
.setNegativeButton(""إلغاء, null)
.show();
}
private void showSettingsDialog() {
int pad = (int) (20 * getResources().getDisplayMetrics().density);
LinearLayout layout = new LinearLayout(this);
layout.setOrientation(LinearLayout.VERTICAL);
layout.setPadding(pad, pad / 2, pad, pad / 2);
// Fix #16 & F5: Profile Avatar Picker Button
MaterialButton btnPickAvatar = new MaterialButton(this);
boolean hasAvatar = !prefs.getUserAvatarUrl().isEmpty();

🖼  "🖼 اختي صو بروفا: ")تغيير صورة البروفايل (مفعّلة

btnPickAvatar.setText(hasAvatar ? "\uD83D\uDDBC \u062A\u063A\u064A\u064A\u0631 \u0635\u0648\u0631\u0629 \u0627\u0644\u0628\u0631\u0648\u0641\u0627\u064A\u0644" : "\uD83D\uDDBC \u0627\u062E\u062A\u064A\u0627\u0631 \u0635\u0648\u0631\u0629 \u0628\u0631\u0648\u0641\u0627\u064A\u0644");

btnPickAvatar.setOnClickListener(v -> avatarPickerLauncher.launch("image/*"));
layout.addView(btnPickAvatar);
// Bio input
EditText etBio = new EditText(this);
etBio.setHint("( نبذة قصيرةBio)");
etBio.setText(prefs.getUserBio());
layout.addView(etBio);

// Theme selector
TextView tvThemeLabel = new TextView(this);
tvThemeLabel.setText("( السمة واأللوانTheme):");
tvThemeLabel.setPadding(0, pad / 2, 0, 8);
layout.addView(tvThemeLabel);
String[] themeNames = {"Dark Lime", "AMOLED Black", "Light Blue"};
String[] themeValues = {"dark", "amoled", "light"};
Spinner spTheme = new Spinner(this);
spTheme.setAdapter(new ArrayAdapter<>(this,
android.R.layout.simple_spinner_dropdown_item, themeNames));
String currentTheme = prefs.getThemeMode();
for (int i = 0; i < themeValues.length; i++) {
if (themeValues[i].equalsIgnoreCase(currentTheme)) spTheme.setSelection(i);
}
layout.addView(spTheme);
// Language selector for Auto-Translate
TextView tvLangLabel = new TextView(this);
tvLangLabel.setText("لغة الترجمة المفضلة:");
tvLangLabel.setPadding(0, pad / 2, 0, 8);
layout.addView(tvLangLabel);
String[] langNames = {"\u0627\u0644\u0639\u0631\u0628\u064A\u0629 (ar)", "English (en)", "Fran\u00E7ais (fr)", "Espa\u00F1ol (es)", "T\u00FCrk\u00E7e (tr)"};

String[] langCodes = {"ar", "en", "fr", "es", "tr"};
Spinner spLang = new Spinner(this);
spLang.setAdapter(new ArrayAdapter<>(this,
android.R.layout.simple_spinner_dropdown_item, langNames));
String currentLang = prefs.getPreferredLanguage();
for (int i = 0; i < langCodes.length; i++) {
if (langCodes[i].equalsIgnoreCase(currentLang)) spLang.setSelection(i);
}
layout.addView(spLang);
// Auto-translate switch
SwitchMaterial swAutoTranslate = new SwitchMaterial(this);
swAutoTranslate.setText("\u062A\u0631\u062C\u0645\u0629 \u0627\u0644\u0631\u0633\u0627\u0626\u0644 \u0627\u0644\u0648\u0627\u0631\u062F\u0629 \u062A\u0644\u0642\u0627\u0626\u064A\u0627");
swAutoTranslate.setChecked(prefs.isAutoTranslateEnabled());
swAutoTranslate.setPadding(0, pad / 2, 0, 0);
layout.addView(swAutoTranslate);
// Notifications switch
SwitchMaterial swNotifications = new SwitchMaterial(this);
swNotifications.setText("تفعيل إشعارات الرسائل الجديدة");
swNotifications.setChecked(prefs.isNotificationsEnabled());
layout.addView(swNotifications);
new MaterialAlertDialogBuilder(this)
.setTitle(")"إعدادات الحساب والمظهر

.setView(layout)
.setPositiveButton(""حفظ وتطبيق, (d, w) -> {
prefs.setUserBio(etBio.getText().toString().trim());
String chosenTheme = themeValues[spTheme.getSelectedItemPosition()];
String chosenLang = langCodes[spLang.getSelectedItemPosition()];
prefs.setThemeMode(chosenTheme);
prefs.setPreferredLanguage(chosenLang);
prefs.setAutoTranslateEnabled(swAutoTranslate.isChecked());
prefs.setNotificationsEnabled(swNotifications.isChecked());
if (swNotifications.isChecked()) {
requestNotificationPermissionIfNeeded();
}
ThemeHelper.applyAppNightMode(chosenTheme);
applyCurrentTheme();
refreshRecentRoomsUI();
})
.setNegativeButton(""إلغاء, null)
.show();
}
private void refreshRecentRoomsUI() {
List<RecentRoom> recent = prefs.getRecentRooms();
chipGroupRecentRooms.removeAllViews();

if (recent.isEmpty()) {
recentRoomsCard.setVisibility(View.GONE);
return;
}
recentRoomsCard.setVisibility(View.VISIBLE);
for (RecentRoom r : recent) {
Chip chip = new Chip(this);
chip.setText(r.name + " #" + r.code);
chip.setChipBackgroundColor(ColorStateList.valueOf(palette.panelSoft));
chip.setTextColor(palette.text);
chip.setChipStrokeColor(ColorStateList.valueOf(palette.accent));
chip.setChipStrokeWidth(2f);
chip.setCloseIconVisible(true);
chip.setCloseIconTint(ColorStateList.valueOf(palette.muted));
chip.setOnClickListener(v -> {
saveUserNameFromInput();
mqttManager.joinRoom(r.code, r.name, null);
openRoomActivity(r.code, r.name);
});
chip.setOnCloseIconClickListener(v -> {
prefs.removeRecentRoom(r.code);

refreshRecentRoomsUI();
});
chipGroupRecentRooms.addView(chip);
}
}
private void handleIncomingDeepLink(Intent intent) {
if (intent == null || intent.getData() == null) return;
Uri data = intent.getData();
String roomParam = data.getQueryParameter("room");
if (roomParam != null && RoomCryptoUtils.isValidRoomCode(roomParam)) {
saveUserNameFromInput();
String clean = roomParam.trim().toUpperCase(Locale.US);
mqttManager.joinRoom(clean, "Room " + clean, null);
openRoomActivity(clean, "Room " + clean);
}
}
private void openRoomActivity(String roomCode, String roomName) {
Intent i = new Intent(this, RoomActivity.class);
i.putExtra("room_code", roomCode);
i.putExtra("room_name", roomName);
startActivity(i);
}

private void applyCurrentTheme() {
palette = ThemeHelper.getPalette(prefs.getThemeMode());
getWindow().setStatusBarColor(palette.bg);
getWindow().setNavigationBarColor(palette.bg);
rootScroll.setBackgroundColor(palette.bg);
mainCard.setCardBackgroundColor(palette.panel);
mainCard.setStrokeColor(palette.panelHover);
recentRoomsCard.setCardBackgroundColor(palette.panel);
recentRoomsCard.setStrokeColor(palette.panelHover);
tvAppTitle.setTextColor(palette.text);
tvOnlineStatus.setTextColor(palette.accent);
tvRecentTitle.setTextColor(palette.muted);
etUserName.setTextColor(palette.text);
etUserName.setHintTextColor(palette.subtle);
etUserName.setBackgroundTintList(ColorStateList.valueOf(palette.panelSoft));
etRoomName.setTextColor(palette.text);
etRoomName.setHintTextColor(palette.subtle);
etRoomName.setBackgroundTintList(ColorStateList.valueOf(palette.panelSoft));
etPublicPassword.setTextColor(palette.text);
etPublicPassword.setHintTextColor(palette.subtle);
cbPublicGroup.setTextColor(palette.text);

cbPublicGroup.setButtonTintList(ColorStateList.valueOf(palette.accent));
btnCreateRoom.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
btnCreateRoom.setTextColor(palette.accentInk);
btnJoinByCode.setStrokeColor(ColorStateList.valueOf(palette.accent));
btnJoinByCode.setTextColor(palette.accent);
btnBrowsePublicGroups.setTextColor(palette.purple);
btnThemeSettings.setTextColor(palette.text);
btnThemeSettings.setStrokeColor(ColorStateList.valueOf(palette.panelHover));
}
@Override
public void onConnectionChanged(boolean connected, String brokerUrl) {
int count = mqttManager.getGlobalOnlineCount();
if (connected) {
tvOnlineStatus.setText("● " + count + " ( متصل اآلنSynced)");
tvOnlineStatus.setTextColor(palette.accent);
} else {
// Fix #10: Honest offline status
tvOnlineStatus.setText("\u25CC \u063A\u064A\u0631 \u0645\u062A\u0635\u0644");
tvOnlineStatus.setTextColor(palette.muted);
}
}
@Override

public void onGlobalPresenceChanged(int onlineCount) {
if (mqttManager.isConnected()) {
tvOnlineStatus.setText("● " + onlineCount + " ( متصل اآلنSynced)");
} else {
tvOnlineStatus.setText("\u25CC \u063A\u064A\u0631 \u0645\u062A\u0635\u0644");
}
}
@Override
public void onDirectoryUpdated(DirectoryData directory) {
int count = (directory != null && directory.groups != null) ? directory.groups.size() : 0;

        btnBrowsePublicGroups.setText("\uD83C\uDF10 \u062A\u0635\u0641\u062D \u0627\u0644\u0645\u062C\u0645\u0648\u0639\u0627\u062A \u0627\u0644\u0639\u0627\u0645\u0629 \u0627\u0644\u0646\u0634\u0637\u0629 (" + count + ")");
}
}
