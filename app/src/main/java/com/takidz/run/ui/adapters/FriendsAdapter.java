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
import com.takidz.run.models.SignalMessage;

import java.util.ArrayList;
import java.util.List;

public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.ViewHolder> {

    public interface FriendsActionListener {
        void onAcceptFriendRequest(SignalMessage request);
        void onDeclineFriendRequest(SignalMessage request);
        void onJoinRoomInvite(SignalMessage invite);
        void onDismissRoomInvite(SignalMessage invite);
        void onInviteFriendToRoom(OnlineUser friend);
        void onRemoveFriend(OnlineUser friend);
    }

    public static class DisplayItem {
        public static final int TYPE_REQUEST = 0;
        public static final int TYPE_INVITE = 1;
        public static final int TYPE_FRIEND = 2;

        public int type;
        public SignalMessage signal;
        public OnlineUser friend;

        public DisplayItem(int type, SignalMessage signal, OnlineUser friend) {
            this.type = type;
            this.signal = signal;
            this.friend = friend;
        }
    }

    private final Context context;
    private final FriendsActionListener listener;
    private List<DisplayItem> items = new ArrayList<>();

    public FriendsAdapter(Context context, FriendsActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setData(List<SignalMessage> requests, List<SignalMessage> invites, List<OnlineUser> friends) {
        items.clear();
        if (invites != null) {
            for (SignalMessage i : invites) {
                items.add(new DisplayItem(DisplayItem.TYPE_INVITE, i, null));
            }
        }
        if (requests != null) {
            for (SignalMessage r : requests) {
                items.add(new DisplayItem(DisplayItem.TYPE_REQUEST, r, null));
            }
        }
        if (friends != null) {
            for (OnlineUser f : friends) {
                items.add(new DisplayItem(DisplayItem.TYPE_FRIEND, null, f));
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DisplayItem item = items.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvAvatarFallback;
        TextView tvTitle;
        TextView tvSub;
        LinearLayout layoutActions;
        MaterialButton btnSecondary;
        MaterialButton btnPrimary;

        ViewHolder(View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvAvatarFallback = itemView.findViewById(R.id.tvAvatarFallback);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSub = itemView.findViewById(R.id.tvSub);
            layoutActions = itemView.findViewById(R.id.layoutActions);
            btnSecondary = itemView.findViewById(R.id.btnSecondary);
            btnPrimary = itemView.findViewById(R.id.btnPrimary);
        }

        void bind(DisplayItem item) {
            if (item.type == DisplayItem.TYPE_INVITE) {
                SignalMessage invite = item.signal;
                tvTitle.setText(invite.fromUserName);
                tvSub.setText("Invited you to Room #" + invite.roomCode + " (" + invite.roomName + ")");
                setupAvatar(invite.fromUserName, invite.fromAvatarUrl);

                btnPrimary.setText("Join Room");
                btnPrimary.setIconResource(R.drawable.ic_chat);
                btnPrimary.setOnClickListener(v -> {
                    if (listener != null) listener.onJoinRoomInvite(invite);
                });

                btnSecondary.setText("Dismiss");
                btnSecondary.setIconResource(R.drawable.ic_close);
                btnSecondary.setOnClickListener(v -> {
                    if (listener != null) listener.onDismissRoomInvite(invite);
                });
            } else if (item.type == DisplayItem.TYPE_REQUEST) {
                SignalMessage req = item.signal;
                tvTitle.setText(req.fromUserName);
                tvSub.setText("Sent you a friend request");
                setupAvatar(req.fromUserName, req.fromAvatarUrl);

                btnPrimary.setText("Accept");
                btnPrimary.setIconResource(R.drawable.ic_check);
                btnPrimary.setOnClickListener(v -> {
                    if (listener != null) listener.onAcceptFriendRequest(req);
                });

                btnSecondary.setText("Decline");
                btnSecondary.setIconResource(R.drawable.ic_close);
                btnSecondary.setOnClickListener(v -> {
                    if (listener != null) listener.onDeclineFriendRequest(req);
                });
            } else {
                OnlineUser friend = item.friend;
                tvTitle.setText(friend.name);
                tvSub.setText("Friend");
                setupAvatar(friend.name, friend.avatarUrl);

                btnPrimary.setText("Invite");
                btnPrimary.setIconResource(R.drawable.ic_share);
                btnPrimary.setOnClickListener(v -> {
                    if (listener != null) listener.onInviteFriendToRoom(friend);
                });

                btnSecondary.setText("Remove");
                btnSecondary.setIconResource(R.drawable.ic_delete);
                btnSecondary.setOnClickListener(v -> {
                    if (listener != null) listener.onRemoveFriend(friend);
                });
            }
        }

        private void setupAvatar(String name, String avatarUrl) {
            String initial = (name != null && !name.isEmpty()) ? name.substring(0, 1).toUpperCase() : "U";
            tvAvatarFallback.setText(initial);
            if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
                ivAvatar.setVisibility(View.VISIBLE);
                tvAvatarFallback.setVisibility(View.GONE);
                Glide.with(context).load(avatarUrl).circleCrop().into(ivAvatar);
            } else {
                ivAvatar.setVisibility(View.GONE);
                tvAvatarFallback.setVisibility(View.VISIBLE);
            }
        }
    }
}
