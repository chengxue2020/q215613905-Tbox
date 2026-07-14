package com.github.tvbox.osc.receiver;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.ui.activity.DetailActivity;

public class PushReceiver extends BroadcastReceiver {
    public static String action = "android.content.movie.push.Action";

    public static void send(Context context, String url) {
        if (context == null || TextUtils.isEmpty(url)) return;
        Intent intent = new Intent();
        intent.setAction(action);
        intent.setPackage(context.getPackageName());
        intent.setComponent(new ComponentName(context, PushReceiver.class));
        intent.putExtra("url", url);
        context.sendBroadcast(intent);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!action.equals(intent.getAction()) || intent.getExtras() == null) return;
        String url = intent.getExtras().getString("url");
        if (TextUtils.isEmpty(url) || ApiConfig.get().getSource("push_agent") == null) return;
        Intent newIntent = new Intent(context, DetailActivity.class);
        newIntent.putExtra("id", url);
        newIntent.putExtra("sourceKey", "push_agent");
        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(newIntent);
    }
}
