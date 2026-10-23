package com.takidz.run.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.takidz.run.R;
import com.takidz.run.models.Message;
import com.takidz.run.models.RoomData;
import com.takidz.run.models.TaskItem;
import com.takidz.run.network.CatboxUploader;
import com.takidz.run.network.MqttRoomManager;
import com.takidz.run.network.TranslationHelper;
import com.takidz.run.ui.adapters.MessagesAdapter;
import com.takidz.run.ui.adapters.TasksAdapter;
import com.takidz.run.ui.views.RoomPatternBackgroundView;
import com.takidz.run.ui.views.TaskProgressRingView;
import com.takidz.run.utils.FeedbackTouchHelper;
import com.takidz.run.utils.LocaleHelper;
import com.takidz.run.utils.MotionHelper;
import com.takidz.run.utils.PrefsManager;
import com.takidz.run.utils.ThemeHelper;
import com.takidz.run.utils.VoiceRecorderHelper;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Run v3.0.0 RoomActivity:
 * - #14 & #15: Warm Paper editorial room header, highlighter accent, pill composer, and 60fps animations.
 * - #16: Adaptive responsive layout (single-pane on phones; two-pane Study Desk with Chat + Tasks/Notes side-by-side on tablets/wide screens).
 * - #17: Per-room identity (color & background pattern chosen by room owner, synced via MQTT to all members & late joiners).
 * - #18: Quick gestures (Swipe message -> reply/quote; Long-press message -> emoji reaction picker; Swipe down -> close media viewer) + first-use hints.
 * - #20: Light sound & vibration touches on send/receive and gestures.
 * - #23: Smart invite link sharing (HTTPS App Link + runapp:// scheme).
 * - #26: Sent / Delivered / Read check-mark indicators.
 * - #27: Timed room mute (1h / 8h / until tomorrow) with visible countdown badge & 1-tap unmute.
 * - #28: Full Arabic RTL mode + automatic incoming message translation.
 * - #29: Live connection quality indicator (Connected / Weak signal / Reconnecting).
 * - #31: Personal Saved Messages ("My saved") bookmarking & automatic jump-to-message from My Saved.
 */
public class RoomActivity extends AppCompatActivity implements MqttRoomManager.RoomListener {

    private static final int REQ_RECORD_AUDIO = 201;
    private static final String[] QUICK_EMOJIS = new String[]{"👍", "❤️", "😂", "🔥", "👏", "🎯"};

    private PrefsManager prefs;
    private MqttRoomManager mqttManager;
    private CatboxUploader catboxUploader;
    private TranslationHelper translationHelper;
    private VoiceRecorderHelper voiceHelper;
    private ThemeHelper.Palette palette;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String roomCode;
    private String roomName;
    private String pendingJumpMessageId = null;
    private Message.ReplyRef activeReplyRef = null;
    private int currentTab = 0; // 0: Chat, 1: Tasks, 2: Notes
    private int lastMessageCount = 0;
    private int roomOnlineCount = 1;
    private int roomAccentColor;
    private String roomBgStyle = "paper";
    private boolean isTwoPaneTabletDesk = false;

    // Root & Background
    private FrameLayout rootRoomFrame;
    private RoomPatternBackgroundView roomPatternBackground;
    private LinearLayout rootRoomLayout;
    private MaterialCardView topBarCard;
    private ImageButton btnBack;
    private TextView tvRoomHeaderOrb;
    private TextView tvRoomTitle;
    private View viewRoomTitleHighlighter;
    private TextView tvRoomCodeBadge;
    private TextView tvRoomSyncStatus;
    private TextView tvRoomMuteStatusPill;
    private ImageButton btnRoomIdentity;
    private ImageButton btnMuteRoom;
    private ImageButton btnShareRoom;
    private ImageButton btnDeleteRoom;

    // 3 Tab Buttons
    private LinearLayout layoutRoomTabsBar;
    private MaterialButton btnTabChat;
    private MaterialButton btnTabTasks;
    private MaterialButton btnTabNotes;

    // Chat Panel Views
    private LinearLayout chatPanel;
    private MaterialCardView cardGestureHintBanner;
    private TextView tvGestureHintText;
    private MaterialButton btnDismissGestureHint;
    private RecyclerView rvMessages;
    private MaterialButton btnScrollToBottom;
    private TextView tvTypingIndicator;
    private TextView tvRecordingTimerBanner;
    private LinearLayout replyBanner;
    private TextView tvReplyingToText;
    private ImageButton btnCancelReply;
    private MaterialCardView cardComposerBar;
    private ImageButton btnAttachFile;
    private ImageButton btnRecordVoice;
    private EditText etMessageInput;
    private MaterialButton btnSendMessage;

    // Tasks Panel Views
    private LinearLayout tasksPanel;
    private MaterialCardView progressHeaderCard;
    private TaskProgressRingView progressRingView;
    private TextView tvTasksSummaryTitle;
    private TextView tvTasksSummarySub;
    private EditText etNewTaskInput;
    private MaterialButton btnAddTask;
    private RecyclerView rvTasks;

    // Shared Notes Panel Views
    private LinearLayout notesPanel;
    private MaterialCardView cardNotesPaper;
    private TextView tvNoteStatus;
    private MaterialButton btnNoteSync;
    private MaterialButton btnNoteBullet;
    private MaterialButton btnNoteCheck;
    private MaterialButton btnNoteHeading;
    private MaterialButton btnNoteCopy;
    private MaterialButton btnNoteClear;
    private EditText etSharedNoteContent;
    private TextView tvNoteWordCount;
    private boolean isUpdatingNoteRemotely = false;

    private final Runnable noteDebounceSaveRunnable = new Runnable() {
        @Override
        public void run() {
            if (isUpdatingNoteRemotely) return;
            String text = etSharedNoteContent.getText().toString();
            mqttManager.updateRoomNote(text);
            tvNoteStatus.setText(prefs.isArabic() ? "تم الحفظ والمزامنة مع الغرفة" : "Saved & synced with room");
        }
    };

