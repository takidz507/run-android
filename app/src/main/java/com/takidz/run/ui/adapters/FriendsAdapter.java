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
import com.takidz.run.models.SignalMessage;
import com.takidz.run.utils.PrefsManager;
import com.takidz.run.utils.ThemeHelper;

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
    private final PrefsManager prefs;
    private ThemeHelper.Palette palette;
    private final FriendsActionListener listener;
    private final List<DisplayItem> items = new ArrayList<>();

    public FriendsAdapter(Context context, FriendsActionListener listener) {
        this.context = context;
        this.prefs = new PrefsManager(context);
        this.palette = ThemeHelper.getPalette(prefs.getThemeMode());
        this.listener = listener;
    }

    public void updatePalette(ThemeHelper.Palette newPalette) {
        this.palette = newPalette;
        notifyDataSetChanged();
    }

    public void setData(List<SignalMessage> requests, List<SignalMessage> invites, List<OnlineUser> friends) {
        this.palette = ThemeHelper.getPalette(prefs.getThemeMode());
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
        MaterialCardView cardRoot;
        ImageView ivAvatar;
        TextView tvAvatarFallback;
        TextView tvTitle;
        TextView tvSub;
        LinearLayout layoutActions;
        MaterialButton btnSecondary;
        MaterialButton btnPrimary;

        ViewHolder(View itemView) {
            super(itemView);
            cardRoot = (itemView instanceof MaterialCardView) ? (MaterialCardView) itemView : null;
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvAvatarFallback = itemView.findViewById(R.id.tvAvatarFallback);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSub = itemView.findViewById(R.id.tvSub);
            layoutActions = itemView.findViewById(R.id.layoutActions);
            btnSecondary = itemView.findViewById(R.id.btnSecondary);
            btnPrimary = itemView.findViewById(R.id.btnPrimary);
        }

        void bind(DisplayItem item) {
            boolean ar = prefs.isArabic();
            if (cardRoot != null) {
                cardRoot.setCardBackgroundColor(palette.panel);
                cardRoot.setStrokeColor(palette.panelHover);
            }
            tvTitle.setTextColor(palette.text);
            tvSub.setTextColor(palette.muted);

            btnPrimary.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
            btnPrimary.setTextColor(palette.accentInk);
            btnPrimary.setIconTint(ColorStateList.valueOf(palette.accentInk));

            btnSecondary.setBackgroundTintList(ColorStateList.valueOf(palette.panelSoft));
            btnSecondary.setTextColor(palette.text);
            btnSecondary.setIconTint(ColorStateList.valueOf(palette.text));

            if (item.type == DisplayItem.TYPE_INVITE) {
                SignalMessage invite = item.signal;
                tvTitle.setText(invite.fromUserName);
                tvSub.setText(ar
                        ? ("دعاك إلى غرفة #" + invite.roomCode + " (" + invite.roomName + ")")
                        : ("Invited you to Room #" + invite.roomCode + " (" + invite.roomName + ")"));
                setupAvatar(invite.fromUserName, invite.fromAvatarUrl);

                btnPrimary.setText(ar ? "انضمام" : "Join Room");
                btnPrimary.setIconResource(R.drawable.ic_chat);
                btnPrimary.setOnClickListener(v -> {
                    if (listener != null) listener.onJoinRoomInvite(invite);
                });

                btnSecondary.setText(ar ? "تجاهل" : "Dismiss");
                btnSecondary.setIconResource(R.drawable.ic_close);
                btnSecondary.setOnClickListener(v -> {
                    if (listener != null) listener.onDismissRoomInvite(invite);
                });
            } else if (item.type == DisplayItem.TYPE_REQUEST) {
                SignalMessage req = item.signal;
                tvTitle.setText(req.fromUserName);
                tvSub.setText(ar ? "أرسل لك طلب صداقة" : "Sent you a friend request");
                setupAvatar(req.fromUserName, req.fromAvatarUrl);

                btnPrimary.setText(ar ? "قبول" : "Accept");
                btnPrimary.setIconResource(R.drawable.ic_check);
                btnPrimary.setOnClickListener(v -> {
                    if (listener != null) listener.onAcceptFriendRequest(req);
                });

                btnSecondary.setText(ar ? "رفض" : "Decline");
                btnSecondary.setIconResource(R.drawable.ic_close);
                btnSecondary.setOnClickListener(v -> {
                    if (listener != null) listener.onDeclineFriendRequest(req);
                });
            } else {
                OnlineUser friend = item.friend;
                tvTitle.setText(friend.name);
                tvSub.setText(ar ? "صديق ★" : "Friend ★");
                setupAvatar(friend.name, friend.avatarUrl);

                btnPrimary.setText(ar ? "دعوة للغرفة" : "Invite");
                btnPrimary.setIconResource(R.drawable.ic_share);
                btnPrimary.setOnClickListener(v -> {
                    if (listener != null) listener.onInviteFriendToRoom(friend);
                });

                btnSecondary.setText(ar ? "إزالة" : "Remove");
                btnSecondary.setIconResource(R.drawable.ic_delete);
                btnSecondary.setOnClickListener(v -> {
                    if (listener != null) listener.onRemoveFriend(friend);
                });
            }
        }

        private void setupAvatar(String name, String avatarUrl) {
            String initial = (name != null && !name.isEmpty()) ? name.substring(0, 1).toUpperCase() : "U";
            tvAvatarFallback.setText(initial);
            tvAvatarFallback.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
            tvAvatarFallback.setTextColor(palette.accentInk);
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
