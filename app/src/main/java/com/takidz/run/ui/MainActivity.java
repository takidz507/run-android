package com.takidz.run.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.takidz.run.R;
import com.takidz.run.models.DirectoryData;
import com.takidz.run.models.RecentRoom;
import com.takidz.run.network.MqttRoomManager;
import com.takidz.run.utils.PrefsManager;
import com.takidz.run.utils.RoomCryptoUtils;
import com.takidz.run.utils.ThemeHelper;

import java.util.List;
import java.util.Locale;

/**
 * Redesigned Native Android Home Screen for `run`:
 * - Fixes the missing "Room Name (optional)" field from the web version.
 * - Integrates "Create Room" and "Join with Code" buttons directly inside the main card.
 * - Live MQTT global online counter (`X متصل الآن`).
 * - Recent Rooms chips (`Pick up where you left off` - up to 12 rooms).
 * - Public Groups Browser with full support for password-protected groups (SHA-256 verifier + key derivation).
 * - Instant Theme & Profile Settings (Default Dark, AMOLED Black, Light Mode, Auto-Translate).
 */
public class MainActivity extends AppCompatActivity implements MqttRoomManager.GlobalListener {

    private PrefsManager prefs;
    private MqttRoomManager mqttManager;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new PrefsManager(this);
        mqttManager = MqttRoomManager.getInstance(this);

        initViews();
        applyCurrentTheme();
        populateSavedUser();
        refreshRecentRoomsUI();

