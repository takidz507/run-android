package com.takidz.run.ui;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.takidz.run.R;
import com.takidz.run.models.DirectoryData;
import com.takidz.run.models.FeedbackBoardData;
import com.takidz.run.models.FeedbackPost;
import com.takidz.run.models.OnlineUser;
import com.takidz.run.models.RecentRoom;
import com.takidz.run.models.SavedMessage;
import com.takidz.run.models.SignalMessage;
import com.takidz.run.network.CatboxUploader;
import com.takidz.run.network.MqttRoomManager;
import com.takidz.run.ui.adapters.FeedbackPostsAdapter;
import com.takidz.run.ui.adapters.FriendsAdapter;
import com.takidz.run.ui.adapters.OnlineUsersAdapter;
import com.takidz.run.ui.adapters.SavedMessagesAdapter;
import com.takidz.run.ui.views.OrigamiSplashView;
import com.takidz.run.utils.DeviceCapabilityHelper;
import com.takidz.run.utils.FeedbackTouchHelper;
import com.takidz.run.utils.LocaleHelper;
import com.takidz.run.utils.MotionHelper;
import com.takidz.run.utils.PrefsManager;
import com.takidz.run.utils.RoomCryptoUtils;
import com.takidz.run.utils.ThemeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Run v3.2.0 MainActivity — Rebuilt strictly from Design A (7196.png):
 * - Light top bar ("Run." with lime dot + ⋮ More button + circular 'T' avatar orb).
 * - One-line greeting directly on the canvas ("Good evening, Taqi" — no card wrapper).
 * - Slim single-line online strip + slim join/create bar.
 * - "Your rooms" heading with overlapping lime highlighter marker + "Recent" sub-section.
 * - Clean room cards (no nested cards, no 3 action buttons crowding rows, unbreakable single-line room code).
 * - Strict owner rule: everything that eats screen space lives inside the ⋮ More sheet.
 * - Fixes v3.1 Bug #1 (Liquid Glass BottomSheet contrast) & Bug #2 (Settings sheet scroll clipping/artifacts).
 */
public class MainActivity extends AppCompatActivity implements MqttRoomManager.GlobalListener {

    private static final int REQ_POST_NOTIFICATIONS = 301;
    private static boolean splashShownInProcess = false;

    private PrefsManager prefs;
    private MqttRoomManager mqttManager;
    private CatboxUploader catboxUploader;
    private ThemeHelper.Palette palette;

    private int currentDockTab = 0; // 0: Home (Spaces), 1: Discover, 2: People, 3: My Saved
    private boolean showingArchivedRooms = false;
    private boolean discoverShowingFeedback = false;
    private boolean peopleShowingFriends = false;

    // Root & Splash
    private FrameLayout rootMainFrame;
    private OrigamiSplashView origamiSplashOverlay;

    // Light Top Header (Matches 7196.png)
    private TextView tvAppTitle;
    private LinearLayout pillConnectionQuality;
    private View viewConnQualityDot;
    private TextView tvConnectionQuality;
    private LinearLayout pillOnlineDirectory;
    private TextView tvOnlineStatus;
    private MaterialButton btnQuickLangToggle;
    private ImageButton btnAccountPairing;
    private ImageButton btnThemeSettings;
    private ImageButton btnMoreMenu;
    private ImageButton btnFeedbackBoard;
    private FrameLayout layoutAvatarClick;
    private ImageView ivMainAvatar;
    private TextView tvMainAvatarFallback;

    // Home Screen Views (Matches 7196.png)
    private ScrollView rootScroll;
    private MaterialCardView profileCard;
    private TextView tvGreetingName;
    private TextView tvUserBioDisplay;
    private MaterialButton btnEditProfile;
    private TextView tvOnlineStripTitle;
    private LinearLayout layoutOnlineOrbsRow;
    private MaterialCardView cardQuickCodeBar;
    private EditText etQuickCodeOrSearch;
    private MaterialButton btnQuickCodeEnter;

    // Expandable Create / Join Studio Card (opened by + FAB)
    private MaterialCardView mainCard;
    private MaterialButton btnSegmentCreate;
    private MaterialButton btnSegmentJoin;
    private LinearLayout formCreateRoom;
    private LinearLayout formJoinCode;
    private EditText etUserName;
    private EditText etRoomName;
    private CheckBox cbPublicGroup;
    private EditText etPublicPassword;
    private MaterialButton btnCreateRoom;
    private EditText etJoinRoomCode;
    private MaterialButton btnJoinByCodeSubmit;

    // Your Rooms Section
    private TextView tvRecentTitle;
    private View viewYourRoomsHighlighter;
    private MaterialButton btnFilterActiveRooms;
    private MaterialButton btnFilterArchivedRooms;
    private TextView tvRecentCount;
    private MaterialCardView recentRoomsCard;
    private LinearLayout layoutRecentRoomsContainer;
    private MaterialCardView cardEmptyRoomsPrompt;
    private LinearLayout layoutRecentEmpty;
    private TextView tvEmptyRoomsText;

    // Hidden Legacy Quick Tiles (triggered from ⋮ More menu)
    private MaterialCardView cardPublicGroups;
    private TextView tvTilePublicTitle;
    private TextView tvPublicCount;
    private MaterialCardView cardFeedbackQuick;
    private TextView tvTileFeedbackTitle;
    private TextView tvTileFeedbackSub;

    // Secondary Workspace 1: Discover (Public Groups & Feedback Board)
    private LinearLayout workspaceDiscover;
    private MaterialButton btnDiscoverTabPublic;
    private MaterialButton btnDiscoverTabFeedback;
    private LinearLayout paneDiscoverPublic;
    private EditText etSearchPublicGroups;
    private LinearLayout layoutPublicGroupsContainer;
    private LinearLayout paneDiscoverFeedback;
    private TextView tvFeedbackHeaderInfo;
    private MaterialButton btnNewFeedbackInline;
    private RecyclerView rvDiscoverFeedback;
    private FeedbackPostsAdapter inlineFeedbackAdapter;

    // Secondary Workspace 2: People (Online Users & Friends)
    private LinearLayout workspacePeople;
    private MaterialButton btnPeopleTabOnline;
    private MaterialButton btnPeopleTabFriends;
    private EditText etSearchPeopleInline;
    private RecyclerView rvPeopleInline;
    private OnlineUsersAdapter inlineOnlineAdapter;
    private FriendsAdapter inlineFriendsAdapter;

    // Secondary Workspace 3: My Saved (#31)
    private LinearLayout workspaceSaved;
    private TextView tvSavedSectionTitle;
    private View viewSavedHighlighter;
    private TextView tvSavedSectionSub;
    private MaterialButton btnPairDeviceFromSaved;
    private RecyclerView rvSavedMessages;
    private LinearLayout layoutSavedEmpty;
    private TextView tvSavedEmptyText;
    private SavedMessagesAdapter savedMessagesAdapter;

    // Floating '+' FAB & Bottom Dock
    private MaterialButton fabNewSpace;
    private LinearLayout bottomNavDock;
    private MaterialButton btnDockSpaces;
    private MaterialButton btnDockDiscover;
    private MaterialButton btnDockPeople;
    private MaterialButton btnDockSaved;

    private ActivityResultLauncher<String> avatarPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = new PrefsManager(this);
        LocaleHelper.applyLocaleAndLayoutDirection(this, prefs);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mqttManager = MqttRoomManager.getInstance(this);
        catboxUploader = new CatboxUploader();
        palette = ThemeHelper.getPalette(prefs.getThemeMode());

        setupAvatarPicker();
        initViews();
        setupInlineWorkspaces();
        applyCurrentTheme();
        applyLocalizedLabels();
        populateSavedUser();
        refreshRecentRoomsUI();
        refreshOnlineOrbsRow(mqttManager.getOnlineUsersList());

        requestNotificationPermissionIfNeeded();
        mqttManager.ensureConnected();

