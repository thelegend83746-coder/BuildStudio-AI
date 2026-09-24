package com.tyron.compiler;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import com.apk.builder.model.Project;
import java.io.File;

public class BuildService extends Service {

    public static final String ACTION_BUILD = "com.build.studio.action.BUILD";
    public static final String EXTRA_PROJECT_PATH = "project_path";
    public static final String EXTRA_PROJECT_NAME = "project_name";
    public static final String EXTRA_PACKAGE_NAME = "package_name";
    public static final String CHANNEL_ID = "build_service_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_BUILD.equals(intent.getAction())) {
            String path = intent.getStringExtra(EXTRA_PROJECT_PATH);
            String name = intent.getStringExtra(EXTRA_PROJECT_NAME);
            String pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME);

            Project project = new Project(name, pkg, path);
            startForeground(1001, buildNotification("Preparing build pipeline...", 0, 5));

            CompilerAsyncTask task = new CompilerAsyncTask(this, project, new CompilerAsyncTask.CompilerCallback() {
                @Override
                public void onProgress(String message, int step, int total) {
                    Notification n = buildNotification(message, step, total);
                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    nm.notify(1001, n);
                }

                @Override
                public void onComplete(CompilerResult result) {
                    stopForeground(true);
                    if (result.isSuccess() && result.getOutputApk() != null) {
                        showInstallNotification(result.getOutputApk());
                    }
                    stopSelf();
                }
            });
            task.execute();
        }
        return START_NOT_STICKY;
    }

    private Notification buildNotification(String text, int progress, int max) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("BUILD STUDIO - Compiling APK")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setProgress(max, progress, false)
                .setOngoing(true)
                .build();
    }

    private void showInstallNotification(File apkFile) {
        Uri apkUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", apkFile);
        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pi = PendingIntent.getActivity(this, 0, installIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Build Succeeded!")
                .setContentText("Tap to install " + apkFile.getName())
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(1002, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Build Studio Compiler Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
