package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.DanmuSearchResult;

import java.util.ArrayList;

public class SearchDanmuAdapter extends BaseQuickAdapter<DanmuSearchResult, BaseViewHolder> {

    public SearchDanmuAdapter() {
        super(R.layout.item_search_danmu_result, new ArrayList<DanmuSearchResult>());
    }

    @Override
    protected void convert(BaseViewHolder helper, DanmuSearchResult item) {
        helper.setText(R.id.danmuName, item.getName());
    }
}
