package com.takidz.run.ui;

import android.Manifest;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
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
import com.takidz.run.ui.views.TaskProgressRingView;
import com.takidz.run.utils.PrefsManager;
import com.takidz.run.utils.ThemeHelper;
import com.takidz.run.utils.VoiceRecorderHelper;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Audit-Hardened Native Room Screen for `run`:
 * - #2: Bounded auto-translation + ID-based adapter notifications (notifyMessageUpdatedById).
 * - #3 & #8: Stops voice recording safely in onPause() + 120s max timer with live countdown banner.
 * - #5: Smart auto-scroll (only scrolls when near bottom or sending own message; shows "رسائل جديدة ↓" button otherwise).
 * - #13 / F2: Fullscreen image viewer dialog on image click.
 * - #14 / F3: Tap quoted reply to scroll to & highlight original message.
 * - #15 / F4: Pre-upload attachment preview & optional caption dialog.
 */
public class RoomActivity extends AppCompatActivity implements MqttRoomManager.RoomListener {

    private static final int REQ_RECORD_AUDIO = 201;

    private PrefsManager prefs;
    private MqttRoomManager mqttManager;
    private CatboxUploader catboxUploader;
    private TranslationHelper translationHelper;
    private VoiceRecorderHelper voiceHelper;
    private ThemeHelper.Palette palette;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String roomCode;
    private String roomName;
    private Message.ReplyRef activeReplyRef = null;
    private boolean showingChatTab = true;
    private int lastMessageCount = 0;

    // Header Views
    private LinearLayout rootRoomLayout;
    private MaterialCardView topBarCard;
    private ImageButton btnBack;
    private TextView tvRoomTitle;
    private TextView tvRoomCodeBadge;
    private TextView tvRoomSyncStatus;
    private ImageButton btnShareRoom;
    private ImageButton btnDeleteRoom;

    // Tab Switcher Buttons
    private MaterialButton btnTabChat;
    private MaterialButton btnTabTasks;

    // Chat Panel Views
    private LinearLayout chatPanel;
    private RecyclerView rvMessages;
    private MaterialButton btnScrollToBottom;
    private TextView tvTypingIndicator;
    private TextView tvRecordingTimerBanner;
    private LinearLayout replyBanner;
    private TextView tvReplyingToText;
    private ImageButton btnCancelReply;
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

    private MessagesAdapter messagesAdapter;
    private TasksAdapter tasksAdapter;

    private ActivityResultLauncher<String> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        prefs = new PrefsManager(this);
        mqttManager = MqttRoomManager.getInstance(this);
        catboxUploader = new CatboxUploader();
        translationHelper = new TranslationHelper();
        voiceHelper = new VoiceRecorderHelper(this);
        palette = ThemeHelper.getPalette(prefs.getThemeMode());

        roomCode = getIntent().getStringExtra("room_code");
        roomName = getIntent().getStringExtra("room_name");
        if (roomCode == null || roomCode.isEmpty()) {
            finish();
            return;
        }

