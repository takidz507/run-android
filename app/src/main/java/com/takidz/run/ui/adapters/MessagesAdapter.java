package com.takidz.run.ui.adapters;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.takidz.run.R;
import com.takidz.run.models.Message;
import com.takidz.run.models.RoomData;
import com.takidz.run.utils.LocaleHelper;
import com.takidz.run.utils.MotionHelper;
import com.takidz.run.utils.PrefsManager;
import com.takidz.run.utils.ThemeHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Run v3.0.0 MessagesAdapter:
 * - #14: Warm Paper tactile message bubbles (own messages use signature warm lime surface).
 * - #15 & #21: Smooth entrance motion respecting Lite Mode.
 * - #17: Tints accents with room's custom identity color.
 * - #18: Long-press opens reaction & quick-action picker; renders interactive emoji reaction pills.
 * - #26: Renders Sent (✓), Delivered (✓✓), and Read (highlighted ✓✓) indicators on outgoing messages.
 * - #28: Full Arabic RTL support and localized timestamps.
 * - #31: Bookmark/Save indicator and 1-tap toggle into "My saved".
 */
public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.MsgViewHolder> {

    public interface MessageInteractionListener {
        void onReplyClicked(Message message);
        void onReplyQuoteClicked(String targetMessageId);
        void onTranslateClicked(Message message);
        void onPlayVoiceClicked(Message message);
        void onImageClicked(String imageUrlOrDataUri, String caption);
        default void onSaveClicked(Message message) {}
        default void onReactionToggled(Message message, String emoji) {}
        default void onMessageLongPressed(Message message, View anchorView) {}
    }

    private final Context context;
    private final PrefsManager prefs;
    private final String currentUserId;
    private final MessageInteractionListener listener;
    private String roomCode = "";
    private List<Message> messages = new ArrayList<>();
    private Map<String, RoomData.AvatarProfile> avatars = new HashMap<>();
    private ThemeHelper.Palette palette;
    private int roomAccentColor;
    private String playingMessageId = null;
    private String highlightedMessageId = null;
    private int lastAnimatedPosition = -1;

    public MessagesAdapter(Context context, String currentUserId, ThemeHelper.Palette palette, MessageInteractionListener listener) {
        this.context = context;
        this.prefs = new PrefsManager(context);
        this.currentUserId = currentUserId;
        this.palette = palette;
        this.roomAccentColor = palette.accent;
        this.listener = listener;
    }

    public void setRoomCode(String code) {
        this.roomCode = code != null ? code : "";
    }

    public void setRoomAccentColor(int accentColor) {
        this.roomAccentColor = accentColor;
        notifyItemRangeChanged(0, messages.size());
    }

    public void updatePalette(ThemeHelper.Palette newPalette) {
        this.palette = newPalette;
        notifyItemRangeChanged(0, messages.size());
    }

    public void setAvatars(Map<String, RoomData.AvatarProfile> avatarMap) {
        this.avatars = avatarMap != null ? new HashMap<>(avatarMap) : new HashMap<>();
    }

    public void setPlayingMessageId(String msgId) {
        String oldId = this.playingMessageId;
        this.playingMessageId = msgId;
        if (oldId != null) notifyMessageUpdatedById(oldId);
        if (msgId != null) notifyMessageUpdatedById(msgId);
    }

    public void setAudioPlayingMessageId(String msgId) {
        setPlayingMessageId(msgId);
    }

    public void setMessages(List<Message> msgs) {
        submitList(msgs);
    }

    public Message getMessageAt(int position) {
        if (position >= 0 && position < messages.size()) {
            return messages.get(position);
        }
        return null;
    }

    public void highlightMessageTemporarily(String msgId) {
        this.highlightedMessageId = msgId;
        notifyMessageUpdatedById(msgId);
    }

    public void clearHighlight(String msgId) {
        if (Objects.equals(this.highlightedMessageId, msgId)) {
            this.highlightedMessageId = null;
            notifyMessageUpdatedById(msgId);
        }
    }

    public void notifyMessageUpdatedById(String messageId) {
        if (messageId == null) return;
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            if (m != null && messageId.equals(m.id)) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    public int findPositionById(String messageId) {
        if (messageId == null) return -1;
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            if (m != null && messageId.equals(m.id)) {
                return i;
            }
        }
        return -1;
    }

    public void submitList(List<Message> newList) {
        final List<Message> oldList = this.messages;
        final List<Message> updated = newList != null ? new ArrayList<>(newList) : new ArrayList<>();

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return updated.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                Message o = oldList.get(oldItemPosition);
                Message n = updated.get(newItemPosition);
                return o != null && n != null && Objects.equals(o.id, n.id);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Message o = oldList.get(oldItemPosition);
                Message n = updated.get(newItemPosition);
                if (o == null || n == null) return false;
                return Objects.equals(o.text, n.text)
                        && Objects.equals(o.translatedText, n.translatedText)
                        && o.showingTranslated == n.showingTranslated
                        && o.ackState == n.ackState
                        && Objects.equals(o.caption, n.caption)
                        && Objects.equals(o.reactions, n.reactions);
            }
        });

        this.messages = updated;
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public MsgViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
        return new MsgViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull MsgViewHolder holder, int position) {
        Message msg = messages.get(position);
        boolean isArabic = prefs.isArabic();
        boolean isMine = msg.userId != null && msg.userId.equals(currentUserId);
        boolean isHighlighted = msg.id != null && msg.id.equals(highlightedMessageId);
        boolean isSaved = prefs.isMessageSaved(roomCode, msg.id);

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.bubbleCard.getLayoutParams();
        params.gravity = isMine ? Gravity.END : Gravity.START;
        holder.bubbleCard.setLayoutParams(params);

        int bubbleBg = isHighlighted ? palette.panelSoft : (isMine ? palette.ownBubble : palette.panel);
        int bubbleStroke = isHighlighted ? roomAccentColor : (isMine ? palette.ownBubbleStroke : palette.panelHover);
        int mainTextColor = isMine ? palette.ownBubbleText : palette.text;
        int subTextColor = isMine ? ThemeHelper.withAlpha(palette.ownBubbleText, 175) : palette.muted;

        holder.bubbleCard.setCardBackgroundColor(bubbleBg);
        holder.bubbleCard.setStrokeColor(bubbleStroke);
        holder.bubbleCard.setStrokeWidth(isHighlighted ? 5 : 2);

        String youLabel = isArabic ? "أنت" : "You";
        holder.tvAuthor.setText(isMine ? youLabel : (msg.author != null && !msg.author.isEmpty() ? msg.author : "Guest"));
        holder.tvAuthor.setTextColor(isMine ? mainTextColor : palette.text);

        holder.tvSavedBadge.setVisibility(isSaved ? View.VISIBLE : View.GONE);

        // Render sender avatar
        RoomData.AvatarProfile profile = (msg.userId != null && avatars != null) ? avatars.get(msg.userId) : null;
        if (profile != null && profile.avatarUrl != null && !profile.avatarUrl.isEmpty()) {
            holder.ivAuthorAvatar.setVisibility(View.VISIBLE);
            loadImageOrDataUrl(holder.ivAuthorAvatar, profile.avatarUrl, true);
        } else {
            holder.ivAuthorAvatar.setVisibility(View.GONE);
        }

        // Timestamp (#28 Arabic-aware)
        holder.tvTime.setText(LocaleHelper.formatIsoTime(msg.createdAt, isArabic));
        holder.tvTime.setTextColor(subTextColor);

        // #26: Delivery & Read Indicators for own messages
        if (isMine) {
            holder.tvAckStatus.setVisibility(View.VISIBLE);
            if (msg.ackState <= 0) {
                holder.tvAckStatus.setText("○");
                holder.tvAckStatus.setTextColor(subTextColor);
            } else if (msg.ackState == 1) {
                holder.tvAckStatus.setText("✓");
                holder.tvAckStatus.setTextColor(subTextColor);
            } else if (msg.ackState == 2) {
                holder.tvAckStatus.setText("✓✓");
                holder.tvAckStatus.setTextColor(subTextColor);
            } else {
                // Read = highlighted checkmarks
                holder.tvAckStatus.setText("✓✓");
                holder.tvAckStatus.setTextColor(palette.isLight ? palette.success : palette.accent);
            }
        } else {
            holder.tvAckStatus.setVisibility(View.GONE);
        }

        // Quoted reply box jumps to original message on click
        if (msg.replyTo != null && msg.replyTo.text != null) {
            holder.replyContainer.setVisibility(View.VISIBLE);
            GradientDrawable replyBg = new GradientDrawable();
            replyBg.setColor(ThemeHelper.withAlpha(palette.bg, 160));
            replyBg.setCornerRadius(24f);
            replyBg.setStroke(2, ThemeHelper.withAlpha(roomAccentColor, 140));
            holder.replyContainer.setBackground(replyBg);
            holder.tvReplyAuthor.setText(msg.replyTo.author != null ? msg.replyTo.author : (isArabic ? "مستخدم" : "User"));
            holder.tvReplyAuthor.setTextColor(palette.isLight ? palette.text : roomAccentColor);
            holder.tvReplyText.setText(msg.replyTo.text);
            holder.tvReplyText.setTextColor(subTextColor);
            holder.replyContainer.setOnClickListener(v -> {
                if (msg.replyTo.id != null) {
                    listener.onReplyQuoteClicked(msg.replyTo.id);
                }
            });
        } else {
            holder.replyContainer.setVisibility(View.GONE);
            holder.replyContainer.setOnClickListener(null);
        }

        // Main text / Translated text / Caption
        String displayText = (msg.showingTranslated && msg.translatedText != null)
                ? ("🌐 " + msg.translatedText)
                : (msg.text != null ? msg.text : "");

        if (!displayText.isEmpty() && !msg.isVoiceMessage()) {
            holder.tvMessageText.setVisibility(View.VISIBLE);
            holder.tvMessageText.setText(displayText);
            holder.tvMessageText.setTextColor(mainTextColor);
        } else {
            holder.tvMessageText.setVisibility(View.GONE);
        }

        // File / Image / Voice attachment handling (interoperable with Web v2 Pro and Android v2 Pro)
        holder.cardAttachmentImage.setVisibility(View.GONE);
        holder.ivAttachmentImage.setVisibility(View.GONE);
        holder.voiceContainer.setVisibility(View.GONE);
        holder.btnOpenExternalFile.setVisibility(View.GONE);

        if (msg.isVoiceMessage() && msg.file != null) {
            holder.voiceContainer.setVisibility(View.VISIBLE);
            boolean isPlaying = msg.id != null && msg.id.equals(playingMessageId);
            holder.btnPlayVoice.setText(isPlaying ? (isArabic ? "إيقاف" : "Stop") : (isArabic ? "تشغيل" : "Play"));
            holder.btnPlayVoice.setIconResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
            holder.btnPlayVoice.setBackgroundTintList(ColorStateList.valueOf(roomAccentColor));
            holder.btnPlayVoice.setTextColor(palette.accentInk);
            holder.btnPlayVoice.setIconTint(ColorStateList.valueOf(palette.accentInk));
            int dur = msg.file.duration;
            holder.tvVoiceDuration.setText(dur > 0 ? (dur + "s") : (isArabic ? "رسالة صوتية" : "Voice note"));
            holder.tvVoiceDuration.setTextColor(subTextColor);
            holder.btnPlayVoice.setOnClickListener(v -> listener.onPlayVoiceClicked(msg));
        } else if ("file".equals(msg.type) && msg.file != null) {
            String mime = msg.file.mime != null ? msg.file.mime : "";
            String url = msg.file.getEffectiveUrl();
            String nameLower = msg.file.name != null ? msg.file.name.toLowerCase() : "";
            boolean isImg = mime.startsWith("image/") || url.startsWith("data:image/")
                    || nameLower.endsWith(".png") || nameLower.endsWith(".jpg")
                    || nameLower.endsWith(".jpeg") || nameLower.endsWith(".webp") || nameLower.endsWith(".gif");
            if (isImg && !url.isEmpty()) {
                holder.cardAttachmentImage.setVisibility(View.VISIBLE);
                holder.ivAttachmentImage.setVisibility(View.VISIBLE);
                loadImageOrDataUrl(holder.ivAttachmentImage, url, false);
                holder.ivAttachmentImage.setOnClickListener(v ->
                        listener.onImageClicked(url, msg.caption != null ? msg.caption : msg.text)
                );
            } else if (!url.isEmpty()) {
                holder.btnOpenExternalFile.setVisibility(View.VISIBLE);
                boolean isVideo = mime.startsWith("video/");
                String label = isVideo ? (isArabic ? "تشغيل الفيديو: " : "Play video: ")
                        : (isArabic ? "فتح الملف: " : "Open file: ");
                holder.btnOpenExternalFile.setText(label + (msg.file.name != null ? msg.file.name : "attachment"));
                holder.btnOpenExternalFile.setTextColor(mainTextColor);
                holder.btnOpenExternalFile.setIconTint(ColorStateList.valueOf(mainTextColor));
                holder.btnOpenExternalFile.setOnClickListener(v -> {
                    if (url.startsWith("http")) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        context.startActivity(browserIntent);
                    }
                });
            }
        }

        // #18: Render Emoji Reactions Row
        bindReactionsRow(holder, msg);

        // Footer quick actions
        holder.btnReply.setTextColor(subTextColor);
        holder.btnReply.setText(isArabic ? "↩ رد" : "↩ Reply");
        holder.btnReply.setOnClickListener(v -> listener.onReplyClicked(msg));

        holder.btnTranslate.setTextColor(subTextColor);
        holder.btnTranslate.setText(msg.showingTranslated
                ? (isArabic ? "الأصل" : "Original")
                : (isArabic ? "ترجمة" : "Translate"));
        holder.btnTranslate.setOnClickListener(v -> listener.onTranslateClicked(msg));

        holder.btnSaveMsg.setTextColor(isSaved ? (palette.isLight ? palette.text : roomAccentColor) : subTextColor);
        holder.btnSaveMsg.setText(isSaved ? (isArabic ? "★ محفوظة" : "★ Saved") : (isArabic ? "☆ حفظ" : "☆ Save"));
        holder.btnSaveMsg.setOnClickListener(v -> listener.onSaveClicked(msg));

        // #18: Long-press on message bubble opens Emoji Reaction & Action Picker
        holder.bubbleCard.setOnLongClickListener(v -> {
            listener.onMessageLongPressed(msg, holder.bubbleCard);
            return true;
        });

        // #15 & #21: Animate newly arrived message bubbles smoothly (skipped in Lite mode)
        if (position > lastAnimatedPosition && position == messages.size() - 1) {
            lastAnimatedPosition = position;
            MotionHelper.animateMessageBubbleIn(holder.bubbleCard, prefs.isLiteModeEnabled());
        }
    }

    private void bindReactionsRow(MsgViewHolder holder, Message msg) {
        holder.layoutReactionsContainer.removeAllViews();
        if (msg.reactions == null || msg.reactions.isEmpty()) {
            holder.scrollReactions.setVisibility(View.GONE);
            return;
        }

        float d = context.getResources().getDisplayMetrics().density;
        boolean hasAny = false;
        for (Map.Entry<String, List<String>> entry : msg.reactions.entrySet()) {
            String emoji = entry.getKey();
            List<String> uids = entry.getValue();
            if (emoji == null || uids == null || uids.isEmpty()) continue;
            hasAny = true;
            boolean iReacted = uids.contains(currentUserId);

            TextView chip = new TextView(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMarginEnd((int) (6 * d));
            chip.setLayoutParams(lp);
            chip.setPadding((int) (8 * d), (int) (3 * d), (int) (8 * d), (int) (3 * d));
            chip.setText(emoji + " " + uids.size());
            chip.setTextSize(12f);
            chip.setTextColor(palette.text);

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(14 * d);
            bg.setColor(iReacted ? ThemeHelper.withAlpha(roomAccentColor, 95) : palette.panelSoft);
            bg.setStroke((int) (1 * d), iReacted ? roomAccentColor : palette.panelHover);
            chip.setBackground(bg);

            chip.setOnClickListener(v -> listener.onReactionToggled(msg, emoji));
            holder.layoutReactionsContainer.addView(chip);
        }
        holder.scrollReactions.setVisibility(hasAny ? View.VISIBLE : View.GONE);
    }

    private void loadImageOrDataUrl(ImageView target, String url, boolean circleCrop) {
        if (url.startsWith("data:image/")) {
            try {
                int comma = url.indexOf(',');
                byte[] decoded = Base64.decode(url.substring(comma + 1), Base64.DEFAULT);
                if (circleCrop) {
                    Glide.with(context).load(decoded).circleCrop().into(target);
                } else {
                    Glide.with(context).load(decoded).into(target);
                }
            } catch (Exception ignored) {}
        } else {
            if (circleCrop) {
                Glide.with(context).load(url).circleCrop().into(target);
            } else {
                Glide.with(context).load(url).into(target);
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public static class MsgViewHolder extends RecyclerView.ViewHolder {
        public final MaterialCardView bubbleCard;
        final ImageView ivAuthorAvatar;
        final TextView tvAuthor;
        final TextView tvSavedBadge;
        final TextView tvTime;
        final TextView tvAckStatus;
        final LinearLayout replyContainer;
        final TextView tvReplyAuthor;
        final TextView tvReplyText;
        final TextView tvMessageText;
        final MaterialCardView cardAttachmentImage;
        final ImageView ivAttachmentImage;
        final LinearLayout voiceContainer;
        final MaterialButton btnPlayVoice;
        final TextView tvVoiceDuration;
        final MaterialButton btnOpenExternalFile;
        final HorizontalScrollView scrollReactions;
        final LinearLayout layoutReactionsContainer;
        final TextView btnReply;
        final TextView btnTranslate;
        final TextView btnSaveMsg;

        MsgViewHolder(@NonNull View itemView) {
            super(itemView);
            bubbleCard = itemView.findViewById(R.id.bubbleCard);
            ivAuthorAvatar = itemView.findViewById(R.id.ivAuthorAvatar);
            tvAuthor = itemView.findViewById(R.id.tvAuthor);
            tvSavedBadge = itemView.findViewById(R.id.tvSavedBadge);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvAckStatus = itemView.findViewById(R.id.tvAckStatus);
            replyContainer = itemView.findViewById(R.id.replyContainer);
            tvReplyAuthor = itemView.findViewById(R.id.tvReplyAuthor);
            tvReplyText = itemView.findViewById(R.id.tvReplyText);
            tvMessageText = itemView.findViewById(R.id.tvMessageText);
            cardAttachmentImage = itemView.findViewById(R.id.cardAttachmentImage);
            ivAttachmentImage = itemView.findViewById(R.id.ivAttachmentImage);
            voiceContainer = itemView.findViewById(R.id.voiceContainer);
            btnPlayVoice = itemView.findViewById(R.id.btnPlayVoice);
            tvVoiceDuration = itemView.findViewById(R.id.tvVoiceDuration);
            btnOpenExternalFile = itemView.findViewById(R.id.btnOpenExternalFile);
            scrollReactions = itemView.findViewById(R.id.scrollReactions);
            layoutReactionsContainer = itemView.findViewById(R.id.layoutReactionsContainer);
            btnReply = itemView.findViewById(R.id.btnReply);
            btnTranslate = itemView.findViewById(R.id.btnTranslate);
            btnSaveMsg = itemView.findViewById(R.id.btnSaveMsg);
        }
    }
}
