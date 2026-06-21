package com.github.tvbox.osc.ui.adapter;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ButtonAdapter<T> extends ListAdapter<T, ButtonAdapter.SelectViewHolder> {

    public interface SelectDialogInterface<T> {
        void click(T value, int pos);

        String getDisplay(T val);
    }

    static class SelectViewHolder extends RecyclerView.ViewHolder {
        SelectViewHolder(@NonNull @NotNull View itemView) {
            super(itemView);
        }
    }

    private final ArrayList<T> data = new ArrayList<>();
    private final SelectDialogInterface<T> dialogInterface;
    private int select;

    public ButtonAdapter(SelectDialogInterface<T> dialogInterface, DiffUtil.ItemCallback<T> diffCallback) {
        super(diffCallback);
        this.dialogInterface = dialogInterface;
    }

    public void setData(List<T> newData, int defaultSelect) {
        data.clear();
        data.addAll(newData);
        select = Math.max(0, Math.min(defaultSelect, data.size() - 1));
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @NonNull
    @NotNull
    @Override
    public SelectViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        return new SelectViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_button, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull @NotNull SelectViewHolder holder, @SuppressLint("RecyclerView") int position) {
        T value = data.get(position);
        TextView item = holder.itemView.findViewById(R.id.tvName);
        item.setText(dialogInterface.getDisplay(value));
        if (position == select) {
            item.setTextColor(0xff02f8e1);
            item.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        } else {
            item.setTextColor(Color.WHITE);
            item.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));
        }
        holder.itemView.setOnClickListener(v -> {
            if (position == select) return;
            int oldSelect = select;
            select = position;
            notifyItemChanged(oldSelect);
            notifyItemChanged(select);
            dialogInterface.click(value, position);
        });
    }
}
