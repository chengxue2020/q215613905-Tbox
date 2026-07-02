package com.github.tvbox.osc.ui.adapter;

import android.graphics.Color;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.LiveSettingGroup;

import java.util.ArrayList;


/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
public class LiveSettingGroupAdapter extends BaseQuickAdapter<LiveSettingGroup, BaseViewHolder> {
    private int selectedGroupIndex = -1;
    private int focusedGroupIndex = -1;

    public LiveSettingGroupAdapter() {
        super(R.layout.item_live_setting_group, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder holder, LiveSettingGroup group) {
        TextView tvGroupName = holder.getView(R.id.tvSettingGroupName);
        tvGroupName.setText(group.getGroupName());
        tvGroupName.setSelected(true);
        int groupIndex = group.getGroupIndex();
        holder.itemView.setSelected(groupIndex == selectedGroupIndex);
        if (groupIndex == selectedGroupIndex && groupIndex != focusedGroupIndex) {
            tvGroupName.setTextColor(mContext.getResources().getColor(R.color.color_1890FF));
        } else {
            tvGroupName.setTextColor(Color.WHITE);
        }
    }

    public void setSelectedGroupIndex(int selectedGroupIndex) {
        int preSelectedGroupIndex = this.selectedGroupIndex;
        this.selectedGroupIndex = findPositionByGroupIndex(selectedGroupIndex) != -1 ? selectedGroupIndex : -1;
        int preSelectedPosition = findPositionByGroupIndex(preSelectedGroupIndex);
        if (preSelectedPosition != -1)
            notifyItemChanged(preSelectedPosition);
        int selectedPosition = findPositionByGroupIndex(this.selectedGroupIndex);
        if (selectedPosition != -1)
            notifyItemChanged(selectedPosition);
    }

    public int getSelectedGroupIndex() {
        return selectedGroupIndex;
    }

    public void setFocusedGroupIndex(int focusedGroupIndex) {
        this.focusedGroupIndex = findPositionByGroupIndex(focusedGroupIndex) != -1 ? focusedGroupIndex : -1;
        int focusedPosition = findPositionByGroupIndex(this.focusedGroupIndex);
        if (focusedPosition != -1)
            notifyItemChanged(focusedPosition);
        else {
            int selectedPosition = findPositionByGroupIndex(this.selectedGroupIndex);
            if (selectedPosition != -1)
                notifyItemChanged(selectedPosition);
        }
    }

    public int findPositionByGroupIndex(int groupIndex) {
        for (int i = 0; i < getData().size(); i++) {
            if (getData().get(i).getGroupIndex() == groupIndex) return i;
        }
        return -1;
    }
}
