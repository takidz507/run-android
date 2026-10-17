package com.takidz.run.ui.adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.takidz.run.utils.ThemeHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

/**
 * Audit-Hardened MessagesAdapter:
 * - #2 & #12: Uses DiffUtil instead of blanket notifyDataSetChanged() and provides notifyMessageUpdatedById().
 * - #13 & F2: Click listener on attached images & videos for fullscreen preview / playback.
 * - #14 & F3: Click listener on quoted reply container to jump & highlight original message.
 * - #25: Converts UTC ISO-8601 timestamps to device local TimeZone.
 * - F5: Displays sender profile avatar circle next to name when available.
 */
public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.MsgViewHolder> {

    public interface MessageInteractionListener {
        void onReplyClicked(Message message);
        void onReplyQuoteClicked(String targetMessageId);
        void onTranslateClicked(Message message);
        void onPlayVoiceClicked(Message message);
        void onImageClicked(String imageUrlOrDataUri, String caption);
    }

    private final Context context;
    private final String currentUserId;
    private final MessageInteractionListener listener;
    private List<Message> messages = new ArrayList<>();
    private Map<String, RoomData.AvatarProfile> avatars = new HashMap<>();
    private ThemeHelper.Palette palette;
    private String playingMessageId = null;
    private String highlightedMessageId = null;

    public MessagesAdapter(Context context, String currentUserId, ThemeHelper.Palette palette, MessageInteractionListener listener) {
        this.context = context;
        this.currentUserId = currentUserId;
        this.palette = palette;
        this.listener = listener;
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

    /**
     * Fix #2: Safely updates a message by its ID rather than a stale loop index.
     */
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

    /**
     * Fix #12: Uses DiffUtil to prevent UI flicker and Glide image reloading on every state update.
     */
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
                        && Objects.equals(o.caption, n.caption);
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
        boolean isMine = msg.userId != null && msg.userId.equals(currentUserId);
        boolean isHighlighted = msg.id != null && msg.id.equals(highlightedMessageId);

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.bubbleCard.getLayoutParams();
        params.gravity = isMine ? Gravity.END : Gravity.START;
        holder.bubbleCard.setLayoutParams(params);
        holder.bubbleCard.setCardBackgroundColor(isHighlighted ? palette.panelSoft : (isMine ? palette.panelHover : palette.panel));
        holder.bubbleCard.setStrokeColor(isHighlighted ? palette.purple : (isMine ? palette.accent : palette.panelSoft));
        holder.bubbleCard.setStrokeWidth(isHighlighted ? 4 : 2);

        holder.tvAuthor.setText(isMine ? "أنت (" + msg.author + ")" : msg.author);
        holder.tvAuthor.setTextColor(isMine ? palette.accent : palette.purple);

        // Fix #16 & F5: Render author avatar if present in room avatars
        RoomData.AvatarProfile profile = (msg.userId != null && avatars != null) ? avatars.get(msg.userId) : null;
        if (profile != null && profile.avatarUrl != null && !profile.avatarUrl.isEmpty()) {
            holder.ivAuthorAvatar.setVisibility(View.VISIBLE);
            loadImageOrDataUrl(holder.ivAuthorAvatar, profile.avatarUrl, true);
        } else {
            holder.ivAuthorAvatar.setVisibility(View.GONE);
        }

        // Fix #25: Format timestamp in local device timezone
        holder.tvTime.setText(formatIsoTimeLocal(msg.createdAt));
        holder.tvTime.setTextColor(palette.subtle);

        // Fix #14 & F3: Quoted reply box jumps to original message on click
        if (msg.replyTo != null && msg.replyTo.text != null) {
            holder.replyContainer.setVisibility(View.VISIBLE);
            holder.replyContainer.setBackgroundColor(palette.bgElevated);
            holder.tvReplyAuthor.setText(msg.replyTo.author != null ? msg.replyTo.author : "مستخدم");
            holder.tvReplyAuthor.setTextColor(palette.accent);
            holder.tvReplyText.setText(msg.replyTo.text);
            holder.tvReplyText.setTextColor(palette.muted);
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
                ? msg.translatedText
                : (msg.text != null ? msg.text : "");

        if (!displayText.isEmpty()) {
            holder.tvMessageText.setVisibility(View.VISIBLE);
            holder.tvMessageText.setText(displayText);
            holder.tvMessageText.setTextColor(palette.text);
        } else {
            holder.tvMessageText.setVisibility(View.GONE);
        }

        // File / Image / Voice attachment handling
        holder.ivAttachmentImage.setVisibility(View.GONE);
        holder.voiceContainer.setVisibility(View.GONE);
        holder.btnOpenExternalFile.setVisibility(View.GONE);

        if ("voice".equals(msg.type) && msg.file != null) {
            holder.voiceContainer.setVisibility(View.VISIBLE);
            boolean isPlaying = msg.id != null && msg.id.equals(playingMessageId);
            holder.btnPlayVoice.setText(isPlaying ? "إيقاف ⏹" : "تشغيل 🔊");
            holder.btnPlayVoice.setBackgroundTintList(android.content.res.ColorStateList.valueOf(palette.accent));
            holder.btnPlayVoice.setTextColor(palette.accentInk);
            int dur = msg.file.duration;
            holder.tvVoiceDuration.setText(dur > 0 ? (dur + " ثانية") : "تسجيل صوتي");
            holder.tvVoiceDuration.setTextColor(palette.muted);
            holder.btnPlayVoice.setOnClickListener(v -> listener.onPlayVoiceClicked(msg));
        } else if ("file".equals(msg.type) && msg.file != null && msg.file.url != null) {
            String mime = msg.file.mime != null ? msg.file.mime : "";
            String url = msg.file.url;
            if (mime.startsWith("image/") || url.startsWith("data:image/")) {
                holder.ivAttachmentImage.setVisibility(View.VISIBLE);
                loadImageOrDataUrl(holder.ivAttachmentImage, url, false);
                // Fix #13 & F2: Open fullscreen image viewer on tap
                holder.ivAttachmentImage.setOnClickListener(v ->
                        listener.onImageClicked(url, msg.caption != null ? msg.caption : msg.text)
                );
            } else {
                holder.btnOpenExternalFile.setVisibility(View.VISIBLE);
                boolean isVideo = mime.startsWith("video/");
                String label = isVideo ? "🎬 تشغيل الفيديو: " : "📎 فتح الملف: ";
                holder.btnOpenExternalFile.setText(label + (msg.file.name != null ? msg.file.name : "ملف مرفق"));
                holder.btnOpenExternalFile.setTextColor(palette.accent);
                holder.btnOpenExternalFile.setOnClickListener(v -> {
                    if (url.startsWith("http")) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        context.startActivity(browserIntent);
                    }
                });
            }
        }

        // Actions: Reply & Translate
        holder.btnReply.setTextColor(palette.muted);
        holder.btnReply.setOnClickListener(v -> listener.onReplyClicked(msg));

        holder.btnTranslate.setTextColor(palette.accent);
        holder.btnTranslate.setText(msg.showingTranslated ? "عرض النص الأصلي" : "ترجمة الرسالة");
        holder.btnTranslate.setOnClickListener(v -> listener.onTranslateClicked(msg));
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

    /**
     * Fix #25: Converts UTC ISO-8601 timestamp to user's local timezone (e.g. Algeria UTC+1).
     */
    private String formatIsoTimeLocal(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = parser.parse(iso);
            if (date != null) {
                SimpleDateFormat localFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                localFormat.setTimeZone(TimeZone.getDefault());
                return localFormat.format(date);
            }
        } catch (Exception ignored) {}
        if (iso.length() >= 16) {
            return iso.substring(11, 16);
        }
        return "";
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MsgViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView bubbleCard;
        final ImageView ivAuthorAvatar;
        final TextView tvAuthor;
        final TextView tvTime;
        final LinearLayout replyContainer;
        final TextView tvReplyAuthor;
        final TextView tvReplyText;
        final TextView tvMessageText;
        final ImageView ivAttachmentImage;
        final LinearLayout voiceContainer;
        final MaterialButton btnPlayVoice;
        final TextView tvVoiceDuration;
        final MaterialButton btnOpenExternalFile;
        final TextView btnReply;
        final TextView btnTranslate;

        MsgViewHolder(@NonNull View itemView) {
            super(itemView);
            bubbleCard = itemView.findViewById(R.id.bubbleCard);
            ivAuthorAvatar = itemView.findViewById(R.id.ivAuthorAvatar);
            tvAuthor = itemView.findViewById(R.id.tvAuthor);
            tvTime = itemView.findViewById(R.id.tvTime);
            replyContainer = itemView.findViewById(R.id.replyContainer);
            tvReplyAuthor = itemView.findViewById(R.id.tvReplyAuthor);
            tvReplyText = itemView.findViewById(R.id.tvReplyText);
            tvMessageText = itemView.findViewById(R.id.tvMessageText);
            ivAttachmentImage = itemView.findViewById(R.id.ivAttachmentImage);
            voiceContainer = itemView.findViewById(R.id.voiceContainer);
            btnPlayVoice = itemView.findViewById(R.id.btnPlayVoice);
            tvVoiceDuration = itemView.findViewById(R.id.tvVoiceDuration);
            btnOpenExternalFile = itemView.findViewById(R.id.btnOpenExternalFile);
            btnReply = itemView.findViewById(R.id.btnReply);
            btnTranslate = itemView.findViewById(R.id.btnTranslate);
        }
    }
}