        setupFilePicker();
        initViews();
        applyTheme();
        switchTab(true);
    }

    private void setupFilePicker() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        // Fix #15 / F4: Prompt for optional caption before uploading
                        showAttachmentCaptionDialog(uri);
                    }
                }
        );
    }

    private void initViews() {
        rootRoomLayout = findViewById(R.id.rootRoomLayout);
        topBarCard = findViewById(R.id.topBarCard);
        btnBack = findViewById(R.id.btnBack);
        tvRoomTitle = findViewById(R.id.tvRoomTitle);
        tvRoomCodeBadge = findViewById(R.id.tvRoomCodeBadge);
        tvRoomSyncStatus = findViewById(R.id.tvRoomSyncStatus);
        btnShareRoom = findViewById(R.id.btnShareRoom);
        btnDeleteRoom = findViewById(R.id.btnDeleteRoom);

        btnTabChat = findViewById(R.id.btnTabChat);
        btnTabTasks = findViewById(R.id.btnTabTasks);

        // Chat views
        chatPanel = findViewById(R.id.chatPanel);
        rvMessages = findViewById(R.id.rvMessages);
        btnScrollToBottom = findViewById(R.id.btnScrollToBottom);
        tvTypingIndicator = findViewById(R.id.tvTypingIndicator);
        tvRecordingTimerBanner = findViewById(R.id.tvRecordingTimerBanner);
        replyBanner = findViewById(R.id.replyBanner);
        tvReplyingToText = findViewById(R.id.tvReplyingToText);
        btnCancelReply = findViewById(R.id.btnCancelReply);
        btnAttachFile = findViewById(R.id.btnAttachFile);
        btnRecordVoice = findViewById(R.id.btnRecordVoice);
        etMessageInput = findViewById(R.id.etMessageInput);
        btnSendMessage = findViewById(R.id.btnSendMessage);

        // Tasks views
        tasksPanel = findViewById(R.id.tasksPanel);
        progressHeaderCard = findViewById(R.id.progressHeaderCard);
        progressRingView = findViewById(R.id.progressRingView);
        tvTasksSummaryTitle = findViewById(R.id.tvTasksSummaryTitle);
        tvTasksSummarySub = findViewById(R.id.tvTasksSummarySub);
        etNewTaskInput = findViewById(R.id.etNewTaskInput);
        btnAddTask = findViewById(R.id.btnAddTask);
        rvTasks = findViewById(R.id.rvTasks);

        tvRoomTitle.setText(roomName != null ? roomName : ("Room " + roomCode));
        tvRoomCodeBadge.setText("#" + roomCode);

        btnBack.setOnClickListener(v -> finish());
        tvRoomCodeBadge.setOnClickListener(v -> copyToClipboard("Room Code", roomCode, "تم نسخ كود الغرفة: " + roomCode));
        btnShareRoom.setOnClickListener(v -> showInviteShareDialog());
        btnDeleteRoom.setOnClickListener(v -> confirmDeleteRoom());

        btnTabChat.setOnClickListener(v -> switchTab(true));
        btnTabTasks.setOnClickListener(v -> switchTab(false));

        // Setup Messages RecyclerView
        LinearLayoutManager chatLm = new LinearLayoutManager(this);
        chatLm.setStackFromEnd(true);
        rvMessages.setLayoutManager(chatLm);
        messagesAdapter = new MessagesAdapter(this, prefs.getUserId(), palette, new MessagesAdapter.MessageInteractionListener() {
            @Override
            public void onReplyClicked(Message message) {
                activeReplyRef = new Message.ReplyRef(message.id, message.text, message.author);
                replyBanner.setVisibility(View.VISIBLE);
                tvReplyingToText.setText("الرد على " + message.author + ": " + message.text);
                etMessageInput.requestFocus();
            }

            @Override
            public void onReplyQuoteClicked(String targetMessageId) {
                // Fix #14 / F3: Jump to original quoted message
                scrollToMessageById(targetMessageId);
            }

            @Override
            public void onTranslateClicked(Message message) {
                handleTranslateMessage(message);
            }

            @Override
            public void onPlayVoiceClicked(Message message) {
                handleVoicePlayback(message);
            }

            @Override
            public void onImageClicked(String imageUrlOrDataUri, String caption) {
                // Fix #13 / F2: Fullscreen image viewer
                showFullscreenImageDialog(imageUrlOrDataUri, caption);
            }
        });
        rvMessages.setAdapter(messagesAdapter);

        // Fix #5: Hide floating "New Messages ↓" button when user scrolls to bottom
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
            mqttManager.addTask(taskTitle);
            etNewTaskInput.setText("");
        });
    }

    private boolean isNearBottom() {
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return true;
        int total = messagesAdapter.getItemCount();
        if (total <= 3) return true;
        int lastVisible = lm.findLastVisibleItemPosition();
        return lastVisible >= total - 3;
    }

    /**
     * Fix #14 & F3: Scrolls smoothly to the quoted original message and highlights it briefly.
     */
    private void scrollToMessageById(String targetId) {
        int pos = messagesAdapter.findPositionById(targetId);
        if (pos >= 0) {
            rvMessages.smoothScrollToPosition(pos);
            messagesAdapter.highlightMessageTemporarily(targetId);
            mainHandler.postDelayed(() -> messagesAdapter.clearHighlight(targetId), 1500);
        } else {
            Toast.makeText(this, "الرسالة الأصلية غير موجودة في السجل الحالي", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Fix #13 & F2: Fullscreen image viewer dialog.
     */
    private void showFullscreenImageDialog(String urlOrDataUri, String caption) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        ImageView fullImageView = new ImageView(this);
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

        fullImageView.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(root);
        dialog.show();
    }

    /**
     * Fix #15 & F4: Pre-upload dialog with image preview and optional caption field.
     */
    private void showAttachmentCaptionDialog(Uri uri) {
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad / 2, pad, 0);

        String mime = getContentResolver().getType(uri);
        if (mime != null && mime.startsWith("image/")) {
            ImageView preview = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (180 * getResources().getDisplayMetrics().density)
            );
            lp.bottomMargin = pad / 2;
            preview.setLayoutParams(lp);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this).load(uri).into(preview);
            container.addView(preview);
        }

        EditText etCaption = new EditText(this);
        etCaption.setHint("أضف وصفاً أو تعليقاً مع المرفق (اختياري)...");
        etCaption.setSingleLine(false);
        etCaption.setMaxLines(3);
        container.addView(etCaption);

        new MaterialAlertDialogBuilder(this)
                .setTitle("إرسال مرفق")
                .setView(container)
                .setPositiveButton("رفع وإرسال", (d, w) -> {
                    String caption = etCaption.getText().toString().trim();
                    uploadAndSendAttachment(uri, caption);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void switchTab(boolean showChat) {
        this.showingChatTab = showChat;
        chatPanel.setVisibility(showChat ? View.VISIBLE : View.GONE);
        tasksPanel.setVisibility(showChat ? View.GONE : View.VISIBLE);

        if (showChat) {
            btnTabChat.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
            btnTabChat.setTextColor(palette.accentInk);
            btnTabTasks.setBackgroundTintList(ColorStateList.valueOf(palette.panelSoft));
            btnTabTasks.setTextColor(palette.text);
        } else {
            btnTabTasks.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
            btnTabTasks.setTextColor(palette.accentInk);
            btnTabChat.setBackgroundTintList(ColorStateList.valueOf(palette.panelSoft));
            btnTabChat.setTextColor(palette.text);
        }
    }

    private void uploadAndSendAttachment(Uri uri, String optionalCaption) {
        Toast.makeText(this, "جاري معالجة ورفع المرفق...", Toast.LENGTH_SHORT).show();
        catboxUploader.processAndUploadUri(this, uri, new CatboxUploader.UploadCallback() {
            @Override
            public void onSuccess(String fileUrlOrDataUrl, String fileName, long fileSize, String mimeType) {
                Message.FileAttachment att = new Message.FileAttachment();
                att.url = fileUrlOrDataUrl;
                att.name = fileName;
                att.size = fileSize;
                att.mime = mimeType;
                String finalCaption = (optionalCaption != null && !optionalCaption.isEmpty()) ? optionalCaption : fileName;
                mqttManager.sendFileOrVoiceMessage("file", att, finalCaption, activeReplyRef);
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            return;
        }

        if (!voiceHelper.isRecording()) {
            try {
                // Fix #8: Start recording with 120s max timer and live countdown banner
                voiceHelper.startRecording(this, new VoiceRecorderHelper.RecordingProgressListener() {
                    @Override
                    public void onTick(int elapsedSeconds, int maxSeconds) {
                        tvRecordingTimerBanner.setVisibility(View.VISIBLE);
                        tvRecordingTimerBanner.setText(String.format(Locale.US,
                                "🔴 جاري التسجيل الصوتي... %02d:%02d / %02d:%02d (اضغط للإرسال)",
                                elapsedSeconds / 60, elapsedSeconds % 60,
                                maxSeconds / 60, maxSeconds % 60));
                    }

                    @Override
                    public void onMaxDurationReached(File outputFile, int durationSeconds) {
                        resetVoiceRecordingUI();
                        Toast.makeText(RoomActivity.this, "تم الوصول للحد الأقصى (120 ثانية) — جاري إرسال التسجيل...", Toast.LENGTH_SHORT).show();
                        uploadRecordedVoiceFile(outputFile, durationSeconds);
                    }
                });
                btnRecordVoice.setColorFilter(palette.danger);
                tvRecordingTimerBanner.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                resetVoiceRecordingUI();
                Toast.makeText(this, "تعذر بدء التسجيل: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            int durationSec = voiceHelper.stopRecording();
            resetVoiceRecordingUI();
            File audioFile = voiceHelper.getCurrentOutputFile();
            uploadRecordedVoiceFile(audioFile, durationSec);
        }
    }

    private void resetVoiceRecordingUI() {
        btnRecordVoice.setColorFilter(palette.accent);
        tvRecordingTimerBanner.setVisibility(View.GONE);
    }

    private void uploadRecordedVoiceFile(File audioFile, int durationSec) {
        if (audioFile != null && audioFile.exists()) {
            Toast.makeText(this, "جاري إرسال المقطع الصوتي (" + durationSec + " ثانية)...", Toast.LENGTH_SHORT).show();
            catboxUploader.processAndUploadFile(audioFile, "audio/mp4", new CatboxUploader.UploadCallback() {
                @Override
                public void onSuccess(String fileUrlOrDataUrl, String fileName, long fileSize, String mimeType) {
                    Message.FileAttachment att = new Message.FileAttachment();
                    att.url = fileUrlOrDataUrl;
                    att.name = fileName;
                    att.size = fileSize;
                    att.mime = mimeType;
                    att.duration = durationSec;
                    mqttManager.sendFileOrVoiceMessage("voice", att, "رسالة صوتية (" + durationSec + "s)", activeReplyRef);
                    // Clean up local recording file after upload
                    try { audioFile.delete(); } catch (Exception ignored) {}
                }

                @Override
                public void onError(String errorMessage) {
                    Toast.makeText(RoomActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    /**
     * Fix #2: Uses notifyMessageUpdatedById(message.id) instead of stale position index.
     */
    private void handleTranslateMessage(Message message) {
        if (message.showingTranslated) {
            message.showingTranslated = false;
            messagesAdapter.notifyMessageUpdatedById(message.id);
            return;
        }

        if (message.translatedText != null && !message.translatedText.isEmpty()) {
            message.showingTranslated = true;
            messagesAdapter.notifyMessageUpdatedById(message.id);
            return;
        }

        String targetLang = prefs.getPreferredLanguage();
        translationHelper.translate(message.id, message.text, targetLang, new TranslationHelper.TranslationCallback() {
            @Override
            public void onTranslated(String translatedText) {
                message.translatedText = translatedText;
                message.showingTranslated = true;
                messagesAdapter.notifyMessageUpdatedById(message.id);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(RoomActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleVoicePlayback(Message message) {
        if (message.file == null || message.file.url == null) return;
        if (message.id != null && message.id.equals(voiceHelper.getPlayingMessageId())) {
            voiceHelper.stopPlayback();
            messagesAdapter.setPlayingMessageId(null);
            return;
        }
        voiceHelper.playVoiceUrlOrDataUri(this, message.id, message.file.url, new VoiceRecorderHelper.PlaybackListener() {
            @Override
            public void onStarted(String messageId) {
                messagesAdapter.setPlayingMessageId(messageId);
            }

            @Override
            public void onCompleted(String messageId) {
                messagesAdapter.setPlayingMessageId(null);
            }

            @Override
            public void onError(String error) {
                messagesAdapter.setPlayingMessageId(null);
                Toast.makeText(RoomActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showInviteShareDialog() {
        String shareUrl = "https://takidz507.github.io/run/?room=" + roomCode;
        new MaterialAlertDialogBuilder(this)
                .setTitle("مشاركة الغرفة (" + roomCode + ")")
                .setMessage("الكود المباشر: " + roomCode + "

رابط الدعوة المباشر:
" + shareUrl)
                .setPositiveButton("مشاركة الرابط", (d, w) -> {
                    Intent sendIntent = new Intent(Intent.ACTION_SEND);
                    sendIntent.setType("text/plain");
                    sendIntent.putExtra(Intent.EXTRA_TEXT, "انضم إلى غرفتي على تطبيق run:
الكود: " + roomCode + "
الرابط: " + shareUrl);
                    startActivity(Intent.createChooser(sendIntent, "مشاركة الغرفة عبر"));
                })
                .setNeutralButton("نسخ الكود", (d, w) -> copyToClipboard("Room Code", roomCode, "تم نسخ الكود: " + roomCode))
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void confirmDeleteRoom() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("حذف الغرفة نهائياً؟")
                .setMessage("سيتم مسح حالة الغرفة وحضورها من الخادم لجميع المشاركين وإزالتها من الغرف الأخيرة.")
                .setPositiveButton("حذف الغرفة", (d, w) -> mqttManager.deleteActiveRoom())
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void copyToClipboard(String label, String value, String toastMsg) {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cb != null) {
            cb.setPrimaryClip(ClipData.newPlainClip(label, value));
            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show();
        }
    }

    private void applyTheme() {
        getWindow().setStatusBarColor(palette.bg);
        getWindow().setNavigationBarColor(palette.bg);
        rootRoomLayout.setBackgroundColor(palette.bg);

        topBarCard.setCardBackgroundColor(palette.panel);
        topBarCard.setStrokeColor(palette.panelHover);
        tvRoomTitle.setTextColor(palette.text);
        tvRoomCodeBadge.setTextColor(palette.accent);
        tvRoomSyncStatus.setTextColor(palette.success);

        btnBack.setColorFilter(palette.text);
        btnShareRoom.setColorFilter(palette.accent);
        btnDeleteRoom.setColorFilter(palette.danger);

        progressHeaderCard.setCardBackgroundColor(palette.panel);
        progressHeaderCard.setStrokeColor(palette.panelHover);
        progressRingView.setColors(palette.accent, palette.panelHover, palette.text);
        tvTasksSummaryTitle.setTextColor(palette.text);
        tvTasksSummarySub.setTextColor(palette.muted);

        etMessageInput.setTextColor(palette.text);
        etMessageInput.setHintTextColor(palette.subtle);
        btnSendMessage.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        btnSendMessage.setTextColor(palette.accentInk);
        btnAttachFile.setColorFilter(palette.accent);
        btnRecordVoice.setColorFilter(palette.accent);

        etNewTaskInput.setTextColor(palette.text);
        etNewTaskInput.setHintTextColor(palette.subtle);
        btnAddTask.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        btnAddTask.setTextColor(palette.accentInk);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mqttManager.setAppForeground(true);
        mqttManager.setChatActivelyVisible(true);
        mqttManager.addRoomListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mqttManager.setChatActivelyVisible(false);
        mqttManager.removeRoomListener(this);
        voiceHelper.stopPlayback();
        // Fix #3: Stop & discard any ongoing voice recording if user leaves the screen
        if (voiceHelper.isRecording()) {
            voiceHelper.cancelRecording();
            resetVoiceRecordingUI();
            Toast.makeText(this, "تم إلغاء التسجيل الصوتي بسبب مغادرة الشاشة.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRoomStateUpdated(RoomData roomData) {
        if (roomData == null) return;
        if (roomData.meta != null && roomData.meta.name != null && !roomData.meta.name.isEmpty()) {
            tvRoomTitle.setText(roomData.meta.name);
        }

        messagesAdapter.setAvatars(roomData.avatars);

        // Fix #5: Check if user is near bottom BEFORE updating list
        boolean wasNearBottom = isNearBottom();
        int newCount = roomData.messages != null ? roomData.messages.size() : 0;
        boolean hasNewMessages = newCount > lastMessageCount;
        lastMessageCount = newCount;

        messagesAdapter.submitList(roomData.messages);

        if (hasNewMessages && newCount > 0) {
            Message latest = roomData.messages.get(newCount - 1);
            boolean sentByMe = latest.userId != null && latest.userId.equals(prefs.getUserId());
            if (wasNearBottom || sentByMe) {
                rvMessages.scrollToPosition(newCount - 1);
                btnScrollToBottom.setVisibility(View.GONE);
            } else {
                btnScrollToBottom.setVisibility(View.VISIBLE);
            }
        }

        // Fix #2: Bounded, deduplicated auto-translation with safe ID-based adapter refresh
        if (prefs.isAutoTranslateEnabled() && roomData.messages != null) {
            String myId = prefs.getUserId();
            String targetLang = prefs.getPreferredLanguage();
            for (Message m : roomData.messages) {
                if (m != null && m.id != null && m.userId != null && !m.userId.equals(myId)
                        && m.translatedText == null && "text".equals(m.type)) {
                    translationHelper.translate(m.id, m.text, targetLang, new TranslationHelper.TranslationCallback() {
                        @Override
                        public void onTranslated(String translatedText) {
                            m.translatedText = translatedText;
                            m.showingTranslated = true;
                            messagesAdapter.notifyMessageUpdatedById(m.id);
                        }

                        @Override
                        public void onError(String error) {}
                    });
                }
            }
        }

        tasksAdapter.submitList(roomData.tasks);
        int totalTasks = roomData.tasks != null ? roomData.tasks.size() : 0;
        int doneTasks = 0;
        if (roomData.tasks != null) {
            for (TaskItem t : roomData.tasks) {
                if (t.done) doneTasks++;
            }
        }
        progressRingView.setProgress(doneTasks, totalTasks);
        tvTasksSummarySub.setText(doneTasks + " / " + totalTasks + " مهام مكتملة");

        btnTabChat.setText("💬 الدردشة (" + newCount + ")");
        btnTabTasks.setText("✓ المهام (" + doneTasks + "/" + totalTasks + ")");
    }

    @Override
    public void onRoomPresenceChanged(int roomOnlineCount) {
        if (mqttManager.isConnected()) {
            tvRoomSyncStatus.setText("● متصل (" + roomOnlineCount + " بالغرفة)");
            tvRoomSyncStatus.setTextColor(palette.success);
        } else {
            tvRoomSyncStatus.setText("◌ غير متصل");
            tvRoomSyncStatus.setTextColor(palette.muted);
        }
    }

    @Override
    public void onTypingUsersChanged(List<String> typingNames) {
        if (typingNames == null || typingNames.isEmpty()) {
            tvTypingIndicator.setVisibility(View.GONE);
        } else {
            tvTypingIndicator.setVisibility(View.VISIBLE);
            tvTypingIndicator.setText("✍️ " + String.join("، ", typingNames) + " يكتب الآن...");
        }
    }

    @Override
    public void onRoomDeleted() {
        Toast.makeText(this, "لم تعد هذه الغرفة موجودة — تمت إزالتها من الغرف الأخيرة.", Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    public void onConnectionStateChanged(boolean connected) {
        if (connected) {
            tvRoomSyncStatus.setText("● متصل (" + mqttManager.getRoomOnlineCount() + " بالغرفة)");
            tvRoomSyncStatus.setTextColor(palette.success);
        } else {
            tvRoomSyncStatus.setText("◌ غير متصل — جاري إعادة الاتصال...");
            tvRoomSyncStatus.setTextColor(palette.muted);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            toggleVoiceRecording();
        }
    }
}
