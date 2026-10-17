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
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.takidz.run.R;
import com.takidz.run.models.Message;
import com.takidz.run.utils.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.MsgViewHolder> {

    public interface MessageInteractionListener {
        void onReplyClicked(Message message);
        void onTranslateClicked(Message message, int position);
        void onPlayVoiceClicked(Message message, int position);
    }

    private final Context context;
    private final String currentUserId;
    private final MessageInteractionListener listener;
    private List<Message> messages = new ArrayList<>();
    private ThemeHelper.Palette palette;
    private String playingMessageId = null;

    public MessagesAdapter(Context context, String currentUserId, ThemeHelper.Palette palette, MessageInteractionListener listener) {
        this.context = context;
        this.currentUserId = currentUserId;
        this.palette = palette;
        this.listener = listener;
    }

    public void updatePalette(ThemeHelper.Palette newPalette) {
        this.palette = newPalette;
        notifyDataSetChanged();
    }

    public void setPlayingMessageId(String msgId) {
        this.playingMessageId = msgId;
        notifyDataSetChanged();
    }

    public void submitList(List<Message> newList) {
        this.messages = newList != null ? new ArrayList<>(newList) : new ArrayList<>();
        notifyDataSetChanged();
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

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.bubbleCard.getLayoutParams();
        params.gravity = isMine ? Gravity.END : Gravity.START;
        holder.bubbleCard.setLayoutParams(params);
        holder.bubbleCard.setCardBackgroundColor(isMine ? palette.panelHover : palette.panel);
        holder.bubbleCard.setStrokeColor(isMine ? palette.accent : palette.panelSoft);

        holder.tvAuthor.setText(isMine ? "أنت (" + msg.author + ")" : msg.author);
        holder.tvAuthor.setTextColor(isMine ? palette.accent : palette.purple);

        String timeFormatted = formatIsoTime(msg.createdAt);
        holder.tvTime.setText(timeFormatted);
        holder.tvTime.setTextColor(palette.subtle);

        // Reply quote box
        if (msg.replyTo != null && msg.replyTo.text != null) {
            holder.replyContainer.setVisibility(View.VISIBLE);
            holder.replyContainer.setBackgroundColor(palette.bgElevated);
            holder.tvReplyAuthor.setText(msg.replyTo.author != null ? msg.replyTo.author : "مستخدم");
            holder.tvReplyAuthor.setTextColor(palette.accent);
            holder.tvReplyText.setText(msg.replyTo.text);
            holder.tvReplyText.setTextColor(palette.muted);
        } else {
            holder.replyContainer.setVisibility(View.GONE);
        }

        // Main text / Translated text
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
            holder.btnPlayVoice.setOnClickListener(v -> listener.onPlayVoiceClicked(msg, holder.getAdapterPosition()));
        } else if ("file".equals(msg.type) && msg.file != null && msg.file.url != null) {
            String mime = msg.file.mime != null ? msg.file.mime : "";
            String url = msg.file.url;
            if (mime.startsWith("image/") || url.startsWith("data:image/")) {
                holder.ivAttachmentImage.setVisibility(View.VISIBLE);
                if (url.startsWith("data:image/")) {
                    try {
                        int comma = url.indexOf(',');
                        byte[] decoded = Base64.decode(url.substring(comma + 1), Base64.DEFAULT);
                        Glide.with(context).load(decoded).into(holder.ivAttachmentImage);
                    } catch (Exception ignored) {}
                } else {
                    Glide.with(context).load(url).into(holder.ivAttachmentImage);
                }
            } else {
                holder.btnOpenExternalFile.setVisibility(View.VISIBLE);
                holder.btnOpenExternalFile.setText("📎 فتح الملف: " + (msg.file.name != null ? msg.file.name : "ملف مرفق"));
                holder.btnOpenExternalFile.setTextColor(palette.accent);
                holder.btnOpenExternalFile.setOnClickListener(v -> {
                    if (url.startsWith("http")) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        context.startActivity(browserIntent);
                    }
                });
            }
        }

        // Actions: Reply & Translate (Show Original toggle)
        holder.btnReply.setTextColor(palette.muted);
        holder.btnReply.setOnClickListener(v -> listener.onReplyClicked(msg));

        holder.btnTranslate.setTextColor(palette.accent);
        holder.btnTranslate.setText(msg.showingTranslated ? "عرض النص الأصلي" : "ترجمة الرسالة");
        holder.btnTranslate.setOnClickListener(v -> listener.onTranslateClicked(msg, holder.getAdapterPosition()));
    }

    private String formatIsoTime(String iso) {
        if (iso == null || iso.length() < 16) return "";
        try {
            return iso.substring(11, 16);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MsgViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView bubbleCard;
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
