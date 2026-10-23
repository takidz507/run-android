package com.takidz.run.ui;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import androidx.core.graphics.ColorUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
 * Run v3.0.0 MainActivity:
 * - #14: Genuinely new Warm Paper editorial workspace structure with Floating Pill Bottom Dock
 *        (Spaces · Discover · People · My Saved) and 4 switchable themes.
 * - #15 & #21: Smooth 60fps transitions with automatic Lite Mode adaptation.
 * - #16: Responsive across phones, foldables, and tablets.
 * - #19: Animated Origami splash screen (< 1.1s, tap to skip).
 * - #22: Live theme preview on a real interactive UI surface in Settings before applying.
 * - #23: Smart invite links (HTTPS App Links + runapp:// scheme + deferred first-run clipboard bridge).
 * - #24: Device-serial deduplicated online counter.
 * - #25 & #30: Encrypted 6-character code transfer & Web-App one-account pairing.
 * - #27: Visible timed mute status on room cards.
 * - #28: Full Arabic mode with complete RTL mirroring and Arabic dates/numbers.
 * - #29: Live connection quality indicator (Connected / Weak signal / Reconnecting).
 * - #31: Personal Saved Messages ("My saved") with one-tap jump back to message in room.
 */
public class MainActivity extends AppCompatActivity implements MqttRoomManager.GlobalListener {

    private static final int REQ_POST_NOTIFICATIONS = 301;
    private static boolean splashShownInProcess = false;

    private PrefsManager prefs;
    private MqttRoomManager mqttManager;
    private CatboxUploader catboxUploader;
    private ThemeHelper.Palette palette;

    private int currentDockTab = 0; // 0: Spaces, 1: Discover, 2: People, 3: My Saved
    private boolean showingArchivedRooms = false;
    private boolean discoverShowingFeedback = false;
    private boolean peopleShowingFriends = false;

    // Root & Splash
    private FrameLayout rootMainFrame;
    private OrigamiSplashView origamiSplashOverlay;

    // Top Editorial Header
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

    // Destination 0: Spaces
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

    // Expandable Create / Join Studio Card
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

    // Quick Discover Tiles
    private MaterialCardView cardPublicGroups;
    private TextView tvTilePublicTitle;
    private TextView tvPublicCount;
    private MaterialCardView cardFeedbackQuick;
    private TextView tvTileFeedbackTitle;
    private TextView tvTileFeedbackSub;

    // Destination 1: Discover Workspace
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

    // Destination 2: People Workspace
    private LinearLayout workspacePeople;
    private MaterialButton btnPeopleTabOnline;
    private MaterialButton btnPeopleTabFriends;
    private EditText etSearchPeopleInline;
    private RecyclerView rvPeopleInline;
    private OnlineUsersAdapter inlineOnlineAdapter;
    private FriendsAdapter inlineFriendsAdapter;

    // Destination 3: My Saved Workspace (#31)
    private LinearLayout workspaceSaved;
    private TextView tvSavedSectionTitle;
    private View viewSavedHighlighter;
    private TextView tvSavedSectionSub;
    private MaterialButton btnPairDeviceFromSaved;
    private RecyclerView rvSavedMessages;
    private LinearLayout layoutSavedEmpty;
    private TextView tvSavedEmptyText;
    private SavedMessagesAdapter savedMessagesAdapter;

    // Floating FAB & Bottom Dock
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

        // Check if launched via deep link (#23)
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
            // #23: Deferred deep link check on first run after install
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

        // Segment Switcher: Create vs Join
        btnSegmentCreate.setOnClickListener(v -> switchHeroSegment(true));
        btnSegmentJoin.setOnClickListener(v -> switchHeroSegment(false));

