package com.github.tvbox.osc.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.ui.adapter.LocalFileAdapter;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class LocalFileActivity extends BaseActivity {
    public static final String EXTRA_LIVE = "live";

    private TvRecyclerView fileList;
    private TextView tvTitle;
    private TextView tvPath;
    private TextView tvEmpty;
    private LocalFileAdapter adapter;
    private File currentDir;
    private File rootDir;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_local_file;
    }

    @Override
    protected void init() {
        tvTitle = findViewById(R.id.tvTitle);
        tvPath = findViewById(R.id.tvPath);
        tvEmpty = findViewById(R.id.tvEmpty);
        fileList = findViewById(R.id.fileList);
        adapter = new LocalFileAdapter();
        fileList.setAdapter(adapter);
        fileList.setLayoutManager(new V7LinearLayoutManager(this, 1, false));
        fileList.setSpacingWithMargins(0, AutoSizeUtils.mm2px(this, 8));
        adapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                File item = (File) adapter.getItem(position);
                if (item != null) open(item);
            }
        });
        initRoot();
        boolean live = getIntent() != null && getIntent().getBooleanExtra(EXTRA_LIVE, false);
        tvTitle.setText(live ? "选择直播配置文件" : "选择点播配置文件");
        loadDir(rootDir);
    }

    private void initRoot() {
        File storage = Environment.getExternalStorageDirectory();
        File tvBox = new File(storage, "TVBox");
        if (!tvBox.exists()) tvBox.mkdirs();
        rootDir = tvBox.exists() && tvBox.isDirectory() ? tvBox : storage;
    }

    private void loadDir(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            Toast.makeText(this, "无法打开目录", Toast.LENGTH_SHORT).show();
            return;
        }
        currentDir = dir;
        tvPath.setText(dir.getAbsolutePath());
        List<File> files = new ArrayList<File>();
        if (!isStorageRoot(dir) && dir.getParentFile() != null) files.add(dir.getParentFile());
        File[] children = dir.listFiles();
        if (children != null) files.addAll(Arrays.asList(children));
        adapter.setParentDir(isStorageRoot(dir) ? null : dir.getParentFile());
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                if (left.equals(currentDir.getParentFile())) return -1;
                if (right.equals(currentDir.getParentFile())) return 1;
                if (left.isDirectory() != right.isDirectory()) return left.isDirectory() ? -1 : 1;
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        adapter.setNewData(files);
        tvEmpty.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
        fileList.postDelayed(new Runnable() {
            @Override
            public void run() {
                fileList.requestFocus();
            }
        }, 100);
    }

    private void open(File item) {
        if (item.isDirectory()) {
            loadDir(item);
            return;
        }
        Intent data = new Intent();
        data.setData(Uri.fromFile(item));
        setResult(Activity.RESULT_OK, data);
        finish();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (currentDir != null && !isStorageRoot(currentDir) && currentDir.getParentFile() != null) {
                loadDir(currentDir.getParentFile());
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isStorageRoot(File dir) {
        try {
            return Environment.getExternalStorageDirectory().getCanonicalPath().equals(dir.getCanonicalPath());
        } catch (Throwable ignored) {
            return Environment.getExternalStorageDirectory().getAbsolutePath().equals(dir.getAbsolutePath());
        }
    }
}
