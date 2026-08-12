package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.SubtitleHelper;

import org.jetbrains.annotations.NotNull;

public class SubtitleDialog extends BaseDialog {

    public TextView selectInternal;
    private TextView selectLocal;
    private TextView selectRemote;
    private TextView subtitleSizeMinus;
    private TextView subtitleSizeText;
    private TextView subtitleSizePlus;
    private TextView subtitleTimeMinus;
    private TextView subtitleTimeText;
    private TextView subtitleTimePlus;
    private TextView subtitleStyleOne;
    private TextView subtitleStyleTwo;
    private TextView subtitlePositionText;
    private TextView subtitleTimeHint;
    private boolean exoInternalSubtitle;

    private SearchSubtitleListener mSearchSubtitleListener;
    private LocalFileChooserListener mLocalFileChooserListener;
    private SubtitleViewListener mSubtitleViewListener;

    public SubtitleDialog(@NonNull @NotNull Context context) {
        super(context);
        if (context instanceof Activity) {
            setOwnerActivity((Activity) context);
        }
        setContentView(R.layout.dialog_subtitle);
        initView(context);
    }

    private void initView(Context context) {
        selectInternal = findViewById(R.id.selectInternal);
        selectLocal = findViewById(R.id.selectLocal);
        selectRemote = findViewById(R.id.selectRemote);
        subtitleSizeMinus = findViewById(R.id.subtitleSizeMinus);
        subtitleSizeText = findViewById(R.id.subtitleSizeText);
        subtitleSizePlus = findViewById(R.id.subtitleSizePlus);
        subtitleTimeMinus = findViewById(R.id.subtitleTimeMinus);
        subtitleTimeText = findViewById(R.id.subtitleTimeText);
        subtitleTimePlus = findViewById(R.id.subtitleTimePlus);
        subtitleStyleOne = findViewById(R.id.subtitleStyleOne);
        subtitleStyleTwo = findViewById(R.id.subtitleStyleTwo);
        subtitlePositionText = findViewById(R.id.subtitlePositionText);
        subtitleTimeHint = findViewById(R.id.subtitleTimeHint);

        selectLocal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                dismiss();
                mLocalFileChooserListener.openLocalFileChooserDialog();
            }
        });

        selectRemote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                dismiss();
                mSearchSubtitleListener.openSearchSubtitleDialog();
            }
        });

        int size = SubtitleHelper.getTextSize(getOwnerActivity());
        subtitleSizeText.setText(Integer.toString(size));

        subtitleSizeMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (exoInternalSubtitle) {
                    int scale = Math.max(50, SubtitleHelper.getExoSubtitleScale() - 5);
                    subtitleSizeText.setText(scale + "%");
                    SubtitleHelper.setExoSubtitleScale(scale);
                    mSubtitleViewListener.setSubtitleScale(scale);
                    return;
                }
                String sizeStr = subtitleSizeText.getText().toString();
                int curSize = Integer.parseInt(sizeStr);
                curSize -= 2;
                if (curSize <= 12) {
                    curSize = 12;
                }
                subtitleSizeText.setText(Integer.toString(curSize));
                SubtitleHelper.setTextSize(curSize);
                mSubtitleViewListener.setTextSize(curSize);
            }
        });
        subtitleSizePlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (exoInternalSubtitle) {
                    int scale = Math.min(200, SubtitleHelper.getExoSubtitleScale() + 5);
                    subtitleSizeText.setText(scale + "%");
                    SubtitleHelper.setExoSubtitleScale(scale);
                    mSubtitleViewListener.setSubtitleScale(scale);
                    return;
                }
                String sizeStr = subtitleSizeText.getText().toString();
                int curSize = Integer.parseInt(sizeStr);
                curSize += 2;
                if (curSize >= 60) {
                    curSize = 60;
                }
                subtitleSizeText.setText(Integer.toString(curSize));
                SubtitleHelper.setTextSize(curSize);
                mSubtitleViewListener.setTextSize(curSize);
            }
        });

        int timeDelay = SubtitleHelper.getTimeDelay();
        String timeStr = "0";
        if (timeDelay != 0) {
            double dbTimeDelay = timeDelay/1000;
            timeStr = Double.toString(dbTimeDelay);
        }
        subtitleTimeText.setText(timeStr);

        subtitleTimeMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                String timeStr = subtitleTimeText.getText().toString();
                double time = Float.parseFloat(timeStr);
                double oneceDelay = -0.5;
                time += oneceDelay;
                if (time == 0.0) {
                    timeStr = "0";
                } else {
                    timeStr = Double.toString(time);
                }
                subtitleTimeText.setText(timeStr);
                int mseconds = (int)(oneceDelay*1000);
                SubtitleHelper.setTimeDelay((int)(time*1000));
                mSubtitleViewListener.setSubtitleDelay(mseconds);
            }
        });
        subtitleTimePlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                String timeStr = subtitleTimeText.getText().toString();
                double time = Float.parseFloat(timeStr);
                double oneceDelay = 0.5;
                time += oneceDelay;
                if (time == 0.0) {
                    timeStr = "0";
                } else {
                    timeStr = Double.toString(time);
                }
                subtitleTimeText.setText(timeStr);
                int mseconds = (int)(oneceDelay*1000);
                SubtitleHelper.setTimeDelay((int)(time*1000));
                mSubtitleViewListener.setSubtitleDelay(mseconds);
            }
        });
        selectInternal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                dismiss();
                mSubtitleViewListener.selectInternalSubtitle();
            }
        });

        subtitleStyleOne.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (exoInternalSubtitle) {
                    float position = Math.min(80.0f, SubtitleHelper.getExoSubtitlePosition() + 0.5f);
                    SubtitleHelper.setExoSubtitlePosition(position);
                    subtitlePositionText.setText(position == 0.0f ? "0" : position + "%");
                    mSubtitleViewListener.moveSubtitle(position);
                    return;
                }
                int style = 0;
                dismiss();
                mSubtitleViewListener.setTextStyle(style);
                Toast.makeText(getContext(), "设置样式成功", Toast.LENGTH_SHORT).show();
            }
        });

        subtitleStyleTwo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (exoInternalSubtitle) {
                    float position = Math.max(-80.0f, SubtitleHelper.getExoSubtitlePosition() - 0.5f);
                    SubtitleHelper.setExoSubtitlePosition(position);
                    subtitlePositionText.setText(position == 0.0f ? "0" : position + "%");
                    mSubtitleViewListener.moveSubtitle(position);
                    return;
                }
                int style = 1;
                dismiss();
                mSubtitleViewListener.setTextStyle(style);
                Toast.makeText(getContext(), "设置样式成功", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void setExoInternalSubtitle(boolean exoInternalSubtitle) {
        this.exoInternalSubtitle = exoInternalSubtitle;
        if (exoInternalSubtitle) {
            subtitleSizeText.setText(SubtitleHelper.getExoSubtitleScale() + "%");
            subtitleStyleOne.setText("字幕上移");
            subtitleStyleTwo.setText("字幕下移");
            subtitleStyleTwo.setTextColor(getContext().getResources().getColor(R.color.dialog_text_primary));
            float position = SubtitleHelper.getExoSubtitlePosition();
            subtitlePositionText.setText(position == 0.0f ? "0" : position + "%");
            subtitleTimeHint.setText("字幕延时对内置字幕有效");
        } else {
            subtitleSizeText.setText(Integer.toString(SubtitleHelper.getTextSize(getOwnerActivity())));
            subtitleStyleOne.setText("字幕样式一");
            subtitleStyleTwo.setText("字幕样式二");
            subtitleStyleTwo.setTextColor(getContext().getResources().getColor(R.color.color_FFB6C1));
            subtitlePositionText.setText("");
            subtitleTimeHint.setText("字幕延时仅对外挂字幕有效");
        }
    }

    public void setLocalFileChooserListener(LocalFileChooserListener localFileChooserListener) {
        mLocalFileChooserListener = localFileChooserListener;
    }

    public interface LocalFileChooserListener {
        void openLocalFileChooserDialog();
    }

    public void setSearchSubtitleListener(SearchSubtitleListener searchSubtitleListener) {
        mSearchSubtitleListener = searchSubtitleListener;
    }

    public interface SearchSubtitleListener {
        void openSearchSubtitleDialog();
    }

    public void setSubtitleViewListener(SubtitleViewListener subtitleViewListener) {
        mSubtitleViewListener = subtitleViewListener;
    }

    public interface SubtitleViewListener {
        void setTextSize(int size);
        void setSubtitleDelay(int milliseconds);
        void selectInternalSubtitle();
        void setTextStyle(int style);
        void setSubtitleScale(int scale);
        void moveSubtitle(float offset);
    }
}
