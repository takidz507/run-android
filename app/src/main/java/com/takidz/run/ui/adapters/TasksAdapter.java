package com.takidz.run.ui.adapters;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.takidz.run.R;
import com.takidz.run.models.TaskItem;
import com.takidz.run.utils.ThemeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TasksAdapter extends RecyclerView.Adapter<TasksAdapter.TaskViewHolder> {

    public interface TaskActionListener {
        void onTaskToggled(TaskItem task, boolean isDone);
        void onTaskDeleted(TaskItem task);
    }

    private List<TaskItem> tasks = new ArrayList<>();
    private ThemeHelper.Palette palette;
    private final TaskActionListener listener;

    public TasksAdapter(ThemeHelper.Palette palette, TaskActionListener listener) {
        this.palette = palette;
        this.listener = listener;
    }

    public void updatePalette(ThemeHelper.Palette newPalette) {
        this.palette = newPalette;
        notifyItemRangeChanged(0, tasks.size());
    }

    /**
     * Fix #12: Uses DiffUtil for smooth task list updates without full redraws.
     */
    public void submitList(List<TaskItem> newTasks) {
        final List<TaskItem> oldList = this.tasks;
        final List<TaskItem> updated = newTasks != null ? new ArrayList<>(newTasks) : new ArrayList<>();

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
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
                TaskItem o = oldList.get(oldItemPosition);
                TaskItem n = updated.get(newItemPosition);
                return o != null && n != null && Objects.equals(o.id, n.id);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                TaskItem o = oldList.get(oldItemPosition);
                TaskItem n = updated.get(newItemPosition);
                if (o == null || n == null) return false;
                return o.done == n.done && Objects.equals(o.text, n.text);
            }
        });

        this.tasks = updated;
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        TaskItem task = tasks.get(position);
        holder.taskCard.setCardBackgroundColor(palette.panel);
        holder.taskCard.setStrokeColor(task.done ? palette.success : palette.panelSoft);

        holder.cbTaskDone.setOnCheckedChangeListener(null);
        holder.cbTaskDone.setChecked(task.done);
        holder.cbTaskDone.setButtonTintList(android.content.res.ColorStateList.valueOf(palette.accent));

        holder.tvTaskText.setText(task.text);
        holder.tvTaskText.setTextColor(task.done ? palette.muted : palette.text);
        if (task.done) {
            holder.tvTaskText.setPaintFlags(holder.tvTaskText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            holder.tvTaskText.setPaintFlags(holder.tvTaskText.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
        }

        String creator = (task.author != null && !task.author.isEmpty()) ? task.author : "Guest";
        holder.tvTaskAuthor.setText("أضافها: " + creator);
        holder.tvTaskAuthor.setTextColor(palette.subtle);

        holder.cbTaskDone.setOnCheckedChangeListener((buttonView, isChecked) -> {
            listener.onTaskToggled(task, isChecked);
        });
        holder.btnDeleteTask.setColorFilter(palette.danger);
        holder.btnDeleteTask.setOnClickListener(v -> listener.onTaskDeleted(task));
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView taskCard;
        final CheckBox cbTaskDone;
        final TextView tvTaskText;
        final TextView tvTaskAuthor;
        final ImageButton btnDeleteTask;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            taskCard = itemView.findViewById(R.id.taskCard);
            cbTaskDone = itemView.findViewById(R.id.cbTaskDone);
            tvTaskText = itemView.findViewById(R.id.tvTaskText);
            tvTaskAuthor = itemView.findViewById(R.id.tvTaskAuthor);
            btnDeleteTask = itemView.findViewById(R.id.btnDeleteTask);
        }
    }
}