        cbPublicGroup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            etPublicPassword.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Floating Bottom Dock Navigation (#14)
        btnDockSpaces.setOnClickListener(v -> switchDockWorkspace(0));
        btnDockDiscover.setOnClickListener(v -> switchDockWorkspace(1));
        btnDockPeople.setOnClickListener(v -> switchDockWorkspace(2));
        btnDockSaved.setOnClickListener(v -> switchDockWorkspace(3));

        // Floating '+' Button toggles the Create/Join Studio Card
        fabNewSpace.setOnClickListener(v -> {
            FeedbackTouchHelper.onGestureTick(this, prefs, v);
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

        // Quick Code / Filter Bar
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
            String raw = etQuickCodeOrSearch.getText().toString().trim();
            String code = extractCodeFromInputOrLink(raw);
            if (RoomCryptoUtils.isValidRoomCode(code)) {
                ensureDefaultDisplayName();
                etQuickCodeOrSearch.setText("");
                mqttManager.joinRoom(code, "Room " + code, null);
                openRoomActivity(code, "Room " + code, null);
            } else {
                // Open Create/Join studio if not a valid code yet
                mainCard.setVisibility(View.VISIBLE);
                switchHeroSegment(false);
                etJoinRoomCode.setText(raw);
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

        // #29: Calm connection quality status sheet on tap
        pillConnectionQuality.setOnClickListener(v -> showConnectionStatusToast());

        // Online Directory Pill click -> switches to People workspace
        pillOnlineDirectory.setOnClickListener(v -> switchDockWorkspace(2));

        // #28: Quick Language Switcher (EN <-> عربي RTL)
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

        // #25 & #30: Account Pairing & Phone Transfer Dialog
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

        // #22: Live Theme Preview & Profile Settings Sheet
        profileCard.setOnClickListener(v -> showLiveThemeAndSettingsSheet());
        btnEditProfile.setOnClickListener(v -> showLiveThemeAndSettingsSheet());
        btnThemeSettings.setOnClickListener(v -> showLiveThemeAndSettingsSheet());
        btnMoreMenu.setOnClickListener(v -> showMoreSheet());
        layoutAvatarClick.setOnClickListener(v -> showLiveThemeAndSettingsSheet());

        btnCreateRoom.setOnClickListener(v -> onCreateRoomClicked());
        btnJoinByCodeSubmit.setOnClickListener(v -> onJoinRoomClicked());
    }

    private void setupInlineWorkspaces() {
        // 1. Discover Workspace (Public Groups + Feedback Board)
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

        // 2. People Workspace (Online Users + Friends & Invites)
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

        // 3. My Saved Workspace (#31)
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

        rootScroll.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        workspaceDiscover.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        workspacePeople.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        workspaceSaved.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
        fabNewSpace.setVisibility(index == 0 ? View.VISIBLE : View.GONE);

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
            tvTitle.setTypeface(tvTitle.getTypeface(), android.graphics.Typeface.BOLD);
            textCol.addView(tvTitle);

            TextView tvSub = new TextView(this);
            tvSub.setText(g.hasPassword
                    ? (ar ? "محمية بكلمة مرور" : "Password protected")
                    : (ar ? ("مفتوحة للانضمام • #" + (g.key != null ? g.key : "")) : ("Open group • #" + (g.key != null ? g.key : ""))));
            tvSub.setTextColor(palette.muted);
            tvSub.setTextSize(12f);
            textCol.addView(tvSub);

            row.addView(textCol);

            // Allow creator to remove their own public listing (matches Web v2 Pro)
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

    private void refreshOnlineOrbsRow(List<OnlineUser> onlineUsers) {
        layoutOnlineOrbsRow.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        boolean ar = prefs.isArabic();

        List<OnlineUser> list = onlineUsers != null ? onlineUsers : new ArrayList<>();
        for (OnlineUser u : list) {
            if (u == null) continue;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMarginEnd((int) (14 * d));
            item.setLayoutParams(lp);

            TextView orb = new TextView(this);
            int orbSize = (int) (48 * d);
            orb.setLayoutParams(new LinearLayout.LayoutParams(orbSize, orbSize));
            orb.setGravity(Gravity.CENTER);
            String initial = (u.name != null && !u.name.isEmpty()) ? u.name.substring(0, 1).toUpperCase(Locale.US) : "U";
            orb.setText(initial);
            orb.setTextSize(16f);
            orb.setTypeface(orb.getTypeface(), android.graphics.Typeface.BOLD);
            orb.setTextColor(palette.text);

            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(u.isSelf ? ThemeHelper.withAlpha(palette.accent, 65) : palette.panel);
            circle.setStroke((int) (2.2f * d), palette.accent);
            orb.setBackground(circle);
            item.addView(orb);

            TextView nameTv = new TextView(this);
            nameTv.setText(u.isSelf ? (ar ? "أنت" : "You") : u.name);
            nameTv.setTextSize(11f);
            nameTv.setTextColor(palette.text);
            nameTv.setMaxLines(1);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            nameLp.topMargin = (int) (4 * d);
            nameTv.setLayoutParams(nameLp);
            item.addView(nameTv);

            item.setOnClickListener(v -> {
                if (u.isSelf) {
                    showLiveThemeAndSettingsSheet();
                } else {
                    showInviteUserDialog(u);
                }
            });
            layoutOnlineOrbsRow.addView(item);
        }

        // Add "+ Invite" orb at the end (matches S2 Space)
        LinearLayout addWrap = new LinearLayout(this);
        addWrap.setOrientation(LinearLayout.VERTICAL);
        addWrap.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView plusOrb = new TextView(this);
        int orbSize = (int) (48 * d);
        plusOrb.setLayoutParams(new LinearLayout.LayoutParams(orbSize, orbSize));
        plusOrb.setGravity(Gravity.CENTER);
        plusOrb.setText("+");
        plusOrb.setTextSize(20f);
        plusOrb.setTextColor(palette.text);
        GradientDrawable dashed = new GradientDrawable();
        dashed.setShape(GradientDrawable.OVAL);
        dashed.setColor(palette.panelSoft);
        dashed.setStroke((int) (1.5f * d), palette.accent);
        plusOrb.setBackground(dashed);
        addWrap.addView(plusOrb);

        TextView invLabel = new TextView(this);
        invLabel.setText(ar ? "المتصلون" : "Directory");
        invLabel.setTextSize(11f);
        invLabel.setTextColor(palette.muted);
        LinearLayout.LayoutParams invLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        invLp.topMargin = (int) (4 * d);
        invLabel.setLayoutParams(invLp);
        addWrap.addView(invLabel);
        addWrap.setOnClickListener(v -> switchDockWorkspace(2));
        layoutOnlineOrbsRow.addView(addWrap);
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
        if (name.isEmpty()) name = "Guest";
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
            prefs.setUserName(typed.isEmpty() ? "Guest" : typed);
        }
    }

    private void saveUserNameFromInput() {
        String name = etUserName.getText().toString().trim();
        if (name.isEmpty()) name = prefs.getUserName().isEmpty() ? "Guest" : prefs.getUserName();
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

    /**
     * #23: Extracts room code from raw code, HTTPS link, or custom runapp:// link.
     */
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

    /**
     * #23: Smart Invite Link handler — opens the app straight into the invited room with zero intermediate steps.
     */
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

    private void refreshRecentRoomsUI() {
        List<RecentRoom> allRecent = prefs.getRecentRooms();
        layoutRecentRoomsContainer.removeAllViews();
        boolean ar = prefs.isArabic();
        String filterText = etQuickCodeOrSearch != null
                ? etQuickCodeOrSearch.getText().toString().trim().toLowerCase(Locale.US) : "";

        // Highlight Active vs Archived filter pill
        btnFilterActiveRooms.setBackgroundTintList(ColorStateList.valueOf(!showingArchivedRooms ? palette.accent : palette.panelSoft));
        btnFilterActiveRooms.setTextColor(!showingArchivedRooms ? palette.accentInk : palette.text);
        btnFilterArchivedRooms.setBackgroundTintList(ColorStateList.valueOf(showingArchivedRooms ? palette.accent : palette.panelSoft));
        btnFilterArchivedRooms.setTextColor(showingArchivedRooms ? palette.accentInk : palette.text);

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
                    : (ar ? "لا توجد غرف بعد. اضغط + لإنشاء مساحة أو الانضمام برمز!" : "No spaces yet. Tap + to create or join a room!"));
            return;
        }

        cardEmptyRoomsPrompt.setVisibility(View.GONE);
        layoutRecentEmpty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        float d = getResources().getDisplayMetrics().density;
        for (RecentRoom r : filtered) {
            View itemView = inflater.inflate(R.layout.item_recent_room, layoutRecentRoomsContainer, false);
            MaterialCardView card = itemView.findViewById(R.id.cardRecentRoomItem);
            TextView tvOrb = itemView.findViewById(R.id.tvRoomInitialsOrb);
            TextView tvName = itemView.findViewById(R.id.tvRecentRoomName);
            TextView tvPreview = itemView.findViewById(R.id.tvRecentRoomPreview);
            TextView tvCode = itemView.findViewById(R.id.tvRecentRoomCode);
            TextView tvMuted = itemView.findViewById(R.id.tvRoomMutedBadge);
            TextView tvTime = itemView.findViewById(R.id.tvRecentRoomTime);
            ImageButton btnCopy = itemView.findViewById(R.id.btnCopyRecentCode);
            ImageButton btnArchive = itemView.findViewById(R.id.btnArchiveRecent);
            ImageButton btnDelete = itemView.findViewById(R.id.btnDeleteRecent);

            card.setCardBackgroundColor(palette.panel);
            card.setStrokeColor(palette.panelHover);

            String title = r.name != null && !r.name.isEmpty() ? r.name : ("Room " + r.code);
            tvName.setText(title);
            tvName.setTextColor(palette.text);

            // #17: Tint room orb with room's custom identity color
            int roomAccent = ThemeHelper.parseRoomAccentColor(r.accentColor, palette);
            String initials = title.length() >= 2 ? title.substring(0, 2).toUpperCase(Locale.US) : title.toUpperCase(Locale.US);
            tvOrb.setText(initials);
            GradientDrawable orbBg = new GradientDrawable();
            orbBg.setCornerRadius(16 * d);
            orbBg.setColor(ThemeHelper.withAlpha(roomAccent, palette.isLight ? 95 : 75));
            orbBg.setStroke((int) (1.5f * d), roomAccent);
            tvOrb.setBackground(orbBg);
            tvOrb.setTextColor(palette.text);

            String preview = (r.lastMessagePreview != null && !r.lastMessagePreview.isEmpty())
                    ? r.lastMessagePreview
                    : (ar ? "اضغط للدخول إلى المساحة المباشرة" : "Tap to open live space");
            tvPreview.setText(preview);
            tvPreview.setTextColor(palette.muted);

            tvCode.setText("● #" + r.code);
            tvCode.setBackgroundTintList(ColorStateList.valueOf(ThemeHelper.withAlpha(roomAccent, palette.isLight ? 120 : 85)));
            tvCode.setTextColor(palette.text);

            // #27: Show Timed Mute badge if room is muted
            if (prefs.isRoomMuted(r.code)) {
                tvMuted.setVisibility(View.VISIBLE);
                tvMuted.setText("🔕 " + prefs.getRoomMuteRemainingLabel(r.code));
                tvMuted.setTextColor(palette.muted);
            } else {
                tvMuted.setVisibility(View.GONE);
            }

            tvTime.setText(LocaleHelper.formatRelativeTime(r.getEffectiveTime(), ar));
            tvTime.setTextColor(palette.subtle);

            itemView.setOnClickListener(v -> {
                ensureDefaultDisplayName();
                mqttManager.joinRoom(r.code, title, null);
                openRoomActivity(r.code, title, null);
            });

            btnCopy.setColorFilter(palette.muted);
            btnCopy.setOnClickListener(v -> {
                String inviteUrl = "https://takidz507.github.io/run/?room=" + r.code;
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Room Invite", inviteUrl));
                    Toast.makeText(this,
                            ar ? ("تم نسخ رابط الدعوة #" + r.code) : ("Copied invite link for #" + r.code),
                            Toast.LENGTH_SHORT).show();
                }
            });

            btnArchive.setColorFilter(palette.muted);
            btnArchive.setOnClickListener(v -> {
                boolean nextArch = !r.archived;
                prefs.setRoomArchived(r.code, nextArch);
                Toast.makeText(this,
                        nextArch
                                ? (ar ? "تمت أرشفة الغرفة" : "Room archived")
                                : (ar ? "تمت استعادة الغرفة" : "Room unarchived"),
                        Toast.LENGTH_SHORT).show();
                refreshRecentRoomsUI();
            });

            btnDelete.setColorFilter(palette.subtle);
            btnDelete.setOnClickListener(v -> {
                prefs.removeRecentRoom(r.code);
                refreshRecentRoomsUI();
            });

            layoutRecentRoomsContainer.addView(itemView);
        }
    }

    private void showInviteUserDialog(OnlineUser user) {
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_invite_user, null);
        LocaleHelper.applyViewDirection(root, prefs.isArabic());
        root.setBackgroundColor(palette.panel);

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
        root.setBackgroundColor(palette.panel);

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
    // #22: Live Theme Preview on a Real Interactive UI Surface + Settings Sheet
    // =========================================================================


    /**
     * v3.1 (owner's rule): "whatever eats space goes into the three dots."
     * The More sheet gathers every secondary destination that used to sit on
     * the home screen as cards/buttons: Discover shortcuts, archived rooms,
     * account pairing/transfer, language, and theme & profile. Each row simply
     * re-fires the existing control's click, so no behavior is duplicated.
     */
    private void showMoreSheet() {
        boolean ar = prefs.isArabic();
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_RunApp);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(16), dp(20), dp(24));
        container.setBackgroundColor(palette.panel);
        LocaleHelper.applyViewDirection(container, ar);

