package com.takidz.run.ui.adapters;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.takidz.run.R;
import com.takidz.run.models.TaskItem;
import com.takidz.run.utils.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

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
        notifyDataSetChanged();
    }

    public void submitList(List<TaskItem> newTasks) {
        this.tasks = newTasks != null ? new ArrayList<>(newTasks) : new ArrayList<>();
        notifyDataSetChanged();
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
