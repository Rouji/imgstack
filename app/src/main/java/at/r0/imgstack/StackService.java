package at.r0.imgstack;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.annotation.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import java.util.stream.Collectors;

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

    public final static String ACTION_STACK = "at.r0.imgstack.STACK";
    public final static String ACTION_DELETE = "at.r0.imgstack.DELETE";

    public StackService()
    {
        super("StackService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent)
    {
        String action = intent.getAction();
        if (action.equals(ACTION_DELETE))
        {
            new File(intent.getStringExtra("FILE_PATH")).delete();
            ((NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(intent.getIntExtra("NOTIFICATION_ID", -1));
            return;
        }
        else if (!action.equals((ACTION_STACK)))
        {
            return;
        }

        final ResultReceiver receiver = intent.getParcelableExtra("RECEIVER");
        final StackNotification notification = new StackNotification(this);

        notification.start();

        try
        {
            List<InputStream> streams = new LinkedList<>();
            ArrayList<Uri> uris = intent.getExtras().getParcelableArrayList("IMAGE_URIS");
            ContentResolver cr = this.getContentResolver();
            for (Uri u : uris)
                streams.add(cr.openInputStream(u));

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
                }
                else
                {
                    s.stack(rgb, grey, rgb);
                    rgb.convertTo(tmp, CvType.CV_32FC3, 1.0 / (255.0 * n));
                    Core.add(out, tmp, out);
                }
                progress(receiver, prog, n);
                notification.progress(prog, n);
            }
            out.convertTo(out, CvType.CV_8UC3, 255.0);

            File outputFile = saveMat(out);
            result(receiver, outputFile);
            notification.success(outputFile);
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

}