        TextView title = new TextView(this);
        title.setText(ar ? "المزيد" : "More");
        title.setTextColor(palette.text);
        title.setTextSize(17f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(8));
        container.addView(title);

        addMoreRow(container, "🌐", ar ? "المجموعات العامة" : "Public Groups", dialog, () -> cardPublicGroups.performClick());
        addMoreRow(container, "💡", ar ? "لوحة المقترحات" : "Feedback Board", dialog, () -> cardFeedbackQuick.performClick());
        addMoreRow(container, "🗄", ar ? "الغرف المؤرشفة" : "Archived Rooms", dialog, () -> {
            btnDockSpaces.performClick();
            btnFilterArchivedRooms.performClick();
        });
        addMoreRow(container, "🔗", ar ? "ربط / نقل الحساب" : "Pair / Transfer Account", dialog, () -> btnAccountPairing.performClick());
        addMoreRow(container, "🌍", ar ? "Switch to English" : "التبديل إلى العربية", dialog, () -> btnQuickLangToggle.performClick());
        addMoreRow(container, "🎨", ar ? "المظهر والملف الشخصي" : "Theme & Profile", dialog, this::showLiveThemeAndSettingsSheet);

        dialog.setContentView(container);
        MotionHelper.animatePanelEntrance(container, prefs.isLiteModeEnabled());
        dialog.show();
    }

    private void addMoreRow(LinearLayout container, String icon, String label,
                            BottomSheetDialog dialog, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(13), dp(6), dp(13));
        row.setClickable(true);
        row.setFocusable(true);
        android.util.TypedValue ripple = new android.util.TypedValue();
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
        labelView.setTypeface(labelView.getTypeface(), android.graphics.Typeface.BOLD);
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

    private void showLiveThemeAndSettingsSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_RunApp);
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_theme_live_preview, null);
        LocaleHelper.applyViewDirection(root, prefs.isArabic());
        dialog.setContentView(root);

        ScrollView scrollSheet = root.findViewById(R.id.scrollSettingsSheet);
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

        // 4 Selectable Theme Cards
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

        // Live UI Surface updater (#22: previews on a real UI surface before applying!)
        Runnable updateLivePreviewSurface = () -> {
            ThemeHelper.Palette p = ThemeHelper.getPalette(previewedThemeKey[0]);
            scrollSheet.setBackgroundColor(p.bg);
            tvSheetTitle.setTextColor(p.text);
            tvSheetSub.setTextColor(p.muted);
            btnClose.setColorFilter(p.text);

            cardLiveSurface.setCardBackgroundColor(p.bgElevated);
            cardLiveSurface.setStrokeColor(p.panelHover);
            tvPreviewLogo.setTextColor(p.text);
            viewPreviewHighlighter.setBackgroundTintList(ColorStateList.valueOf(p.highlighter));
            tvPreviewOrb.setBackgroundTintList(ColorStateList.valueOf(p.accent));
            tvPreviewOrb.setTextColor(p.accentInk);

            cardPreviewRoom.setCardBackgroundColor(p.panel);
            cardPreviewRoom.setStrokeColor(p.panelHover);
            tvPreviewRoomTitle.setTextColor(p.text);
            tvPreviewRoomSub.setTextColor(p.muted);
            tvPreviewOnlinePill.setBackgroundTintList(ColorStateList.valueOf(p.highlighter));
            tvPreviewOnlinePill.setTextColor(p.accentInk);

            cardPreviewIncoming.setCardBackgroundColor(p.panel);
            cardPreviewIncoming.setStrokeColor(p.panelHover);
            tvPreviewIncomingAuthor.setTextColor(p.text);
            tvPreviewIncomingText.setTextColor(p.text);

            cardPreviewOutgoing.setCardBackgroundColor(p.ownBubble);
            cardPreviewOutgoing.setStrokeColor(p.ownBubbleStroke);
            tvPreviewOutgoingAuthor.setTextColor(p.ownBubbleText);
            tvPreviewOutgoingText.setTextColor(p.ownBubbleText);

            cardPreviewTask.setCardBackgroundColor(p.panel);
            cardPreviewTask.setStrokeColor(p.panelHover);
            cbPreviewTask.setButtonTintList(ColorStateList.valueOf(p.accent));
            tvPreviewTaskText.setTextColor(p.text);

            // Highlight selected theme card
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
                cards[i].setCardBackgroundColor(p.panel);
                cards[i].setStrokeColor(selected ? p.accent : p.panelHover);
                cards[i].setStrokeWidth(selected ? 5 : 2);
                checks[i].setVisibility(selected ? View.VISIBLE : View.GONE);
                checks[i].setBackgroundTintList(ColorStateList.valueOf(p.accent));
                checks[i].setTextColor(p.accentInk);
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

            etName.setTextColor(p.text);
            etName.setHintTextColor(p.subtle);
            etBio.setTextColor(p.text);
            etBio.setHintTextColor(p.subtle);
            cbTouch.setTextColor(p.text);
            cbLite.setTextColor(p.text);
            cbDark.setTextColor(p.text);
            cbDark.setButtonTintList(ColorStateList.valueOf(p.accent));
            cbDark.setChecked(ThemeHelper.isDarkKey(previewedThemeKey[0]));
            tvLiteInfo.setTextColor(p.muted);
            cbNotify.setTextColor(p.text);
            cbTranslate.setTextColor(p.text);

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

        ScrollView scroll = root.findViewById(R.id.scrollPairingSheet);
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

        scroll.setBackgroundColor(palette.bg);
        cardGen.setCardBackgroundColor(palette.panel);
        cardGen.setStrokeColor(palette.panelHover);
        cardRedeem.setCardBackgroundColor(palette.panel);
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

    private void applyLocalizedLabels() {
        boolean ar = prefs.isArabic();
        btnQuickLangToggle.setText(ar ? "EN" : "عربي");
        tvOnlineStripTitle.setText(ar ? "متصلون الآن" : "Online now");
        etQuickCodeOrSearch.setHint(ar ? "أدخل رمز غرفة (6 أحرف)، رابط دعوة، أو ابحث…" : "Enter 6-char room code, invite link, or filter…");
        btnQuickCodeEnter.setText(ar ? "انضمام ←" : "Join →");
        btnSegmentCreate.setText(ar ? "إنشاء مساحة" : "Create Space");
        btnSegmentJoin.setText(ar ? "انضمام برمز" : "Join by Code");
        etUserName.setHint(ar ? "اسم العرض الخاص بك" : "Your display name");
        etRoomName.setHint(ar ? "اسم الغرفة (اختياري)" : "Space / Room name (optional)");
        cbPublicGroup.setText(ar ? "إدراج في دليل المجموعات العامة" : "List in Public Groups Directory");
        etPublicPassword.setHint(ar ? "كلمة المرور (اختياري)" : "Password protection (optional)");
        btnCreateRoom.setText(ar ? "إنشاء وفتح الغرفة" : "Create & Open Space");
        etJoinRoomCode.setHint(ar ? "رمز الغرفة (6 أو 12 حرفاً) أو الرابط" : "Enter 6 or 12 character code or link");
        btnJoinByCodeSubmit.setText(ar ? "انضمام للغرفة" : "Join Space Now");

        tvRecentTitle.setText(ar ? "غرفك" : "Your rooms");
        btnFilterActiveRooms.setText(ar ? "النشطة" : "Active");
        btnFilterArchivedRooms.setText(ar ? "المؤرشفة" : "Archived");
        btnEditProfile.setText(ar ? "المظهر والملف" : "Theme & Profile");

        tvTilePublicTitle.setText(ar ? "🌐 المجموعات العامة" : "🌐 Public Groups");
        tvTileFeedbackTitle.setText(ar ? "💡 لوحة المقترحات" : "💡 Feedback Board");
        tvTileFeedbackSub.setText(ar ? "شارك وصوّت للأفكار" : "Vote & suggest ideas");

        btnDockSpaces.setText(ar ? "مساحاتي" : "Spaces");
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
        // v3.1: every theme speaks its own motion language
        MotionHelper.setThemeProfile(palette.themeKey);
        boolean glassTheme = ThemeHelper.isGlassTheme(palette.themeKey);
        if (glassTheme) {
            // Liquid Glass backdrop: a deep, slowly-shifting gradient the
            // translucent panels float over (iPhone-style).
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
        btnMoreMenu.setColorFilter(palette.text);
        tvAppTitle.setTextColor(palette.text);
        btnAccountPairing.setColorFilter(palette.text);
        btnThemeSettings.setColorFilter(palette.text);

        btnQuickLangToggle.setBackgroundTintList(ColorStateList.valueOf(palette.panelSoft));
        btnQuickLangToggle.setTextColor(palette.text);

        tvMainAvatarFallback.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        tvMainAvatarFallback.setTextColor(palette.accentInk);

        profileCard.setCardBackgroundColor(palette.panel);
        profileCard.setStrokeColor(palette.panelHover);
        profileCard.setStrokeWidth(0);
        tvGreetingName.setTextColor(palette.text);
        tvUserBioDisplay.setTextColor(palette.muted);
        btnEditProfile.setBackgroundTintList(ColorStateList.valueOf(palette.panelSoft));
        btnEditProfile.setTextColor(palette.text);

        tvOnlineStripTitle.setTextColor(palette.text);

        cardQuickCodeBar.setCardBackgroundColor(palette.panel);
        cardQuickCodeBar.setStrokeColor(palette.panelHover);
        cardQuickCodeBar.setStrokeWidth(0);
        etQuickCodeOrSearch.setTextColor(palette.text);
        etQuickCodeOrSearch.setHintTextColor(palette.subtle);
        btnQuickCodeEnter.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        btnQuickCodeEnter.setTextColor(palette.accentInk);

        mainCard.setCardBackgroundColor(palette.panel);
        mainCard.setStrokeColor(palette.panelHover);
        mainCard.setStrokeWidth(0);
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

        tvRecentTitle.setTextColor(palette.text);
        viewYourRoomsHighlighter.setBackgroundTintList(ColorStateList.valueOf(palette.highlighter));
        tvRecentCount.setTextColor(palette.muted);
        cardEmptyRoomsPrompt.setCardBackgroundColor(palette.panel);
        cardEmptyRoomsPrompt.setStrokeColor(palette.panelHover);
        tvEmptyRoomsText.setTextColor(palette.muted);

        cardPublicGroups.setCardBackgroundColor(palette.panel);
        cardPublicGroups.setStrokeColor(palette.panelHover);
        tvTilePublicTitle.setTextColor(palette.text);
        tvPublicCount.setTextColor(palette.muted);

        cardFeedbackQuick.setCardBackgroundColor(palette.panel);
        cardFeedbackQuick.setStrokeColor(palette.panelHover);
        tvTileFeedbackTitle.setTextColor(palette.text);
        tvTileFeedbackSub.setTextColor(palette.muted);

        tvSavedSectionTitle.setTextColor(palette.text);
        viewSavedHighlighter.setBackgroundTintList(ColorStateList.valueOf(palette.highlighter));
        tvSavedSectionSub.setTextColor(palette.muted);
        btnPairDeviceFromSaved.setBackgroundTintList(ColorStateList.valueOf(palette.panelSoft));
        btnPairDeviceFromSaved.setTextColor(palette.text);

        if (glassTheme) {
            // The '+' becomes a frosted glass droplet: translucent white fill,
            // bright glass edge, white glyph — and a soft sheen on theme apply.
            fabNewSpace.setBackgroundTintList(ColorStateList.valueOf(Color.argb(64, 255, 255, 255)));
            fabNewSpace.setTextColor(Color.WHITE);
            fabNewSpace.setStrokeColor(ColorStateList.valueOf(Color.argb(150, 255, 255, 255)));
            fabNewSpace.setStrokeWidth((int) (1.2f * getResources().getDisplayMetrics().density));
            MotionHelper.animateGlassSheen(fabNewSpace, prefs.isLiteModeEnabled());
        } else {
            fabNewSpace.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
            fabNewSpace.setTextColor(palette.accentInk);
            fabNewSpace.setStrokeWidth(0);
        }

        GradientDrawable dockBg = new GradientDrawable();
        dockBg.setCornerRadius(32f * getResources().getDisplayMetrics().density);
        dockBg.setColor(palette.dockBg);
        if (glassTheme) {
            dockBg.setStroke((int) (1f * getResources().getDisplayMetrics().density), palette.panelHover);
        }
        bottomNavDock.setBackground(dockBg);
        updateDockButtonsVisuals();

        if ( getWindow() != null ) {
            getWindow().setStatusBarColor(palette.bg);
            getWindow().setNavigationBarColor(palette.bg);
        }
    }

    /**
     * #29: Updates live connection quality pill (Connected / Weak signal / Reconnecting automatically).
     */
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
            int count = mqttManager.getGlobalOnlineCount();
            tvOnlineStatus.setText("● " + LocaleHelper.formatNumber(count, ar) + (ar ? " متصل الآن" : " online"));
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
            tvOnlineStatus.setText("● " + LocaleHelper.formatNumber(onlineCount, ar) + (ar ? " متصل الآن" : " online"));
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
