package at.r0.imgstack;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.annotation.Nullable;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Core;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import org.opencv.android.OpenCVLoader;

public class StackService extends IntentService
{
    static
    {
        if (!OpenCVLoader.initDebug())
            Log.d("StackService", "nope!");
    }

    public class ResultCodes
    {
        public static final int PROGRESS = 1;
        public static final int FINISHED = 2;
        public static final int ERROR = 3;
    }

    private static final int notificationID = 1;

    public StackService()
    {
        super("StackService");
    }

    private List<InputStream> clipDataToInputStreams(ClipData cd) throws FileNotFoundException
    {
        ContentResolver cr = this.getContentResolver();
        LinkedList<InputStream> streams = new LinkedList<>();
        for (int i=0; i<cd.getItemCount(); ++i)
        {
            InputStream is = cr.openInputStream(cd.getItemAt(i).getUri());
            streams.add(is);
        }
        return streams;
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent)
    {
        final ResultReceiver receiver = intent.getParcelableExtra("RECEIVER");

        Notification.Builder builder = null;
            builder = new Notification.Builder(getBaseContext())
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setTicker("ticker")
                    .setContentTitle("title")
                    .setContentText("text")
                    .setOngoing(true)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setProgress(0,0, false);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
        {
            NotificationChannel chan = new NotificationChannel("at.r0.imgstack", "bgservice",
                                                               NotificationManager.IMPORTANCE_NONE);
            chan.setLightColor(Color.BLUE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager mgt = (NotificationManager) getSystemService(
                    Context.NOTIFICATION_SERVICE);
            mgt.createNotificationChannel(chan);
            builder.setChannelId(chan.getId());
        }
        startForeground(notificationID, builder.build());

        try
        {
            List<InputStream> streams;
            try
            {
                streams = clipDataToInputStreams(intent.getClipData());
            }
            catch (FileNotFoundException e)
            {
                e.printStackTrace();
                return;
            }

            Stacker s = null;
            int n = streams.size();
            Mat grey = new Mat();
            Mat rgb = new Mat();
            Mat out = new Mat();
            Mat tmp = new Mat();
            int prog = 0;
            for (InputStream in : streams)
            {
                ++prog;
                try {streamToMat(in, rgb);}catch (IOException ex) {return;}
                Imgproc.cvtColor(rgb, grey, Imgproc.COLOR_BGRA2GRAY);
                if (s == null)
                {
                    s = new Stacker(grey);
                    rgb.convertTo(out, CvType.CV_32FC3, 1.0 / (255.0 * n));
                    progress(receiver, prog, n);
                    continue;
                }
                s.stack(rgb, grey, rgb);
                rgb.convertTo(tmp, CvType.CV_32FC3, 1.0 / (255.0 * n));
                Core.add(out, tmp, out);
                progress(receiver, prog, n);
            }
            out.convertTo(out, CvType.CV_8UC3, 255.0);

            File outputFile = saveMat(out);
            result(receiver, outputFile);
        }
        catch (Exception ex)
        {
            error(receiver, ex.getMessage());
        }
        finally
        {
            stopForeground(true);
        }
    }

    private void streamToMat(InputStream in, Mat out) throws IOException
    {
        BitmapFactory.Options bmOpt = new BitmapFactory.Options();
        bmOpt.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bm = BitmapFactory.decodeStream(in, null, bmOpt);
        Utils.bitmapToMat(bm, out);
    }

    private void progress(ResultReceiver recv, int prog, int max)
    {
        if (recv == null)
            return;
        Bundle b = new Bundle();
        b.putInt("PROGRESS", prog);
        b.putInt("NUM_IMG", max);
        recv.send(ResultCodes.PROGRESS, b);
    }

    private void result(ResultReceiver recv, File result)
    {
        if (recv == null)
            return;
        Bundle b = new Bundle();
        b.putString("FILE_PATH", result.getAbsolutePath());
        recv.send(ResultCodes.FINISHED, b);
    }

    private void error(ResultReceiver recv, String message)
    {
        if (recv == null)
            return;
        Bundle b = new Bundle();
        b.putString("MESSAGE", message);
        recv.send(ResultCodes.ERROR, b);
    }

    private File saveMat(Mat mat) throws FileNotFoundException
    {
        Bitmap bm = Bitmap.createBitmap(mat.width(),mat.height(),Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bm);

        File sd = Environment.getExternalStorageDirectory();
        File dcim = new File(sd.getAbsolutePath() + "/DCIM/ImgStack");
        if (!dcim.exists())
            dcim.mkdirs();
        File imgFile = new File(dcim,new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Calendar.getInstance().getTime())+".jpg");
        bm.compress(Bitmap.CompressFormat.JPEG, 90, new FileOutputStream(imgFile));
        return imgFile;
    }

    class StackTask extends AsyncTask<List<InputStream>, Double, Mat>
    {
        @Override
        protected Mat doInBackground(List<InputStream>... args)
        {
            List<InputStream> inputStreams = args[0];
            Stacker s = null;
            int n = inputStreams.size();
            Mat grey = new Mat();
            Mat rgb = new Mat();
            Mat out = new Mat();
            Mat tmp = new Mat();
            int prog = 0;
            for (InputStream in : inputStreams)
            {
                try{streamToMat(in, rgb);}catch(IOException ex){return null;}
                Imgproc.cvtColor(rgb, grey, Imgproc.COLOR_BGRA2GRAY);
                if (s == null)
                {
                    s = new Stacker(grey);
                    rgb.convertTo(out, CvType.CV_32FC3, 1.0/(255.0*n));
                    continue;
                }
                s.stack(rgb, grey, rgb);
                rgb.convertTo(tmp, CvType.CV_32FC3, 1.0/(255.0*n));
                Core.add(out, tmp, out);
                publishProgress((double)++prog/n);
            }
            out.convertTo(out, CvType.CV_8UC3, 255.0);
            return out;
        }

        @Override
        protected void onPostExecute(Mat output)
        {
        }

    }

}
