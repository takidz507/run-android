package com.takidz.run.ui.adapters;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.takidz.run.R;
import com.takidz.run.models.SavedMessage;
import com.takidz.run.utils.LocaleHelper;
import com.takidz.run.utils.PrefsManager;
import com.takidz.run.utils.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * #31: Adapter for Personal Saved Messages ("My saved") gathered across all rooms,
 * with a one-tap jump back to the original message in its room.
 */
public class SavedMessagesAdapter extends RecyclerView.Adapter<SavedMessagesAdapter.SavedViewHolder> {

    public interface SavedMessageListener {
        void onJumpToMessage(SavedMessage item);
        void onRemoveSavedMessage(SavedMessage item);
    }

    private final PrefsManager prefs;
    private ThemeHelper.Palette palette;
    private final SavedMessageListener listener;
    private List<SavedMessage> items = new ArrayList<>();

    public SavedMessagesAdapter(PrefsManager prefs, ThemeHelper.Palette palette, SavedMessageListener listener) {
        this.prefs = prefs;
        this.palette = palette;
        this.listener = listener;
    }

    public void updatePalette(ThemeHelper.Palette newPalette) {
        this.palette = newPalette;
        notifyDataSetChanged();
    }

    public void setItems(List<SavedMessage> list) {
        this.items = list != null ? new ArrayList<>(list) : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SavedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_saved_message, parent, false);
        return new SavedViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull SavedViewHolder holder, int position) {
        SavedMessage item = items.get(position);
        boolean ar = prefs.isArabic();

        holder.cardSavedMessage.setCardBackgroundColor(palette.panel);
        holder.cardSavedMessage.setStrokeColor(palette.panelHover);

        String roomLabel = (item.roomName != null ? item.roomName : ("Room " + item.roomCode)) + " • #" + item.roomCode;
        holder.tvSavedRoomBadge.setText(roomLabel);
        holder.tvSavedRoomBadge.setBackgroundTintList(ColorStateList.valueOf(palette.highlighter));
        holder.tvSavedRoomBadge.setTextColor(palette.accentInk);

        String timeStr = LocaleHelper.formatIsoTime(item.createdAt, ar);
        holder.tvSavedAuthorTime.setText(item.author + (timeStr.isEmpty() ? "" : (" • " + timeStr)));
        holder.tvSavedAuthorTime.setTextColor(palette.muted);

        holder.tvSavedContent.setText(item.text);
        holder.tvSavedContent.setTextColor(palette.text);

        holder.btnJumpToSavedMessage.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        holder.btnJumpToSavedMessage.setTextColor(palette.accentInk);
        holder.btnJumpToSavedMessage.setText(ar ? "الانتقال للرسالة ↖" : "Jump to message ↗");

        View.OnClickListener jumpClick = v -> {
            if (listener != null) listener.onJumpToMessage(item);
        };
        holder.cardSavedMessage.setOnClickListener(jumpClick);
        holder.btnJumpToSavedMessage.setOnClickListener(jumpClick);

        holder.btnRemoveSaved.setColorFilter(palette.subtle);
        holder.btnRemoveSaved.setOnClickListener(v -> {
            if (listener != null) listener.onRemoveSavedMessage(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class SavedViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView cardSavedMessage;
        final TextView tvSavedRoomBadge;
        final TextView tvSavedAuthorTime;
        final TextView tvSavedContent;
        final ImageButton btnRemoveSaved;
        final MaterialButton btnJumpToSavedMessage;

        SavedViewHolder(@NonNull View itemView) {
            super(itemView);
            cardSavedMessage = itemView.findViewById(R.id.cardSavedMessage);
            tvSavedRoomBadge = itemView.findViewById(R.id.tvSavedRoomBadge);
            tvSavedAuthorTime = itemView.findViewById(R.id.tvSavedAuthorTime);
            tvSavedContent = itemView.findViewById(R.id.tvSavedContent);
            btnRemoveSaved = itemView.findViewById(R.id.btnRemoveSaved);
            btnJumpToSavedMessage = itemView.findViewById(R.id.btnJumpToSavedMessage);
        }
    }
}
