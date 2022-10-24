package com.github.tvbox.osc.ui.dialog;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.RemoteConfig;
import com.github.tvbox.osc.util.RemoteConfigName;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.callback.FileCallback;
import com.lzy.okgo.model.Progress;
import com.lzy.okgo.model.Response;
import java.io.File;
import java.io.FileOutputStream;
import com.github.tvbox.osc.R;
import com.lzy.okgo.request.base.Request;

import org.jetbrains.annotations.NotNull;

/**
 * 检查更新工具类
 */
public class UpdateDialog extends BaseDialog  {
    private static NotificationCompat.Builder builder;
    private static NotificationManager manager;
    private static final int UPDATE_ID = 0;
    private static Context context;
    private static Toast toast;

    // 更新需要静态参数
    private static String CurrVersion="1.0.0";
    private static int CurrVersionNum=1000; // 1*1000+2*100+3*10
    private static String NewVersion="1.0.0";
    private static int NewVersionNum=1000;
    private static Boolean ForceUpdate;
    private static String UpdateDesc;
    private static String UpdateUrl;
    public static Boolean IsNewUpdate; // 是否有新更新

    // UI参数
    private TextView log_head;
    private TextView msg_tv;
    private Button update;
    private Button notNow;
    private ProgressBar progressBar;
    private TextView progressBarText;


