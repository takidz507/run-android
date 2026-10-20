package com.takidz.run.ui;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import com.takidz.run.models.SignalMessage;
import com.takidz.run.network.CatboxUploader;
import com.takidz.run.network.MqttRoomManager;
import com.takidz.run.ui.adapters.FeedbackPostsAdapter;
import com.takidz.run.ui.adapters.FriendsAdapter;
import com.takidz.run.ui.adapters.OnlineUsersAdapter;
import com.takidz.run.utils.PrefsManager;
import com.takidz.run.utils.RoomCryptoUtils;
import com.takidz.run.utils.ThemeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Redesigned Home Screen with Google Classroom simplicity × Google Pixel polish.
 * Features:
 * 1. Online Users Directory (live counter pill opens directory, friend requests, room invites)
 * 2. Public Feedback Board (suggestions with 👍/❤️ votes on MQTT)
 * 3. Streamlined Create / Join hero card
 * 4. Recent Rooms list with copy & delete
 * 5. Full Material Design 3 and multi-theme support
 */
public class MainActivity extends AppCompatActivity implements MqttRoomManager.GlobalListener {

    private static final int REQ_POST_NOTIFICATIONS = 301;

    private PrefsManager prefs;
    private MqttRoomManager mqttManager;
    private CatboxUploader catboxUploader;
    private ThemeHelper.Palette palette;

    private ScrollView rootScroll;
    private TextView tvAppTitle;
    private LinearLayout pillOnlineDirectory;
    private TextView tvOnlineStatus;
    private ImageButton btnFeedbackBoard;
    private ImageButton btnThemeSettings;

    // Profile Card
    private MaterialCardView profileCard;
    private ImageView ivMainAvatar;
    private TextView tvMainAvatarFallback;
    private TextView tvGreetingName;
    private TextView tvUserBioDisplay;
    private MaterialButton btnEditProfile;

    // Hero Action Card (Segmented)
    private MaterialCardView mainCard;
    private MaterialButton btnSegmentCreate;
    private MaterialButton btnSegmentJoin;
    private LinearLayout formCreateRoom;
    private LinearLayout formJoinCode;

    // Create Form
    private EditText etUserName;
    private EditText etRoomName;
    private CheckBox cbPublicGroup;
    private EditText etPublicPassword;
    private MaterialButton btnCreateRoom;

    // Join Form
    private EditText etJoinRoomCode;
    private MaterialButton btnJoinByCodeSubmit;

    // Quick Action Cards
    private MaterialCardView cardPublicGroups;
    private TextView tvPublicCount;
    private MaterialCardView cardFeedbackQuick;

    // Recent Rooms
    private MaterialCardView recentRoomsCard;
    private TextView tvRecentCount;
    private LinearLayout layoutRecentRoomsContainer;
    private LinearLayout layoutRecentEmpty;

