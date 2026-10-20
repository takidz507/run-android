package com.takidz.run.ui.adapters;

import android.content.Context;
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
import com.takidz.run.R;
import com.takidz.run.models.OnlineUser;
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
    private final ThemeHelper.Palette palette;
    private final UserActionListener listener;
    private List<OnlineUser> users = new ArrayList<>();

    public OnlineUsersAdapter(Context context, PrefsManager prefs, ThemeHelper.Palette palette, UserActionListener listener) {
        this.context = context;
        this.prefs = prefs;
        this.palette = palette;
        this.listener = listener;
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
            tvUserName.setText(user.name);

            String initial = (user.name != null && !user.name.isEmpty()) ? user.name.substring(0, 1).toUpperCase() : "U";
            tvAvatarFallback.setText(initial);
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
                tvUserSub.setText("Active now (This device)");
                layoutActions.setVisibility(View.GONE);
            } else {
                tvSelfBadge.setVisibility(View.GONE);
                long secAgo = Math.max(0, (System.currentTimeMillis() - user.lastSeenTs) / 1000);
                if (secAgo < 25) {
                    tvUserSub.setText("Active now" + (user.bio.isEmpty() ? "" : " • " + user.bio));
                } else {
                    tvUserSub.setText("Last seen " + secAgo + "s ago" + (user.bio.isEmpty() ? "" : " • " + user.bio));
                }
                layoutActions.setVisibility(View.VISIBLE);

                boolean isFriend = prefs.isFriend(user.userId);
                boolean hasSent = prefs.hasSentRequest(user.userId);

                if (isFriend) {
                    btnAddFriend.setText("Friends ✓");
                    btnAddFriend.setEnabled(false);
                    btnAddFriend.setIconResource(R.drawable.ic_check);
                } else if (hasSent) {
                    btnAddFriend.setText("Pending");
                    btnAddFriend.setEnabled(false);
                    btnAddFriend.setIconResource(R.drawable.ic_sync);
                } else {
                    btnAddFriend.setText("Add Friend");
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
