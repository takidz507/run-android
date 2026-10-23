package com.takidz.run.ui.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
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
import com.takidz.run.models.OnlineUser;
import com.takidz.run.utils.LocaleHelper;
import com.takidz.run.utils.PrefsManager;
import com.takidz.run.utils.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

public class OnlineUsersAdapter extends RecyclerView.Adapter<OnlineUsersAdapter.ViewHolder> {

    public interface UserActionListener {
        void onInviteClicked(OnlineUser user);
        void onAddFriendClicked(OnlineUser user);
    }

    private final Context context;
    private final PrefsManager prefs;
    private ThemeHelper.Palette palette;
    private final UserActionListener listener;
    private List<OnlineUser> users = new ArrayList<>();

    public OnlineUsersAdapter(Context context, PrefsManager prefs, ThemeHelper.Palette palette, UserActionListener listener) {
        this.context = context;
        this.prefs = prefs;
        this.palette = palette;
        this.listener = listener;
    }

    public void updatePalette(ThemeHelper.Palette newPalette) {
        this.palette = newPalette;
        notifyDataSetChanged();
    }

    public void setUsers(List<OnlineUser> newUsers) {
        this.users = newUsers != null ? newUsers : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_online_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OnlineUser user = users.get(position);
        holder.bind(user);
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardRoot;
        ImageView ivUserAvatar;
        TextView tvAvatarFallback;
        TextView tvUserName;
        TextView tvSelfBadge;
        TextView tvUserSub;
        LinearLayout layoutActions;
        MaterialButton btnInviteRoom;
        MaterialButton btnAddFriend;

        ViewHolder(View itemView) {
            super(itemView);
            cardRoot = (itemView instanceof MaterialCardView) ? (MaterialCardView) itemView : null;
            ivUserAvatar = itemView.findViewById(R.id.ivUserAvatar);
            tvAvatarFallback = itemView.findViewById(R.id.tvAvatarFallback);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvSelfBadge = itemView.findViewById(R.id.tvSelfBadge);
            tvUserSub = itemView.findViewById(R.id.tvUserSub);
            layoutActions = itemView.findViewById(R.id.layoutActions);
            btnInviteRoom = itemView.findViewById(R.id.btnInviteRoom);
            btnAddFriend = itemView.findViewById(R.id.btnAddFriend);
        }

        void bind(OnlineUser user) {
            boolean ar = prefs.isArabic();
            if (cardRoot != null) {
                cardRoot.setCardBackgroundColor(palette.panel);
                cardRoot.setStrokeColor(palette.panelHover);
            }

            tvUserName.setText(user.name);
            tvUserName.setTextColor(palette.text);
            tvUserSub.setTextColor(palette.muted);

            String initial = (user.name != null && !user.name.isEmpty()) ? user.name.substring(0, 1).toUpperCase() : "U";
            tvAvatarFallback.setText(initial);
            tvAvatarFallback.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
            tvAvatarFallback.setTextColor(palette.accentInk);
            if (user.avatarUrl != null && !user.avatarUrl.trim().isEmpty()) {
                ivUserAvatar.setVisibility(View.VISIBLE);
                tvAvatarFallback.setVisibility(View.GONE);
                Glide.with(context).load(user.avatarUrl).circleCrop().into(ivUserAvatar);
            } else {
                ivUserAvatar.setVisibility(View.GONE);
                tvAvatarFallback.setVisibility(View.VISIBLE);
            }

            if (user.isSelf) {
                tvSelfBadge.setVisibility(View.VISIBLE);
                tvSelfBadge.setText(ar ? "أنت" : "You");
                tvSelfBadge.setBackgroundTintList(ColorStateList.valueOf(palette.highlighter));
                tvSelfBadge.setTextColor(palette.accentInk);
                tvUserSub.setText(ar ? "نشط الآن (هذا الجهاز)" : "Active now (This device)");
                layoutActions.setVisibility(View.GONE);
            } else {
                tvSelfBadge.setVisibility(View.GONE);
                String rel = LocaleHelper.formatRelativeTimeMs(user.lastSeenTs, ar);
                String bioSuffix = (user.bio != null && !user.bio.isEmpty()) ? (" • " + user.bio) : "";
                tvUserSub.setText(rel + " • " + (user.platform != null ? user.platform : "Web") + bioSuffix);
                layoutActions.setVisibility(View.VISIBLE);

                boolean isFriend = prefs.isFriend(user.userId);
                boolean hasSent = prefs.hasSentRequest(user.userId);

                btnInviteRoom.setText(ar ? "دعوة" : "Invite");
                btnInviteRoom.setBackgroundTintList(ColorStateList.valueOf(palette.panelSoft));
                btnInviteRoom.setTextColor(palette.text);
                btnInviteRoom.setIconTint(ColorStateList.valueOf(palette.text));

                btnAddFriend.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
                btnAddFriend.setTextColor(palette.accentInk);
                btnAddFriend.setIconTint(ColorStateList.valueOf(palette.accentInk));

                if (isFriend) {
                    btnAddFriend.setText(ar ? "صديق ✓" : "Friends ✓");
                    btnAddFriend.setEnabled(false);
                    btnAddFriend.setIconResource(R.drawable.ic_check);
                } else if (hasSent) {
                    btnAddFriend.setText(ar ? "مرسل" : "Pending");
                    btnAddFriend.setEnabled(false);
                    btnAddFriend.setIconResource(R.drawable.ic_sync);
                } else {
                    btnAddFriend.setText(ar ? "+ صديق" : "Add Friend");
                    btnAddFriend.setEnabled(true);
                    btnAddFriend.setIconResource(R.drawable.ic_person_add);
                    btnAddFriend.setOnClickListener(v -> {
                        if (listener != null) listener.onAddFriendClicked(user);
                    });
                }

                btnInviteRoom.setOnClickListener(v -> {
                    if (listener != null) listener.onInviteClicked(user);
                });
            }
        }
    }
}