    private ActivityResultLauncher<String> avatarPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new PrefsManager(this);
        mqttManager = MqttRoomManager.getInstance(this);
        catboxUploader = new CatboxUploader();
        palette = ThemeHelper.getPalette(prefs.getThemeMode());

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
                        Toast.makeText(this, "Uploading avatar...", Toast.LENGTH_SHORT).show();
                        catboxUploader.processAndUploadUri(this, uri, new CatboxUploader.UploadCallback() {
                            @Override
                            public void onSuccess(String fileUrlOrDataUrl, String fileName, long fileSize, String mimeType) {
                                prefs.setUserAvatarUrl(fileUrlOrDataUrl);
                                mqttManager.syncLocalProfileToActiveRoom();
                                populateSavedUser();
                                Toast.makeText(MainActivity.this, "Avatar updated successfully!", Toast.LENGTH_SHORT).show();
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
        mqttManager.setAppForeground(true);
        mqttManager.addGlobalListener(this);
        populateSavedUser();
        refreshRecentRoomsUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mqttManager.removeGlobalListener(this);
    }

    private void initViews() {
        rootScroll = findViewById(R.id.rootScroll);
        tvAppTitle = findViewById(R.id.tvAppTitle);
        pillOnlineDirectory = findViewById(R.id.pillOnlineDirectory);
        tvOnlineStatus = findViewById(R.id.tvOnlineStatus);
        btnFeedbackBoard = findViewById(R.id.btnFeedbackBoard);
        btnThemeSettings = findViewById(R.id.btnThemeSettings);

        profileCard = findViewById(R.id.profileCard);
        ivMainAvatar = findViewById(R.id.ivMainAvatar);
        tvMainAvatarFallback = findViewById(R.id.tvMainAvatarFallback);
        tvGreetingName = findViewById(R.id.tvGreetingName);
        tvUserBioDisplay = findViewById(R.id.tvUserBioDisplay);
        btnEditProfile = findViewById(R.id.btnEditProfile);

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

        cardPublicGroups = findViewById(R.id.cardPublicGroups);
        tvPublicCount = findViewById(R.id.tvPublicCount);
        cardFeedbackQuick = findViewById(R.id.cardFeedbackQuick);

        recentRoomsCard = findViewById(R.id.recentRoomsCard);
        tvRecentCount = findViewById(R.id.tvRecentCount);
        layoutRecentRoomsContainer = findViewById(R.id.layoutRecentRoomsContainer);
        layoutRecentEmpty = findViewById(R.id.layoutRecentEmpty);

        // Segment Switcher: Create vs Join
        btnSegmentCreate.setOnClickListener(v -> switchHeroSegment(true));
        btnSegmentJoin.setOnClickListener(v -> switchHeroSegment(false));

        cbPublicGroup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            etPublicPassword.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Online Directory Pill click -> Opens Online Community Directory
        pillOnlineDirectory.setOnClickListener(v -> showOnlineUsersDirectory());

        btnFeedbackBoard.setOnClickListener(v -> showFeedbackBoard());
        cardFeedbackQuick.setOnClickListener(v -> showFeedbackBoard());
        cardPublicGroups.setOnClickListener(v -> showPublicGroupsDialog());

        profileCard.setOnClickListener(v -> showSettingsDialog());
        btnEditProfile.setOnClickListener(v -> showSettingsDialog());
        btnThemeSettings.setOnClickListener(v -> showSettingsDialog());

        btnCreateRoom.setOnClickListener(v -> onCreateRoomClicked());
        btnJoinByCodeSubmit.setOnClickListener(v -> onJoinRoomClicked());
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
        String name = prefs.getUserName();
        if (name.isEmpty()) name = "Guest";
        etUserName.setText(name);
        tvGreetingName.setText(name);

        String bio = prefs.getUserBio();
        tvUserBioDisplay.setText(bio.isEmpty() ? "Tap to customize profile" : bio);

        String initial = name.substring(0, 1).toUpperCase();
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

    private void saveUserNameFromInput() {
        String name = etUserName.getText().toString().trim();
        if (name.isEmpty()) name = "Guest";
        prefs.setUserName(name);
        tvGreetingName.setText(name);
    }

    private void onCreateRoomClicked() {
        saveUserNameFromInput();
        String roomName = etRoomName.getText().toString().trim();
        boolean isPublic = cbPublicGroup.isChecked();
        String optionalPw = isPublic ? etPublicPassword.getText().toString().trim() : null;

        String code = mqttManager.createNewRoom(roomName, isPublic, optionalPw);
        openRoomActivity(code, roomName.isEmpty() ? ("Room " + code) : roomName);
    }

    private void onJoinRoomClicked() {
        saveUserNameFromInput();
        String raw = etJoinRoomCode.getText().toString().trim();
        String extracted = extractCodeFromInputOrLink(raw);
        if (!RoomCryptoUtils.isValidRoomCode(extracted)) {
            Toast.makeText(this, "Please enter a valid 6 or 12 character room code or link.", Toast.LENGTH_LONG).show();
            return;
        }
        mqttManager.joinRoom(extracted, "Room " + extracted, null);
        openRoomActivity(extracted, "Room " + extracted);
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

    private void openRoomActivity(String code, String name) {
        Intent intent = new Intent(this, RoomActivity.class);
        intent.putExtra("room_code", code);
        intent.putExtra("room_name", name);
        startActivity(intent);
    }

    private void handleIncomingDeepLink(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri uri = intent.getData();
        String roomParam = uri.getQueryParameter("room");
        if (roomParam != null) {
            String cleanCode = roomParam.trim().toUpperCase(Locale.US);
            if (RoomCryptoUtils.isValidRoomCode(cleanCode)) {
                mqttManager.joinRoom(cleanCode, "Room " + cleanCode, null);
                openRoomActivity(cleanCode, "Room " + cleanCode);
            }
        }
    }

    private void refreshRecentRoomsUI() {
        List<RecentRoom> recent = prefs.getRecentRooms();
        layoutRecentRoomsContainer.removeAllViews();

        if (recent.isEmpty()) {
            layoutRecentEmpty.setVisibility(View.VISIBLE);
            tvRecentCount.setText("0 rooms");
            return;
        }

        layoutRecentEmpty.setVisibility(View.GONE);
        tvRecentCount.setText(recent.size() + (recent.size() == 1 ? " room" : " rooms"));

        LayoutInflater inflater = LayoutInflater.from(this);
        for (RecentRoom r : recent) {
            View itemView = inflater.inflate(R.layout.item_recent_room, layoutRecentRoomsContainer, false);
            TextView tvName = itemView.findViewById(R.id.tvRecentRoomName);
            TextView tvCode = itemView.findViewById(R.id.tvRecentRoomCode);
            TextView tvTime = itemView.findViewById(R.id.tvRecentRoomTime);
            ImageButton btnCopy = itemView.findViewById(R.id.btnCopyRecentCode);
            ImageButton btnDelete = itemView.findViewById(R.id.btnDeleteRecent);

            tvName.setText(r.name != null ? r.name : ("Room " + r.code));
            tvCode.setText("#" + r.code);
            String timeStr = r.visitedAt != null ? r.visitedAt : r.joinedAt;
            tvTime.setText(timeStr != null && timeStr.length() >= 10 ? timeStr.substring(0, 10) : "Recent");

            itemView.setOnClickListener(v -> {
                saveUserNameFromInput();
                mqttManager.joinRoom(r.code, r.name, null);
                openRoomActivity(r.code, r.name);
            });

            btnCopy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Room Code", r.code));
                    Toast.makeText(this, "Copied #" + r.code, Toast.LENGTH_SHORT).show();
                }
            });

            btnDelete.setOnClickListener(v -> {
                prefs.removeRecentRoom(r.code);
                refreshRecentRoomsUI();
            });

            layoutRecentRoomsContainer.addView(itemView);
        }
    }

    // ==========================================
    // Feature 1: Online Users Community Directory
    // ==========================================

    private void showOnlineUsersDirectory() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_RunApp);
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_online_directory, null);
        dialog.setContentView(root);

        TextView tvTitle = root.findViewById(R.id.tvDirTitle);
        ImageButton btnClose = root.findViewById(R.id.btnCloseDir);
        MaterialButton btnTabOnline = root.findViewById(R.id.btnTabOnline);
        MaterialButton btnTabFriends = root.findViewById(R.id.btnTabFriends);
        EditText etSearch = root.findViewById(R.id.etSearchUsers);
        RecyclerView rvUsers = root.findViewById(R.id.rvDirectoryUsers);
        LinearLayout layoutEmpty = root.findViewById(R.id.layoutEmptyState);
        TextView tvEmpty = root.findViewById(R.id.tvEmptyMessage);

        rvUsers.setLayoutManager(new LinearLayoutManager(this));

        final boolean[] viewingOnlineTab = {true};

        OnlineUsersAdapter usersAdapter = new OnlineUsersAdapter(this, prefs, palette, new OnlineUsersAdapter.UserActionListener() {
            @Override
            public void onInviteClicked(OnlineUser user) {
                showInviteUserDialog(user);
            }

            @Override
            public void onAddFriendClicked(OnlineUser user) {
                mqttManager.sendFriendRequest(user);
                Toast.makeText(MainActivity.this, "Friend request sent to " + user.name, Toast.LENGTH_SHORT).show();
            }
        });

        final FriendsAdapter[] friendsAdapterHolder = new FriendsAdapter[1];
        FriendsAdapter friendsAdapter = new FriendsAdapter(this, new FriendsAdapter.FriendsActionListener() {
            @Override
            public void onAcceptFriendRequest(SignalMessage request) {
                mqttManager.acceptFriendRequest(request);
                Toast.makeText(MainActivity.this, "Accepted " + request.fromUserName + " as a friend!", Toast.LENGTH_SHORT).show();
                updateFriendsTab(friendsAdapterHolder[0], layoutEmpty, tvEmpty);
            }

            @Override
            public void onDeclineFriendRequest(SignalMessage request) {
                mqttManager.declineFriendRequest(request);
                Toast.makeText(MainActivity.this, "Declined request", Toast.LENGTH_SHORT).show();
                updateFriendsTab(friendsAdapterHolder[0], layoutEmpty, tvEmpty);
            }

            @Override
            public void onJoinRoomInvite(SignalMessage invite) {
                dialog.dismiss();
                mqttManager.joinRoom(invite.roomCode, invite.roomName, null);
                openRoomActivity(invite.roomCode, invite.roomName);
            }

            @Override
            public void onDismissRoomInvite(SignalMessage invite) {
                prefs.removeRoomInvite(invite.id);
                updateFriendsTab(friendsAdapterHolder[0], layoutEmpty, tvEmpty);
            }

            @Override
            public void onInviteFriendToRoom(OnlineUser friend) {
                showInviteUserDialog(friend);
            }

            @Override
            public void onRemoveFriend(OnlineUser friend) {
                prefs.removeFriend(friend.userId);
                Toast.makeText(MainActivity.this, "Removed " + friend.name, Toast.LENGTH_SHORT).show();
                updateFriendsTab(friendsAdapterHolder[0], layoutEmpty, tvEmpty);
            }
        });
        friendsAdapterHolder[0] = friendsAdapter;

        Runnable refreshCurrentTab = () -> {
            if (viewingOnlineTab[0]) {
                rvUsers.setAdapter(usersAdapter);
                List<OnlineUser> list = mqttManager.getOnlineUsersList();
                String query = etSearch.getText().toString().trim().toLowerCase();
                List<OnlineUser> filtered = new ArrayList<>();
                for (OnlineUser u : list) {
                    if (query.isEmpty() || u.name.toLowerCase().contains(query)) {
                        filtered.add(u);
                    }
                }
                usersAdapter.setUsers(filtered);
                layoutEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
                tvEmpty.setText("No users matching search online right now");
                btnTabOnline.setText("Online (" + list.size() + ")");
            } else {
                rvUsers.setAdapter(friendsAdapter);
                updateFriendsTab(friendsAdapter, layoutEmpty, tvEmpty);
            }
        };

        btnTabOnline.setOnClickListener(v -> {
            viewingOnlineTab[0] = true;
            btnTabOnline.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
            btnTabOnline.setTextColor(palette.accentInk);
            btnTabFriends.setBackgroundTintList(ColorStateList.valueOf(palette.panelSoft));
            btnTabFriends.setTextColor(palette.text);
            root.findViewById(R.id.layoutSearch).setVisibility(View.VISIBLE);
            refreshCurrentTab.run();
        });

        btnTabFriends.setOnClickListener(v -> {
            viewingOnlineTab[0] = false;
            btnTabFriends.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
            btnTabFriends.setTextColor(palette.accentInk);
            btnTabOnline.setBackgroundTintList(ColorStateList.valueOf(palette.panelSoft));
            btnTabOnline.setTextColor(palette.text);
            root.findViewById(R.id.layoutSearch).setVisibility(View.GONE);
            refreshCurrentTab.run();
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (viewingOnlineTab[0]) refreshCurrentTab.run();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        refreshCurrentTab.run();
        dialog.show();
    }

    private void updateFriendsTab(FriendsAdapter adapter, LinearLayout emptyLayout, TextView emptyMsg) {
        List<SignalMessage> requests = prefs.getPendingRequestsReceived();
        List<SignalMessage> invites = prefs.getPendingRoomInvites();
        List<OnlineUser> friends = prefs.getFriends();
        adapter.setData(requests, invites, friends);
        boolean isEmpty = (requests.isEmpty() && invites.isEmpty() && friends.isEmpty());
        emptyLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        emptyMsg.setText("No friends, requests, or room invites yet");
    }

    private void showInviteUserDialog(OnlineUser user) {
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_invite_user, null);
        TextView tvSub = root.findViewById(R.id.tvInviteDialogSub);
        LinearLayout layoutRooms = root.findViewById(R.id.layoutRoomsList);
        EditText etCustomCode = root.findViewById(R.id.etCustomRoomCode);
        MaterialButton btnCancel = root.findViewById(R.id.btnCancelInvite);
        MaterialButton btnSend = root.findViewById(R.id.btnSendInvite);

        tvSub.setText("Invite " + user.name + " to join a room:");

        final String[] selectedCode = {null};
        final String[] selectedName = {null};

        List<RecentRoom> recent = prefs.getRecentRooms();
        for (RecentRoom r : recent) {
            MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText(r.name + " (#" + r.code + ")");
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
                Toast.makeText(this, "Please enter or select a valid room code.", Toast.LENGTH_SHORT).show();
                return;
            }
            String rName = selectedName[0] != null ? selectedName[0] : ("Room " + code);
            mqttManager.sendRoomInvite(user, code, rName);
            Toast.makeText(this, "Invitation sent to " + user.name + "!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    // ==========================================
    // Feature 3: Public Community Feedback Board
    // ==========================================

    private void showFeedbackBoard() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_RunApp);
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_feedback_board, null);
        dialog.setContentView(root);

        ImageButton btnClose = root.findViewById(R.id.btnCloseFeedback);
        RecyclerView rvPosts = root.findViewById(R.id.rvFeedbackPosts);
        LinearLayout layoutEmpty = root.findViewById(R.id.layoutEmptyFeedback);
        MaterialButton btnNewSuggestion = root.findViewById(R.id.btnNewSuggestion);

        Chip chipAll = root.findViewById(R.id.chipFilterAll);
        Chip chipIdeas = root.findViewById(R.id.chipFilterIdeas);
        Chip chipFeatures = root.findViewById(R.id.chipFilterFeatures);
        Chip chipBugs = root.findViewById(R.id.chipFilterBugs);

        rvPosts.setLayoutManager(new LinearLayoutManager(this));

        final String[] currentFilter = {"All"};

        FeedbackPostsAdapter adapter = new FeedbackPostsAdapter(this, prefs, palette, (post, isHeart) -> {
            mqttManager.toggleFeedbackVote(post.id, isHeart);
        });
        rvPosts.setAdapter(adapter);

        Runnable updateList = () -> {
            FeedbackBoardData data = mqttManager.getFeedbackBoardData();
            List<FeedbackPost> allPosts = (data != null && data.posts != null) ? data.posts : new ArrayList<>();
            List<FeedbackPost> filtered = new ArrayList<>();
            for (FeedbackPost p : allPosts) {
                if ("All".equals(currentFilter[0]) || (p.category != null && p.category.equalsIgnoreCase(currentFilter[0]))) {
                    filtered.add(p);
                }
            }
            // Sort by total score
            filtered.sort((a, b) -> Integer.compare(b.getTotalScore(), a.getTotalScore()));
            adapter.setPosts(filtered);
            layoutEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        };

        chipAll.setOnClickListener(v -> { currentFilter[0] = "All"; updateList.run(); });
        chipIdeas.setOnClickListener(v -> { currentFilter[0] = "Idea"; updateList.run(); });
        chipFeatures.setOnClickListener(v -> { currentFilter[0] = "Feature"; updateList.run(); });
        chipBugs.setOnClickListener(v -> { currentFilter[0] = "Bug"; updateList.run(); });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnNewSuggestion.setOnClickListener(v -> showNewSuggestionDialog(updateList));

        updateList.run();
        dialog.show();
    }

    private void showNewSuggestionDialog(Runnable onPosted) {
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_new_suggestion, null);
        EditText etTitle = root.findViewById(R.id.etSuggestionTitle);
        EditText etContent = root.findViewById(R.id.etSuggestionContent);
        MaterialButton btnCancel = root.findViewById(R.id.btnCancelSuggestion);
        MaterialButton btnSubmit = root.findViewById(R.id.btnSubmitSuggestion);

        Chip chipIdea = root.findViewById(R.id.chipCatIdea);
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
                Toast.makeText(this, "Please enter a title for your suggestion", Toast.LENGTH_SHORT).show();
                return;
            }

            String cat = "Idea";
            if (chipFeature.isChecked()) cat = "Feature";
            else if (chipBug.isChecked()) cat = "Bug";
            else if (chipImprovement.isChecked()) cat = "Improvement";

            mqttManager.createFeedbackPost(title, content, cat);
            Toast.makeText(this, "Suggestion submitted!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            if (onPosted != null) onPosted.run();
        });

        dialog.show();
    }

    private void showPublicGroupsDialog() {
        saveUserNameFromInput();
        DirectoryData dir = mqttManager.getDirectoryData();
        List<DirectoryData.PublicGroupEntry> groups = (dir != null && dir.groups != null) ? dir.groups : null;

        if (groups == null || groups.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Public Groups")
                    .setMessage("No public groups listed yet. Create one by checking 'List in Public Directory'!")
                    .setPositiveButton("OK", null)
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
                .setTitle("Browse Public Groups (" + groups.size() + ")")
                .setItems(labels, (dialog, which) -> {
                    DirectoryData.PublicGroupEntry selected = groups.get(which);
                    if (selected.hasPassword) {
                        promptPasswordForPublicGroup(selected);
                    } else if (selected.key != null && !selected.key.isEmpty()) {
                        mqttManager.joinRoom(selected.key, selected.name, null);
                        openRoomActivity(selected.key, selected.name);
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void promptPasswordForPublicGroup(DirectoryData.PublicGroupEntry group) {
        EditText pwInput = new EditText(this);
        pwInput.setHint("Group Password");
        pwInput.setSingleLine(true);

        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(pwInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle("🔒 Protected Group: " + group.name)
                .setMessage("Enter the group password to derive the secure channel key:")
                .setView(container)
                .setPositiveButton("Enter", (d, w) -> {
                    String pw = pwInput.getText().toString().trim();
                    String computedVerifier = RoomCryptoUtils.computePasswordVerifier(group.seed, pw);
                    if (group.verifier != null && group.verifier.equalsIgnoreCase(computedVerifier)) {
                        String derivedTopicKey = RoomCryptoUtils.deriveProtectedGroupTopicKey(group.seed, pw);
                        mqttManager.joinRoom(derivedTopicKey, group.name, null);
                        openRoomActivity(derivedTopicKey, group.name);
                    } else {
                        Toast.makeText(this, "Incorrect password!", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSettingsDialog() {
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad / 2, pad, pad / 2);

        // Profile Avatar Picker Button
        MaterialButton btnPickAvatar = new MaterialButton(this);
        boolean hasAvatar = !prefs.getUserAvatarUrl().isEmpty();
        btnPickAvatar.setText(hasAvatar ? "Change Avatar Image" : "Choose Profile Avatar");
        btnPickAvatar.setOnClickListener(v -> avatarPickerLauncher.launch("image/*"));
        layout.addView(btnPickAvatar);

        // Bio input
        EditText etBio = new EditText(this);
        etBio.setHint("Short bio or status");
        etBio.setText(prefs.getUserBio());
        layout.addView(etBio);

        // Theme selector
        TextView tvThemeLabel = new TextView(this);
        tvThemeLabel.setText("Theme & Appearance:");
        tvThemeLabel.setPadding(0, pad / 2, 0, 8);
        layout.addView(tvThemeLabel);

        String[] themeNames = {"Dark Lime (Default)", "AMOLED Black", "Light Mode"};
        String[] themeValues = {"dark", "amoled", "light"};
        Spinner spTheme = new Spinner(this);
        spTheme.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, themeNames));
        String currentTheme = prefs.getThemeMode();
        if ("amoled".equalsIgnoreCase(currentTheme)) spTheme.setSelection(1);
        else if ("light".equalsIgnoreCase(currentTheme)) spTheme.setSelection(2);
        else spTheme.setSelection(0);
        layout.addView(spTheme);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Profile & Settings")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newBio = etBio.getText().toString().trim();
                    prefs.setUserBio(newBio);

                    int selectedThemeIdx = spTheme.getSelectedItemPosition();
                    String chosenTheme = themeValues[selectedThemeIdx];
                    if (!chosenTheme.equalsIgnoreCase(currentTheme)) {
                        prefs.setThemeMode(chosenTheme);
                        ThemeHelper.applyAppNightMode(chosenTheme);
                        recreate();
                    }
                    populateSavedUser();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyCurrentTheme() {
        palette = ThemeHelper.getPalette(prefs.getThemeMode());
        rootScroll.setBackgroundColor(palette.bg);
        mainCard.setCardBackgroundColor(palette.panel);
        mainCard.setStrokeColor(palette.panelHover);
        profileCard.setCardBackgroundColor(palette.panel);
        profileCard.setStrokeColor(palette.panelHover);
        recentRoomsCard.setCardBackgroundColor(palette.panel);
        recentRoomsCard.setStrokeColor(palette.panelHover);
        btnCreateRoom.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        btnCreateRoom.setTextColor(palette.accentInk);
        btnJoinByCodeSubmit.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        btnJoinByCodeSubmit.setTextColor(palette.accentInk);
    }

    // ==========================================
    // Global MQTT Callbacks
    // ==========================================

    @Override
    public void onConnectionChanged(boolean connected, String brokerUrl) {
        runOnUiThread(() -> {
            if (connected) {
                tvOnlineStatus.setText(mqttManager.getGlobalOnlineCount() + " Online");
                tvOnlineStatus.setTextColor(palette.accent);
            } else {
                tvOnlineStatus.setText("Connecting...");
                tvOnlineStatus.setTextColor(palette.muted);
            }
        });
    }

    @Override
    public void onGlobalPresenceChanged(int onlineCount) {
        runOnUiThread(() -> {
            tvOnlineStatus.setText(onlineCount + (onlineCount == 1 ? " Online" : " Online"));
        });
    }

    @Override
    public void onDirectoryUpdated(DirectoryData directory) {
        runOnUiThread(() -> {
            int count = (directory != null && directory.groups != null) ? directory.groups.size() : 0;
            tvPublicCount.setText(count + (count == 1 ? " public group" : " public groups"));
        });
    }

    @Override
    public void onSignalReceived(SignalMessage signal) {
        runOnUiThread(() -> {
            if (SignalMessage.TYPE_ROOM_INVITE.equals(signal.type)) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Room Invitation")
                        .setMessage(signal.fromUserName + " invited you to join '" + signal.roomName + "' (#" + signal.roomCode + ")")
                        .setPositiveButton("Join", (d, w) -> {
                            mqttManager.joinRoom(signal.roomCode, signal.roomName, null);
                            openRoomActivity(signal.roomCode, signal.roomName);
                        })
                        .setNegativeButton("Dismiss", null)
                        .show();
            } else if (SignalMessage.TYPE_FRIEND_REQUEST.equals(signal.type)) {
                Toast.makeText(this, "New friend request from " + signal.fromUserName, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