    // #27: Periodic timer to refresh Timed Mute remaining time & auto-expire visually
    private final Runnable muteStatusRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            updateMuteHeaderUI();
            mainHandler.postDelayed(this, 30_000L);
        }
    };

    private MessagesAdapter messagesAdapter;
    private TasksAdapter tasksAdapter;
    private ActivityResultLauncher<String> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = new PrefsManager(this);
        LocaleHelper.applyLocaleAndLayoutDirection(this, prefs);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        mqttManager = MqttRoomManager.getInstance(this);
        catboxUploader = new CatboxUploader();
        translationHelper = new TranslationHelper();
        voiceHelper = new VoiceRecorderHelper(this);
        palette = ThemeHelper.getPalette(prefs.getThemeMode());
        roomAccentColor = palette.accent;

        roomCode = getIntent().getStringExtra("room_code");
        roomName = getIntent().getStringExtra("room_name");
        pendingJumpMessageId = getIntent().getStringExtra("jump_to_message_id");
        if (roomCode == null || roomCode.isEmpty()) {
            finish();
            return;
        }

        // Ensure MQTT manager is joined to this room (e.g. if opened from notification)
        if (mqttManager.getActiveRoomCode() == null || !mqttManager.getActiveRoomCode().equalsIgnoreCase(roomCode)) {
            mqttManager.joinRoom(roomCode, roomName != null ? roomName : ("Room " + roomCode), null);
        }

        setupFilePicker();
        initViews();
        setupSwipeToReplyGesture();
        setupNotesTab();
        applyThemeAndRoomIdentity();
        applyLocalizedRoomLabels();
        updateAdaptiveLayoutMode(getResources().getConfiguration());
        switchTab(0);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateAdaptiveLayoutMode(newConfig);
    }

    /**
     * #16: Responsive/Adaptive Layout — enables Two-Pane Study Desk mode on tablets, foldables, and wide landscape screens.
     */
    private void updateAdaptiveLayoutMode(Configuration config) {
        isTwoPaneTabletDesk = config != null && config.screenWidthDp >= 600;
        switchTab(currentTab);
    }

    private void setupFilePicker() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        showAttachmentCaptionDialog(uri);
                    }
                }
        );
    }

    private void initViews() {
        rootRoomFrame = findViewById(R.id.rootRoomFrame);
        roomPatternBackground = findViewById(R.id.roomPatternBackground);
        rootRoomLayout = findViewById(R.id.rootRoomLayout);
        topBarCard = findViewById(R.id.topBarCard);
        btnBack = findViewById(R.id.btnBack);
        tvRoomHeaderOrb = findViewById(R.id.tvRoomHeaderOrb);
        tvRoomTitle = findViewById(R.id.tvRoomTitle);
        viewRoomTitleHighlighter = findViewById(R.id.viewRoomTitleHighlighter);
        tvRoomCodeBadge = findViewById(R.id.tvRoomCodeBadge);
        tvRoomSyncStatus = findViewById(R.id.tvRoomSyncStatus);
        tvRoomMuteStatusPill = findViewById(R.id.tvRoomMuteStatusPill);
        btnRoomIdentity = findViewById(R.id.btnRoomIdentity);
        btnMuteRoom = findViewById(R.id.btnMuteRoom);
        btnShareRoom = findViewById(R.id.btnShareRoom);
        btnDeleteRoom = findViewById(R.id.btnDeleteRoom);

        layoutRoomTabsBar = findViewById(R.id.layoutRoomTabsBar);
        btnTabChat = findViewById(R.id.btnTabChat);
        btnTabTasks = findViewById(R.id.btnTabTasks);
        btnTabNotes = findViewById(R.id.btnTabNotes);

        chatPanel = findViewById(R.id.chatPanel);
        cardGestureHintBanner = findViewById(R.id.cardGestureHintBanner);
        tvGestureHintText = findViewById(R.id.tvGestureHintText);
        btnDismissGestureHint = findViewById(R.id.btnDismissGestureHint);
        rvMessages = findViewById(R.id.rvMessages);
        btnScrollToBottom = findViewById(R.id.btnScrollToBottom);
        tvTypingIndicator = findViewById(R.id.tvTypingIndicator);
        tvRecordingTimerBanner = findViewById(R.id.tvRecordingTimerBanner);
        replyBanner = findViewById(R.id.replyBanner);
        tvReplyingToText = findViewById(R.id.tvReplyingToText);
        btnCancelReply = findViewById(R.id.btnCancelReply);
        cardComposerBar = findViewById(R.id.cardComposerBar);
        btnAttachFile = findViewById(R.id.btnAttachFile);
        btnRecordVoice = findViewById(R.id.btnRecordVoice);
        etMessageInput = findViewById(R.id.etMessageInput);
        btnSendMessage = findViewById(R.id.btnSendMessage);

        tasksPanel = findViewById(R.id.tasksPanel);
        progressHeaderCard = findViewById(R.id.progressHeaderCard);
        progressRingView = findViewById(R.id.progressRingView);
        tvTasksSummaryTitle = findViewById(R.id.tvTasksSummaryTitle);
        tvTasksSummarySub = findViewById(R.id.tvTasksSummarySub);
        etNewTaskInput = findViewById(R.id.etNewTaskInput);
        btnAddTask = findViewById(R.id.btnAddTask);
        rvTasks = findViewById(R.id.rvTasks);

        notesPanel = findViewById(R.id.notesPanel);
        cardNotesPaper = findViewById(R.id.cardNotesPaper);
        tvNoteStatus = findViewById(R.id.tvNoteStatus);
        btnNoteSync = findViewById(R.id.btnNoteSync);
        btnNoteBullet = findViewById(R.id.btnNoteBullet);
        btnNoteCheck = findViewById(R.id.btnNoteCheck);
        btnNoteHeading = findViewById(R.id.btnNoteHeading);
        btnNoteCopy = findViewById(R.id.btnNoteCopy);
        btnNoteClear = findViewById(R.id.btnNoteClear);
        etSharedNoteContent = findViewById(R.id.etSharedNoteContent);
        tvNoteWordCount = findViewById(R.id.tvNoteWordCount);

        String titleText = roomName != null && !roomName.isEmpty() ? roomName : ("Room " + roomCode);
        tvRoomTitle.setText(titleText);
        tvRoomCodeBadge.setText("#" + roomCode);
        updateRoomHeaderOrbText(titleText);

        // #18: First-use discoverable gesture hint banner
        if (!prefs.hasSeenGestureHint()) {
            cardGestureHintBanner.setVisibility(View.VISIBLE);
        }
        btnDismissGestureHint.setOnClickListener(v -> {
            prefs.setSeenGestureHint(true);
            cardGestureHintBanner.setVisibility(View.GONE);
        });

        btnBack.setOnClickListener(v -> finish());
        tvRoomCodeBadge.setOnClickListener(v -> copyToClipboard("Room Code", roomCode,
                prefs.isArabic() ? ("تم نسخ رمز الغرفة #" + roomCode) : ("Copied room code #" + roomCode)));

        // #17: Per-Room Identity Color & Background Picker
        btnRoomIdentity.setOnClickListener(v -> showRoomIdentitySheet());

        // #27: Timed Room Mute Sheet
        btnMuteRoom.setOnClickListener(v -> showTimedMuteSheet());
        tvRoomMuteStatusPill.setOnClickListener(v -> showTimedMuteSheet());

        // #23: Smart Invite Share Sheet
        btnShareRoom.setOnClickListener(v -> showSmartInviteShareDialog());

        // Leave / Delete / Archive Room Sheet
        btnDeleteRoom.setOnClickListener(v -> confirmLeaveArchiveOrDeleteRoom());

        btnTabChat.setOnClickListener(v -> switchTab(0));
        btnTabTasks.setOnClickListener(v -> switchTab(1));
        btnTabNotes.setOnClickListener(v -> switchTab(2));

        // Setup Messages RecyclerView
        LinearLayoutManager chatLm = new LinearLayoutManager(this);
        chatLm.setStackFromEnd(true);
        rvMessages.setLayoutManager(chatLm);
        messagesAdapter = new MessagesAdapter(this, prefs.getUserId(), palette, new MessagesAdapter.MessageInteractionListener() {
            @Override
            public void onReplyClicked(Message message) {
                activateReplyToMessage(message);
            }

            @Override
            public void onReplyQuoteClicked(String targetMessageId) {
                scrollToMessageById(targetMessageId);
            }

            @Override
            public void onTranslateClicked(Message message) {
                handleTranslateMessage(message, true);
            }

            @Override
            public void onPlayVoiceClicked(Message message) {
                handleVoicePlayback(message);
            }

            @Override
            public void onImageClicked(String imageUrlOrDataUri, String caption) {
                showFullscreenImageDialog(imageUrlOrDataUri, caption);
            }

            @Override
            public void onSaveClicked(Message message) {
                toggleSaveMessage(message);
            }

            @Override
            public void onReactionToggled(Message message, String emoji) {
                FeedbackTouchHelper.onGestureTick(RoomActivity.this, prefs, rvMessages);
                mqttManager.toggleMessageReaction(message.id, emoji);
            }

            @Override
            public void onMessageLongPressed(Message message, View anchorView) {
                FeedbackTouchHelper.onGestureTick(RoomActivity.this, prefs, anchorView);
                dismissGestureHintIfVisible();
                showMessageReactionAndActionSheet(message);
            }
        });
        messagesAdapter.setRoomCode(roomCode);
        rvMessages.setAdapter(messagesAdapter);

        rvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (isNearBottom()) {
                    btnScrollToBottom.setVisibility(View.GONE);
                }
            }
        });

        btnScrollToBottom.setOnClickListener(v -> {
            btnScrollToBottom.setVisibility(View.GONE);
            int count = messagesAdapter.getItemCount();
            if (count > 0) {
                rvMessages.smoothScrollToPosition(count - 1);
            }
        });

        // Setup Tasks RecyclerView
        rvTasks.setLayoutManager(new LinearLayoutManager(this));
        tasksAdapter = new TasksAdapter(palette, new TasksAdapter.TaskActionListener() {
            @Override
            public void onTaskToggled(TaskItem task, boolean isDone) {
                FeedbackTouchHelper.onGestureTick(RoomActivity.this, prefs, rvTasks);
                mqttManager.toggleTask(task.id, isDone);
            }

            @Override
            public void onTaskDeleted(TaskItem task) {
                mqttManager.deleteTask(task.id);
            }
        });
        rvTasks.setAdapter(tasksAdapter);

        btnCancelReply.setOnClickListener(v -> {
            activeReplyRef = null;
            replyBanner.setVisibility(View.GONE);
        });

        etMessageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s != null && s.length() > 0) {
                    mqttManager.sendTypingSignal();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnSendMessage.setOnClickListener(v -> {
            String text = etMessageInput.getText().toString().trim();
            if (text.isEmpty()) return;
            MotionHelper.animateButtonPulse(btnSendMessage, prefs.isLiteModeEnabled());
            FeedbackTouchHelper.onMessageSent(this, prefs, btnSendMessage);
            mqttManager.sendTextMessage(text, activeReplyRef);
            etMessageInput.setText("");
            activeReplyRef = null;
            replyBanner.setVisibility(View.GONE);
            if (messagesAdapter.getItemCount() > 0) {
                rvMessages.scrollToPosition(messagesAdapter.getItemCount() - 1);
            }
        });

        btnAttachFile.setOnClickListener(v -> filePickerLauncher.launch("*/*"));
        btnRecordVoice.setOnClickListener(v -> toggleVoiceRecording());

        btnAddTask.setOnClickListener(v -> {
            String taskTitle = etNewTaskInput.getText().toString().trim();
            if (taskTitle.isEmpty()) return;
            FeedbackTouchHelper.onMessageSent(this, prefs, btnAddTask);
            mqttManager.addTask(taskTitle);
            etNewTaskInput.setText("");
        });
    }

    /**
     * #18: Swipe on a message -> reply/quote it without fighting vertical scrolling.
     */
    private void setupSwipeToReplyGesture() {
        final float density = getResources().getDisplayMetrics().density;
        final float maxSwipePx = 68f * density;
        final float triggerThresholdPx = 44f * density;
        final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Drawable replyIcon = ContextCompat.getDrawable(this, R.drawable.ic_reply);

        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT
        ) {
            private boolean triggeredHapticForCurrentSwipe = false;
            private int swipedPositionPendingReply = -1;

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // Never remove the item; spring back and trigger reply
                int pos = viewHolder.getBindingAdapterPosition();
                messagesAdapter.notifyItemChanged(pos);
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                // Returning > 1.0f prevents the item from ever being dismissed off-screen
                return 2.0f;
            }

            @Override
            public float getSwipeEscapeVelocity(float defaultValue) {
                return Float.MAX_VALUE;
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    float clampedDx = Math.max(-maxSwipePx, Math.min(maxSwipePx, dX * 0.55f));
                    float absDx = Math.abs(clampedDx);

                    // Draw subtle reply indicator circle behind the swiped message
                    if (absDx > 8f * density) {
                        float progress = Math.min(1f, absDx / triggerThresholdPx);
                        View itemView = viewHolder.itemView;
                        float cy = itemView.getTop() + itemView.getHeight() / 2f;
                        float cx = clampedDx > 0
                                ? (itemView.getLeft() + 26f * density)
                                : (itemView.getRight() - 26f * density);
                        float radius = (14f * density) * (0.6f + 0.4f * progress);

                        circlePaint.setColor(progress >= 1f ? roomAccentColor : palette.panelSoft);
                        c.drawCircle(cx, cy, radius, circlePaint);

                        if (replyIcon != null) {
                            int iconHalf = (int) (8f * density * (0.7f + 0.3f * progress));
                            replyIcon.setTint(progress >= 1f ? palette.accentInk : palette.text);
                            replyIcon.setBounds((int) cx - iconHalf, (int) cy - iconHalf, (int) cx + iconHalf, (int) cy + iconHalf);
                            replyIcon.draw(c);
                        }
                    }

                    if (isCurrentlyActive && absDx >= triggerThresholdPx && !triggeredHapticForCurrentSwipe) {
                        triggeredHapticForCurrentSwipe = true;
                        swipedPositionPendingReply = viewHolder.getBindingAdapterPosition();
                        FeedbackTouchHelper.onGestureTick(RoomActivity.this, prefs, viewHolder.itemView);
                    } else if (isCurrentlyActive && absDx < triggerThresholdPx * 0.7f) {
                        triggeredHapticForCurrentSwipe = false;
                    }

                    if (!isCurrentlyActive && triggeredHapticForCurrentSwipe && swipedPositionPendingReply >= 0) {
                        final int targetPos = swipedPositionPendingReply;
                        triggeredHapticForCurrentSwipe = false;
                        swipedPositionPendingReply = -1;
                        mainHandler.post(() -> {
                            Message msg = messagesAdapter.getMessageAt(targetPos);
                            if (msg != null) {
                                dismissGestureHintIfVisible();
                                activateReplyToMessage(msg);
                            }
                        });
                    }

                    super.onChildDraw(c, recyclerView, viewHolder, clampedDx, dY, actionState, isCurrentlyActive);
                } else {
                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                }
            }
        };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rvMessages);
    }

    private void dismissGestureHintIfVisible() {
        if (cardGestureHintBanner.getVisibility() == View.VISIBLE) {
            prefs.setSeenGestureHint(true);
            cardGestureHintBanner.setVisibility(View.GONE);
        }
    }

    private void activateReplyToMessage(Message message) {
        if (message == null) return;
        boolean ar = prefs.isArabic();
        activeReplyRef = new Message.ReplyRef(message.id, message.text, message.author);
        replyBanner.setVisibility(View.VISIBLE);
        tvReplyingToText.setText((ar ? "الرد على " : "Replying to ") + message.author + ": " + message.text);
        etMessageInput.requestFocus();
    }

    /**
     * #18 & #31: Long-press reaction picker & quick message actions sheet.
     */
    private void showMessageReactionAndActionSheet(Message message) {
        if (message == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_RunApp);
        float d = getResources().getDisplayMetrics().density;
        boolean ar = prefs.isArabic();

        LinearLayout root = new LinearLayout(this);
        LocaleHelper.applyViewDirection(root, ar);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.panel);
        int pad = (int) (20 * d);
        root.setPadding(pad, pad, pad, pad);

        // 1. Emoji Reaction Picker Row (#18)
        LinearLayout emojiRow = new LinearLayout(this);
        emojiRow.setOrientation(LinearLayout.HORIZONTAL);
        emojiRow.setGravity(Gravity.CENTER);
        for (String emoji : QUICK_EMOJIS) {
            TextView emojiBtn = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (48 * d), 1f);
            lp.setMargins((int) (4 * d), 0, (int) (4 * d), 0);
            emojiBtn.setLayoutParams(lp);
            emojiBtn.setGravity(Gravity.CENTER);
            emojiBtn.setText(emoji);
            emojiBtn.setTextSize(22f);

            GradientDrawable circle = new GradientDrawable();
            circle.setCornerRadius(24 * d);
            circle.setColor(palette.panelSoft);
            emojiBtn.setBackground(circle);

            emojiBtn.setOnClickListener(v -> {
                FeedbackTouchHelper.onGestureTick(this, prefs, v);
                mqttManager.toggleMessageReaction(message.id, emoji);
                dialog.dismiss();
            });
            emojiRow.addView(emojiBtn);
        }
        root.addView(emojiRow);

        // 2. Quick Actions (Save to My Saved #31, Reply, Translate, Copy)
        boolean isSaved = prefs.isMessageSaved(roomCode, message.id);

        MaterialButton btnSave = new MaterialButton(this);
        btnSave.setBackgroundTintList(ColorStateList.valueOf(com.google.android.material.color.MaterialColors.getColor(btnSave, com.google.android.material.R.attr.colorSecondaryContainer)));
        btnSave.setTextColor(com.google.android.material.color.MaterialColors.getColor(btnSave, com.google.android.material.R.attr.colorOnSecondaryContainer));
        btnSave.setIconTint(ColorStateList.valueOf(com.google.android.material.color.MaterialColors.getColor(btnSave, com.google.android.material.R.attr.colorOnSecondaryContainer)));
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (46 * d)
        );
        actionLp.topMargin = (int) (14 * d);
        btnSave.setLayoutParams(actionLp);
        btnSave.setIconResource(R.drawable.ic_bookmark);
        btnSave.setText(isSaved
                ? (ar ? "إزالة من محفوظاتي (My Saved)" : "Remove from My Saved")
                : (ar ? "حفظ في محفوظاتي (My Saved)" : "Save to My Saved"));
        btnSave.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        btnSave.setTextColor(palette.accentInk);
        btnSave.setIconTint(ColorStateList.valueOf(palette.accentInk));
        btnSave.setOnClickListener(v -> {
            toggleSaveMessage(message);
            dialog.dismiss();
        });
        root.addView(btnSave);

        MaterialButton btnReplyAction = new MaterialButton(this);
        btnReplyAction.setBackgroundTintList(ColorStateList.valueOf(com.google.android.material.color.MaterialColors.getColor(btnReplyAction, com.google.android.material.R.attr.colorSecondaryContainer)));
        btnReplyAction.setTextColor(com.google.android.material.color.MaterialColors.getColor(btnReplyAction, com.google.android.material.R.attr.colorOnSecondaryContainer));
        btnReplyAction.setIconTint(ColorStateList.valueOf(com.google.android.material.color.MaterialColors.getColor(btnReplyAction, com.google.android.material.R.attr.colorOnSecondaryContainer)));
        LinearLayout.LayoutParams repLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (44 * d)
        );
        repLp.topMargin = (int) (8 * d);
        btnReplyAction.setLayoutParams(repLp);
        btnReplyAction.setIconResource(R.drawable.ic_reply);
        btnReplyAction.setText(ar ? "الرد على هذه الرسالة" : "Reply / Quote Message");
        btnReplyAction.setBackgroundTintList(ColorStateList.valueOf(palette.panelSoft));
        btnReplyAction.setTextColor(palette.text);
        btnReplyAction.setIconTint(ColorStateList.valueOf(palette.text));
        btnReplyAction.setOnClickListener(v -> {
            activateReplyToMessage(message);
            dialog.dismiss();
        });
        root.addView(btnReplyAction);

        MaterialButton btnCopyAction = new MaterialButton(this);
        btnCopyAction.setBackgroundTintList(ColorStateList.valueOf(com.google.android.material.color.MaterialColors.getColor(btnCopyAction, com.google.android.material.R.attr.colorSecondaryContainer)));
        btnCopyAction.setTextColor(com.google.android.material.color.MaterialColors.getColor(btnCopyAction, com.google.android.material.R.attr.colorOnSecondaryContainer));
        btnCopyAction.setIconTint(ColorStateList.valueOf(com.google.android.material.color.MaterialColors.getColor(btnCopyAction, com.google.android.material.R.attr.colorOnSecondaryContainer)));
        btnCopyAction.setLayoutParams(repLp);
        btnCopyAction.setIconResource(R.drawable.ic_content_copy);
        btnCopyAction.setText(ar ? "نسخ النص" : "Copy Message Text");
        btnCopyAction.setBackgroundTintList(ColorStateList.valueOf(palette.panelSoft));
        btnCopyAction.setTextColor(palette.text);
        btnCopyAction.setIconTint(ColorStateList.valueOf(palette.text));
        btnCopyAction.setOnClickListener(v -> {
            copyToClipboard("Message", message.text != null ? message.text : "",
                    ar ? "تم نسخ الرسالة" : "Copied message");
            dialog.dismiss();
        });
        root.addView(btnCopyAction);

        dialog.setContentView(root);
        dialog.show();
    }

    private void toggleSaveMessage(Message message) {
        if (message == null) return;
        boolean ar = prefs.isArabic();
        boolean nowSaved = prefs.toggleSaveMessage(roomCode, tvRoomTitle.getText().toString(), message);
        messagesAdapter.notifyMessageUpdatedById(message.id);
        Toast.makeText(this,
                nowSaved
                        ? (ar ? "تم الحفظ في محفوظاتي ★" : "Saved to My Saved ★")
                        : (ar ? "تمت الإزالة من محفوظاتي" : "Removed from My Saved"),
                Toast.LENGTH_SHORT).show();
    }

    // =========================================================================
    // #17: Per-Room Identity Color & Background Picker Sheet
    // =========================================================================

    private void showRoomIdentitySheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_RunApp);
        float d = getResources().getDisplayMetrics().density;
        boolean ar = prefs.isArabic();
        boolean isOwner = prefs.isRoomOwner(roomCode, mqttManager.getActiveRoomData());

        LinearLayout root = new LinearLayout(this);
        LocaleHelper.applyViewDirection(root, ar);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.panel);
        int pad = (int) (20 * d);
        root.setPadding(pad, pad, pad, pad);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(ar ? "هوية الغرفة (اللون والخلفية)" : "Room Identity (Color & Background)");
        tvTitle.setTextColor(palette.text);
        tvTitle.setTextSize(19f);
        tvTitle.setTypeface(tvTitle.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(isOwner
                ? (ar ? "أنت مالك الغرفة — اختيارك يظهر لجميع الأعضاء مباشرة عبر MQTT." : "Chosen by room owner — synced identically to every member via MQTT.")
                : (ar ? "تتم مزامنة لون وخلفية هذه الغرفة من قِبل منشئ الغرفة." : "Synced from the room owner for all members in this room."));
        tvSub.setTextColor(palette.muted);
        tvSub.setTextSize(12f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subLp.topMargin = (int) (4 * d);
        subLp.bottomMargin = (int) (14 * d);
        tvSub.setLayoutParams(subLp);
        root.addView(tvSub);

        final String[] chosenHex = {String.format("#%06X", (0xFFFFFF & roomAccentColor))};
        final String[] chosenStyle = {roomBgStyle};

        // 1. Color Swatches Row
        HorizontalScrollView colorScroll = new HorizontalScrollView(this);
        colorScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        for (String hex : ThemeHelper.ROOM_ACCENT_HEX) {
            View swatch = new View(this);
            int size = (int) (42 * d);
            LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(size, size);
            sLp.setMarginEnd((int) (10 * d));
            swatch.setLayoutParams(sLp);

            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Color.parseColor(hex));
            circle.setStroke((int) (2 * d), palette.text);
            swatch.setBackground(circle);

            if (isOwner) {
                swatch.setOnClickListener(v -> {
                    chosenHex[0] = hex;
                    roomAccentColor = Color.parseColor(hex);
                    roomPatternBackground.setRoomIdentity(chosenStyle[0], roomAccentColor, palette.isLight);
                    FeedbackTouchHelper.onGestureTick(this, prefs, v);
                });
            }
            colorRow.addView(swatch);
        }
        colorScroll.addView(colorRow);
        root.addView(colorScroll);

        // 2. Background Style Buttons
        String[] styleLabelsEn = {"Warm Paper", "Dot Grid", "Ruled Lines", "Graph Grid", "Calm Waves", "Origami"};
        String[] styleLabelsAr = {"ورق دافئ", "نقاط هندسية", "دفتر مسطّر", "شبكة دراسة", "تموجات هادئة", "أوريغامي"};

        HorizontalScrollView bgScroll = new HorizontalScrollView(this);
        bgScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams bgScrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bgScrollLp.topMargin = (int) (14 * d);
        bgScroll.setLayoutParams(bgScrollLp);

        LinearLayout bgRow = new LinearLayout(this);
        bgRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < ThemeHelper.ROOM_BG_STYLES.length; i++) {
            final String styleKey = ThemeHelper.ROOM_BG_STYLES[i];
            MaterialButton btnStyle = new MaterialButton(this);
        btnStyle.setBackgroundTintList(ColorStateList.valueOf(com.google.android.material.color.MaterialColors.getColor(btnStyle, com.google.android.material.R.attr.colorSecondaryContainer)));
        btnStyle.setTextColor(com.google.android.material.color.MaterialColors.getColor(btnStyle, com.google.android.material.R.attr.colorOnSecondaryContainer));
        btnStyle.setIconTint(ColorStateList.valueOf(com.google.android.material.color.MaterialColors.getColor(btnStyle, com.google.android.material.R.attr.colorOnSecondaryContainer)));
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, (int) (38 * d)
            );
            bLp.setMarginEnd((int) (8 * d));
            btnStyle.setLayoutParams(bLp);
            btnStyle.setText(ar ? styleLabelsAr[i] : styleLabelsEn[i]);
            btnStyle.setTextSize(12f);
            btnStyle.setBackgroundTintList(ColorStateList.valueOf(palette.panelSoft));
            btnStyle.setTextColor(palette.text);
            if (isOwner) {
                btnStyle.setOnClickListener(v -> {
                    chosenStyle[0] = styleKey;
                    roomBgStyle = styleKey;
                    roomPatternBackground.setRoomIdentity(chosenStyle[0], roomAccentColor, palette.isLight);
                    FeedbackTouchHelper.onGestureTick(this, prefs, v);
                });
            }
            bgRow.addView(btnStyle);
        }
        bgScroll.addView(bgRow);
        root.addView(bgScroll);

        if (isOwner) {
            MaterialButton btnSaveIdentity = new MaterialButton(this);
            LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) (48 * d)
            );
            saveLp.topMargin = (int) (18 * d);
            btnSaveIdentity.setLayoutParams(saveLp);
            btnSaveIdentity.setText(ar ? "حفظ ومزامنة هوية الغرفة للجميع" : "Sync Identity to Room");
            btnSaveIdentity.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
            btnSaveIdentity.setTextColor(palette.accentInk);
            btnSaveIdentity.setCornerRadius((int) (16 * d));
            btnSaveIdentity.setOnClickListener(v -> {
                mqttManager.updateActiveRoomIdentity(chosenHex[0], chosenStyle[0]);
                applyThemeAndRoomIdentity();
                Toast.makeText(this,
                        ar ? "تمت مزامنة لون وخلفية الغرفة لجميع الأعضاء!" : "Room identity synced to all members!",
                        Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            root.addView(btnSaveIdentity);
        }

        dialog.setContentView(root);
        dialog.show();
    }

    // =========================================================================
    // #27: Timed Room Mute Dialog (1 hour / 8 hours / Until tomorrow / Unmute)
    // =========================================================================

    private void showTimedMuteSheet() {
        boolean ar = prefs.isArabic();
        boolean currentlyMuted = prefs.isRoomMuted(roomCode);
        String remaining = prefs.getRoomMuteRemainingLabel(roomCode);

        String[] options;
        if (currentlyMuted) {
            options = new String[]{
                    ar ? ("🔔 إلغاء الكتم الآن (" + remaining + ")") : ("🔔 Unmute room now (" + remaining + ")"),
                    ar ? "🔕 كتم لمدة ساعة واحدة" : "🔕 Mute for 1 hour",
                    ar ? "🔕 كتم لمدة 8 ساعات" : "🔕 Mute for 8 hours",
                    ar ? "🔕 كتم حتى الغد (8:00 صباحاً)" : "🔕 Mute until tomorrow (8:00 AM)"
            };
        } else {
            options = new String[]{
                    ar ? "🔕 كتم لمدة ساعة واحدة" : "🔕 Mute for 1 hour",
                    ar ? "🔕 كتم لمدة 8 ساعات" : "🔕 Mute for 8 hours",
                    ar ? "🔕 كتم حتى الغد (8:00 صباحاً)" : "🔕 Mute until tomorrow (8:00 AM)"
            };
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(ar ? "كتم إشعارات هذه الغرفة" : "Timed Room Mute")
                .setItems(options, (dialog, which) -> {
                    int idx = currentlyMuted ? which : (which + 1);
                    if (idx == 0) {
                        prefs.clearRoomMute(roomCode);
                        Toast.makeText(this, ar ? "تم تفعيل إشعارات الغرفة" : "Room notifications unmuted", Toast.LENGTH_SHORT).show();
                    } else if (idx == 1) {
                        prefs.setRoomMutedForDurationMs(roomCode, 3600_000L);
                        Toast.makeText(this, ar ? "تم كتم الغرفة لمدة ساعة" : "Muted for 1 hour", Toast.LENGTH_SHORT).show();
                    } else if (idx == 2) {
                        prefs.setRoomMutedForDurationMs(roomCode, 8L * 3600_000L);
                        Toast.makeText(this, ar ? "تم كتم الغرفة لمدة 8 ساعات" : "Muted for 8 hours", Toast.LENGTH_SHORT).show();
                    } else if (idx == 3) {
                        prefs.setRoomMutedUntilTomorrow(roomCode);
                        Toast.makeText(this, ar ? "تم كتم الغرفة حتى صباح الغد" : "Muted until tomorrow", Toast.LENGTH_SHORT).show();
                    }
                    updateMuteHeaderUI();
                })
                .setNegativeButton(ar ? "إغلاق" : "Close", null)
                .show();
    }

    private void updateMuteHeaderUI() {
        if (prefs.isRoomMuted(roomCode)) {
            btnMuteRoom.setImageResource(R.drawable.ic_notifications_off);
            btnMuteRoom.setColorFilter(palette.warning);
            tvRoomMuteStatusPill.setVisibility(View.VISIBLE);
            tvRoomMuteStatusPill.setText("🔕 " + prefs.getRoomMuteRemainingLabel(roomCode));
            tvRoomMuteStatusPill.setTextColor(palette.warning);
        } else {
            btnMuteRoom.setImageResource(R.drawable.ic_notifications);
            btnMuteRoom.setColorFilter(palette.text);
            tvRoomMuteStatusPill.setVisibility(View.GONE);
        }
    }

    private void setupNotesTab() {
        etSharedNoteContent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateNoteStats(s != null ? s.toString() : "");
                if (!isUpdatingNoteRemotely) {
                    tvNoteStatus.setText(prefs.isArabic() ? "جارٍ الكتابة… (مزامنة تلقائية)" : "Editing… (auto-syncing)");
                    mainHandler.removeCallbacks(noteDebounceSaveRunnable);
                    mainHandler.postDelayed(noteDebounceSaveRunnable, 1200L);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnNoteSync.setOnClickListener(v -> {
            mainHandler.removeCallbacks(noteDebounceSaveRunnable);
            String text = etSharedNoteContent.getText().toString();
            mqttManager.updateRoomNote(text);
            tvNoteStatus.setText(prefs.isArabic() ? "تمت المزامنة الآن" : "Synced with room now");
        });

        btnNoteBullet.setOnClickListener(v -> insertTextAtCursor("\n• "));
        btnNoteCheck.setOnClickListener(v -> insertTextAtCursor("\n[ ] "));
        btnNoteHeading.setOnClickListener(v -> insertTextAtCursor("\n## "));

        btnNoteCopy.setOnClickListener(v -> {
            String content = etSharedNoteContent.getText().toString();
            copyToClipboard("Shared Notes", content,
                    prefs.isArabic() ? "تم نسخ الملاحظات المشتركة" : "Copied notes to clipboard");
        });

        btnNoteClear.setOnClickListener(v -> {
            boolean ar = prefs.isArabic();
            new MaterialAlertDialogBuilder(this)
                    .setTitle(ar ? "مسح الملاحظات المشتركة؟" : "Clear Shared Notes")
                    .setMessage(ar ? "هل تريد مسح جميع الملاحظات المشتركة في هذه الغرفة؟" : "Clear all shared notes for this room?")
                    .setPositiveButton(ar ? "مسح" : "Clear", (dialog, which) -> {
                        etSharedNoteContent.setText("");
                        mqttManager.updateRoomNote("");
                        tvNoteStatus.setText(ar ? "تم مسح الملاحظات" : "Notes cleared");
                    })
                    .setNegativeButton(ar ? "إلغاء" : "Cancel", null)
                    .show();
        });
    }

    private void insertTextAtCursor(String insertion) {
        int start = Math.max(etSharedNoteContent.getSelectionStart(), 0);
        int end = Math.max(etSharedNoteContent.getSelectionEnd(), 0);
        etSharedNoteContent.getText().replace(Math.min(start, end), Math.max(start, end), insertion, 0, insertion.length());
        etSharedNoteContent.setSelection(start + insertion.length());
    }

    private void updateNoteStats(String content) {
        boolean ar = prefs.isArabic();
        int chars = content.length();
        String trimmed = content.trim();
        int words = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
        if (ar) {
            tvNoteWordCount.setText(LocaleHelper.formatNumber(words, true) + " كلمة • " + LocaleHelper.formatNumber(chars, true) + " حرف");
        } else {
            tvNoteWordCount.setText(words + (words == 1 ? " word" : " words") + " • " + chars + (chars == 1 ? " character" : " characters"));
        }
    }

    private void switchTab(int tabIndex) {
        currentTab = tabIndex;
        boolean lite = prefs.isLiteModeEnabled();

        if (isTwoPaneTabletDesk) {
            // #16: Tablet / Wide Landscape Two-Pane Study Desk (Chat always on left, Tasks or Notes on right)
            chatPanel.setVisibility(View.VISIBLE);
            if (tabIndex == 2) {
                tasksPanel.setVisibility(View.GONE);
                notesPanel.setVisibility(View.VISIBLE);
                MotionHelper.animatePanelEntrance(notesPanel, lite);
            } else {
                tasksPanel.setVisibility(View.VISIBLE);
                notesPanel.setVisibility(View.GONE);
                MotionHelper.animatePanelEntrance(tasksPanel, lite);
            }
        } else {
            chatPanel.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
            tasksPanel.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
            notesPanel.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);
            View active = tabIndex == 0 ? chatPanel : (tabIndex == 1 ? tasksPanel : notesPanel);
            MotionHelper.animatePanelEntrance(active, lite);
        }

        int activeFill = palette.isLight ? palette.dockBg : roomAccentColor;
        int activeInk = palette.isLight ? palette.dockText : palette.accentInk;

        btnTabChat.setBackgroundTintList(ColorStateList.valueOf(tabIndex == 0 ? activeFill : palette.panelSoft));
        btnTabChat.setTextColor(tabIndex == 0 ? activeInk : palette.text);

        btnTabTasks.setBackgroundTintList(ColorStateList.valueOf(tabIndex == 1 ? activeFill : palette.panelSoft));
        btnTabTasks.setTextColor(tabIndex == 1 ? activeInk : palette.text);

        btnTabNotes.setBackgroundTintList(ColorStateList.valueOf(tabIndex == 2 ? activeFill : palette.panelSoft));
        btnTabNotes.setTextColor(tabIndex == 2 ? activeInk : palette.text);
    }

    private boolean isNearBottom() {
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return true;
        int total = messagesAdapter.getItemCount();
        if (total <= 3) return true;
        int lastVisible = lm.findLastVisibleItemPosition();
        return lastVisible >= total - 3;
    }

    private void scrollToMessageById(String targetId) {
        int pos = messagesAdapter.findPositionById(targetId);
        if (pos >= 0) {
            if (currentTab != 0 && !isTwoPaneTabletDesk) {
                switchTab(0);
            }
            rvMessages.smoothScrollToPosition(pos);
            messagesAdapter.highlightMessageTemporarily(targetId);
            mainHandler.postDelayed(() -> messagesAdapter.clearHighlight(targetId), 1800);
        } else {
            Toast.makeText(this,
                    prefs.isArabic() ? "لم يتم العثور على الرسالة في السجل الحالي" : "Message not found in current log",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * #18: Media viewer with smooth Swipe-Down-to-Close gesture and first-use hint.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void showFullscreenImageDialog(String urlOrDataUri, String caption) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        final ImageView fullImageView = new ImageView(this);
        fullImageView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        fullImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        if (urlOrDataUri.startsWith("data:image/")) {
            try {
                int comma = urlOrDataUri.indexOf(',');
                byte[] decoded = Base64.decode(urlOrDataUri.substring(comma + 1), Base64.DEFAULT);
                Glide.with(this).load(decoded).into(fullImageView);
            } catch (Exception ignored) {}
        } else {
            Glide.with(this).load(urlOrDataUri).into(fullImageView);
        }
        root.addView(fullImageView);

        // Top hint pill ("↓ Swipe down to close")
        float d = getResources().getDisplayMetrics().density;
        TextView tvSwipeHint = new TextView(this);
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        );
        hintLp.topMargin = (int) (36 * d);
        tvSwipeHint.setLayoutParams(hintLp);
        tvSwipeHint.setPadding((int) (14 * d), (int) (6 * d), (int) (14 * d), (int) (6 * d));
        tvSwipeHint.setText(prefs.isArabic() ? "↓ اسحب للأسفل للإغلاق" : "↓ Swipe down to close");
        tvSwipeHint.setTextColor(Color.WHITE);
        tvSwipeHint.setTextSize(12f);
        GradientDrawable hintBg = new GradientDrawable();
        hintBg.setCornerRadius(18 * d);
        hintBg.setColor(0x88222222);
        tvSwipeHint.setBackground(hintBg);
        root.addView(tvSwipeHint);

        if (caption != null && !caption.trim().isEmpty()) {
            TextView tvCap = new TextView(this);
            FrameLayout.LayoutParams capLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
            );
            tvCap.setLayoutParams(capLp);
            tvCap.setBackgroundColor(0x99000000);
            tvCap.setTextColor(Color.WHITE);
            tvCap.setGravity(Gravity.CENTER);
            tvCap.setPadding(32, 24, 32, 36);
            tvCap.setText(caption);
            root.addView(tvCap);
        }

        // #18: Swipe down to close media viewer
        final float dismissThresholdPx = 105f * d;
        fullImageView.setOnTouchListener(new View.OnTouchListener() {
            float downY = 0f;
            float downX = 0f;
            boolean draggingVertical = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = event.getRawY();
                        downX = event.getRawX();
                        draggingVertical = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dy = event.getRawY() - downY;
                        float dx = event.getRawX() - downX;
                        if (!draggingVertical && Math.abs(dy) > 12f * d && Math.abs(dy) > Math.abs(dx)) {
                            draggingVertical = true;
                        }
                        if (draggingVertical && dy > 0) {
                            fullImageView.setTranslationY(dy);
                            float progress = Math.min(1f, dy / (dismissThresholdPx * 2.2f));
                            float scale = 1f - 0.18f * progress;
                            fullImageView.setScaleX(scale);
                            fullImageView.setScaleY(scale);
                            int bgAlpha = (int) ((1f - progress * 0.75f) * 255);
                            root.setBackgroundColor(Color.argb(bgAlpha, 0, 0, 0));
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        float totalDy = event.getRawY() - downY;
                        if (draggingVertical && totalDy >= dismissThresholdPx) {
                            prefs.setSeenMediaSwipeHint(true);
                            FeedbackTouchHelper.onGestureTick(RoomActivity.this, prefs, fullImageView);
                            fullImageView.animate()
                                    .translationY(root.getHeight() * 0.6f)
                                    .alpha(0f)
                                    .setDuration(160L)
                                    .withEndAction(dialog::dismiss)
                                    .start();
                        } else if (draggingVertical) {
                            fullImageView.animate()
                                    .translationY(0f)
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(160L)
                                    .start();
                            root.setBackgroundColor(Color.BLACK);
                        } else if (Math.abs(totalDy) < 10f * d) {
                            dialog.dismiss();
                        }
                        return true;
                }
                return false;
            }
        });

        dialog.setContentView(root);
        dialog.show();
    }

    private void showAttachmentCaptionDialog(Uri fileUri) {
        boolean ar = prefs.isArabic();
        EditText captionInput = new EditText(this);
        captionInput.setHint(ar ? "أضف تعليقاً اختيارياً…" : "Add an optional caption…");
        captionInput.setSingleLine(true);

        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(captionInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle(ar ? "إرسال مرفق" : "Send Attachment")
                .setView(container)
                .setPositiveButton(ar ? "إرسال" : "Send", (dialog, which) -> {
                    String caption = captionInput.getText().toString().trim();
                    uploadAndSendAttachment(fileUri, caption);
                })
                .setNegativeButton(ar ? "إلغاء" : "Cancel", null)
                .show();
    }

    private void uploadAndSendAttachment(Uri uri, String caption) {
        boolean ar = prefs.isArabic();
        Toast.makeText(this, ar ? "جارٍ رفع المرفق…" : "Uploading attachment…", Toast.LENGTH_SHORT).show();
        catboxUploader.processAndUploadUri(this, uri, new CatboxUploader.UploadCallback() {
            @Override
            public void onSuccess(String fileUrlOrDataUrl, String fileName, long fileSize, String mimeType) {
                Message.FileAttachment att = new Message.FileAttachment();
                att.name = fileName;
                att.url = fileUrlOrDataUrl;
                if (fileUrlOrDataUrl.startsWith("data:")) {
                    att.dataUrl = fileUrlOrDataUrl;
                }
                att.size = fileSize;
                att.mime = mimeType;
                att.caption = caption;
                att.voice = false;

                FeedbackTouchHelper.onMessageSent(RoomActivity.this, prefs, btnSendMessage);
                mqttManager.sendFileOrVoiceMessage("file", att, caption, activeReplyRef);
                activeReplyRef = null;
                replyBanner.setVisibility(View.GONE);
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(RoomActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void toggleVoiceRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQ_RECORD_AUDIO
            );
            return;
        }

        if (voiceHelper.isRecording()) {
            stopAndSendVoiceRecording();
        } else {
            startVoiceRecording();
        }
    }

    private void startVoiceRecording() {
        boolean ar = prefs.isArabic();
        try {
            voiceHelper.startRecording(this, new VoiceRecorderHelper.RecordingProgressListener() {
                @Override
                public void onTick(int elapsedSeconds, int maxSeconds) {
                    tvRecordingTimerBanner.setVisibility(View.VISIBLE);
                    tvRecordingTimerBanner.setText(ar
                            ? ("🎙 جارٍ التسجيل: " + elapsedSeconds + " ث / " + maxSeconds + " ث (اضغط الميكروفون للإرسال)")
                            : ("🎙 Recording: " + elapsedSeconds + "s / " + maxSeconds + "s (Tap mic to send)"));
                }

                @Override
                public void onMaxDurationReached(File recordedFile, int durationSeconds) {
                    tvRecordingTimerBanner.setVisibility(View.GONE);
                    btnRecordVoice.setColorFilter(palette.muted);
                    uploadVoiceFile(recordedFile, durationSeconds);
                }
            });
            btnRecordVoice.setColorFilter(palette.danger);
            tvRecordingTimerBanner.setVisibility(View.VISIBLE);
            tvRecordingTimerBanner.setText(ar ? "🎙 جارٍ التسجيل… (اضغط للإرسال)" : "🎙 Recording… (Tap to Send)");
        } catch (Exception e) {
            Toast.makeText(this, ar ? "تعذر بدء التسجيل الصوتي" : "Could not start voice recording", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopAndSendVoiceRecording() {
        tvRecordingTimerBanner.setVisibility(View.GONE);
        btnRecordVoice.setColorFilter(palette.muted);
        File recFile = voiceHelper.getCurrentOutputFile();
        int dur = voiceHelper.stopRecording();
        if (recFile != null && recFile.exists() && dur >= 1) {
            uploadVoiceFile(recFile, dur);
        } else {
            Toast.makeText(this,
                    prefs.isArabic() ? "التسجيل قصير جداً" : "Recording cancelled or too short",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadVoiceFile(File file, int durationSec) {
        catboxUploader.processAndUploadLocalFile(file, "audio/mp4", new CatboxUploader.UploadCallback() {
            @Override
            public void onSuccess(String fileUrlOrDataUrl, String fileName, long fileSize, String mimeType) {
                Message.FileAttachment att = new Message.FileAttachment();
                att.name = "Voice message";
                att.url = fileUrlOrDataUrl;
                if (fileUrlOrDataUrl.startsWith("data:")) {
                    att.dataUrl = fileUrlOrDataUrl;
                }
                att.size = fileSize;
                att.mime = mimeType;
                att.duration = durationSec;
                att.voice = true;

                FeedbackTouchHelper.onMessageSent(RoomActivity.this, prefs, btnSendMessage);
                mqttManager.sendFileOrVoiceMessage("voice", att, "Voice message (" + durationSec + "s)", activeReplyRef);
                activeReplyRef = null;
                replyBanner.setVisibility(View.GONE);
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(RoomActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleTranslateMessage(Message message, boolean manualClick) {
        if (message == null || message.text == null || message.text.trim().isEmpty()) return;
        if (manualClick && message.showingTranslated) {
            message.showingTranslated = false;
            messagesAdapter.notifyMessageUpdatedById(message.id);
            return;
        }
        if (message.translatedText != null) {
            message.showingTranslated = true;
            messagesAdapter.notifyMessageUpdatedById(message.id);
            return;
        }

        String targetLang = prefs.isArabic() ? "ar" : prefs.getPreferredLanguage();
        translationHelper.translate(message.id, message.text, targetLang, new TranslationHelper.TranslationCallback() {
            @Override
            public void onSuccess(String translatedText, String detectedSourceLang) {
                mainHandler.post(() -> {
                    message.translatedText = translatedText;
                    message.showingTranslated = true;
                    messagesAdapter.notifyMessageUpdatedById(message.id);
                });
            }

            @Override
            public void onError(String error) {
                if (manualClick) {
                    mainHandler.post(() -> Toast.makeText(RoomActivity.this, error, Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void handleVoicePlayback(Message message) {
        if (message.file == null) return;
        String effectiveUrl = message.file.getEffectiveUrl();
        if (effectiveUrl.isEmpty()) return;

        if (message.id != null && message.id.equals(voiceHelper.getPlayingMessageId())) {
            voiceHelper.stopPlayback();
            messagesAdapter.setAudioPlayingMessageId(null);
            return;
        }

        voiceHelper.playVoiceMessage(message.id, effectiveUrl, new VoiceRecorderHelper.PlaybackListener() {
            @Override
            public void onStarted(String messageId) {
                messagesAdapter.setAudioPlayingMessageId(messageId);
            }

            @Override
            public void onCompleted(String messageId) {
                messagesAdapter.setAudioPlayingMessageId(null);
            }

            @Override
            public void onError(String error) {
                messagesAdapter.setAudioPlayingMessageId(null);
                Toast.makeText(RoomActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * #23: Smart Invite Share Dialog with both HTTPS App Link (and Web bridge) and direct runapp:// scheme.
     */
    private void showSmartInviteShareDialog() {
        boolean ar = prefs.isArabic();
        String rTitle = tvRoomTitle.getText().toString();
        String webAppLink = "https://takidz507.github.io/run/?room=" + roomCode;
        String appSchemeLink = "runapp://room/" + roomCode;
        String shareBody = (ar ? ("انضم إلى غرفة '" + rTitle + "' على تطبيق Run:\n") : ("Join '" + rTitle + "' on Run:\n"))
                + webAppLink
                + "\n" + (ar ? "أو افتح مباشرة في التطبيق: " : "Direct app link: ") + appSchemeLink
                + "\n" + (ar ? "رمز الغرفة: #" : "Room code: #") + roomCode;

        String[] actions = new String[]{
                ar ? "🔗 مشاركة رابط الدعوة الذكي" : "🔗 Share Smart Invite Link",
                ar ? ("📋 نسخ الرابط (" + webAppLink + ")") : ("📋 Copy Invite Link (" + webAppLink + ")"),
                ar ? ("#️⃣ نسخ رمز الغرفة (#" + roomCode + ")") : ("#️⃣ Copy Room Code (#" + roomCode + ")")
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle(ar ? "دعوة إلى الغرفة" : "Invite People to Space")
                .setItems(actions, (d, which) -> {
                    if (which == 0) {
                        // Also copy link to clipboard so deferred first-run bridge works automatically!
                        copyToClipboard("Run Invite Link", webAppLink, null);
                        Intent sendIntent = new Intent(Intent.ACTION_SEND);
                        sendIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
                        sendIntent.setType("text/plain");
                        startActivity(Intent.createChooser(sendIntent, ar ? "مشاركة الدعوة" : "Share Room Invitation"));
                    } else if (which == 1) {
                        copyToClipboard("Run Invite Link", webAppLink,
                                ar ? "تم نسخ رابط الدعوة الذكي!" : "Smart invite link copied!");
                    } else {
                        copyToClipboard("Room Code", roomCode,
                                ar ? ("تم نسخ الرمز #" + roomCode) : ("Copied room code #" + roomCode));
                    }
                })
                .setNegativeButton(ar ? "إغلاق" : "Close", null)
                .show();
    }

    private void confirmLeaveArchiveOrDeleteRoom() {
        boolean ar = prefs.isArabic();
        String[] options = new String[]{
                ar ? "📦 أرشفة الغرفة والعودة للرئيسية" : "📦 Archive Space & Return Home",
                ar ? "🚪 مغادرة الغرفة مؤقتاً" : "🚪 Leave Space (Keep in Your Rooms)",
                ar ? "🗑 حذف الغرفة نهائياً لدى الجميع" : "🗑 Delete Space Permanently for Everyone"
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle(tvRoomTitle.getText().toString())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        prefs.setRoomArchived(roomCode, true);
                        mqttManager.leaveActiveRoom();
                        finish();
                    } else if (which == 1) {
                        mqttManager.leaveActiveRoom();
                        finish();
                    } else if (which == 2) {
                        mqttManager.deleteActiveRoom();
                        finish();
                    }
                })
                .setNegativeButton(ar ? "إلغاء" : "Cancel", null)
                .show();
    }

    private void copyToClipboard(String label, String text, String toast) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
            if (toast != null && !toast.isEmpty()) {
                Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateRoomHeaderOrbText(String name) {
        String clean = (name != null && !name.trim().isEmpty()) ? name.trim() : roomCode;
        String initials = clean.length() >= 2 ? clean.substring(0, 2).toUpperCase(Locale.US) : clean.toUpperCase(Locale.US);
        tvRoomHeaderOrb.setText(initials);
    }

    private void applyLocalizedRoomLabels() {
        boolean ar = prefs.isArabic();
        btnTabTasks.setText(ar ? "المهام" : "Tasks");
        btnTabNotes.setText(ar ? "الملاحظات" : "Notes");
        etMessageInput.setHint(ar ? "اكتب رسالة…" : "Write a message…");
        etNewTaskInput.setHint(ar ? "أضف مهمة مشتركة…" : "Add a shared task…");
        btnAddTask.setText(ar ? "إضافة" : "Add");
        tvGestureHintText.setText(ar
                ? "💡 اسحب الرسالة للرد • اضغط مطولاً للتفاعل أو الحفظ • اسحب الصورة للأسفل للإغلاق"
                : "💡 Swipe a message to reply • Long-press to react or save • Swipe down on photos to close");
        btnDismissGestureHint.setText(ar ? "حسناً" : "Got it");
        updateMuteHeaderUI();
        updateHeaderSubtitle();
    }

    private void applyThemeAndRoomIdentity() {
        palette = ThemeHelper.getPalette(prefs.getThemeMode());
        RoomData.RoomIdentity id = mqttManager.getActiveRoomIdentity();
        if (id != null) {
            roomAccentColor = ThemeHelper.parseRoomAccentColor(id.accentColor, palette);
            if (id.bgStyle != null && !id.bgStyle.isEmpty()) {
                roomBgStyle = id.bgStyle;
            }
        } else {
            roomAccentColor = palette.accent;
        }

        rootRoomFrame.setBackgroundColor(palette.bg);
        roomPatternBackground.setRoomIdentity(roomBgStyle, roomAccentColor, palette.isLight);

        topBarCard.setCardBackgroundColor(palette.panel);
        topBarCard.setStrokeColor(palette.panelHover);
        btnBack.setColorFilter(palette.text);
        tvRoomTitle.setTextColor(palette.text);

        // #17: Tint Room Header Orb & Warm Paper Highlighter Bar with Room's Identity Color
        float d = getResources().getDisplayMetrics().density;
        GradientDrawable orbBg = new GradientDrawable();
        orbBg.setShape(GradientDrawable.OVAL);
        orbBg.setColor(roomAccentColor);
        tvRoomHeaderOrb.setBackground(orbBg);
        tvRoomHeaderOrb.setTextColor(palette.accentInk);

        viewRoomTitleHighlighter.setBackgroundTintList(ColorStateList.valueOf(
                ThemeHelper.withAlpha(roomAccentColor, palette.isLight ? 165 : 130)
        ));

        tvRoomCodeBadge.setBackgroundTintList(ColorStateList.valueOf(
                ThemeHelper.withAlpha(roomAccentColor, palette.isLight ? 115 : 85)
        ));
        tvRoomCodeBadge.setTextColor(palette.text);

        btnRoomIdentity.setColorFilter(roomAccentColor);
        btnShareRoom.setColorFilter(palette.text);
        btnDeleteRoom.setColorFilter(palette.text);
        updateMuteHeaderUI();

        cardGestureHintBanner.setCardBackgroundColor(palette.panel);
        cardGestureHintBanner.setStrokeColor(palette.panelHover);
        tvGestureHintText.setTextColor(palette.muted);
        btnDismissGestureHint.setTextColor(palette.text);

        cardComposerBar.setCardBackgroundColor(palette.panel);
        cardComposerBar.setStrokeColor(palette.panelHover);
        etMessageInput.setTextColor(palette.text);
        etMessageInput.setHintTextColor(palette.subtle);
        btnAttachFile.setColorFilter(palette.muted);
        btnRecordVoice.setColorFilter(palette.muted);
        btnSendMessage.setBackgroundTintList(ColorStateList.valueOf(roomAccentColor));
        btnSendMessage.setIconTint(ColorStateList.valueOf(palette.accentInk));

        progressHeaderCard.setCardBackgroundColor(palette.panel);
        progressHeaderCard.setStrokeColor(palette.panelHover);
        progressRingView.setColors(roomAccentColor, palette.panelSoft, palette.text);
        tvTasksSummaryTitle.setTextColor(palette.text);
        tvTasksSummarySub.setTextColor(palette.muted);
        etNewTaskInput.setTextColor(palette.text);
        etNewTaskInput.setHintTextColor(palette.subtle);
        btnAddTask.setBackgroundTintList(ColorStateList.valueOf(roomAccentColor));
        btnAddTask.setTextColor(palette.accentInk);

        cardNotesPaper.setCardBackgroundColor(palette.panel);
        cardNotesPaper.setStrokeColor(palette.panelHover);
        etSharedNoteContent.setTextColor(palette.text);
        etSharedNoteContent.setHintTextColor(palette.subtle);
        tvNoteStatus.setTextColor(palette.muted);
        tvNoteWordCount.setTextColor(palette.muted);

        messagesAdapter.updatePalette(palette);
        messagesAdapter.setRoomAccentColor(roomAccentColor);
        tasksAdapter.updatePalette(palette);
        switchTab(currentTab);

        if (getWindow() != null) {
            getWindow().setStatusBarColor(palette.bg);
            getWindow().setNavigationBarColor(palette.bg);
        }
    }

    /**
     * #29: Live connection quality + room online count + message count subtitle.
     */
    private void updateHeaderSubtitle() {
        boolean ar = prefs.isArabic();
        MqttRoomManager.ConnectionQuality q = mqttManager.getConnectionQuality();
        String connLabel;
        if (q == MqttRoomManager.ConnectionQuality.CONNECTED) {
            connLabel = ar ? "● متصل" : "● Connected";
            tvRoomSyncStatus.setTextColor(palette.muted);
        } else if (q == MqttRoomManager.ConnectionQuality.WEAK) {
            connLabel = ar ? "◐ إشارة ضعيفة" : "◐ Weak signal";
            tvRoomSyncStatus.setTextColor(palette.warning);
        } else {
            connLabel = ar ? "↻ يعيد الاتصال…" : "↻ Reconnecting…";
            tvRoomSyncStatus.setTextColor(palette.muted);
        }

        if (ar) {
            tvRoomSyncStatus.setText(
                    LocaleHelper.formatNumber(roomOnlineCount, true) + " متصل · "
                            + LocaleHelper.formatNumber(lastMessageCount, true) + " رسالة · " + connLabel
            );
        } else {
            tvRoomSyncStatus.setText(roomOnlineCount + " online · " + lastMessageCount + " messages · " + connLabel);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocaleHelper.applyLocaleAndLayoutDirection(this, prefs);
        mqttManager.setAppForeground(true);
        mqttManager.setChatActivelyVisible(true);
        mqttManager.addRoomListener(this);
        mainHandler.post(muteStatusRefreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(muteStatusRefreshRunnable);
        mqttManager.setChatActivelyVisible(false);
        mqttManager.removeRoomListener(this);
        if (voiceHelper.isRecording()) {
            voiceHelper.cancelRecording();
            tvRecordingTimerBanner.setVisibility(View.GONE);
            btnRecordVoice.setColorFilter(palette.muted);
        }
        voiceHelper.stopPlayback();
    }

    // ==========================================
    // RoomListener Callbacks
    // ==========================================

    @Override
    public void onRoomStateUpdated(RoomData roomData) {
        runOnUiThread(() -> {
            if (roomData == null) return;
            boolean ar = prefs.isArabic();
            if (roomData.meta != null && roomData.meta.name != null) {
                tvRoomTitle.setText(roomData.meta.name);
                updateRoomHeaderOrbText(roomData.meta.name);
            }

            // #17: Check if RoomData carries updated identity
            if (roomData.identity != null || (roomData.meta != null && roomData.meta.accentColor != null)) {
                String hex = roomData.identity != null ? roomData.identity.accentColor : roomData.meta.accentColor;
                String style = roomData.identity != null ? roomData.identity.bgStyle : roomData.meta.bgStyle;
                int parsedColor = ThemeHelper.parseRoomAccentColor(hex, palette);
                if (parsedColor != roomAccentColor || (style != null && !style.equals(roomBgStyle))) {
                    roomAccentColor = parsedColor;
                    if (style != null) roomBgStyle = style;
                    applyThemeAndRoomIdentity();
                }
            }

            if (roomData.avatars != null) {
                messagesAdapter.setAvatars(roomData.avatars);
            }

            // 1. Messages Update + Auto-Translation
            List<Message> msgs = roomData.messages != null ? roomData.messages : List.of();
            if (prefs.isAutoTranslateEnabled()) {
                String myId = prefs.getUserId();
                for (Message m : msgs) {
                    if (m != null && "text".equals(m.type) && m.userId != null && !m.userId.equals(myId) && m.translatedText == null) {
                        handleTranslateMessage(m, false);
                    }
                }
            }

            btnTabChat.setText((ar ? "المحادثة (" : "Chat (") + LocaleHelper.formatNumber(msgs.size(), ar) + ")");
            int newCount = msgs.size();
            boolean hasNew = newCount > lastMessageCount;
            lastMessageCount = newCount;
            updateHeaderSubtitle();

            messagesAdapter.setMessages(msgs);

            // #31: Handle one-tap jump from "My saved"
            if (pendingJumpMessageId != null) {
                int targetPos = messagesAdapter.findPositionById(pendingJumpMessageId);
                if (targetPos >= 0) {
                    final String jumpId = pendingJumpMessageId;
                    pendingJumpMessageId = null;
                    rvMessages.post(() -> scrollToMessageById(jumpId));
                }
            } else if (hasNew) {
                if (isNearBottom()) {
                    rvMessages.scrollToPosition(Math.max(0, newCount - 1));
                    btnScrollToBottom.setVisibility(View.GONE);
                } else {
                    btnScrollToBottom.setVisibility(View.VISIBLE);
                }
            }

            // 2. Tasks Update
            List<TaskItem> tasks = roomData.tasks != null ? roomData.tasks : List.of();
            tasksAdapter.setTasks(tasks);
            int doneCount = 0;
            for (TaskItem t : tasks) {
                if (t.done) doneCount++;
            }
            int totalTasks = tasks.size();
            progressRingView.setProgress(doneCount, totalTasks);
            if (ar) {
                tvTasksSummaryTitle.setText("اكتمل " + LocaleHelper.formatNumber(doneCount, true) + " من " + LocaleHelper.formatNumber(totalTasks, true));
                tvTasksSummarySub.setText(totalTasks == 0 ? "أضف مهام أدناه للمتابعة الجماعية"
                        : (doneCount == totalTasks ? "🎉 اكتملت جميع المهام!" : ("متبقي " + (totalTasks - doneCount) + " مهام")));
            } else {
                tvTasksSummaryTitle.setText(doneCount + " of " + totalTasks + " completed");
                tvTasksSummarySub.setText(totalTasks == 0 ? "Add tasks below for shared tracking"
                        : (doneCount == totalTasks ? "🎉 All tasks completed!" : ((totalTasks - doneCount) + " remaining tasks")));
            }

            // 3. Shared Notes Update
            if (roomData.note != null) {
                String remoteNote = roomData.note.content != null ? roomData.note.content : "";
                String currentText = etSharedNoteContent.getText().toString();
                if (!remoteNote.equals(currentText) && !etSharedNoteContent.hasFocus()) {
                    isUpdatingNoteRemotely = true;
                    etSharedNoteContent.setText(remoteNote);
                    updateNoteStats(remoteNote);
                    isUpdatingNoteRemotely = false;
                }
                String author = (roomData.note.lastEditedBy != null && !roomData.note.lastEditedBy.isEmpty())
                        ? roomData.note.lastEditedBy : (ar ? "مستخدم" : "User");
                tvNoteStatus.setText(ar
                        ? ("آخر تعديل بواسطة " + author + " • متزامن")
                        : ("Last edited by " + author + " • Synced"));
            }
        });
    }

    @Override
    public void onRoomIdentityUpdated(RoomData.RoomIdentity identity) {
        runOnUiThread(() -> {
            if (identity == null) return;
            roomAccentColor = ThemeHelper.parseRoomAccentColor(identity.accentColor, palette);
            if (identity.bgStyle != null && !identity.bgStyle.isEmpty()) {
                roomBgStyle = identity.bgStyle;
            }
            applyThemeAndRoomIdentity();
        });
    }

    @Override
    public void onRoomPresenceChanged(int count) {
        runOnUiThread(() -> {
            this.roomOnlineCount = Math.max(1, count);
            updateHeaderSubtitle();
        });
    }

    @Override
    public void onTypingUsersChanged(List<String> typingNames) {
        runOnUiThread(() -> {
            boolean ar = prefs.isArabic();
            if (typingNames == null || typingNames.isEmpty()) {
                tvTypingIndicator.setVisibility(View.GONE);
            } else {
                tvTypingIndicator.setVisibility(View.VISIBLE);
                if (typingNames.size() == 1) {
                    tvTypingIndicator.setText(typingNames.get(0) + (ar ? " يكتب الآن…" : " is typing…"));
                } else {
                    tvTypingIndicator.setText(typingNames.size() + (ar ? " أعضاء يكتبون الآن…" : " members are typing…"));
                }
            }
        });
    }

    @Override
    public void onRoomDeleted() {
        runOnUiThread(() -> {
            Toast.makeText(this,
                    prefs.isArabic() ? "تم إغلاق أو حذف هذه الغرفة" : "This room was closed or deleted",
                    Toast.LENGTH_LONG).show();
            finish();
        });
    }

    @Override
    public void onConnectionStateChanged(boolean connected) {
        runOnUiThread(this::updateHeaderSubtitle);
    }

    @Override
    public void onConnectionQualityChanged(MqttRoomManager.ConnectionQuality quality) {
        runOnUiThread(this::updateHeaderSubtitle);
    }
}
