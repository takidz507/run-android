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
 * Redesigned Room Activity:
 * - 3 Tabs: Chat, Tasks, and Shared Notes
 * - Material 3 Pixel & Classroom polish
 * - Real-time sync of Chat, Tasks (with progress ring), and Shared Notes
 * - Voice recorder (120s max with live countdown), attachment previews, quotes, auto-translate
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
    private int currentTab = 0; // 0: Chat, 1: Tasks, 2: Notes
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

    // 3 Tab Buttons
    private MaterialButton btnTabChat;
    private MaterialButton btnTabTasks;
    private MaterialButton btnTabNotes;

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

    // Shared Notes Panel Views
    private LinearLayout notesPanel;
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
            tvNoteStatus.setText("Saved & synced with room");
        }
    };

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
        setupNotesTab();
        applyTheme();
        switchTab(0);
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
        btnTabNotes = findViewById(R.id.btnTabNotes);

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

        // Notes views
        notesPanel = findViewById(R.id.notesPanel);
        tvNoteStatus = findViewById(R.id.tvNoteStatus);
        btnNoteSync = findViewById(R.id.btnNoteSync);
        btnNoteBullet = findViewById(R.id.btnNoteBullet);
        btnNoteCheck = findViewById(R.id.btnNoteCheck);
        btnNoteHeading = findViewById(R.id.btnNoteHeading);
        btnNoteCopy = findViewById(R.id.btnNoteCopy);
        btnNoteClear = findViewById(R.id.btnNoteClear);
        etSharedNoteContent = findViewById(R.id.etSharedNoteContent);
        tvNoteWordCount = findViewById(R.id.tvNoteWordCount);

        tvRoomTitle.setText(roomName != null ? roomName : ("Room " + roomCode));
        tvRoomCodeBadge.setText("#" + roomCode);

        btnBack.setOnClickListener(v -> finish());
        tvRoomCodeBadge.setOnClickListener(v -> copyToClipboard("Room Code", roomCode, "Copied room code #" + roomCode));
        btnShareRoom.setOnClickListener(v -> showInviteShareDialog());
        btnDeleteRoom.setOnClickListener(v -> confirmDeleteRoom());

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
                activeReplyRef = new Message.ReplyRef(message.id, message.text, message.author);
                replyBanner.setVisibility(View.VISIBLE);
                tvReplyingToText.setText("Replying to " + message.author + ": " + message.text);
                etMessageInput.requestFocus();
            }

            @Override
            public void onReplyQuoteClicked(String targetMessageId) {
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
                showFullscreenImageDialog(imageUrlOrDataUri, caption);
            }
        });
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

    private void setupNotesTab() {
        etSharedNoteContent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateNoteStats(s != null ? s.toString() : "");
                if (!isUpdatingNoteRemotely) {
                    tvNoteStatus.setText("Editing... (auto-syncing)");
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
            tvNoteStatus.setText("Synced with room now");
            Toast.makeText(this, "Notes synced with room!", Toast.LENGTH_SHORT).show();
        });

        btnNoteBullet.setOnClickListener(v -> insertTextAtCursor("\n• "));
        btnNoteCheck.setOnClickListener(v -> insertTextAtCursor("\n[ ] "));
        btnNoteHeading.setOnClickListener(v -> insertTextAtCursor("\n## "));

        btnNoteCopy.setOnClickListener(v -> {
            String content = etSharedNoteContent.getText().toString();
            copyToClipboard("Shared Notes", content, "Copied notes to clipboard");
        });

        btnNoteClear.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Clear Shared Notes")
                    .setMessage("Are you sure you want to clear all shared notes for this room?")
                    .setPositiveButton("Clear", (dialog, which) -> {
                        etSharedNoteContent.setText("");
                        mqttManager.updateRoomNote("");
                        tvNoteStatus.setText("Notes cleared");
                    })
                    .setNegativeButton("Cancel", null)
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
        int chars = content.length();
        String trimmed = content.trim();
        int words = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
        tvNoteWordCount.setText(words + (words == 1 ? " word" : " words") + " • " + chars + (chars == 1 ? " character" : " characters"));
    }

    private void switchTab(int tabIndex) {
        currentTab = tabIndex;
        chatPanel.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        tasksPanel.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        notesPanel.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);

        // Highlight active tab
        btnTabChat.setBackgroundTintList(ColorStateList.valueOf(tabIndex == 0 ? palette.accent : palette.panelSoft));
        btnTabChat.setTextColor(tabIndex == 0 ? palette.accentInk : palette.text);

        btnTabTasks.setBackgroundTintList(ColorStateList.valueOf(tabIndex == 1 ? palette.accent : palette.panelSoft));
        btnTabTasks.setTextColor(tabIndex == 1 ? palette.accentInk : palette.text);

        btnTabNotes.setBackgroundTintList(ColorStateList.valueOf(tabIndex == 2 ? palette.accent : palette.panelSoft));
        btnTabNotes.setTextColor(tabIndex == 2 ? palette.accentInk : palette.text);
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
            rvMessages.smoothScrollToPosition(pos);
            messagesAdapter.highlightMessageTemporarily(targetId);
            mainHandler.postDelayed(() -> messagesAdapter.clearHighlight(targetId), 1500);
        } else {
            Toast.makeText(this, "Original message not found in current log", Toast.LENGTH_SHORT).show();
        }
    }

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

    private void showAttachmentCaptionDialog(Uri fileUri) {
        EditText captionInput = new EditText(this);
        captionInput.setHint("Add an optional caption...");
        captionInput.setSingleLine(true);

        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(captionInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Send Attachment")
                .setMessage("Uploading file via P2P / Catbox...")
                .setView(container)
                .setPositiveButton("Send", (dialog, which) -> {
                    String caption = captionInput.getText().toString().trim();
                    uploadAndSendAttachment(fileUri, caption);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void uploadAndSendAttachment(Uri uri, String caption) {
        Toast.makeText(this, "Uploading attachment...", Toast.LENGTH_SHORT).show();
        catboxUploader.processAndUploadUri(this, uri, new CatboxUploader.UploadCallback() {
            @Override
            public void onSuccess(String fileUrlOrDataUrl, String fileName, long fileSize, String mimeType) {
                Message.FileAttachment att = new Message.FileAttachment();
                att.name = fileName;
                att.url = fileUrlOrDataUrl;
                att.size = fileSize;
                att.mime = mimeType;
                att.caption = caption;

                mqttManager.sendFileOrVoiceMessage("file", att, caption, activeReplyRef);
                activeReplyRef = null;
                replyBanner.setVisibility(View.GONE);
                Toast.makeText(RoomActivity.this, "Attachment sent!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(RoomActivity.this, "Upload failed: " + errorMessage, Toast.LENGTH_LONG).show();
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
        try {
            voiceHelper.startRecording(this, new VoiceRecorderHelper.RecordingProgressListener() {
                @Override
                public void onTick(int elapsedSeconds, int maxSeconds) {
                    int remaining = maxSeconds - elapsedSeconds;
                    tvRecordingTimerBanner.setVisibility(View.VISIBLE);
                    tvRecordingTimerBanner.setText("🎙 Recording: " + elapsedSeconds + "s / " + maxSeconds + "s (Tap to Send)");
                }

                @Override
                public void onMaxDurationReached(File recordedFile, int durationSeconds) {
                    tvRecordingTimerBanner.setVisibility(View.GONE);
                    btnRecordVoice.setColorFilter(palette.accent);
                    uploadVoiceFile(recordedFile, durationSeconds);
                }
            });
            btnRecordVoice.setColorFilter(palette.danger);
            tvRecordingTimerBanner.setVisibility(View.VISIBLE);
            tvRecordingTimerBanner.setText("🎙 Recording... (Tap to Send)");
        } catch (Exception e) {
            Toast.makeText(this, "Could not start voice recording", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopAndSendVoiceRecording() {
        tvRecordingTimerBanner.setVisibility(View.GONE);
        btnRecordVoice.setColorFilter(palette.accent);
        File recFile = voiceHelper.getCurrentOutputFile();
        int dur = voiceHelper.stopRecording();
        if (recFile != null && recFile.exists() && dur >= 1) {
            uploadVoiceFile(recFile, dur);
        } else {
            Toast.makeText(this, "Recording cancelled or too short", Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadVoiceFile(File file, int durationSec) {
        Toast.makeText(this, "Sending voice message...", Toast.LENGTH_SHORT).show();
        catboxUploader.processAndUploadLocalFile(file, "audio/mp4", new CatboxUploader.UploadCallback() {
            @Override
            public void onSuccess(String fileUrlOrDataUrl, String fileName, long fileSize, String mimeType) {
                Message.FileAttachment att = new Message.FileAttachment();
                att.name = "Voice Message (" + durationSec + "s)";
                att.url = fileUrlOrDataUrl;
                att.size = fileSize;
                att.mime = mimeType;
                att.duration = durationSec;

                mqttManager.sendFileOrVoiceMessage("voice", att, "🎙 Voice Message (" + durationSec + "s)", activeReplyRef);
                activeReplyRef = null;
                replyBanner.setVisibility(View.GONE);
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(RoomActivity.this, "Voice upload failed: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleTranslateMessage(Message message) {
        if (message.showingTranslated) {
            message.showingTranslated = false;
            messagesAdapter.notifyMessageUpdatedById(message.id);
            return;
        }

        if (message.translatedText != null) {
            message.showingTranslated = true;
            messagesAdapter.notifyMessageUpdatedById(message.id);
            return;
        }

        String targetLang = prefs.getPreferredLanguage();
        translationHelper.translateText(message.text, targetLang, new TranslationHelper.TranslationCallback() {
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
                mainHandler.post(() -> Toast.makeText(RoomActivity.this, "Translation failed", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void handleVoicePlayback(Message message) {
        if (message.file == null || message.file.url == null) return;
        voiceHelper.playVoiceMessage(message.id, message.file.url, new VoiceRecorderHelper.PlaybackListener() {
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
                Toast.makeText(RoomActivity.this, "Audio playback error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showInviteShareDialog() {
        String webUrl = "https://takidz507.github.io/run/?room=" + roomCode;
        String shareBody = "Join room '" + (roomName != null ? roomName : ("Room " + roomCode))
                + "' on Run with code #" + roomCode + " or link: " + webUrl;

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, "Share Room Invitation");
        startActivity(shareIntent);
    }

    private void confirmDeleteRoom() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Leave or Delete Room")
                .setMessage("Do you want to leave this room or delete it completely for all members?")
                .setPositiveButton("Delete for All", (dialog, which) -> {
                    mqttManager.deleteActiveRoom();
                    finish();
                })
                .setNegativeButton("Leave Room", (dialog, which) -> {
                    mqttManager.leaveActiveRoom();
                    finish();
                })
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void copyToClipboard(String label, String text, String toast) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
        }
    }

    private void applyTheme() {
        palette = ThemeHelper.getPalette(prefs.getThemeMode());
        rootRoomLayout.setBackgroundColor(palette.bg);
        topBarCard.setCardBackgroundColor(palette.panel);
        topBarCard.setStrokeColor(palette.panelHover);
        progressHeaderCard.setCardBackgroundColor(palette.panel);
        progressHeaderCard.setStrokeColor(palette.panelHover);
        chatPanel.setBackgroundColor(palette.bg);
        tasksPanel.setBackgroundColor(palette.bg);
        notesPanel.setBackgroundColor(palette.bg);
        btnSendMessage.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        btnSendMessage.setIconTint(ColorStateList.valueOf(palette.accentInk));
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
        if (voiceHelper.isRecording()) {
            voiceHelper.cancelRecording();
            tvRecordingTimerBanner.setVisibility(View.GONE);
            btnRecordVoice.setColorFilter(palette.accent);
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
            if (roomData.meta != null && roomData.meta.name != null) {
                tvRoomTitle.setText(roomData.meta.name);
            }

            // 1. Messages Update
            List<Message> msgs = roomData.messages != null ? roomData.messages : List.of();
            btnTabChat.setText("Chat (" + msgs.size() + ")");
            int newCount = msgs.size();
            boolean hasNew = newCount > lastMessageCount;
            lastMessageCount = newCount;

            messagesAdapter.setMessages(msgs);

            if (hasNew) {
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
            tvTasksSummaryTitle.setText(doneCount + " of " + totalTasks + " completed");
            if (totalTasks == 0) {
                tvTasksSummarySub.setText("Add tasks below for shared tracking");
            } else if (doneCount == totalTasks) {
                tvTasksSummarySub.setText("🎉 All tasks completed! Great job!");
            } else {
                tvTasksSummarySub.setText((totalTasks - doneCount) + " remaining tasks to complete");
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
                        ? roomData.note.lastEditedBy : "User";
                tvNoteStatus.setText("Last edited by " + author + " • Synced with room");
            }
        });
    }

    @Override
    public void onRoomPresenceChanged(int roomOnlineCount) {
        runOnUiThread(() -> {
            tvRoomSyncStatus.setText("● " + roomOnlineCount + " in room • Synced");
        });
    }

    @Override
    public void onTypingUsersChanged(List<String> typingNames) {
        runOnUiThread(() -> {
            if (typingNames == null || typingNames.isEmpty()) {
                tvTypingIndicator.setVisibility(View.GONE);
            } else {
                tvTypingIndicator.setVisibility(View.VISIBLE);
                if (typingNames.size() == 1) {
                    tvTypingIndicator.setText(typingNames.get(0) + " is typing...");
                } else {
                    tvTypingIndicator.setText(typingNames.size() + " members are typing...");
                }
            }
        });
    }

    @Override
    public void onRoomDeleted() {
        runOnUiThread(() -> {
            Toast.makeText(this, "This room was closed or deleted", Toast.LENGTH_LONG).show();
            finish();
        });
    }

    @Override
    public void onConnectionStateChanged(boolean connected) {
        runOnUiThread(() -> {
            if (connected) {
                tvRoomSyncStatus.setTextColor(palette.success);
                tvRoomSyncStatus.setText("● Synced");
            } else {
                tvRoomSyncStatus.setTextColor(palette.muted);
                tvRoomSyncStatus.setText("Connecting...");
            }
        });
    }
}