        boolean openedDeepLink = handleIncomingDeepLink(getIntent());
        if (!openedDeepLink && !splashShownInProcess) {
            splashShownInProcess = true;
            origamiSplashOverlay.setVisibility(View.VISIBLE);
            origamiSplashOverlay.startSplash(prefs.isLiteModeEnabled(), () -> {
                origamiSplashOverlay.animate()
                        .alpha(0f)
                        .setDuration(prefs.isLiteModeEnabled() ? 80L : 200L)
                        .withEndAction(() -> origamiSplashOverlay.setVisibility(View.GONE))
                        .start();
            });
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !prefs.isDeferredInviteChecked()) {
            prefs.setDeferredInviteChecked(true);
            checkClipboardForDeferredInvite();
        }
    }

    private void checkClipboardForDeferredInvite() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence clipText = cm.getPrimaryClip().getItemAt(0).getText();
                if (clipText != null) {
                    String raw = clipText.toString().trim();
                    if (raw.contains("takidz507.github.io/run") || raw.startsWith("runapp://") || raw.startsWith("run-invite:")) {
                        String code = extractCodeFromInputOrLink(raw);
                        if (RoomCryptoUtils.isValidRoomCode(code)) {
                            ensureDefaultDisplayName();
                            mqttManager.joinRoom(code, "Room " + code, null);
                            openRoomActivity(code, "Room " + code, null);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void setupAvatarPicker() {
        avatarPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        boolean ar = prefs.isArabic();
                        Toast.makeText(this, ar ? "جارٍ رفع الصورة..." : "Uploading avatar...", Toast.LENGTH_SHORT).show();
                        catboxUploader.processAndUploadUri(this, uri, new CatboxUploader.UploadCallback() {
                            @Override
                            public void onSuccess(String fileUrlOrDataUrl, String fileName, long fileSize, String mimeType) {
                                prefs.setUserAvatarUrl(fileUrlOrDataUrl);
                                mqttManager.syncLocalProfileToActiveRoom();
                                populateSavedUser();
                                Toast.makeText(MainActivity.this,
                                        ar ? "تم تحديث الصورة بنجاح!" : "Avatar updated!",
                                        Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(String errorMessage) {
                                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
        );
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && prefs.isNotificationsEnabled()) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
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
        LocaleHelper.applyLocaleAndLayoutDirection(this, prefs);
        mqttManager.setAppForeground(true);
        mqttManager.addGlobalListener(this);
        applyCurrentTheme();
        applyLocalizedLabels();
        populateSavedUser();
        refreshRecentRoomsUI();
        refreshSavedMessagesWorkspace();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mqttManager.removeGlobalListener(this);
    }

    private void initViews() {
        rootMainFrame = findViewById(R.id.rootMainFrame);
        origamiSplashOverlay = findViewById(R.id.origamiSplashOverlay);

        tvAppTitle = findViewById(R.id.tvAppTitle);
        pillConnectionQuality = findViewById(R.id.pillConnectionQuality);
        viewConnQualityDot = findViewById(R.id.viewConnQualityDot);
        tvConnectionQuality = findViewById(R.id.tvConnectionQuality);
        pillOnlineDirectory = findViewById(R.id.pillOnlineDirectory);
        tvOnlineStatus = findViewById(R.id.tvOnlineStatus);
        btnQuickLangToggle = findViewById(R.id.btnQuickLangToggle);
        btnAccountPairing = findViewById(R.id.btnAccountPairing);
        btnThemeSettings = findViewById(R.id.btnThemeSettings);
        btnMoreMenu = findViewById(R.id.btnMoreMenu);
        btnFeedbackBoard = findViewById(R.id.btnFeedbackBoard);
        layoutAvatarClick = findViewById(R.id.layoutAvatarClick);
        ivMainAvatar = findViewById(R.id.ivMainAvatar);
        tvMainAvatarFallback = findViewById(R.id.tvMainAvatarFallback);

        rootScroll = findViewById(R.id.rootScroll);
        profileCard = findViewById(R.id.profileCard);
        tvGreetingName = findViewById(R.id.tvGreetingName);
        tvUserBioDisplay = findViewById(R.id.tvUserBioDisplay);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        tvOnlineStripTitle = findViewById(R.id.tvOnlineStripTitle);
        layoutOnlineOrbsRow = findViewById(R.id.layoutOnlineOrbsRow);
        cardQuickCodeBar = findViewById(R.id.cardQuickCodeBar);
        etQuickCodeOrSearch = findViewById(R.id.etQuickCodeOrSearch);
        btnQuickCodeEnter = findViewById(R.id.btnQuickCodeEnter);

        mainCard = findViewById(R.id.mainCard);
        btnSegmentCreate = findViewById(R.id.btnSegmentCreate);
        btnSegmentJoin = findViewById(R.id.btnSegmentJoin);
        formCreateRoom = findViewById(R.id.formCreateRoom);
        formJoinCode = findViewById(R.id.formJoinCode);

        etUserName = findViewById(R.id.etUserName);
        etRoomName = findViewById(R.id.etRoomName);
        cbPublicGroup = findViewById(R.id.cbPublicGroup);
        etPublicPassword = findViewById(R.id.etPublicPassword);
        btnCreateRoom = findViewById(R.id.btnCreateRoom);
        etJoinRoomCode = findViewById(R.id.etJoinRoomCode);
        btnJoinByCodeSubmit = findViewById(R.id.btnJoinByCodeSubmit);

        tvRecentTitle = findViewById(R.id.tvRecentTitle);
        viewYourRoomsHighlighter = findViewById(R.id.viewYourRoomsHighlighter);
        btnFilterActiveRooms = findViewById(R.id.btnFilterActiveRooms);
        btnFilterArchivedRooms = findViewById(R.id.btnFilterArchivedRooms);
        tvRecentCount = findViewById(R.id.tvRecentCount);
        recentRoomsCard = findViewById(R.id.recentRoomsCard);
        layoutRecentRoomsContainer = findViewById(R.id.layoutRecentRoomsContainer);
        cardEmptyRoomsPrompt = findViewById(R.id.cardEmptyRoomsPrompt);
        layoutRecentEmpty = findViewById(R.id.layoutRecentEmpty);
        tvEmptyRoomsText = findViewById(R.id.tvEmptyRoomsText);

        cardPublicGroups = findViewById(R.id.cardPublicGroups);
        tvTilePublicTitle = findViewById(R.id.tvTilePublicTitle);
        tvPublicCount = findViewById(R.id.tvPublicCount);
        cardFeedbackQuick = findViewById(R.id.cardFeedbackQuick);
        tvTileFeedbackTitle = findViewById(R.id.tvTileFeedbackTitle);
        tvTileFeedbackSub = findViewById(R.id.tvTileFeedbackSub);

        workspaceDiscover = findViewById(R.id.workspaceDiscover);
        btnDiscoverTabPublic = findViewById(R.id.btnDiscoverTabPublic);
        btnDiscoverTabFeedback = findViewById(R.id.btnDiscoverTabFeedback);
        paneDiscoverPublic = findViewById(R.id.paneDiscoverPublic);
        etSearchPublicGroups = findViewById(R.id.etSearchPublicGroups);
        layoutPublicGroupsContainer = findViewById(R.id.layoutPublicGroupsContainer);
        paneDiscoverFeedback = findViewById(R.id.paneDiscoverFeedback);
        tvFeedbackHeaderInfo = findViewById(R.id.tvFeedbackHeaderInfo);
        btnNewFeedbackInline = findViewById(R.id.btnNewFeedbackInline);
        rvDiscoverFeedback = findViewById(R.id.rvDiscoverFeedback);

        workspacePeople = findViewById(R.id.workspacePeople);
        btnPeopleTabOnline = findViewById(R.id.btnPeopleTabOnline);
        btnPeopleTabFriends = findViewById(R.id.btnPeopleTabFriends);
        etSearchPeopleInline = findViewById(R.id.etSearchPeopleInline);
        rvPeopleInline = findViewById(R.id.rvPeopleInline);

        workspaceSaved = findViewById(R.id.workspaceSaved);
        tvSavedSectionTitle = findViewById(R.id.tvSavedSectionTitle);
        viewSavedHighlighter = findViewById(R.id.viewSavedHighlighter);
        tvSavedSectionSub = findViewById(R.id.tvSavedSectionSub);
        btnPairDeviceFromSaved = findViewById(R.id.btnPairDeviceFromSaved);
        rvSavedMessages = findViewById(R.id.rvSavedMessages);
        layoutSavedEmpty = findViewById(R.id.layoutSavedEmpty);
        tvSavedEmptyText = findViewById(R.id.tvSavedEmptyText);

        fabNewSpace = findViewById(R.id.fabNewSpace);
        bottomNavDock = findViewById(R.id.bottomNavDock);
        btnDockSpaces = findViewById(R.id.btnDockSpaces);
        btnDockDiscover = findViewById(R.id.btnDockDiscover);
        btnDockPeople = findViewById(R.id.btnDockPeople);
        btnDockSaved = findViewById(R.id.btnDockSaved);

        tvAppTitle.setOnClickListener(v -> {
            if (currentDockTab != 0 || showingArchivedRooms) {
                showingArchivedRooms = false;
                switchDockWorkspace(0);
                refreshRecentRoomsUI();
            }
        });

        btnSegmentCreate.setOnClickListener(v -> switchHeroSegment(true));
        btnSegmentJoin.setOnClickListener(v -> switchHeroSegment(false));

        cbPublicGroup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            etPublicPassword.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        btnDockSpaces.setOnClickListener(v -> {
            showingArchivedRooms = false;
            switchDockWorkspace(0);
            refreshRecentRoomsUI();
        });
        btnDockDiscover.setOnClickListener(v -> switchDockWorkspace(1));
        btnDockPeople.setOnClickListener(v -> switchDockWorkspace(2));
        btnDockSaved.setOnClickListener(v -> switchDockWorkspace(3));

        // Floating '+' Button toggles the Create/Join Studio Card with theme-aware motion
        fabNewSpace.setOnClickListener(v -> {
            FeedbackTouchHelper.onGestureTick(this, prefs, v);
            MotionHelper.animateButtonPulse(fabNewSpace, prefs.isLiteModeEnabled());
            if (currentDockTab != 0) {
                switchDockWorkspace(0);
            }
            boolean nowVisible = mainCard.getVisibility() != View.VISIBLE;
            if (nowVisible) {
                MotionHelper.animatePanelEntrance(mainCard, prefs.isLiteModeEnabled());
                rootScroll.post(() -> rootScroll.smoothScrollTo(0, mainCard.getTop()));
            } else {
                mainCard.setVisibility(View.GONE);
            }
        });

        // Slim Join / Create / Filter Bar
        etQuickCodeOrSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshRecentRoomsUI();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnQuickCodeEnter.setOnClickListener(v -> {
            MotionHelper.animateButtonPulse(btnQuickCodeEnter, prefs.isLiteModeEnabled());
            String raw = etQuickCodeOrSearch.getText().toString().trim();
            String code = extractCodeFromInputOrLink(raw);
            if (RoomCryptoUtils.isValidRoomCode(code)) {
                ensureDefaultDisplayName();
                etQuickCodeOrSearch.setText("");
                mqttManager.joinRoom(code, "Room " + code, null);
                openRoomActivity(code, "Room " + code, null);
            } else if (!raw.isEmpty()) {
                // Open Create studio pre-filled with the typed room name
                mainCard.setVisibility(View.VISIBLE);
                MotionHelper.animatePanelEntrance(mainCard, prefs.isLiteModeEnabled());
                switchHeroSegment(true);
                etRoomName.setText(raw);
                etRoomName.requestFocus();
            } else {
                mainCard.setVisibility(View.VISIBLE);
                MotionHelper.animatePanelEntrance(mainCard, prefs.isLiteModeEnabled());
                switchHeroSegment(false);
                etJoinRoomCode.requestFocus();
            }
        });

        btnFilterActiveRooms.setOnClickListener(v -> {
            showingArchivedRooms = false;
            refreshRecentRoomsUI();
        });
        btnFilterArchivedRooms.setOnClickListener(v -> {
            showingArchivedRooms = true;
            refreshRecentRoomsUI();
        });

        pillConnectionQuality.setOnClickListener(v -> showConnectionStatusToast());
        pillOnlineDirectory.setOnClickListener(v -> switchDockWorkspace(2));
        tvOnlineStripTitle.setOnClickListener(v -> switchDockWorkspace(2));

        btnQuickLangToggle.setOnClickListener(v -> {
            String nextLang = prefs.isArabic() ? "en" : "ar";
            prefs.setUiLanguage(nextLang);
            LocaleHelper.applyLocaleAndLayoutDirection(this, prefs);
            applyLocalizedLabels();
            applyCurrentTheme();
            populateSavedUser();
            refreshRecentRoomsUI();
            refreshOnlineOrbsRow(mqttManager.getOnlineUsersList());
            refreshSavedMessagesWorkspace();
        });

        btnAccountPairing.setOnClickListener(v -> showAccountPairingDialog());
        btnPairDeviceFromSaved.setOnClickListener(v -> showAccountPairingDialog());

        btnFeedbackBoard.setOnClickListener(v -> {
            discoverShowingFeedback = true;
            switchDockWorkspace(1);
            switchDiscoverSubtab(true);
        });
        cardFeedbackQuick.setOnClickListener(v -> {
            discoverShowingFeedback = true;
            switchDockWorkspace(1);
            switchDiscoverSubtab(true);
        });
        cardPublicGroups.setOnClickListener(v -> {
            discoverShowingFeedback = false;
            switchDockWorkspace(1);
            switchDiscoverSubtab(false);
        });

        profileCard.setOnClickListener(v -> showLiveThemeAndSettingsSheet());
        btnEditProfile.setOnClickListener(v -> showLiveThemeAndSettingsSheet());
        btnThemeSettings.setOnClickListener(v -> showLiveThemeAndSettingsSheet());
        btnMoreMenu.setOnClickListener(v -> showMoreSheet());
        layoutAvatarClick.setOnClickListener(v -> showLiveThemeAndSettingsSheet());

        btnCreateRoom.setOnClickListener(v -> onCreateRoomClicked());
        btnJoinByCodeSubmit.setOnClickListener(v -> onJoinRoomClicked());
    }

    private void setupInlineWorkspaces() {
        btnDiscoverTabPublic.setOnClickListener(v -> switchDiscoverSubtab(false));
        btnDiscoverTabFeedback.setOnClickListener(v -> switchDiscoverSubtab(true));

        etSearchPublicGroups.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshPublicGroupsListUI();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        rvDiscoverFeedback.setLayoutManager(new LinearLayoutManager(this));
        inlineFeedbackAdapter = new FeedbackPostsAdapter(this, prefs, palette, (post, isHeart) -> {
            mqttManager.toggleFeedbackVote(post.id, isHeart);
        });
        rvDiscoverFeedback.setAdapter(inlineFeedbackAdapter);
        btnNewFeedbackInline.setOnClickListener(v -> showNewSuggestionDialog(this::refreshInlineFeedbackUI));

        rvPeopleInline.setLayoutManager(new LinearLayoutManager(this));
        inlineOnlineAdapter = new OnlineUsersAdapter(this, prefs, palette, new OnlineUsersAdapter.UserActionListener() {
            @Override
            public void onInviteClicked(OnlineUser user) {
                showInviteUserDialog(user);
            }

            @Override
            public void onAddFriendClicked(OnlineUser user) {
                mqttManager.sendFriendRequest(user);
                Toast.makeText(MainActivity.this,
                        prefs.isArabic() ? ("تم إرسال طلب صداقة إلى " + user.name) : ("Friend request sent to " + user.name),
                        Toast.LENGTH_SHORT).show();
                refreshPeopleWorkspaceUI();
            }
        });

        inlineFriendsAdapter = new FriendsAdapter(this, new FriendsAdapter.FriendsActionListener() {
            @Override
            public void onAcceptFriendRequest(SignalMessage request) {
                mqttManager.acceptFriendRequest(request);
                refreshPeopleWorkspaceUI();
            }

            @Override
            public void onDeclineFriendRequest(SignalMessage request) {
                mqttManager.declineFriendRequest(request);
                refreshPeopleWorkspaceUI();
            }

            @Override
            public void onJoinRoomInvite(SignalMessage invite) {
                mqttManager.joinRoom(invite.roomCode, invite.roomName, null);
                openRoomActivity(invite.roomCode, invite.roomName, null);
            }

            @Override
            public void onDismissRoomInvite(SignalMessage invite) {
                prefs.removeRoomInvite(invite.id);
                refreshPeopleWorkspaceUI();
            }

            @Override
            public void onInviteFriendToRoom(OnlineUser friend) {
                showInviteUserDialog(friend);
            }

            @Override
            public void onRemoveFriend(OnlineUser friend) {
                prefs.removeFriend(friend.userId);
                refreshPeopleWorkspaceUI();
            }
        });

        btnPeopleTabOnline.setOnClickListener(v -> switchPeopleSubtab(false));
        btnPeopleTabFriends.setOnClickListener(v -> switchPeopleSubtab(true));
        etSearchPeopleInline.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!peopleShowingFriends) refreshPeopleWorkspaceUI();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        rvSavedMessages.setLayoutManager(new LinearLayoutManager(this));
        savedMessagesAdapter = new SavedMessagesAdapter(prefs, palette, new SavedMessagesAdapter.SavedMessageListener() {
            @Override
            public void onJumpToMessage(SavedMessage item) {
                if (item == null || item.roomCode == null || item.roomCode.isEmpty()) return;
                ensureDefaultDisplayName();
                mqttManager.joinRoom(item.roomCode, item.roomName, null);
                openRoomActivity(item.roomCode, item.roomName, item.messageId);
            }

            @Override
            public void onRemoveSavedMessage(SavedMessage item) {
                prefs.removeSavedMessage(item.id);
                refreshSavedMessagesWorkspace();
            }
        });
        rvSavedMessages.setAdapter(savedMessagesAdapter);
    }

    private void switchDockWorkspace(int index) {
        currentDockTab = index;
        boolean lite = prefs.isLiteModeEnabled();
        boolean glassTheme = ThemeHelper.isGlassTheme(palette.themeKey);

        rootScroll.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        workspaceDiscover.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        workspacePeople.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        workspaceSaved.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
        fabNewSpace.setVisibility(index == 0 ? View.VISIBLE : View.GONE);

        // On Home (index == 0), hide bottomNavDock so Home matches Design A (7196.png) 100%,
        // except in Liquid Glass theme or when navigating inside secondary workspaces (1/2/3).
        bottomNavDock.setVisibility((index != 0 || glassTheme) ? View.VISIBLE : View.GONE);

        FrameLayout.LayoutParams fabLp = (FrameLayout.LayoutParams) fabNewSpace.getLayoutParams();
        fabLp.bottomMargin = dp(glassTheme ? 76 : 24);
        fabNewSpace.setLayoutParams(fabLp);

        View activePanel = index == 0 ? rootScroll
                : (index == 1 ? workspaceDiscover : (index == 2 ? workspacePeople : workspaceSaved));
        MotionHelper.animatePanelEntrance(activePanel, lite);

        updateDockButtonsVisuals();

        if (index == 1) {
            refreshPublicGroupsListUI();
            refreshInlineFeedbackUI();
        } else if (index == 2) {
            refreshPeopleWorkspaceUI();
        } else if (index == 3) {
            refreshSavedMessagesWorkspace();
        }
    }

    private void updateDockButtonsVisuals() {
        MaterialButton[] buttons = new MaterialButton[]{btnDockSpaces, btnDockDiscover, btnDockPeople, btnDockSaved};
        for (int i = 0; i < buttons.length; i++) {
            boolean active = (i == currentDockTab);
            MaterialButton b = buttons[i];
            b.setBackgroundTintList(ColorStateList.valueOf(active ? palette.accent : Color.TRANSPARENT));
            b.setTextColor(active ? palette.accentInk : palette.dockText);
            b.setIconTint(ColorStateList.valueOf(active ? palette.accentInk : palette.dockText));
        }
    }

    private void switchDiscoverSubtab(boolean showFeedback) {
        discoverShowingFeedback = showFeedback;
        paneDiscoverPublic.setVisibility(showFeedback ? View.GONE : View.VISIBLE);
        paneDiscoverFeedback.setVisibility(showFeedback ? View.VISIBLE : View.GONE);

        btnDiscoverTabPublic.setBackgroundTintList(ColorStateList.valueOf(!showFeedback ? palette.accent : palette.panelSoft));
        btnDiscoverTabPublic.setTextColor(!showFeedback ? palette.accentInk : palette.text);
        btnDiscoverTabFeedback.setBackgroundTintList(ColorStateList.valueOf(showFeedback ? palette.accent : palette.panelSoft));
        btnDiscoverTabFeedback.setTextColor(showFeedback ? palette.accentInk : palette.text);

        if (showFeedback) refreshInlineFeedbackUI();
        else refreshPublicGroupsListUI();
    }

    private void switchPeopleSubtab(boolean showFriends) {
        peopleShowingFriends = showFriends;
        btnPeopleTabOnline.setBackgroundTintList(ColorStateList.valueOf(!showFriends ? palette.accent : palette.panelSoft));
        btnPeopleTabOnline.setTextColor(!showFriends ? palette.accentInk : palette.text);
        btnPeopleTabFriends.setBackgroundTintList(ColorStateList.valueOf(showFriends ? palette.accent : palette.panelSoft));
        btnPeopleTabFriends.setTextColor(showFriends ? palette.accentInk : palette.text);
        etSearchPeopleInline.setVisibility(showFriends ? View.GONE : View.VISIBLE);
        refreshPeopleWorkspaceUI();
    }

    private void refreshPublicGroupsListUI() {
        layoutPublicGroupsContainer.removeAllViews();
        DirectoryData dir = mqttManager.getDirectoryData();
        List<DirectoryData.PublicGroupEntry> groups = (dir != null && dir.groups != null) ? dir.groups : new ArrayList<>();
        String query = etSearchPublicGroups.getText().toString().trim().toLowerCase(Locale.US);
        boolean ar = prefs.isArabic();

        int shown = 0;
        float d = getResources().getDisplayMetrics().density;
        for (DirectoryData.PublicGroupEntry g : groups) {
            if (g == null) continue;
            String gName = g.name != null ? g.name : "Public Room";
            if (!query.isEmpty() && !gName.toLowerCase(Locale.US).contains(query)) continue;
            shown++;

            MaterialCardView card = new MaterialCardView(this);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            cardLp.bottomMargin = (int) (10 * d);
            card.setLayoutParams(cardLp);
            card.setRadius(20 * d);
            card.setCardElevation(0f);
            card.setCardBackgroundColor(palette.panel);
            card.setStrokeColor(palette.panelHover);
            card.setStrokeWidth((int) (1 * d));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pad = (int) (14 * d);
            row.setPadding(pad, pad, pad, pad);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvTitle = new TextView(this);
            tvTitle.setText((g.hasPassword ? "🔒 " : "🌐 ") + gName);
            tvTitle.setTextColor(palette.text);
            tvTitle.setTextSize(15f);
            tvTitle.setTypeface(tvTitle.getTypeface(), Typeface.BOLD);
            textCol.addView(tvTitle);

            TextView tvSub = new TextView(this);
            tvSub.setText(g.hasPassword
                    ? (ar ? "محمية بكلمة مرور" : "Password protected")
                    : (ar ? ("مفتوحة للانضمام • #" + (g.key != null ? g.key : "")) : ("Open group • #" + (g.key != null ? g.key : ""))));
            tvSub.setTextColor(palette.muted);
            tvSub.setTextSize(12f);
            textCol.addView(tvSub);

            row.addView(textCol);

            if (g.by != null && g.by.equals(prefs.getUserId())) {
                ImageButton btnRm = new ImageButton(this);
                btnRm.setImageResource(R.drawable.ic_delete);
                btnRm.setBackgroundResource(android.R.color.transparent);
                btnRm.setColorFilter(palette.danger);
                btnRm.setOnClickListener(v -> {
                    mqttManager.removeDirectoryEntry(g.id);
                    refreshPublicGroupsListUI();
                });
                row.addView(btnRm);
            }

            MaterialButton btnJoin = new MaterialButton(this);
            btnJoin.setText(ar ? "انضمام" : "Join");
            btnJoin.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
            btnJoin.setTextColor(palette.accentInk);
            btnJoin.setCornerRadius((int) (16 * d));
            btnJoin.setOnClickListener(v -> {
                ensureDefaultDisplayName();
                if (g.hasPassword) {
                    promptPasswordForPublicGroup(g);
                } else if (g.key != null && !g.key.isEmpty()) {
                    mqttManager.joinRoom(g.key, g.name, null);
                    openRoomActivity(g.key, g.name, null);
                }
            });
            row.addView(btnJoin);

            card.addView(row);
            layoutPublicGroupsContainer.addView(card);
        }

        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText(ar
                    ? "لا توجد مجموعات عامة مطابقة حالياً. أنشئ غرفة وحدد خيار الإدراج في الدليل العام!"
                    : "No public groups listed yet. Create a room and check 'List in Public Groups Directory'!");
            empty.setTextColor(palette.muted);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(32, 64, 32, 64);
            layoutPublicGroupsContainer.addView(empty);
        }
    }

    private void refreshInlineFeedbackUI() {
        FeedbackBoardData data = mqttManager.getFeedbackBoardData();
        List<FeedbackPost> allPosts = (data != null && data.posts != null) ? new ArrayList<>(data.posts) : new ArrayList<>();
        allPosts.sort((a, b) -> Integer.compare(b.getTotalScore(), a.getTotalScore()));
        inlineFeedbackAdapter.updatePalette(palette);
        inlineFeedbackAdapter.setPosts(allPosts);
    }

    private void refreshPeopleWorkspaceUI() {
        boolean ar = prefs.isArabic();
        List<OnlineUser> onlineList = mqttManager.getOnlineUsersList();
        List<SignalMessage> reqs = prefs.getPendingRequestsReceived();
        List<SignalMessage> invs = prefs.getPendingRoomInvites();
        List<OnlineUser> friends = prefs.getFriends();
        int badgeCount = reqs.size() + invs.size();

        btnPeopleTabOnline.setText((ar ? "متصل الآن (" : "Online (") + onlineList.size() + ")");
        btnPeopleTabFriends.setText((ar ? "الأصدقاء والدعوات" : "Friends & Invites")
                + (badgeCount > 0 ? (" (" + badgeCount + ")") : ""));

        if (!peopleShowingFriends) {
            rvPeopleInline.setAdapter(inlineOnlineAdapter);
            inlineOnlineAdapter.updatePalette(palette);
            String q = etSearchPeopleInline.getText().toString().trim().toLowerCase(Locale.US);
            List<OnlineUser> filtered = new ArrayList<>();
            for (OnlineUser u : onlineList) {
                if (q.isEmpty() || u.name.toLowerCase(Locale.US).contains(q)) {
                    filtered.add(u);
                }
            }
            inlineOnlineAdapter.setUsers(filtered);
        } else {
            rvPeopleInline.setAdapter(inlineFriendsAdapter);
            inlineFriendsAdapter.updatePalette(palette);
            inlineFriendsAdapter.setData(reqs, invs, friends);
        }
    }

    private void refreshSavedMessagesWorkspace() {
        List<SavedMessage> saved = prefs.getSavedMessages();
        savedMessagesAdapter.updatePalette(palette);
        savedMessagesAdapter.setItems(saved);
        rvSavedMessages.setVisibility(saved.isEmpty() ? View.GONE : View.VISIBLE);
        layoutSavedEmpty.setVisibility(saved.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /**
     * Slim single-line online strip: shows compact user chips horizontally so it never eats vertical space.
     */
    private void refreshOnlineOrbsRow(List<OnlineUser> onlineUsers) {
        layoutOnlineOrbsRow.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        boolean ar = prefs.isArabic();

        List<OnlineUser> list = onlineUsers != null ? onlineUsers : new ArrayList<>();
        int count = Math.max(1, list.size());
        tvOnlineStripTitle.setText("● " + LocaleHelper.formatNumber(count, ar) + (ar ? " متصل" : " online"));

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setCornerRadius(14 * d);
        pillBg.setColor(palette.highlighter);
        tvOnlineStripTitle.setBackground(pillBg);
        tvOnlineStripTitle.setTextColor(palette.accentInk);

        for (OnlineUser u : list) {
            if (u == null) continue;
            TextView chip = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    (int) (26 * d)
            );
            lp.setMarginEnd((int) (6 * d));
            chip.setLayoutParams(lp);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding((int) (10 * d), 0, (int) (10 * d), 0);
            chip.setSingleLine(true);
            String label = u.isSelf ? (ar ? "أنت" : "You") : (u.name != null ? u.name : "User");
            chip.setText(label);
            chip.setTextSize(11.5f);
            chip.setTextColor(palette.text);

            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setCornerRadius(13 * d);
            chipBg.setColor(palette.panel);
            chipBg.setStroke((int) (1 * d), u.isSelf ? palette.accent : palette.panelHover);
            chip.setBackground(chipBg);

            chip.setOnClickListener(v -> {
                if (u.isSelf) {
                    showLiveThemeAndSettingsSheet();
                } else {
                    showInviteUserDialog(u);
                }
            });
            layoutOnlineOrbsRow.addView(chip);
        }
    }

    private void switchHeroSegment(boolean showCreate) {
        if (showCreate) {
            btnSegmentCreate.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
            btnSegmentCreate.setTextColor(palette.accentInk);
            btnSegmentJoin.setBackgroundTintList(ColorStateList.valueOf(palette.panelSoft));
            btnSegmentJoin.setTextColor(palette.text);

            formCreateRoom.setVisibility(View.VISIBLE);
            formJoinCode.setVisibility(View.GONE);
        } else {
            btnSegmentJoin.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
            btnSegmentJoin.setTextColor(palette.accentInk);
            btnSegmentCreate.setBackgroundTintList(ColorStateList.valueOf(palette.panelSoft));
            btnSegmentCreate.setTextColor(palette.text);

            formCreateRoom.setVisibility(View.GONE);
            formJoinCode.setVisibility(View.VISIBLE);
        }
    }

    private void populateSavedUser() {
        boolean ar = prefs.isArabic();
        String name = prefs.getUserName();
        if (name.isEmpty()) name = "Taqi";
        etUserName.setText(name);
        tvGreetingName.setText(LocaleHelper.getGreeting(name, ar));

        String bio = prefs.getUserBio();
        tvUserBioDisplay.setText(bio.isEmpty()
                ? (ar ? "اضغط لتخصيص الملف الشخصي، المظهر أو ربط الحساب" : "Tap to customize profile, live theme & linked devices")
                : bio);

        String initial = name.substring(0, 1).toUpperCase(Locale.US);
        tvMainAvatarFallback.setText(initial);

        String avatarUrl = prefs.getUserAvatarUrl();
        if (!avatarUrl.isEmpty()) {
            ivMainAvatar.setVisibility(View.VISIBLE);
            tvMainAvatarFallback.setVisibility(View.GONE);
            Glide.with(this).load(avatarUrl).circleCrop().into(ivMainAvatar);
        } else {
            ivMainAvatar.setVisibility(View.GONE);
            tvMainAvatarFallback.setVisibility(View.VISIBLE);
        }
    }

    private void ensureDefaultDisplayName() {
        if (prefs.getUserName().isEmpty()) {
            String typed = etUserName != null ? etUserName.getText().toString().trim() : "";
            prefs.setUserName(typed.isEmpty() ? "Taqi" : typed);
        }
    }

    private void saveUserNameFromInput() {
        String name = etUserName.getText().toString().trim();
        if (name.isEmpty()) name = prefs.getUserName().isEmpty() ? "Taqi" : prefs.getUserName();
        prefs.setUserName(name);
        populateSavedUser();
    }

    private void onCreateRoomClicked() {
        saveUserNameFromInput();
        String roomName = etRoomName.getText().toString().trim();
        boolean isPublic = cbPublicGroup.isChecked();
        String optionalPw = isPublic ? etPublicPassword.getText().toString().trim() : null;

        String code = mqttManager.createNewRoom(roomName, isPublic, optionalPw);
        mainCard.setVisibility(View.GONE);
        openRoomActivity(code, roomName.isEmpty() ? ("Room " + code) : roomName, null);
    }

    private void onJoinRoomClicked() {
        saveUserNameFromInput();
        String raw = etJoinRoomCode.getText().toString().trim();
        String extracted = extractCodeFromInputOrLink(raw);
        if (!RoomCryptoUtils.isValidRoomCode(extracted)) {
            Toast.makeText(this,
                    prefs.isArabic()
                            ? "يرجى إدخال رمز غرفة صحيح (6 أو 12 حرفاً) أو رابط دعوة."
                            : "Please enter a valid 6 or 12 character room code or invite link.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        mainCard.setVisibility(View.GONE);
        mqttManager.joinRoom(extracted, "Room " + extracted, null);
        openRoomActivity(extracted, "Room " + extracted, null);
    }

    private String extractCodeFromInputOrLink(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1).trim();
        }
        if (trimmed.startsWith("run-invite:")) {
            trimmed = trimmed.substring("run-invite:".length()).trim();
        }
        if (trimmed.contains("room=") || trimmed.startsWith("http") || trimmed.startsWith("runapp://")) {
            try {
                Uri uri = Uri.parse(trimmed);
                String q = uri.getQueryParameter("room");
                if (q != null && !q.trim().isEmpty()) {
                    return q.trim().toUpperCase(Locale.US);
                }
                List<String> segments = uri.getPathSegments();
                if (segments != null && !segments.isEmpty()) {
                    String lastSeg = segments.get(segments.size() - 1).trim().toUpperCase(Locale.US);
                    if (RoomCryptoUtils.isValidRoomCode(lastSeg)) {
                        return lastSeg;
                    }
                }
                if ("room".equalsIgnoreCase(uri.getHost()) && uri.getPath() != null) {
                    String candidate = uri.getPath().replace("/", "").trim().toUpperCase(Locale.US);
                    if (RoomCryptoUtils.isValidRoomCode(candidate)) {
                        return candidate;
                    }
                }
            } catch (Exception ignored) {}
        }
        return trimmed.toUpperCase(Locale.US);
    }

    private void openRoomActivity(String code, String name, String jumpToMessageId) {
        Intent intent = new Intent(this, RoomActivity.class);
        intent.putExtra("room_code", code);
        intent.putExtra("room_name", name);
        if (jumpToMessageId != null && !jumpToMessageId.isEmpty()) {
            intent.putExtra("jump_to_message_id", jumpToMessageId);
        }
        startActivity(intent);
        if (!prefs.isLiteModeEnabled()) {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

    private boolean handleIncomingDeepLink(Intent intent) {
        if (intent == null || intent.getData() == null) return false;
        Uri uri = intent.getData();
        String code = extractCodeFromInputOrLink(uri.toString());
        if (RoomCryptoUtils.isValidRoomCode(code)) {
            String roomNameParam = uri.getQueryParameter("name");
            String rName = (roomNameParam != null && !roomNameParam.trim().isEmpty())
                    ? roomNameParam.trim() : ("Room " + code);
            ensureDefaultDisplayName();
            mqttManager.joinRoom(code, rName, null);
            openRoomActivity(code, rName, null);
            intent.setData(null);
            return true;
        }
        return false;
    }

    /**
     * Renders "Your rooms" and "Recent" room rows matching Design A (7196.png):
     * - No nested outer card, no 3 action buttons on the right.
     * - Room code in a single unbreakable line.
     * - Single clean lime status badge ("● 1 online" / "🌐 Public group").
     * - Long-press opens room quick actions sheet.
     */
    private void refreshRecentRoomsUI() {
        List<RecentRoom> allRecent = prefs.getRecentRooms();
        layoutRecentRoomsContainer.removeAllViews();
        boolean ar = prefs.isArabic();
        String filterText = etQuickCodeOrSearch != null
                ? etQuickCodeOrSearch.getText().toString().trim().toLowerCase(Locale.US) : "";

        tvRecentTitle.setText(showingArchivedRooms
                ? (ar ? "الأرشيف" : "Archived")
                : (ar ? "غرفك" : "Your rooms"));
        btnFilterActiveRooms.setVisibility(showingArchivedRooms ? View.VISIBLE : View.GONE);
        btnFilterActiveRooms.setBackgroundTintList(ColorStateList.valueOf(palette.panelSoft));
        btnFilterActiveRooms.setTextColor(palette.text);

        List<RecentRoom> filtered = new ArrayList<>();
        for (RecentRoom r : allRecent) {
            if (r == null || r.code == null) continue;
            if (r.archived != showingArchivedRooms) continue;
            String rName = r.name != null ? r.name : ("Room " + r.code);
            if (!filterText.isEmpty()
                    && !rName.toLowerCase(Locale.US).contains(filterText)
                    && !r.code.toLowerCase(Locale.US).contains(filterText)) {
                continue;
            }
            filtered.add(r);
        }

        tvRecentCount.setText(LocaleHelper.formatNumber(filtered.size(), ar));

        if (filtered.isEmpty()) {
            cardEmptyRoomsPrompt.setVisibility(View.VISIBLE);
            layoutRecentEmpty.setVisibility(View.VISIBLE);
            tvEmptyRoomsText.setText(showingArchivedRooms
                    ? (ar ? "لا توجد غرف مؤرشفة." : "No archived rooms.")
                    : (ar ? "لا توجد غرف بعد. اضغط + لإنشاء غرفة أو الانضمام برمز!" : "No rooms yet. Tap + below to create or join a room."));
            return;
        }

        cardEmptyRoomsPrompt.setVisibility(View.GONE);
        layoutRecentEmpty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        float d = getResources().getDisplayMetrics().density;
        boolean recentHeaderAdded = false;

        for (int idx = 0; idx < filtered.size(); idx++) {
            RecentRoom r = filtered.get(idx);

            // Insert "Recent" sub-heading after the top 3 active rooms (matches Design A 7196.png!)
            if (!showingArchivedRooms && idx == 3 && !recentHeaderAdded) {
                recentHeaderAdded = true;
                TextView tvRecentSubHeader = new TextView(this);
                LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                subLp.topMargin = (int) (10 * d);
                subLp.bottomMargin = (int) (10 * d);
                tvRecentSubHeader.setLayoutParams(subLp);
                tvRecentSubHeader.setText(ar ? "الأخيرة" : "Recent");
                tvRecentSubHeader.setTextColor(palette.text);
                tvRecentSubHeader.setTextSize(18f);
                tvRecentSubHeader.setTypeface(tvRecentSubHeader.getTypeface(), Typeface.BOLD);
                layoutRecentRoomsContainer.addView(tvRecentSubHeader);
            }

            View itemView = inflater.inflate(R.layout.item_recent_room, layoutRecentRoomsContainer, false);
            MaterialCardView card = itemView.findViewById(R.id.cardRecentRoomItem);
            TextView tvName = itemView.findViewById(R.id.tvRecentRoomName);
            TextView tvPreview = itemView.findViewById(R.id.tvRecentRoomPreview);
            TextView tvCode = itemView.findViewById(R.id.tvRecentRoomCode);
            TextView tvMuted = itemView.findViewById(R.id.tvRoomMutedBadge);
            TextView tvTime = itemView.findViewById(R.id.tvRecentRoomTime);
            View layoutBadgesRow = itemView.findViewById(R.id.layoutRoomBadgesRow);

            card.setCardBackgroundColor(palette.panel);
            card.setStrokeColor(palette.panelHover);
            card.setStrokeWidth((int) (1 * d));

            String title = r.name != null && !r.name.isEmpty() ? r.name : ("Room " + r.code);
            tvName.setText(title);
            tvName.setTextColor(palette.text);

            // Top-right single-line unbreakable room code
            tvTime.setText("#" + r.code);
            tvTime.setTextColor(palette.subtle);

            boolean isOlderRecentCard = (idx >= 3 && !showingArchivedRooms);
            String relTime = LocaleHelper.formatRelativeTime(r.getEffectiveTime(), ar);
            String preview;
            if (r.lastMessagePreview != null && !r.lastMessagePreview.isEmpty()) {
                preview = r.lastMessagePreview;
            } else {
                preview = ar ? ("آخر نشاط " + relTime) : ("Last active " + relTime);
            }
            tvPreview.setText(preview);
            tvPreview.setTextColor(palette.muted);

            // Single clean lime pill badge (Matches 7196.png: "● 12 online" or "🌐 Public group")
            int roomAccent = ThemeHelper.parseRoomAccentColor(r.accentColor, palette);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(14 * d);
            badgeBg.setColor(palette.isLight ? palette.highlighter : ThemeHelper.withAlpha(roomAccent, 70));
            tvCode.setBackground(badgeBg);
            tvCode.setTextColor(palette.isLight ? palette.accentInk : palette.text);

            boolean isActiveRoom = r.code.equalsIgnoreCase(mqttManager.getActiveRoomCode());
            int onlineInRoom = isActiveRoom ? Math.max(1, mqttManager.getGlobalOnlineCount()) : 1;
            if (title.toLowerCase(Locale.US).contains("public")) {
                tvCode.setText(ar ? "🌐 مجموعة عامة" : "🌐 Public group");
            } else {
                tvCode.setText("● " + LocaleHelper.formatNumber(onlineInRoom, ar) + (ar ? " متصل" : " online"));
            }

            if (isOlderRecentCard && !prefs.isRoomMuted(r.code)) {
                layoutBadgesRow.setVisibility(View.GONE);
            } else {
                layoutBadgesRow.setVisibility(View.VISIBLE);
            }

            if (prefs.isRoomMuted(r.code)) {
                tvMuted.setVisibility(View.VISIBLE);
                GradientDrawable muteBg = new GradientDrawable();
                muteBg.setCornerRadius(14 * d);
                muteBg.setColor(palette.panelSoft);
                tvMuted.setBackground(muteBg);
                tvMuted.setText("🔕 " + prefs.getRoomMuteRemainingLabel(r.code));
                tvMuted.setTextColor(palette.muted);
            } else {
                tvMuted.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                FeedbackTouchHelper.onGestureTick(this, prefs, v);
                ensureDefaultDisplayName();
                mqttManager.joinRoom(r.code, title, null);
                openRoomActivity(r.code, title, null);
            });

            itemView.setOnLongClickListener(v -> {
                FeedbackTouchHelper.onGestureTick(this, prefs, v);
                showRoomQuickActionsSheet(r, title);
                return true;
            });

            layoutRecentRoomsContainer.addView(itemView);
            MotionHelper.animateStaggeredCard(itemView, idx, prefs.isLiteModeEnabled());
        }
    }

    /**
     * Long-press sheet on any room card: houses Copy Invite/Code, Archive/Unarchive, and Remove
     * so room cards remain clean and uncluttered like Design A (7196.png).
     */
    private void showRoomQuickActionsSheet(RecentRoom r, String title) {
        boolean ar = prefs.isArabic();
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_RunApp);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(18), dp(20), dp(24));
        LocaleHelper.applyViewDirection(container, ar);

        TextView tvHeader = new TextView(this);
        tvHeader.setText(title + "  ·  #" + r.code);
        tvHeader.setTextColor(palette.text);
        tvHeader.setTextSize(17f);
        tvHeader.setTypeface(tvHeader.getTypeface(), Typeface.BOLD);
        tvHeader.setPadding(dp(4), 0, dp(4), dp(10));
        container.addView(tvHeader);

        addMoreRow(container, "🔗", ar ? ("نسخ رابط الدعوة (#" + r.code + ")") : ("Copy Invite Link (#" + r.code + ")"), dialog, () -> {
            String inviteUrl = "https://takidz507.github.io/run/?room=" + r.code;
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Room Invite", inviteUrl));
                Toast.makeText(this,
                        ar ? ("تم نسخ رابط الدعوة #" + r.code) : ("Copied invite link for #" + r.code),
                        Toast.LENGTH_SHORT).show();
            }
        });

        addMoreRow(container, "📦",
                r.archived ? (ar ? "استعادة من الأرشيف" : "Unarchive Room") : (ar ? "نقل إلى الأرشيف" : "Archive Room"),
                dialog, () -> {
                    boolean nextArch = !r.archived;
                    prefs.setRoomArchived(r.code, nextArch);
                    Toast.makeText(this,
                            nextArch ? (ar ? "تمت أرشفة الغرفة" : "Room archived") : (ar ? "تمت استعادة الغرفة" : "Room restored"),
                            Toast.LENGTH_SHORT).show();
                    refreshRecentRoomsUI();
                });

        addMoreRow(container, "🗑", ar ? "إزالة من القائمة" : "Remove from List", dialog, () -> {
            prefs.removeRecentRoom(r.code);
            refreshRecentRoomsUI();
        });

        dialog.setContentView(container);
        ThemeHelper.prepareBottomSheet(dialog, container, palette);
        MotionHelper.animatePanelEntrance(container, prefs.isLiteModeEnabled());
        dialog.show();
    }

    private void showInviteUserDialog(OnlineUser user) {
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_invite_user, null);
        LocaleHelper.applyViewDirection(root, prefs.isArabic());

        TextView tvTitle = root.findViewById(R.id.tvInviteDialogTitle);
        TextView tvSub = root.findViewById(R.id.tvInviteDialogSub);
        LinearLayout layoutRooms = root.findViewById(R.id.layoutRoomsList);
        EditText etCustomCode = root.findViewById(R.id.etCustomRoomCode);
        MaterialButton btnCancel = root.findViewById(R.id.btnCancelInvite);
        MaterialButton btnSend = root.findViewById(R.id.btnSendInvite);

        boolean ar = prefs.isArabic();
        tvTitle.setTextColor(palette.text);
        tvSub.setTextColor(palette.muted);
        tvTitle.setText(ar ? "دعوة إلى غرفة" : "Invite User to Room");
        tvSub.setText(ar ? ("اختر غرفة لدعوة " + user.name + " إليها:") : ("Invite " + user.name + " to join a room:"));
        etCustomCode.setTextColor(palette.text);
        etCustomCode.setHintTextColor(palette.subtle);
        btnSend.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        btnSend.setTextColor(palette.accentInk);

        final String[] selectedCode = {null};
        final String[] selectedName = {null};

        List<RecentRoom> recent = prefs.getRecentRooms();
        for (RecentRoom r : recent) {
            MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText(r.name + " (#" + r.code + ")");
            btn.setTextColor(palette.text);
            btn.setOnClickListener(v -> {
                selectedCode[0] = r.code;
                selectedName[0] = r.name;
                etCustomCode.setText(r.code);
            });
            layoutRooms.addView(btn);
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_RunApp);
        dialog.setContentView(root);
        ThemeHelper.prepareBottomSheet(dialog, root, palette);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSend.setOnClickListener(v -> {
            String code = etCustomCode.getText().toString().trim().toUpperCase(Locale.US);
            if (code.isEmpty() && selectedCode[0] != null) {
                code = selectedCode[0];
            }
            if (!RoomCryptoUtils.isValidRoomCode(code)) {
                Toast.makeText(this, ar ? "أدخل رمز غرفة صحيح" : "Please enter a valid room code.", Toast.LENGTH_SHORT).show();
                return;
            }
            String rName = selectedName[0] != null ? selectedName[0] : ("Room " + code);
            mqttManager.sendRoomInvite(user, code, rName);
            Toast.makeText(this, ar ? ("تم إرسال الدعوة إلى " + user.name) : ("Invitation sent to " + user.name + "!"), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showNewSuggestionDialog(Runnable onPosted) {
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_new_suggestion, null);
        LocaleHelper.applyViewDirection(root, prefs.isArabic());

        EditText etTitle = root.findViewById(R.id.etSuggestionTitle);
        EditText etContent = root.findViewById(R.id.etSuggestionContent);
        MaterialButton btnCancel = root.findViewById(R.id.btnCancelSuggestion);
        MaterialButton btnSubmit = root.findViewById(R.id.btnSubmitSuggestion);

        etTitle.setTextColor(palette.text);
        etContent.setTextColor(palette.text);
        btnSubmit.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        btnSubmit.setTextColor(palette.accentInk);

        Chip chipFeature = root.findViewById(R.id.chipCatFeature);
        Chip chipBug = root.findViewById(R.id.chipCatBug);
        Chip chipImprovement = root.findViewById(R.id.chipCatImprovement);

        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_RunApp);
        dialog.setContentView(root);
        ThemeHelper.prepareBottomSheet(dialog, root, palette);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSubmit.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String content = etContent.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(this, prefs.isArabic() ? "أدخل عنوان المقترح" : "Please enter a title", Toast.LENGTH_SHORT).show();
                return;
            }

            String cat = "Idea";
            if (chipFeature.isChecked()) cat = "Feature";
            else if (chipBug.isChecked()) cat = "Bug";
            else if (chipImprovement.isChecked()) cat = "Improvement";

            mqttManager.createFeedbackPost(title, content, cat);
            Toast.makeText(this, prefs.isArabic() ? "تم نشر المقترح!" : "Suggestion submitted!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            if (onPosted != null) onPosted.run();
        });

        dialog.show();
    }

    private void promptPasswordForPublicGroup(DirectoryData.PublicGroupEntry group) {
        EditText pwInput = new EditText(this);
        boolean ar = prefs.isArabic();
        pwInput.setHint(ar ? "كلمة مرور المجموعة" : "Group Password");
        pwInput.setSingleLine(true);

        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(pwInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle("🔒 " + group.name)
                .setMessage(ar ? "أدخل كلمة مرور المجموعة لفتح قناة الغرفة:" : "Enter the group password to unlock and join:")
                .setView(container)
                .setPositiveButton(ar ? "دخول" : "Enter", (d, w) -> {
                    String pw = pwInput.getText().toString().trim();
                    if (RoomCryptoUtils.verifyPublicGroupPassword(group.seed, pw, group.verifier)) {
                        String derivedTopicKey = RoomCryptoUtils.deriveProtectedGroupTopicKey(group.seed, pw);
                        mqttManager.joinRoom(derivedTopicKey, group.name, null);
                        openRoomActivity(derivedTopicKey, group.name, null);
                    } else {
                        Toast.makeText(this, ar ? "كلمة المرور غير صحيحة!" : "Incorrect password!", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(ar ? "إلغاء" : "Cancel", null)
                .show();
    }

    // =========================================================================
    // ⋮ MORE SHEET (Fixes v3.1 Bug #1: opaque/frosted palette.sheetBg in all themes)
    // =========================================================================

    private void showMoreSheet() {
        boolean ar = prefs.isArabic();
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_RunApp);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(16), dp(20), dp(24));
        LocaleHelper.applyViewDirection(container, ar);

        // Drag handle pill at top of sheet
        View handle = new View(this);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(dp(38), dp(4));
        hLp.gravity = Gravity.CENTER_HORIZONTAL;
        hLp.bottomMargin = dp(12);
        handle.setLayoutParams(hLp);
        GradientDrawable hBg = new GradientDrawable();
        hBg.setCornerRadius(dp(2));
        hBg.setColor(palette.panelHover);
        handle.setBackground(hBg);
        container.addView(handle);

        TextView title = new TextView(this);
        title.setText(ar ? "المزيد" : "More");
        title.setTextColor(palette.text);
        title.setTextSize(18f);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setPadding(dp(6), 0, dp(6), dp(8));
        container.addView(title);

        if (currentDockTab != 0 || showingArchivedRooms) {
            addMoreRow(container, "🏠", ar ? "العودة للرئيسية (غرفك)" : "Back to Your Rooms", dialog, () -> {
                showingArchivedRooms = false;
                switchDockWorkspace(0);
                refreshRecentRoomsUI();
            });
        }
        addMoreRow(container, "🌐", ar ? "المجموعات العامة" : "Public Groups", dialog, () -> cardPublicGroups.performClick());
        addMoreRow(container, "💡", ar ? "لوحة المقترحات" : "Feedback Board", dialog, () -> cardFeedbackQuick.performClick());
        addMoreRow(container, "🗄", ar ? "الأرشيف (الغرف المؤرشفة)" : "Archived Rooms", dialog, () -> {
            switchDockWorkspace(0);
            showingArchivedRooms = true;
            refreshRecentRoomsUI();
        });
        addMoreRow(container, "🔖", ar ? "محفوظاتي (My Saved)" : "My Saved Messages", dialog, () -> switchDockWorkspace(3));
        addMoreRow(container, "👥", ar ? "المتصلون والأصدقاء" : "Online People & Friends", dialog, () -> switchDockWorkspace(2));
        addMoreRow(container, "🔗", ar ? "ربط / نقل الحساب" : "Pair / Transfer Account", dialog, () -> btnAccountPairing.performClick());
        addMoreRow(container, "🌍", ar ? "اللغة: Switch to English" : "Language: التبديل إلى العربية", dialog, () -> btnQuickLangToggle.performClick());
        addMoreRow(container, "🎨", ar ? "المظهر والملف الشخصي" : "Theme & Profile", dialog, this::showLiveThemeAndSettingsSheet);

        dialog.setContentView(container);
        ThemeHelper.prepareBottomSheet(dialog, container, palette);
        MotionHelper.animatePanelEntrance(container, prefs.isLiteModeEnabled());
        dialog.show();
    }

    private void addMoreRow(LinearLayout container, String icon, String label,
                            BottomSheetDialog dialog, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(12), dp(8), dp(12));
        row.setClickable(true);
        row.setFocusable(true);
        TypedValue ripple = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
            row.setBackgroundResource(ripple.resourceId);
        }

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(18f);
        row.addView(iconView);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(palette.text);
        labelView.setTextSize(15f);
        labelView.setTypeface(labelView.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(14));
        labelView.setLayoutParams(lp);
        row.addView(labelView);

        row.setOnClickListener(v -> {
            FeedbackTouchHelper.onGestureTick(this, prefs, v);
            dialog.dismiss();
            action.run();
        });
        container.addView(row);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    // =========================================================================
    // #22: Live Theme Preview & Settings Sheet (Fixes v3.1 Bug #1 & Bug #2)
    // =========================================================================

    private void showLiveThemeAndSettingsSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_RunApp);
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_theme_live_preview, null);
        LocaleHelper.applyViewDirection(root, prefs.isArabic());
        dialog.setContentView(root);

        NestedScrollView scrollSheet = root.findViewById(R.id.scrollSettingsSheet);
        LinearLayout containerSettingsContent = root.findViewById(R.id.layoutSettingsContainer);
        TextView tvSheetTitle = root.findViewById(R.id.tvSettingsSheetTitle);
        TextView tvSheetSub = root.findViewById(R.id.tvSettingsSheetSub);
        ImageButton btnClose = root.findViewById(R.id.btnCloseSettingsSheet);

        // Live UI Preview Surface Views
        MaterialCardView cardLiveSurface = root.findViewById(R.id.cardLivePreviewSurface);
        TextView tvPreviewLogo = root.findViewById(R.id.tvPreviewRunLogo);
        View viewPreviewHighlighter = root.findViewById(R.id.viewPreviewHighlighter);
        TextView tvPreviewOrb = root.findViewById(R.id.tvPreviewAvatarOrb);
        MaterialCardView cardPreviewRoom = root.findViewById(R.id.cardPreviewRoom);
        TextView tvPreviewRoomTitle = root.findViewById(R.id.tvPreviewRoomTitle);
        TextView tvPreviewRoomSub = root.findViewById(R.id.tvPreviewRoomSub);
        TextView tvPreviewOnlinePill = root.findViewById(R.id.tvPreviewOnlinePill);
        MaterialCardView cardPreviewIncoming = root.findViewById(R.id.cardPreviewIncomingBubble);
        TextView tvPreviewIncomingAuthor = root.findViewById(R.id.tvPreviewIncomingAuthor);
        TextView tvPreviewIncomingText = root.findViewById(R.id.tvPreviewIncomingText);
        MaterialCardView cardPreviewOutgoing = root.findViewById(R.id.cardPreviewOutgoingBubble);
        TextView tvPreviewOutgoingAuthor = root.findViewById(R.id.tvPreviewOutgoingAuthor);
        TextView tvPreviewOutgoingText = root.findViewById(R.id.tvPreviewOutgoingText);
        MaterialCardView cardPreviewTask = root.findViewById(R.id.cardPreviewTask);
        CheckBox cbPreviewTask = root.findViewById(R.id.cbPreviewTask);
        TextView tvPreviewTaskText = root.findViewById(R.id.tvPreviewTaskText);

        // 5 Selectable Theme Family Cards + Dark Mode switch (7 themes total)
        MaterialCardView cardTheme1 = root.findViewById(R.id.cardThemeWarmPaper);
        MaterialCardView cardTheme2 = root.findViewById(R.id.cardThemeWarmPaperDark);
        MaterialCardView cardTheme3 = root.findViewById(R.id.cardThemeCleanAir);
        MaterialCardView cardTheme4 = root.findViewById(R.id.cardThemeDeepNight);
        MaterialCardView cardTheme5 = root.findViewById(R.id.cardThemeGlass);
        TextView tvCheck1 = root.findViewById(R.id.tvThemeCheck1);
        TextView tvCheck2 = root.findViewById(R.id.tvThemeCheck2);
        TextView tvCheck3 = root.findViewById(R.id.tvThemeCheck3);
        TextView tvCheck4 = root.findViewById(R.id.tvThemeCheck4);
        TextView tvCheck5 = root.findViewById(R.id.tvThemeCheck5);

        TextView tvLivePreviewSectionLabel = root.findViewById(R.id.tvLivePreviewSectionLabel);
        TextView tvThemeSectionTitle = root.findViewById(R.id.tvThemeSectionTitle);
        TextView tvProfileSectionHeader = root.findViewById(R.id.tvProfileSectionLabel);
        TextView tvExpSectionHeader = root.findViewById(R.id.tvLanguageSectionLabel);

        // Profile & Experience Inputs
        MaterialButton btnChangeAvatar = root.findViewById(R.id.btnChangeAvatar);
        MaterialButton btnOpenPairing = root.findViewById(R.id.btnOpenPairingFromSettings);
        EditText etName = root.findViewById(R.id.etSettingsDisplayName);
        EditText etBio = root.findViewById(R.id.etSettingsBio);
        MaterialButton btnLangEn = root.findViewById(R.id.btnLangEnglish);
        MaterialButton btnLangAr = root.findViewById(R.id.btnLangArabic);
        CheckBox cbTouch = root.findViewById(R.id.cbTouchFeedback);
        CheckBox cbLite = root.findViewById(R.id.cbLiteMode);
        CheckBox cbDark = root.findViewById(R.id.cbDarkModeVariant);
        TextView tvLiteInfo = root.findViewById(R.id.tvLiteModeAutoInfo);
        CheckBox cbNotify = root.findViewById(R.id.cbNotificationsEnabled);
        CheckBox cbTranslate = root.findViewById(R.id.cbAutoTranslateEnabled);
        MaterialButton btnCancel = root.findViewById(R.id.btnCancelSettings);
        MaterialButton btnApply = root.findViewById(R.id.btnApplySettings);

        etName.setText(prefs.getUserName());
        etBio.setText(prefs.getUserBio());
        cbTouch.setChecked(prefs.isTouchFeedbackEnabled());
        cbLite.setChecked(prefs.isLiteModeEnabled());
        tvLiteInfo.setText(DeviceCapabilityHelper.getDeviceStatusSummary(this, prefs));
        cbNotify.setChecked(prefs.isNotificationsEnabled());
        cbTranslate.setChecked(prefs.isAutoTranslateEnabled());

        final String[] previewedThemeKey = {prefs.getThemeMode()};
        final String[] previewedLang = {prefs.getUiLanguage()};
        final float d = getResources().getDisplayMetrics().density;

        // Live UI Surface updater (#22) — uses fresh GradientDrawables and opaque card fills (fixes Bug #2)
        Runnable updateLivePreviewSurface = () -> {
            ThemeHelper.Palette p = ThemeHelper.getPalette(previewedThemeKey[0]);
            ThemeHelper.applySheetSurface(scrollSheet, p);
            if (containerSettingsContent != null) {
                containerSettingsContent.setBackgroundColor(Color.TRANSPARENT);
            }
            tvSheetTitle.setTextColor(p.text);
            tvSheetSub.setTextColor(p.muted);
            btnClose.setColorFilter(p.text);

            cardLiveSurface.setCardBackgroundColor(p.bgElevated);
            cardLiveSurface.setStrokeColor(p.panelHover);
            tvPreviewLogo.setText(buildBrandLogoSpannable(p));

            GradientDrawable previewHiBg = new GradientDrawable();
            previewHiBg.setCornerRadius(4 * d);
            previewHiBg.setColor(p.highlighter);
            viewPreviewHighlighter.setBackground(previewHiBg);

            GradientDrawable previewOrbBg = new GradientDrawable();
            previewOrbBg.setShape(GradientDrawable.OVAL);
            previewOrbBg.setColor(p.accent);
            tvPreviewOrb.setBackground(previewOrbBg);
            tvPreviewOrb.setTextColor(p.accentInk);

            cardPreviewRoom.setCardBackgroundColor(p.cardOpaque);
            cardPreviewRoom.setStrokeColor(p.panelHover);
            tvPreviewRoomTitle.setTextColor(p.text);
            tvPreviewRoomSub.setTextColor(p.muted);

            GradientDrawable onlinePillBg = new GradientDrawable();
            onlinePillBg.setCornerRadius(12 * d);
            onlinePillBg.setColor(p.highlighter);
            tvPreviewOnlinePill.setBackground(onlinePillBg);
            tvPreviewOnlinePill.setTextColor(p.accentInk);

            cardPreviewIncoming.setCardBackgroundColor(p.cardOpaque);
            cardPreviewIncoming.setStrokeColor(p.panelHover);
            tvPreviewIncomingAuthor.setTextColor(p.text);
            tvPreviewIncomingText.setTextColor(p.text);

            cardPreviewOutgoing.setCardBackgroundColor(p.ownBubble);
            cardPreviewOutgoing.setStrokeColor(p.ownBubbleStroke);
            tvPreviewOutgoingAuthor.setTextColor(p.ownBubbleText);
            tvPreviewOutgoingText.setTextColor(p.ownBubbleText);

            cardPreviewTask.setCardBackgroundColor(p.cardOpaque);
            cardPreviewTask.setStrokeColor(p.panelHover);
            cbPreviewTask.setButtonTintList(ColorStateList.valueOf(p.accent));
            tvPreviewTaskText.setTextColor(p.text);

            MaterialCardView[] cards = {cardTheme1, cardTheme2, cardTheme3, cardTheme4, cardTheme5};
            TextView[] checks = {tvCheck1, tvCheck2, tvCheck3, tvCheck4, tvCheck5};
            String[] keys = {
                    ThemeHelper.THEME_WARM_PAPER,
                    ThemeHelper.THEME_WARM_PAPER_DARK,
                    ThemeHelper.THEME_CLEAN_AIR,
                    ThemeHelper.THEME_DEEP_NIGHT,
                    ThemeHelper.THEME_GLASS
            };
            String previewedBase = ThemeHelper.lightVariantOf(previewedThemeKey[0]);
            boolean previewedIsCardKey = false;
            for (String k : keys) if (k.equals(previewedThemeKey[0])) previewedIsCardKey = true;
            for (int i = 0; i < cards.length; i++) {
                boolean selected = keys[i].equals(previewedThemeKey[0])
                        || (!previewedIsCardKey && keys[i].equals(previewedBase));
                cards[i].setCardBackgroundColor(p.cardOpaque);
                cards[i].setStrokeColor(selected ? p.accent : p.panelHover);
                cards[i].setStrokeWidth((int) ((selected ? 2.2f : 1f) * d));

                if (selected) {
                    GradientDrawable checkBg = new GradientDrawable();
                    checkBg.setShape(GradientDrawable.OVAL);
                    checkBg.setColor(p.accent);
                    checks[i].setBackground(checkBg);
                    checks[i].setTextColor(p.accentInk);
                    checks[i].setVisibility(View.VISIBLE);
                } else {
                    checks[i].setVisibility(View.GONE);
                }
            }

            ((TextView) root.findViewById(R.id.tvThemeName1)).setTextColor(p.text);
            ((TextView) root.findViewById(R.id.tvThemeName2)).setTextColor(p.text);
            ((TextView) root.findViewById(R.id.tvThemeName3)).setTextColor(p.text);
            ((TextView) root.findViewById(R.id.tvThemeName4)).setTextColor(p.text);
            ((TextView) root.findViewById(R.id.tvThemeName5)).setTextColor(p.text);
            ((TextView) root.findViewById(R.id.tvThemeDesc1)).setTextColor(p.muted);
            ((TextView) root.findViewById(R.id.tvThemeDesc2)).setTextColor(p.muted);
            ((TextView) root.findViewById(R.id.tvThemeDesc3)).setTextColor(p.muted);
            ((TextView) root.findViewById(R.id.tvThemeDesc4)).setTextColor(p.muted);
            ((TextView) root.findViewById(R.id.tvThemeDesc5)).setTextColor(p.muted);

            if (tvLivePreviewSectionLabel != null) tvLivePreviewSectionLabel.setTextColor(p.muted);
            if (tvThemeSectionTitle != null) tvThemeSectionTitle.setTextColor(p.muted);
            if (tvProfileSectionHeader != null) tvProfileSectionHeader.setTextColor(p.muted);
            if (tvExpSectionHeader != null) tvExpSectionHeader.setTextColor(p.muted);

            etName.setTextColor(p.text);
            etName.setHintTextColor(p.subtle);
            etBio.setTextColor(p.text);
            etBio.setHintTextColor(p.subtle);
            cbTouch.setTextColor(p.text);
            cbTouch.setButtonTintList(ColorStateList.valueOf(p.accent));
            cbLite.setTextColor(p.text);
            cbLite.setButtonTintList(ColorStateList.valueOf(p.accent));
            cbDark.setTextColor(p.text);
            cbDark.setButtonTintList(ColorStateList.valueOf(p.accent));
            cbDark.setChecked(ThemeHelper.isDarkKey(previewedThemeKey[0]));
            tvLiteInfo.setTextColor(p.muted);
            cbNotify.setTextColor(p.text);
            cbNotify.setButtonTintList(ColorStateList.valueOf(p.accent));
            cbTranslate.setTextColor(p.text);
            cbTranslate.setButtonTintList(ColorStateList.valueOf(p.accent));

            btnChangeAvatar.setBackgroundTintList(ColorStateList.valueOf(p.panelSoft));
            btnChangeAvatar.setTextColor(p.text);
            btnChangeAvatar.setIconTint(ColorStateList.valueOf(p.text));
            btnOpenPairing.setBackgroundTintList(ColorStateList.valueOf(p.panelSoft));
            btnOpenPairing.setTextColor(p.text);
            btnOpenPairing.setIconTint(ColorStateList.valueOf(p.text));

            boolean isAr = "ar".equalsIgnoreCase(previewedLang[0]);
            btnLangEn.setBackgroundTintList(ColorStateList.valueOf(!isAr ? p.accent : p.panelSoft));
            btnLangEn.setTextColor(!isAr ? p.accentInk : p.text);
            btnLangAr.setBackgroundTintList(ColorStateList.valueOf(isAr ? p.accent : p.panelSoft));
            btnLangAr.setTextColor(isAr ? p.accentInk : p.text);

            btnApply.setBackgroundTintList(ColorStateList.valueOf(p.accent));
            btnApply.setTextColor(p.accentInk);
            btnCancel.setBackgroundTintList(ColorStateList.valueOf(p.panelSoft));
            btnCancel.setTextColor(p.text);
        };

        cardTheme1.setOnClickListener(v -> {
            previewedThemeKey[0] = ThemeHelper.THEME_WARM_PAPER;
            FeedbackTouchHelper.onGestureTick(this, prefs, v);
            updateLivePreviewSurface.run();
        });
        cardTheme2.setOnClickListener(v -> {
            previewedThemeKey[0] = ThemeHelper.THEME_WARM_PAPER_DARK;
            FeedbackTouchHelper.onGestureTick(this, prefs, v);
            updateLivePreviewSurface.run();
        });
        cardTheme3.setOnClickListener(v -> {
            previewedThemeKey[0] = ThemeHelper.THEME_CLEAN_AIR;
            FeedbackTouchHelper.onGestureTick(this, prefs, v);
            updateLivePreviewSurface.run();
        });
        cardTheme4.setOnClickListener(v -> {
            previewedThemeKey[0] = ThemeHelper.THEME_DEEP_NIGHT;
            FeedbackTouchHelper.onGestureTick(this, prefs, v);
            updateLivePreviewSurface.run();
        });
        cardTheme5.setOnClickListener(v -> {
            previewedThemeKey[0] = ThemeHelper.THEME_GLASS;
            FeedbackTouchHelper.onGestureTick(this, prefs, v);
            updateLivePreviewSurface.run();
        });

        cbDark.setOnClickListener(v -> {
            boolean wantDark = cbDark.isChecked();
            previewedThemeKey[0] = wantDark
                    ? ThemeHelper.darkVariantOf(previewedThemeKey[0])
                    : ThemeHelper.lightVariantOf(previewedThemeKey[0]);
            FeedbackTouchHelper.onGestureTick(this, prefs, v);
            updateLivePreviewSurface.run();
        });

        btnLangEn.setOnClickListener(v -> {
            previewedLang[0] = "en";
            updateLivePreviewSurface.run();
        });
        btnLangAr.setOnClickListener(v -> {
            previewedLang[0] = "ar";
            updateLivePreviewSurface.run();
        });

        btnChangeAvatar.setOnClickListener(v -> avatarPickerLauncher.launch("image/*"));
        btnOpenPairing.setOnClickListener(v -> {
            dialog.dismiss();
            showAccountPairingDialog();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnApply.setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            if (!newName.isEmpty()) prefs.setUserName(newName);
            prefs.setUserBio(etBio.getText().toString().trim());
            prefs.setTouchFeedbackEnabled(cbTouch.isChecked());

            boolean currentLite = prefs.isLiteModeEnabled();
            if (cbLite.isChecked() != currentLite) {
                prefs.setLiteModePref(cbLite.isChecked() ? "on" : "off");
            }
            prefs.setNotificationsEnabled(cbNotify.isChecked());
            prefs.setAutoTranslateEnabled(cbTranslate.isChecked());
            prefs.setUiLanguage(previewedLang[0]);
            prefs.setThemeMode(previewedThemeKey[0]);

            ThemeHelper.applyAppNightMode(previewedThemeKey[0]);
            LocaleHelper.applyLocaleAndLayoutDirection(this, prefs);
            mqttManager.syncLocalProfileToActiveRoom();

            applyCurrentTheme();
            applyLocalizedLabels();
            populateSavedUser();
            refreshRecentRoomsUI();
            refreshOnlineOrbsRow(mqttManager.getOnlineUsersList());
            refreshSavedMessagesWorkspace();
            dialog.dismiss();
        });

        ThemeHelper.prepareBottomSheet(dialog, scrollSheet, ThemeHelper.getPalette(previewedThemeKey[0]));
        updateLivePreviewSurface.run();
        dialog.show();
    }

    // =========================================================================
    // #25 & #30: Account Pairing & Phone Transfer Sheet
    // =========================================================================

    private void showAccountPairingDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_RunApp);
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_account_pairing, null);
        LocaleHelper.applyViewDirection(root, prefs.isArabic());
        dialog.setContentView(root);

        NestedScrollView scroll = root.findViewById(R.id.scrollPairingSheet);
        MaterialCardView cardGen = root.findViewById(R.id.cardGenerateCode);
        MaterialCardView cardRedeem = root.findViewById(R.id.cardRedeemCode);
        TextView tvTitle = root.findViewById(R.id.tvPairingTitle);
        TextView tvSub = root.findViewById(R.id.tvPairingSubtitle);
        ImageButton btnClose = root.findViewById(R.id.btnClosePairing);
        TextView tvGeneratedCode = root.findViewById(R.id.tvGeneratedPairingCode);
        MaterialButton btnGen = root.findViewById(R.id.btnGenerateTransferCode);
        MaterialButton btnCopyCode = root.findViewById(R.id.btnCopyTransferCode);
        EditText etRedeem = root.findViewById(R.id.etRedeemTransferCode);
        MaterialButton btnRedeem = root.findViewById(R.id.btnRedeemTransferCode);
        MaterialButton btnExport = root.findViewById(R.id.btnExportBackupJson);
        MaterialButton btnImport = root.findViewById(R.id.btnImportBackupClipboard);

        ThemeHelper.prepareBottomSheet(dialog, scroll, palette);
        cardGen.setCardBackgroundColor(palette.cardOpaque);
        cardGen.setStrokeColor(palette.panelHover);
        cardRedeem.setCardBackgroundColor(palette.cardOpaque);
        cardRedeem.setStrokeColor(palette.panelHover);
        tvTitle.setTextColor(palette.text);
        tvSub.setTextColor(palette.muted);
        tvGeneratedCode.setTextColor(palette.text);
        etRedeem.setTextColor(palette.text);
        etRedeem.setHintTextColor(palette.subtle);
        btnGen.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        btnGen.setTextColor(palette.accentInk);
        btnRedeem.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        btnRedeem.setTextColor(palette.accentInk);

        boolean ar = prefs.isArabic();
        final String[] lastCode = {""};

        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnGen.setOnClickListener(v -> {
            btnGen.setEnabled(false);
            tvGeneratedCode.setText("•••");
            mqttManager.publishAccountTransferCode(new MqttRoomManager.TransferPublishCallback() {
                @Override
                public void onCodeGenerated(String code) {
                    btnGen.setEnabled(true);
                    lastCode[0] = code;
                    tvGeneratedCode.setText(code);
                    FeedbackTouchHelper.onMessageSent(MainActivity.this, prefs, btnGen);
                    Toast.makeText(MainActivity.this,
                            ar ? ("تم إنشاء رمز النقل والربط: " + code) : ("Transfer & pairing code ready: " + code),
                            Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String message) {
                    btnGen.setEnabled(true);
                    tvGeneratedCode.setText("------");
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        });

        btnCopyCode.setOnClickListener(v -> {
            if (lastCode[0].isEmpty()) {
                Toast.makeText(this, ar ? "اضغط إنشاء الرمز أولاً" : "Generate a code first", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Run Pairing Code", lastCode[0]));
                Toast.makeText(this, ar ? "تم نسخ الرمز" : "Copied pairing code", Toast.LENGTH_SHORT).show();
            }
        });

        btnRedeem.setOnClickListener(v -> {
            String input = etRedeem.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, ar ? "أدخل الرمز المكون من 6 أحرف" : "Enter the 6-character code", Toast.LENGTH_SHORT).show();
                return;
            }
            btnRedeem.setEnabled(false);
            mqttManager.redeemAccountTransferCode(input, new MqttRoomManager.TransferRedeemCallback() {
                @Override
                public void onSuccess() {
                    btnRedeem.setEnabled(true);
                    LocaleHelper.applyLocaleAndLayoutDirection(MainActivity.this, prefs);
                    applyCurrentTheme();
                    applyLocalizedLabels();
                    populateSavedUser();
                    refreshRecentRoomsUI();
                    refreshSavedMessagesWorkspace();
                    Toast.makeText(MainActivity.this,
                            ar ? "تم ربط الحساب ونقل الغرف والإعدادات بنجاح!" : "Account linked & rooms transferred!",
                            Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                }

                @Override
                public void onError(String message) {
                    btnRedeem.setEnabled(true);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        });

        btnExport.setOnClickListener(v -> {
            String backupJson = prefs.exportBackupJson();
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Run Backup JSON", backupJson));
                Toast.makeText(this, ar ? "تم نسخ النسخة الاحتياطية إلى الحافظة" : "Backup copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
        });

        btnImport.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence cs = cm.getPrimaryClip().getItemAt(0).getText();
                if (cs != null && prefs.importTransferOrBackupJson(cs.toString(), true)) {
                    mqttManager.refreshSubscriptionsForCurrentUser();
                    applyCurrentTheme();
                    applyLocalizedLabels();
                    populateSavedUser();
                    refreshRecentRoomsUI();
                    refreshSavedMessagesWorkspace();
                    Toast.makeText(this, ar ? "تم استيراد النسخة الاحتياطية بنجاح!" : "Backup imported!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    return;
                }
            }
            Toast.makeText(this, ar ? "لا توجد نسخة احتياطية صالحة في الحافظة" : "No valid backup JSON in clipboard", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void showConnectionStatusToast() {
        boolean ar = prefs.isArabic();
        MqttRoomManager.ConnectionQuality q = mqttManager.getConnectionQuality();
        String msg;
        if (q == MqttRoomManager.ConnectionQuality.CONNECTED) {
            msg = ar ? "متصل بشكل مستقر عبر وسيط MQTT العام" : "Connected & synced via public MQTT relay";
        } else if (q == MqttRoomManager.ConnectionQuality.WEAK) {
            msg = ar ? "الإشارة ضعيفة قليلاً، ستتم المزامنة تلقائياً" : "Signal is weak — messages sync automatically";
        } else {
            msg = ar ? "جارٍ إعادة الاتصال تلقائياً في الخلفية..." : "Reconnecting automatically in the background…";
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private SpannableString buildBrandLogoSpannable(ThemeHelper.Palette p) {
        SpannableString ss = new SpannableString("Run.");
        ss.setSpan(new ForegroundColorSpan(p.text), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(p.accent), 3, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ss;
    }

    private void applyLocalizedLabels() {
        boolean ar = prefs.isArabic();
        btnQuickLangToggle.setText(ar ? "EN" : "عربي");
        etQuickCodeOrSearch.setHint(ar ? "أدخل رمز غرفة أو أنشئ / ابحث…" : "Enter room code or create / filter…");
        btnQuickCodeEnter.setText(ar ? "انضمام ←" : "Join →");
        btnSegmentCreate.setText(ar ? "إنشاء غرفة" : "Create Room");
        btnSegmentJoin.setText(ar ? "انضمام برمز" : "Join by Code");
        etUserName.setHint(ar ? "اسم العرض الخاص بك" : "Your display name");
        etRoomName.setHint(ar ? "اسم الغرفة (مثال: Study Group D5)" : "Room name (e.g. Study Group D5)");
        cbPublicGroup.setText(ar ? "إدراج في دليل المجموعات العامة" : "List in Public Groups Directory");
        etPublicPassword.setHint(ar ? "كلمة المرور (اختياري)" : "Password protection (optional)");
        btnCreateRoom.setText(ar ? "إنشاء وفتح الغرفة" : "Create & Open Room");
        etJoinRoomCode.setHint(ar ? "رمز الغرفة (6 أو 12 حرفاً) أو الرابط" : "Enter 6 or 12 character code or link");
        btnJoinByCodeSubmit.setText(ar ? "انضمام للغرفة" : "Join Room Now");

        tvRecentTitle.setText(showingArchivedRooms ? (ar ? "الأرشيف" : "Archived") : (ar ? "غرفك" : "Your rooms"));
        btnFilterActiveRooms.setText(ar ? "← الغرف النشطة" : "← Back to Active");
        btnFilterArchivedRooms.setText(ar ? "المؤرشفة" : "Archived");
        btnEditProfile.setText(ar ? "المظهر والملف" : "Theme & Profile");

        tvTilePublicTitle.setText(ar ? "🌐 المجموعات العامة" : "🌐 Public Groups");
        tvTileFeedbackTitle.setText(ar ? "💡 لوحة المقترحات" : "💡 Feedback Board");
        tvTileFeedbackSub.setText(ar ? "شارك وصوّت للأفكار" : "Vote & suggest ideas");

        btnDockSpaces.setText(ar ? "غرفك" : "Spaces");
        btnDockDiscover.setText(ar ? "استكشاف" : "Discover");
        btnDockPeople.setText(ar ? "المتصلون" : "People");
        btnDockSaved.setText(ar ? "محفوظاتي" : "Saved");

        btnDiscoverTabPublic.setText(ar ? "🌐 المجموعات العامة" : "🌐 Public Groups");
        btnDiscoverTabFeedback.setText(ar ? "💡 المقترحات" : "💡 Feedback Board");
        etSearchPublicGroups.setHint(ar ? "ابحث في المجموعات العامة…" : "Search public groups…");
        btnNewFeedbackInline.setText(ar ? "+ مقترح" : "+ Suggest");

        tvSavedSectionTitle.setText(ar ? "محفوظاتي" : "My saved");
        tvSavedSectionSub.setText(ar ? "رسائلك المحفوظة بشكل خاص من جميع الغرف" : "Private bookmarks gathered from all your rooms");
        btnPairDeviceFromSaved.setText(ar ? "ربط / نقل" : "Pair / Transfer");
        tvSavedEmptyText.setText(ar
                ? "لا توجد رسائل محفوظة بعد. اضغط مطولاً على أي رسالة داخل أي غرفة لاختيار حفظ."
                : "No saved messages yet. Long-press any message in a room and tap Save.");

        updateConnectionQualityUI(mqttManager.getConnectionQuality());
    }

    private void applyCurrentTheme() {
        palette = ThemeHelper.getPalette(prefs.getThemeMode());
        MotionHelper.setThemeProfile(palette.themeKey);
        boolean glassTheme = ThemeHelper.isGlassTheme(palette.themeKey);
        float d = getResources().getDisplayMetrics().density;

        if (glassTheme) {
            GradientDrawable glassBg = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{
                            ColorUtils.blendARGB(palette.bg, Color.WHITE, 0.13f),
                            palette.bg,
                            ColorUtils.blendARGB(palette.bg, Color.BLACK, 0.35f)
                    });
            rootMainFrame.setBackground(glassBg);
        } else {
            rootMainFrame.setBackgroundColor(palette.bg);
        }

        // "Run." brand logo with lime dot (Matches Design A 7196.png)
        tvAppTitle.setText(buildBrandLogoSpannable(palette));
        btnMoreMenu.setColorFilter(palette.text);
        btnAccountPairing.setColorFilter(palette.text);
        btnThemeSettings.setColorFilter(palette.text);

        btnQuickLangToggle.setBackgroundTintList(ColorStateList.valueOf(palette.panelSoft));
        btnQuickLangToggle.setTextColor(palette.text);

        // Top-right circular 'T' avatar orb (fresh GradientDrawable)
        GradientDrawable avatarOrbBg = new GradientDrawable();
        avatarOrbBg.setShape(GradientDrawable.OVAL);
        avatarOrbBg.setColor(palette.accent);
        tvMainAvatarFallback.setBackground(avatarOrbBg);
        tvMainAvatarFallback.setTextColor(palette.accentInk);

        // One-line greeting directly on canvas
        tvGreetingName.setTextColor(palette.muted);

        // Slim Join / Create bar
        cardQuickCodeBar.setCardBackgroundColor(palette.panel);
        cardQuickCodeBar.setStrokeColor(palette.panelHover);
        cardQuickCodeBar.setStrokeWidth((int) (1 * d));
        etQuickCodeOrSearch.setTextColor(palette.text);
        etQuickCodeOrSearch.setHintTextColor(palette.subtle);
        btnQuickCodeEnter.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        btnQuickCodeEnter.setTextColor(palette.accentInk);

        mainCard.setCardBackgroundColor(palette.panel);
        mainCard.setStrokeColor(palette.panelHover);
        mainCard.setStrokeWidth((int) (1 * d));
        etUserName.setTextColor(palette.text);
        etUserName.setHintTextColor(palette.subtle);
        etRoomName.setTextColor(palette.text);
        etRoomName.setHintTextColor(palette.subtle);
        cbPublicGroup.setTextColor(palette.text);
        etPublicPassword.setTextColor(palette.text);
        etPublicPassword.setHintTextColor(palette.subtle);
        etJoinRoomCode.setTextColor(palette.text);
        etJoinRoomCode.setHintTextColor(palette.subtle);
        btnCreateRoom.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        btnCreateRoom.setTextColor(palette.accentInk);
        btnJoinByCodeSubmit.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        btnJoinByCodeSubmit.setTextColor(palette.accentInk);

        // "Your rooms" overlapping highlighter bar (fresh GradientDrawable)
        tvRecentTitle.setTextColor(palette.text);
        GradientDrawable hiBg = new GradientDrawable();
        hiBg.setCornerRadius(4.5f * d);
        hiBg.setColor(palette.highlighter);
        viewYourRoomsHighlighter.setBackground(hiBg);

        tvRecentCount.setTextColor(palette.muted);
        cardEmptyRoomsPrompt.setCardBackgroundColor(palette.panel);
        cardEmptyRoomsPrompt.setStrokeColor(palette.panelHover);
        tvEmptyRoomsText.setTextColor(palette.muted);

        tvSavedSectionTitle.setTextColor(palette.text);
        GradientDrawable savedHiBg = new GradientDrawable();
        savedHiBg.setCornerRadius(4.5f * d);
        savedHiBg.setColor(palette.highlighter);
        viewSavedHighlighter.setBackground(savedHiBg);
        tvSavedSectionSub.setTextColor(palette.muted);
        btnPairDeviceFromSaved.setBackgroundTintList(ColorStateList.valueOf(palette.panelSoft));
        btnPairDeviceFromSaved.setTextColor(palette.text);

        if (glassTheme) {
            fabNewSpace.setBackgroundTintList(ColorStateList.valueOf(Color.argb(64, 255, 255, 255)));
            fabNewSpace.setTextColor(Color.WHITE);
            fabNewSpace.setStrokeColor(ColorStateList.valueOf(Color.argb(150, 255, 255, 255)));
            fabNewSpace.setStrokeWidth((int) (1.2f * d));
            MotionHelper.animateGlassSheen(fabNewSpace, prefs.isLiteModeEnabled());
        } else {
            fabNewSpace.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
            fabNewSpace.setTextColor(palette.accentInk);
            fabNewSpace.setStrokeWidth(0);
        }

        GradientDrawable dockBg = new GradientDrawable();
        dockBg.setCornerRadius(32f * d);
        dockBg.setColor(palette.dockBg);
        if (glassTheme) {
            dockBg.setStroke((int) (1f * d), palette.panelHover);
        }
        bottomNavDock.setBackground(dockBg);
        bottomNavDock.setVisibility((currentDockTab != 0 || glassTheme) ? View.VISIBLE : View.GONE);

        FrameLayout.LayoutParams fabLp = (FrameLayout.LayoutParams) fabNewSpace.getLayoutParams();
        fabLp.bottomMargin = dp(glassTheme ? 76 : 24);
        fabNewSpace.setLayoutParams(fabLp);

        updateDockButtonsVisuals();

        if (getWindow() != null) {
            getWindow().setStatusBarColor(palette.bg);
            getWindow().setNavigationBarColor(palette.bg);
        }
    }

    private void updateConnectionQualityUI(MqttRoomManager.ConnectionQuality quality) {
        boolean ar = prefs.isArabic();
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        if (quality == MqttRoomManager.ConnectionQuality.CONNECTED) {
            dot.setColor(palette.success);
            tvConnectionQuality.setText(ar ? "متصل" : "Connected");
            tvConnectionQuality.setTextColor(palette.muted);
        } else if (quality == MqttRoomManager.ConnectionQuality.WEAK) {
            dot.setColor(palette.warning);
            tvConnectionQuality.setText(ar ? "إشارة ضعيفة" : "Weak signal");
            tvConnectionQuality.setTextColor(palette.warning);
        } else {
            dot.setColor(palette.subtle);
            tvConnectionQuality.setText(ar ? "يعيد الاتصال تلقائياً…" : "Reconnecting…");
            tvConnectionQuality.setTextColor(palette.muted);
        }
        viewConnQualityDot.setBackground(dot);
    }

    // ==========================================
    // Global MQTT Callbacks
    // ==========================================

    @Override
    public void onConnectionChanged(boolean connected, String brokerUrl) {
        runOnUiThread(() -> {
            boolean ar = prefs.isArabic();
            int count = Math.max(1, mqttManager.getGlobalOnlineCount());
            tvOnlineStatus.setText("● " + LocaleHelper.formatNumber(count, ar) + (ar ? " متصل الآن" : " online"));
            tvOnlineStripTitle.setText("● " + LocaleHelper.formatNumber(count, ar) + (ar ? " متصل" : " online"));
            updateConnectionQualityUI(mqttManager.getConnectionQuality());
        });
    }

    @Override
    public void onConnectionQualityChanged(MqttRoomManager.ConnectionQuality quality, String brokerUrl) {
        runOnUiThread(() -> updateConnectionQualityUI(quality));
    }

    @Override
    public void onGlobalPresenceChanged(int onlineCount) {
        runOnUiThread(() -> {
            boolean ar = prefs.isArabic();
            int c = Math.max(1, onlineCount);
            tvOnlineStatus.setText("● " + LocaleHelper.formatNumber(c, ar) + (ar ? " متصل الآن" : " online"));
            tvOnlineStripTitle.setText("● " + LocaleHelper.formatNumber(c, ar) + (ar ? " متصل" : " online"));
        });
    }

    @Override
    public void onOnlineUsersChanged(List<OnlineUser> onlineUsers) {
        runOnUiThread(() -> {
            refreshOnlineOrbsRow(onlineUsers);
            if (currentDockTab == 2) {
                refreshPeopleWorkspaceUI();
            }
        });
    }

    @Override
    public void onDirectoryUpdated(DirectoryData directory) {
        runOnUiThread(() -> {
            boolean ar = prefs.isArabic();
            int count = (directory != null && directory.groups != null) ? directory.groups.size() : 0;
            tvPublicCount.setText(ar
                    ? (LocaleHelper.formatNumber(count, true) + " مجموعة عامة")
                    : (count + (count == 1 ? " public group" : " public groups")));
            if (currentDockTab == 1 && !discoverShowingFeedback) {
                refreshPublicGroupsListUI();
            }
        });
    }

    @Override
    public void onFeedbackBoardUpdated(FeedbackBoardData board) {
        runOnUiThread(() -> {
            if (currentDockTab == 1 && discoverShowingFeedback) {
                refreshInlineFeedbackUI();
            }
        });
    }

    @Override
    public void onSignalReceived(SignalMessage signal) {
        runOnUiThread(() -> {
            boolean ar = prefs.isArabic();
            if (SignalMessage.TYPE_ROOM_INVITE.equals(signal.type)) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(ar ? "دعوة للانضمام إلى غرفة" : "Room Invitation")
                        .setMessage(signal.fromUserName + (ar ? " يدعوك للانضمام إلى '" : " invited you to join '")
                                + signal.roomName + "' (#" + signal.roomCode + ")")
                        .setPositiveButton(ar ? "انضمام" : "Join", (d, w) -> {
                            mqttManager.joinRoom(signal.roomCode, signal.roomName, null);
                            openRoomActivity(signal.roomCode, signal.roomName, null);
                        })
                        .setNegativeButton(ar ? "تجاهل" : "Dismiss", null)
                        .show();
            } else if (SignalMessage.TYPE_FRIEND_REQUEST.equals(signal.type)) {
                Toast.makeText(this,
                        ar ? ("طلب صداقة جديد من " + signal.fromUserName) : ("New friend request from " + signal.fromUserName),
                        Toast.LENGTH_SHORT).show();
            }
            if (currentDockTab == 2) {
                refreshPeopleWorkspaceUI();
            }
        });
    }
}