        mqttManager.ensureConnected();
        handleIncomingDeepLink(getIntent());
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
        input.setHint("أدخل كود الغرفة (6 أو 12 حرفاً) أو الصق الرابط");
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});

        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle("الانضمام إلى غرفة")
                .setMessage("أدخل كود الغرفة المكون من 6 أحرف (أو الصق رابط الدعوة):")
                .setView(container)
                .setPositiveButton("انضمام", (d, w) -> {
                    String raw = input.getText().toString().trim();
                    String extracted = extractCodeFromInputOrLink(raw);
                    if (!RoomCryptoUtils.isValidRoomCode(extracted)) {
                        Toast.makeText(this, "كود الغرفة غير صالح! تأكد من الكود (6 أو 12 حرفاً).", Toast.LENGTH_LONG).show();
                        return;
                    }
                    mqttManager.joinRoom(extracted, "Room " + extracted, null);
                    openRoomActivity(extracted, "Room " + extracted);
                })
                .setNegativeButton("إلغاء", null)
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
        List<DirectoryData.PublicGroupEntry> groups = (dir != null && dir.groups != null) ? dir.groups : null;

        if (groups == null || groups.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("المجموعات العامة النشطة")
                    .setMessage("لا توجد مجموعات عامة مدرجة حالياً. يمكنك إنشاء أول مجموعة عامة بتفعيل خيار 'إدراج كمجموعة عامة'!")
                    .setPositiveButton("حسناً", null)
                    .show();
            return;
        }

        String[] labels = new String[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            DirectoryData.PublicGroupEntry g = groups.get(i);
            String lockIcon = g.hasPassword ? "🔒 " : "🌐 ";
            labels[i] = lockIcon + (g.name != null ? g.name : "Public Room");
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("تصفح المجموعات العامة (" + groups.size() + ")")
                .setItems(labels, (dialog, which) -> {
                    DirectoryData.PublicGroupEntry selected = groups.get(which);
                    if (selected.hasPassword) {
                        promptPasswordForPublicGroup(selected);
                    } else if (selected.key != null && !selected.key.isEmpty()) {
                        mqttManager.joinRoom(selected.key, selected.name, null);
                        openRoomActivity(selected.key, selected.name);
                    }
                })
                .setNegativeButton("إغلاق", null)
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
                .setTitle("🔒 مجموعة محمية: " + group.name)
                .setMessage("أدخل كلمة المرور للتحقق واشتقاق مفتاح الغرفة المشفر:")
                .setView(container)
                .setPositiveButton("دخول", (d, w) -> {
                    String pw = pwInput.getText().toString().trim();
                    String computedVerifier = RoomCryptoUtils.computePasswordVerifier(group.seed, pw);
                    if (group.verifier != null && group.verifier.equalsIgnoreCase(computedVerifier)) {
                        String derivedTopicKey = RoomCryptoUtils.deriveProtectedGroupTopicKey(group.seed, pw);
                        mqttManager.joinRoom(derivedTopicKey, group.name, null);
                        openRoomActivity(derivedTopicKey, group.name);
                    } else {
                        Toast.makeText(this, "كلمة المرور غير صحيحة!", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showSettingsDialog() {
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad / 2, pad, pad / 2);

        // Bio input
        EditText etBio = new EditText(this);
        etBio.setHint("نبذة قصيرة (Bio)");
        etBio.setText(prefs.getUserBio());
        layout.addView(etBio);

        // Theme selector
        TextView tvThemeLabel = new TextView(this);
        tvThemeLabel.setText("السمة والألوان (Theme):");
        tvThemeLabel.setPadding(0, pad / 2, 0, 8);
        layout.addView(tvThemeLabel);

        String[] themeNames = {"الوضع الداكن الافتراضي (Dark Lime)", "وضع الشاشات السوداء (AMOLED Black)", "الوضع الفاتح (Light Blue)"};
        String[] themeValues = {"dark", "amoled", "light"};
        Spinner spTheme = new Spinner(this);
        spTheme.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, themeNames));
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

        String[] langNames = {"العربية (ar)", "English (en)", "Français (fr)", "Español (es)", "Türkçe (tr)"};
        String[] langCodes = {"ar", "en", "fr", "es", "tr"};
        Spinner spLang = new Spinner(this);
        spLang.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, langNames));
        String currentLang = prefs.getPreferredLanguage();
        for (int i = 0; i < langCodes.length; i++) {
            if (langCodes[i].equalsIgnoreCase(currentLang)) spLang.setSelection(i);
        }
        layout.addView(spLang);

        // Auto-translate switch
        SwitchMaterial swAutoTranslate = new SwitchMaterial(this);
        swAutoTranslate.setText("ترجمة الرسائل الواردة تلقائياً");
        swAutoTranslate.setChecked(prefs.isAutoTranslateEnabled());
        swAutoTranslate.setPadding(0, pad / 2, 0, 0);
        layout.addView(swAutoTranslate);

        // Notifications switch
        SwitchMaterial swNotifications = new SwitchMaterial(this);
        swNotifications.setText("تفعيل إشعارات الرسائل الجديدة");
        swNotifications.setChecked(prefs.isNotificationsEnabled());
        layout.addView(swNotifications);

        new MaterialAlertDialogBuilder(this)
                .setTitle("إعدادات الحساب والمظهر")
                .setView(layout)
                .setPositiveButton("حفظ وتطبيق", (d, w) -> {
                    prefs.setUserBio(etBio.getText().toString().trim());
                    String chosenTheme = themeValues[spTheme.getSelectedItemPosition()];
                    String chosenLang = langCodes[spLang.getSelectedItemPosition()];
                    prefs.setThemeMode(chosenTheme);
                    prefs.setPreferredLanguage(chosenLang);
                    prefs.setAutoTranslateEnabled(swAutoTranslate.isChecked());
                    prefs.setNotificationsEnabled(swNotifications.isChecked());

                    ThemeHelper.applyAppNightMode(chosenTheme);
                    applyCurrentTheme();
                    refreshRecentRoomsUI();
                })
                .setNegativeButton("إلغاء", null)
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
            chip.setText(r.name + "  #" + r.code);
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
            tvOnlineStatus.setText("● " + count + " متصل الآن (Synced)");
            tvOnlineStatus.setTextColor(palette.accent);
        } else {
            tvOnlineStatus.setText("◌ جاري الاتصال بالخادم...");
            tvOnlineStatus.setTextColor(palette.muted);
        }
    }

    @Override
    public void onGlobalPresenceChanged(int onlineCount) {
        if (mqttManager.isConnected()) {
            tvOnlineStatus.setText("● " + onlineCount + " متصل الآن (Synced)");
        }
    }

    @Override
    public void onDirectoryUpdated(DirectoryData directory) {
        int count = (directory != null && directory.groups != null) ? directory.groups.size() : 0;
        btnBrowsePublicGroups.setText("🌐 تصفح المجموعات العامة النشطة (" + count + ")");
    }
}