    public UpdateDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_update);
        LOG.i("最新版本--> " + NewVersion + "  更新日志--> " + UpdateDesc + " 下载地址--> " + UpdateUrl);

        this.context = context;
        if (TextUtils.isEmpty(UpdateDesc) || TextUtils.isEmpty(UpdateUrl) || TextUtils.isEmpty(NewVersion)) {
            return;
        }
        log_head = findViewById(R.id.log_head);
        msg_tv = findViewById(R.id.msg_tv);
        log_head.setText("v" + NewVersion + "更新日志：");
        msg_tv.setText(UpdateDesc);
        update = findViewById(R.id.yes_btn);
        notNow = findViewById(R.id.no_btn);
        progressBar = findViewById(R.id.progressBar);
        progressBarText = findViewById(R.id.progressBarText);
        progressBar.setVisibility(View.GONE);
        progressBarText.setVisibility(View.GONE);
        update.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                download(UpdateUrl);
            }
        });
        notNow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (!ForceUpdate){
            super.onBackPressed();
            dismiss();
        }else{
            showToast("强制更新，无法退出！");
        }
    }


    @Override
    public void show() {
        super.show();
        showUpdateDialog(ForceUpdate);
    }

    /**
     * 检查更新
     *
     * @param context
     * @param isOnlyCheck 是否只是检查是否有更新，而并不实际更新
     * @return
     */
    public static void checkUpdate(Context context, boolean isOnlyCheck) {
        IsNewUpdate = false;
        JsonObject updateJosn = null;
        try {
            updateJosn = RemoteConfig.GetValue(RemoteConfigName.UpdateData).getAsJsonObject();
        }catch (Exception e){
            e.printStackTrace();
            return;
        }
        if (updateJosn==null)
            return;
        LOG.i("更新信息："+ updateJosn.toString());
        try {
            NewVersion = updateJosn.get(RemoteConfigName.UpdateData_NewVersion).getAsString();
            ForceUpdate = updateJosn.get(RemoteConfigName.UpdateData_ForceUpdate).getAsBoolean();
            UpdateDesc = updateJosn.get(RemoteConfigName.UpdateData_UpdateDesc).getAsString();
            UpdateUrl = updateJosn.get(RemoteConfigName.UpdateData_UpdateDownloadUrl).getAsString();

            try {
                PackageManager packageManager = context.getPackageManager();
                PackageInfo packInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
                CurrVersion = packInfo.versionName;
            }catch (Exception e){
                e.printStackTrace();
            }
            NewVersionNum = 0;
            CurrVersionNum = 0;
            String[] NewVersions = NewVersion.split("\\.");
            String[] CurrVersions = CurrVersion.split("\\.");
            for (int i = 0; i < NewVersions.length; i++)
            {

                int posNum = Integer.parseInt("1000".substring(0,4-i));
                NewVersionNum += Integer.parseInt(NewVersions[i])*posNum;
                CurrVersionNum += Integer.parseInt(CurrVersions[i])*posNum;
            }

            LOG.i("更新信息: CurrVersionNum："+ CurrVersionNum +"NewVersionNum：" +NewVersionNum);
            if (NewVersionNum > CurrVersionNum) {
                IsNewUpdate = true;
                if (!isOnlyCheck) {
                    UpdateDialog dialog = new UpdateDialog(context);
                    dialog.show();
                }
            }else{
                if (!isOnlyCheck) {
                    LOG.i("已是最新版本");
                    Toast.makeText(context, "已是最新版本", Toast.LENGTH_LONG).show();
                }
            }
        }catch (Throwable th){
            th.printStackTrace();
        };
    }


    /**
     * 显示更新对话框
     *
     * @param isForceUpdate 是否强制更新
     */
    public void showUpdateDialog(final boolean isForceUpdate) {
        LOG.i(ForceUpdate ? "当前为强制更新！" : "当前为非强制更新！");
        this.setCancelable(!isForceUpdate);
        if (isForceUpdate) {//如果是强制更新，则不显示“以后再说”按钮
            notNow.setVisibility(View.GONE);
        }
    }

    /**
     * 下载apk
     */
    private void download(String download_path) {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            String target = String.format("%s/%s.apk", context.getExternalFilesDir("downloads").getAbsolutePath(), MD5.encode(NewVersion));
            File file = new File(target);
            if (file.exists()) {
                InstallApk(file);
                return;
            }

            LOG.i("apk url -->" + download_path);
            LOG.i("apk target -->" + target);//不能用这个target替换下面那个分开的
            ForceUpdate = true;
            progressBar.setVisibility(View.VISIBLE);
            progressBarText.setVisibility(View.VISIBLE);
            this.setCancelable(false);
            OkGo.<File>get(download_path).tag("down_apk").execute(new FileCallback(context.getExternalFilesDir("downloads").getAbsolutePath(), MD5.encode(NewVersion)+".tapk") {
                @Override
                public void onStart(Request<File, ? extends Request> request) {
                    showToast("更新开始下载...");
                    //创建通知栏下载提示
                    builder = new NotificationCompat.Builder(context, "TVBox");
                    builder.setSmallIcon(R.drawable.app_icon)
                            .setOngoing(true)
                            .setContentTitle(String.format("TVBox(%s) 更新中", NewVersion));
                    manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                }

                @Override
                public void downloadProgress(Progress progress) {
                    LOG.i("更新下载进度", progress.toString());
                    int cur = (int)(progress.fraction*100);
                    progressBar.setProgress(cur);
                    progressBarText.setText(cur + "%");
                    builder.setProgress(100, cur, false)//更新进度
                            .setContentText(cur + "%");
                    manager.notify(UPDATE_ID, builder.build());
                }

                @Override
                public void onSuccess(Response<File> response) {
                    LOG.i("更新下载完成...");
                    progressBar.setVisibility(View.GONE);
                    progressBarText.setVisibility(View.GONE);
                    manager.cancel(UPDATE_ID);//取消通知栏下载提示
                    String apkPath = response.body().getAbsoluteFile().getAbsolutePath().replace(".tapk", ".apk");
                    File file = new File(apkPath);
                    try {
                        FileUtils.copyFile(response.body().getAbsoluteFile(), file);
                        response.body().getAbsoluteFile().delete();
                        InstallApk(file);
                    }catch (Exception e){
                        LOG.e("UpdateDialog", "tapk 到 apk复制失败，导致安装失败");
                        e.printStackTrace();
                    }
                }
                @Override
                public void onError(Response<File> response) {
                    LOG.i("更新下载失败...");
                    super.onError(response);
                    showToast("当前网络不可用，请检查网络设置");
                }
            });
        } else {
            showToast("SD卡没有插好");
        }
    }
    //下载成功后自动安装apk并打开
    private void InstallApk(File file){
        Intent intent = new Intent(Intent.ACTION_VIEW);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            LOG.e(context.getApplicationInfo().processName, file.getAbsolutePath());
            Uri uri = FileProvider.getUriForFile(context, context.getApplicationInfo().processName+".fileprovider", file);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
        }else {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Uri uri = Uri.fromFile(file);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
        }
        LOG.i("打开 下载文件", Uri.fromFile(file).getPath());
        try {
            context.startActivity(intent);
        }catch (Exception e){
            LOG.e("更新下载安装出现异常",e.toString());
        }
    }
    public void showToast(String msg){
        if (toast==null)
            toast = Toast.makeText(context, msg, Toast.LENGTH_LONG);
        toast.setText(msg);
        toast.show();
    }
}
