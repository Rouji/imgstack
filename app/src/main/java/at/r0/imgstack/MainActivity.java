package at.r0.imgstack;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.renderscript.ScriptGroup;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Core;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity
{
    private static final int PICK_IMAGES = 1;

    static
    {
        if (!OpenCVLoader.initDebug())
            Log.d("opencv", "nope!");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent in = new Intent();
        in.setType("image/*");
        in.setAction(Intent.ACTION_GET_CONTENT);
        in.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(in, "Select Images"), PICK_IMAGES);
    }

    @Override
    public void onActivityResult(int reqCode, int resCode, Intent data)
    {
        if (reqCode != PICK_IMAGES)
            return;

        List<Uri> files = new LinkedList<>();
        ClipData cd = data.getClipData();
        for (int i=0; i < cd.getItemCount(); ++i)
            files.add(cd.getItemAt(i).getUri());

        doTheThing(files);
    }

    private void doTheThing(List<Uri> images)
    {
        List<InputStream> streams = images.stream().map((i) -> {
            try
            {
                return this.getContentResolver().openInputStream(i);
            }
            catch (FileNotFoundException e)
            {
                e.printStackTrace();
                return null;
            }
        }).collect(Collectors.toList());

        @SuppressLint("StaticFieldLeak")
        AsyncTask<List<InputStream>, Double, Mat> aTask = new AsyncTask<List<InputStream>, Double, Mat>()
        {
            @Override
            protected Mat doInBackground(List<InputStream>... args)
            {
                List<InputStream> inputStreams = args[0];
                Stacker s = null;
                int n = inputStreams.size();
                Scalar fract = new Scalar(1.0/(double)n,
                                          1.0/(double)n,
                                          1.0/(double)n,
                                          1.0/(double)n);
                Mat grey = new Mat();
                Mat rgb = new Mat();
                Mat out = new Mat();
                Mat tmp = new Mat();
                int prog = 0;
                for (InputStream in : inputStreams)
                {
                    publishProgress((double)prog++/n);
                    try{streamToMat(in, rgb);}catch(IOException ex){return null;}
                    Imgproc.cvtColor(rgb, grey, Imgproc.COLOR_BGRA2GRAY);
                    if (s == null)
                    {
                        s = new Stacker(grey);
                        rgb.convertTo(out, CvType.CV_32FC3, 1.0/(255.0*n));
                        //Core.multiply(rgb, fract, out);
                        continue;
                    }
                    s.stack(rgb, grey, rgb);
                    rgb.convertTo(tmp, CvType.CV_32FC3, 1.0/(255.0*n));
                    //Core.multiply(rgb, fract, rgb);
                    Core.add(out, tmp, out);
                }
                out.convertTo(out, CvType.CV_8UC3, 255.0);
                return out;
            }

            @Override
            protected void onProgressUpdate(Double... prog)
            {
                ((TextView)findViewById(R.id.textView)).setText(""+prog[0]);
            }

            @Override
            protected void onPostExecute(Mat output)
            {
                Bitmap bm = Bitmap.createBitmap(output.width(),output.height(),Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(output, bm);
                ((ImageView)findViewById(R.id.img)).setImageBitmap(bm);
                File sd = Environment.getExternalStorageDirectory();
                File dcim = new File(sd.getAbsolutePath() + "/DCIM/stack");
                if (!dcim.exists())
                    dcim.mkdirs();
                File imgFile = new File(dcim,new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Calendar.getInstance().getTime())+".jpg");
                try
                {
                    bm.compress(Bitmap.CompressFormat.JPEG, 90, new FileOutputStream(imgFile));
                }
                catch (FileNotFoundException e)
                {
                    e.printStackTrace();
                }
            }
        };

        aTask.execute(streams);
    }

    private void streamToMat(InputStream in, Mat out) throws IOException
    {
        BitmapFactory.Options bmOpt = new BitmapFactory.Options();
        bmOpt.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bm = BitmapFactory.decodeStream(in, null, bmOpt);
        Utils.bitmapToMat(bm, out);
    }
}
