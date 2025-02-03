package com.github.tvbox.osc.ui.adapter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.collection.LruCache;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.picasso.RoundTransformation;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.MD5;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class GridAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    private boolean mShowList = false;

    // 内存缓存
    private LruCache<String, Bitmap> bitmapCache;
    // 线程池
    private ExecutorService executorService;

    public GridAdapter(boolean l) {
        super(l ? R.layout.item_list : R.layout.item_grid, new ArrayList<>());
        this.mShowList = l;

        // 初始化内存缓存，缓存大小为运行内存的 1/8
        int cacheSize = (int) (Runtime.getRuntime().maxMemory() / 8);
        bitmapCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };

        // 初始化线程池
        executorService = Executors.newFixedThreadPool(5); // 可根据需要调整线程数量
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        if (this.mShowList) {
            helper.setText(R.id.tvNote, item.note);
            helper.setText(R.id.tvName, item.name);
            ImageView ivThumb = helper.getView(R.id.ivThumb);

            if (!TextUtils.isEmpty(item.pic)) {
                item.pic = item.pic.trim();
                if (isBase64Image(item.pic)) {
                    // 异步加载 Base64 图片
                    loadBase64ImageAsync(item.pic, ivThumb);
                } else {
                    // 加载网络图片
                    Picasso.get()
                            .load(DefaultConfig.checkReplaceProxy(item.pic))
                            .transform(new RoundTransformation(MD5.string2MD5(item.pic))
                                    .centerCorp(true)
                                    .override(AutoSizeUtils.mm2px(mContext, 240), AutoSizeUtils.mm2px(mContext, 320))
                                    .roundRadius(AutoSizeUtils.mm2px(mContext, 10), RoundTransformation.RoundType.ALL))
                            .placeholder(R.drawable.img_loading_placeholder)
                            .noFade()
                            .error(R.drawable.img_loading_placeholder)
                            .into(ivThumb);
                }
            } else {
                ivThumb.setImageResource(R.drawable.img_loading_placeholder);
            }
            return;
        }

        // 处理列表模式的其他逻辑
        TextView tvYear = helper.getView(R.id.tvYear);
        if (item.year <= 0) {
            tvYear.setVisibility(View.GONE);
        } else {
            tvYear.setText(String.valueOf(item.year));
            tvYear.setVisibility(View.VISIBLE);
        }
        TextView tvLang = helper.getView(R.id.tvLang);
        tvLang.setVisibility(View.GONE);
        TextView tvArea = helper.getView(R.id.tvArea);
        tvArea.setVisibility(View.GONE);

        if (TextUtils.isEmpty(item.note)) {
            helper.setVisible(R.id.tvNote, false);
        } else {
            helper.setVisible(R.id.tvNote, true);
            helper.setText(R.id.tvNote, item.note);
        }
        helper.setText(R.id.tvName, item.name);
        helper.setText(R.id.tvActor, item.actor);
        ImageView ivThumb = helper.getView(R.id.ivThumb);

        if (!TextUtils.isEmpty(item.pic)) {
            item.pic = item.pic.trim();
            if (isBase64Image(item.pic)) {
                loadBase64ImageAsync(item.pic, ivThumb);
            } else {
                Picasso.get()
                        .load(DefaultConfig.checkReplaceProxy(item.pic))
                        .transform(new RoundTransformation(MD5.string2MD5(item.pic))
                                .centerCorp(true)
                                .override(AutoSizeUtils.mm2px(mContext, 240), AutoSizeUtils.mm2px(mContext, 320))
                                .roundRadius(AutoSizeUtils.mm2px(mContext, 10), RoundTransformation.RoundType.ALL))
                        .placeholder(R.drawable.img_loading_placeholder)
                        .noFade()
                        .error(R.drawable.img_loading_placeholder)
                        .into(ivThumb);
            }
        } else {
            ivThumb.setImageResource(R.drawable.img_loading_placeholder);
        }
    }

    private boolean isBase64Image(String picUrl) {
        return picUrl.startsWith("data:image");
    }

    private void loadBase64ImageAsync(String base64Str, ImageView imageView) {
        Bitmap cachedBitmap = bitmapCache.get(base64Str);
        if (cachedBitmap != null) {
            // 如果缓存中有，直接加载
            imageView.setImageBitmap(cachedBitmap);
        } else {
            // 异步加载
            executorService.execute(() -> {
                Bitmap bitmap = decodeBase64ToBitmap(base64Str);
                if (bitmap != null) {
                    // 存入缓存
                    bitmapCache.put(base64Str, bitmap);
                    // 更新 UI
                    imageView.post(() -> imageView.setImageBitmap(bitmap));
                }
            });
        }
    }

    private Bitmap decodeBase64ToBitmap(String base64Str) {
        try {
            // 提取 Base64 数据部分
            String base64Data = base64Str.substring(base64Str.indexOf(",") + 1);
            byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);

            // 限制解码图片尺寸，防止内存过高
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true; // 只解析尺寸
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length, options);

            options.inSampleSize = calculateInSampleSize(options, 180, 260); // 设置缩放比例
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length, options);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}
