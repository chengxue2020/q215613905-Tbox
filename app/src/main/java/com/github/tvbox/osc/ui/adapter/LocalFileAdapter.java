package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;

public class LocalFileAdapter extends BaseQuickAdapter<File, BaseViewHolder> {
    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("0.#");
    private File parentDir;

    public LocalFileAdapter() {
        super(R.layout.item_local_file, new ArrayList<File>());
    }

    @Override
    protected void convert(BaseViewHolder helper, File item) {
        boolean isParent = parentDir != null && parentDir.equals(item);
        helper.setText(R.id.tvType, item.isDirectory() ? "目录" : "文件");
        helper.setText(R.id.tvName, isParent ? ".." : item.getName());
        helper.setText(R.id.tvInfo, item.isDirectory() ? "进入" : formatSize(item.length()));
    }

    public void setParentDir(File parentDir) {
        this.parentDir = parentDir;
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        double kb = size / 1024.0;
        if (kb < 1024) return SIZE_FORMAT.format(kb) + " KB";
        double mb = kb / 1024.0;
        if (mb < 1024) return SIZE_FORMAT.format(mb) + " MB";
        return SIZE_FORMAT.format(mb / 1024.0) + " GB";
    }
}
