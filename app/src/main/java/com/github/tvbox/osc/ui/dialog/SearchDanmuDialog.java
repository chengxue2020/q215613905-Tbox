package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.DanmakuApi;
import com.github.tvbox.osc.bean.DanmuSearchResult;
import com.github.tvbox.osc.ui.adapter.SearchDanmuAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SearchDanmuDialog extends BaseDialog {
    private TvRecyclerView gridView;
    private SearchDanmuAdapter searchAdapter;
    private EditText searchInput;
    private ProgressBar loadingBar;
    private DanmuLoader danmuLoader;
    private String episode = "";

    public SearchDanmuDialog(@NonNull @NotNull Context context) {
        super(context);
        if (context instanceof Activity) {
            setOwnerActivity((Activity) context);
        }
        setContentView(R.layout.dialog_search_danmu);
        initView();
    }

    private void initView() {
        loadingBar = findViewById(R.id.loadingBar);
        gridView = findViewById(R.id.mGridView);
        searchInput = findViewById(R.id.input);
        TextView searchButton = findViewById(R.id.inputSubmit);
        searchAdapter = new SearchDanmuAdapter();
        gridView.setHasFixedSize(true);
        gridView.setLayoutManager(new V7LinearLayoutManager(getContext(), 1, false));
        gridView.setAdapter(searchAdapter);
        searchAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                loadDanmu(searchAdapter.getData().get(position));
            }
        });
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                search(searchInput.getText().toString().trim());
            }
        });
        searchAdapter.setNewData(new ArrayList<DanmuSearchResult>());
    }

    public void setEpisode(String episode) {
        this.episode = episode == null ? "" : episode;
    }

    public void setSearchWord(String word) {
        String searchWord = word == null ? "" : word.trim();
        searchInput.setText(searchWord);
        searchInput.setSelection(searchWord.length());
        searchInput.requestFocus();
        search(searchWord);
    }

    public void setDanmuLoader(DanmuLoader danmuLoader) {
        this.danmuLoader = danmuLoader;
    }

    private void search(String word) {
        searchAdapter.setNewData(new ArrayList<DanmuSearchResult>());
        if (TextUtils.isEmpty(word)) {
            Toast.makeText(getContext(), "输入内容不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading();
        DanmakuApi.searchList(word, episode, new DanmakuApi.SearchListCallback() {
            @Override
            public void onSuccess(List<DanmuSearchResult> results) {
                showResults(results);
            }

            @Override
            public void onError(String message) {
                showResults(new ArrayList<DanmuSearchResult>());
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadDanmu(DanmuSearchResult result) {
        showLoading();
        DanmakuApi.loadSearchResult(result, new DanmakuApi.SearchResultCallback() {
            @Override
            public void onSuccess(String danmu) {
                if (danmuLoader != null) danmuLoader.loadDanmu(danmu);
                dismiss();
            }

            @Override
            public void onError(String message) {
                loadingBar.setVisibility(View.GONE);
                gridView.setVisibility(View.VISIBLE);
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLoading() {
        loadingBar.setVisibility(View.VISIBLE);
        gridView.setVisibility(View.GONE);
    }

    private void showResults(List<DanmuSearchResult> results) {
        if (results == null) results = new ArrayList<>();
        loadingBar.setVisibility(View.GONE);
        gridView.setVisibility(View.VISIBLE);
        searchAdapter.setNewData(results);
        if (results.isEmpty()) {
            Toast.makeText(getContext(), "未查询到匹配弹幕", Toast.LENGTH_SHORT).show();
            return;
        }
        gridView.requestFocus();
    }

    @Override
    public void onBackPressed() {
        DanmakuApi.cancel();
        dismiss();
    }

    public interface DanmuLoader {
        void loadDanmu(String danmu);
    }
}
