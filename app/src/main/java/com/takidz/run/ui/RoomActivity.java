package com.takidz.run.ui;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
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

/**
 * Native Room Screen for `run`:
 * - Top AppBar with Back button, Room Title, tap-to-copy Room Code badge, Synced status, Share Room, and Delete Room.
 * - Instant Tab Switcher between:
 *   1. الدردشة اللحظية (Chat Panel): messages list, reply preview bar, typing indicator, file/image attachment via Catbox/DataURL, native Voice Recorder, and Google Translate GTX integration.
 *   2. المهام المشتركة (Shared Tasks Panel): Circular Progress Ring (`TaskProgressRingView`), completion stats (`X / Y مهام مكتملة`), quick task input, and collaborative checklist.
 */
public class RoomActivity extends AppCompatActivity implements MqttRoomManager.RoomListener {

    private static final int REQ_RECORD_AUDIO = 201;

    private PrefsManager prefs;
    private MqttRoomManager mqttManager;
    private CatboxUploader catboxUploader;
    private TranslationHelper translationHelper;
    private VoiceRecorderHelper voiceHelper;
    private ThemeHelper.Palette palette;

    private String roomCode;
    private String roomName;
    private Message.ReplyRef activeReplyRef = null;
    private boolean showingChatTab = true;

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
    private TextView tvTypingIndicator;
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
        voiceHelper = new VoiceRecorderHelper();
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
                        uploadAndSendAttachment(uri);
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
        tvTypingIndicator = findViewById(R.id.tvTypingIndicator);
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
            public void onTranslateClicked(Message message, int position) {
                handleTranslateMessage(message, position);
            }

            @Override
            public void onPlayVoiceClicked(Message message, int position) {
                handleVoicePlayback(message);
            }
        });
        rvMessages.setAdapter(messagesAdapter);

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

    private void uploadAndSendAttachment(Uri uri) {
        Toast.makeText(this, "جاري معالجة ورفع المرفق...", Toast.LENGTH_SHORT).show();
        catboxUploader.processAndUploadUri(this, uri, new CatboxUploader.UploadCallback() {
            @Override
            public void onSuccess(String fileUrlOrDataUrl, String fileName, long fileSize, String mimeType) {
                Message.FileAttachment att = new Message.FileAttachment();
                att.url = fileUrlOrDataUrl;
                att.name = fileName;
                att.size = fileSize;
                att.mime = mimeType;
                mqttManager.sendFileOrVoiceMessage("file", att, fileName, activeReplyRef);
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
                voiceHelper.startRecording(this);
                btnRecordVoice.setColorFilter(palette.danger);
                Toast.makeText(this, "🎙 جاري التسجيل الصوتي... اضغط مرة أخرى للإرسال", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "تعذر بدء التسجيل: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            int durationSec = voiceHelper.stopRecording();
            btnRecordVoice.setColorFilter(palette.accent);
            File audioFile = voiceHelper.getCurrentOutputFile();
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
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Toast.makeText(RoomActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    }

    private void handleTranslateMessage(Message message, int position) {
        if (message.showingTranslated) {
            message.showingTranslated = false;
            messagesAdapter.notifyItemChanged(position);
            return;
        }

        if (message.translatedText != null && !message.translatedText.isEmpty()) {
            message.showingTranslated = true;
            messagesAdapter.notifyItemChanged(position);
            return;
        }

        String targetLang = prefs.getPreferredLanguage();
        translationHelper.translate(message.id, message.text, targetLang, new TranslationHelper.TranslationCallback() {
            @Override
            public void onTranslated(String translatedText) {
                message.translatedText = translatedText;
                message.showingTranslated = true;
                messagesAdapter.notifyItemChanged(position);
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
                .setMessage("الكود المباشر: " + roomCode + "\n\nرابط الدعوة المباشر:\n" + shareUrl)
                .setPositiveButton("مشاركة الرابط", (d, w) -> {
                    Intent sendIntent = new Intent(Intent.ACTION_SEND);
                    sendIntent.setType("text/plain");
                    sendIntent.putExtra(Intent.EXTRA_TEXT, "انضم إلى غرفتي على تطبيق run:\nالكود: " + roomCode + "\nالرابط: " + shareUrl);
                    startActivity(Intent.createChooser(sendIntent, "مشاركة الغرفة عبر"));
                })
                .setNeutralButton("نسخ الكود", (d, w) -> copyToClipboard("Room Code", roomCode, "تم نسخ الكود: " + roomCode))
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void confirmDeleteRoom() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("حذف الغرفة نهائياً؟")
                .setMessage("سيتم مسح حالة الغرفة من الخادم لجميع المشاركين وإزالتها من الغرف الأخيرة.")
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
        mqttManager.addRoomListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mqttManager.removeRoomListener(this);
        voiceHelper.stopPlayback();
    }

    @Override
    public void onRoomStateUpdated(RoomData roomData) {
        if (roomData == null) return;
        if (roomData.meta != null && roomData.meta.name != null && !roomData.meta.name.isEmpty()) {
            tvRoomTitle.setText(roomData.meta.name);
        }

        // Auto-translate incoming messages if enabled in user settings
        if (prefs.isAutoTranslateEnabled()) {
            String myId = prefs.getUserId();
            String targetLang = prefs.getPreferredLanguage();
            for (int i = 0; i < roomData.messages.size(); i++) {
                Message m = roomData.messages.get(i);
                if (m.userId != null && !m.userId.equals(myId) && m.translatedText == null && "text".equals(m.type)) {
                    final int idx = i;
                    translationHelper.translate(m.id, m.text, targetLang, new TranslationHelper.TranslationCallback() {
                        @Override
                        public void onTranslated(String translatedText) {
                            m.translatedText = translatedText;
                            m.showingTranslated = true;
                            messagesAdapter.notifyItemChanged(idx);
                        }

                        @Override
                        public void onError(String error) {}
                    });
                }
            }
        }

        messagesAdapter.submitList(roomData.messages);
        if (!roomData.messages.isEmpty()) {
            rvMessages.scrollToPosition(roomData.messages.size() - 1);
        }

        tasksAdapter.submitList(roomData.tasks);
        int totalTasks = roomData.tasks.size();
        int doneTasks = 0;
        for (TaskItem t : roomData.tasks) {
            if (t.done) doneTasks++;
        }
        progressRingView.setProgress(doneTasks, totalTasks);
        tvTasksSummarySub.setText(doneTasks + " / " + totalTasks + " مهام مكتملة");

        btnTabChat.setText("💬 الدردشة (" + roomData.messages.size() + ")");
        btnTabTasks.setText("✓ المهام (" + doneTasks + "/" + totalTasks + ")");
    }

    @Override
    public void onRoomPresenceChanged(int roomOnlineCount) {
        tvRoomSyncStatus.setText("● متصل (" + roomOnlineCount + " بالغرفة)");
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
            tvRoomSyncStatus.setText("◌ جاري إعادة الاتصال...");
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
