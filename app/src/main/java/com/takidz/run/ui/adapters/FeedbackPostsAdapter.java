package com.takidz.run.ui.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.takidz.run.R;
import com.takidz.run.models.FeedbackPost;
import com.takidz.run.utils.PrefsManager;
import com.takidz.run.utils.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

public class FeedbackPostsAdapter extends RecyclerView.Adapter<FeedbackPostsAdapter.ViewHolder> {

    public interface VoteListener {
        void onVote(FeedbackPost post, boolean isHeart);
    }

    private final Context context;
    private final PrefsManager prefs;
    private final ThemeHelper.Palette palette;
    private final VoteListener voteListener;
    private List<FeedbackPost> posts = new ArrayList<>();

    public FeedbackPostsAdapter(Context context, PrefsManager prefs, ThemeHelper.Palette palette, VoteListener voteListener) {
        this.context = context;
        this.prefs = prefs;
        this.palette = palette;
        this.voteListener = voteListener;
    }

    public void setPosts(List<FeedbackPost> newPosts) {
        this.posts = newPosts != null ? newPosts : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_feedback_post, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FeedbackPost post = posts.get(position);
        holder.bind(post);
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategoryChip;
        TextView tvPostAuthorTime;
        TextView tvPostTitle;
        TextView tvPostContent;
        MaterialButton btnUpvote;
        MaterialButton btnHeart;

        ViewHolder(View itemView) {
            super(itemView);
            tvCategoryChip = itemView.findViewById(R.id.tvCategoryChip);
            tvPostAuthorTime = itemView.findViewById(R.id.tvPostAuthorTime);
            tvPostTitle = itemView.findViewById(R.id.tvPostTitle);
            tvPostContent = itemView.findViewById(R.id.tvPostContent);
            btnUpvote = itemView.findViewById(R.id.btnUpvote);
            btnHeart = itemView.findViewById(R.id.btnHeart);
        }

        void bind(FeedbackPost post) {
            tvPostTitle.setText(post.title);
            tvPostContent.setText(post.content);
            tvCategoryChip.setText(post.category != null ? post.category : "Idea");

            String author = (post.author != null && !post.author.isEmpty()) ? post.author : "Guest";
            tvPostAuthorTime.setText(author);

            String myId = prefs.getUserId();
            boolean hasUpvoted = post.hasUpvoted(myId);
            boolean hasHearted = post.hasHearted(myId);

            btnUpvote.setText(String.valueOf(post.getUpvoteCount()));
            btnHeart.setText(String.valueOf(post.getHeartCount()));

            if (hasUpvoted) {
                btnUpvote.setIconTint(ColorStateList.valueOf(palette.accent));
                btnUpvote.setTextColor(palette.accent);
            } else {
                btnUpvote.setIconTint(ColorStateList.valueOf(palette.muted));
                btnUpvote.setTextColor(palette.muted);
            }

            if (hasHearted) {
                btnHeart.setIconTint(ColorStateList.valueOf(palette.danger));
                btnHeart.setTextColor(palette.danger);
            } else {
                btnHeart.setIconTint(ColorStateList.valueOf(palette.muted));
                btnHeart.setTextColor(palette.muted);
            }

            btnUpvote.setOnClickListener(v -> {
                if (voteListener != null) voteListener.onVote(post, false);
            });

            btnHeart.setOnClickListener(v -> {
                if (voteListener != null) voteListener.onVote(post, true);
            });
        }
    }
}
