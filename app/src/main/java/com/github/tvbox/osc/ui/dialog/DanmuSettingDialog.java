package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.adapter.ButtonAdapter;
import com.github.tvbox.osc.util.DanmuHelper;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import master.flame.danmaku.ui.widget.DanmakuView;

public class DanmuSettingDialog extends BaseDialog {
    private final DanmakuView danmakuView;

    public DanmuSettingDialog(@NonNull @NotNull Context context, DanmakuView danmakuView) {
        super(context);
        setContentView(R.layout.dialog_danmu_setting);
        this.danmakuView = danmakuView;
        initOnOff();
        initColor();
        initSpeed();
        initSize();
        initLine();
        initAlpha();
    }

    private void initOnOff() {
        List<Boolean> data = Arrays.asList(true, false);
        setButtonAdapter(R.id.trv_onoff, data, DanmuHelper.isOpen() ? 0 : 1, new ButtonAdapter.SelectDialogInterface<Boolean>() {
            @Override
            public void click(Boolean value, int pos) {
                DanmuHelper.setOpen(value);
                if (danmakuView != null) {
                    if (value) {
                        danmakuView.show();
                    } else {
                        danmakuView.hide();
                    }
                }
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, value));
            }

            @Override
            public String getDisplay(Boolean val) {
                return val ? "开" : "关";
            }
        });
    }

    private void initColor() {
        List<Boolean> data = Arrays.asList(false, true);
        setButtonAdapter(R.id.trv_color, data, DanmuHelper.useRandomColor() ? 1 : 0, new ButtonAdapter.SelectDialogInterface<Boolean>() {
            @Override
            public void click(Boolean value, int pos) {
                DanmuHelper.setRandomColor(value);
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, true));
            }

            @Override
            public String getDisplay(Boolean val) {
                return val ? "随机" : "默认";
            }
        });
    }

    private void initSpeed() {
        List<Float> speeds = Arrays.asList(2.4f, 1.8f, 1.5f, 1.0f);
        int defaultPos = Math.max(0, speeds.indexOf(DanmuHelper.getSpeed()));
        setButtonAdapter(R.id.speed, speeds, defaultPos, new ButtonAdapter.SelectDialogInterface<Float>() {
            @Override
            public void click(Float value, int pos) {
                DanmuHelper.setSpeed(value);
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, false));
            }

            @Override
            public String getDisplay(Float val) {
                int idx = speeds.indexOf(val);
                return idx < 0 ? "适中" : new String[]{"超慢", "慢", "适中", "快"}[idx];
            }
        });
    }

    private void initSize() {
        TextView sizeText = findViewById(R.id.size);
        TextView sizeSub = findViewById(R.id.sizeSub);
        TextView sizeAdd = findViewById(R.id.sizeAdd);
        AtomicInteger size = new AtomicInteger(Math.round(DanmuHelper.getSizeScale() * 10));
        renderSize(sizeText, size.get());
        sizeSub.setOnClickListener(v -> {
            if (size.get() <= 6) return;
            size.decrementAndGet();
            DanmuHelper.setSizeScale(size.get() / 10f);
            renderSize(sizeText, size.get());
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, false));
        });
        sizeAdd.setOnClickListener(v -> {
            if (size.get() >= 20) return;
            size.incrementAndGet();
            DanmuHelper.setSizeScale(size.get() / 10f);
            renderSize(sizeText, size.get());
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, false));
        });
    }

    private void initLine() {
        TextView lineText = findViewById(R.id.line);
        TextView lineSub = findViewById(R.id.lineSub);
        TextView lineAdd = findViewById(R.id.lineAdd);
        AtomicInteger line = new AtomicInteger(DanmuHelper.getMaxLine());
        renderLine(lineText, line.get());
        lineSub.setOnClickListener(v -> {
            if (line.get() <= 1) return;
            line.decrementAndGet();
            DanmuHelper.setMaxLine(line.get());
            renderLine(lineText, line.get());
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, false));
        });
        lineAdd.setOnClickListener(v -> {
            if (line.get() >= 15) return;
            line.incrementAndGet();
            DanmuHelper.setMaxLine(line.get());
            renderLine(lineText, line.get());
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, false));
        });
    }

    private void initAlpha() {
        TextView alphaText = findViewById(R.id.alpha);
        TextView alphaSub = findViewById(R.id.alphaSub);
        TextView alphaAdd = findViewById(R.id.alphaAdd);
        AtomicInteger alpha = new AtomicInteger(Math.round(DanmuHelper.getAlpha() * 100));
        renderAlpha(alphaText, alpha.get());
        alphaSub.setOnClickListener(v -> {
            if (alpha.get() <= 10) return;
            alpha.addAndGet(-10);
            DanmuHelper.setAlpha(alpha.get() / 100f);
            renderAlpha(alphaText, alpha.get());
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, false));
        });
        alphaAdd.setOnClickListener(v -> {
            if (alpha.get() >= 100) return;
            alpha.addAndGet(10);
            DanmuHelper.setAlpha(alpha.get() / 100f);
            renderAlpha(alphaText, alpha.get());
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, false));
        });
    }

    private void renderSize(TextView view, int value) {
        view.setText(value + "档");
    }

    private void renderLine(TextView view, int value) {
        view.setText(value + "行");
    }

    private void renderAlpha(TextView view, int value) {
        view.setText(value + "%");
    }

    private <T> void setButtonAdapter(int rid, List<T> data, int select, ButtonAdapter.SelectDialogInterface<T> dialogInterface) {
        ButtonAdapter<T> adapter = new ButtonAdapter<>(dialogInterface, new DiffUtil.ItemCallback<T>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull T oldItem, @NonNull @NotNull T newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull T oldItem, @NonNull @NotNull T newItem) {
                return oldItem.equals(newItem);
            }
        });
        adapter.setData(data, select);
        TvRecyclerView tvRecyclerView = findViewById(rid);
        tvRecyclerView.setLayoutManager(new V7LinearLayoutManager(getContext(), 0, false));
        tvRecyclerView.setAdapter(adapter);
        tvRecyclerView.setSelectedPosition(select);
        tvRecyclerView.post(() -> {
            tvRecyclerView.smoothScrollToPosition(select);
            tvRecyclerView.setSelectionWithSmooth(select);
        });
    }
}
