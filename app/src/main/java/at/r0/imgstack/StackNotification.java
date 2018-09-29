package at.r0.imgstack;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.util.Random;

public class StackNotification
{
    private Service service;
    private Notification.Builder bldr;
    private NotificationChannel chan;
    private NotificationManager mgr;
    private static final int foregroundID = 42;
    private final static Random rnd = new Random();
    private final static String deleteAction = "at.r0.imgstack.DELETE";

    public StackNotification(Service service)
    {
        this.service = service;

        mgr = (NotificationManager) service.getSystemService(
                Context.NOTIFICATION_SERVICE);

        bldr = new Notification.Builder(service.getBaseContext())
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Stacking...")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setProgress(0,0, true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            chan = new NotificationChannel("at.r0.imgstack.stackservice",
                                           "Foreground Service",
                                           NotificationManager.IMPORTANCE_NONE);
            chan.setLightColor(Color.BLUE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            mgr.createNotificationChannel(chan);
            bldr.setChannelId(chan.getId());
        }
    }

    public void start()
    {
        service.startForeground(foregroundID, bldr.build());
    }

    public void progress(int p, int n)
    {
        bldr.setProgress(n, p, false)
            .setContentTitle(String.format("Stacking... (%d/%d)", p, n));
        service.startForeground(foregroundID, bldr.build());
    }

    public void success(File imgFile)
    {
        int id = rnd.nextInt();
        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        openIntent.setData(Uri.parse("file://"+imgFile.getAbsolutePath()));
        openIntent.setType("image/*");
        PendingIntent openPending = PendingIntent.getActivity(service, (int)System.currentTimeMillis(), openIntent, 0);

        Intent deleteIntent = new Intent(service, StackService.class);
        deleteIntent.setAction(StackService.ACTION_DELETE);
        deleteIntent.putExtra("FILE_PATH", imgFile.getAbsolutePath());
        deleteIntent.putExtra("NOTIFICATION_ID", id);
        PendingIntent deletePending = PendingIntent.getService(service, 0, deleteIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        Notification.BigPictureStyle style = new Notification.BigPictureStyle();
        style.bigPicture(BitmapFactory.decodeFile(imgFile.getAbsolutePath()));
        bldr.setStyle(style)
            .setProgress(0,0, false)
            .setContentTitle("Finished Image Stack")
            .setContentText("Saved file: "+imgFile.getName())
            .setOngoing(false)
            .addAction(0, "Open", openPending)
            .addAction(0, "Delete", deletePending);
        mgr.notify(id, bldr.build());
    }
}

