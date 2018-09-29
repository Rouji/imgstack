package at.r0.imgstack;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.io.File;


public class MainActivity extends AppCompatActivity
{
    private static final int PICK_IMAGES = 1;

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

        ResultReceiver rr = new ResultReceiver(new Handler()){
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData)
            {
                if (resultCode == StackService.ResultCodes.PROGRESS)
                {
                    ((TextView)findViewById(R.id.textView)).setText("blah"+resultData.getInt("PROGRESS"));
                }
                else if (resultCode == StackService.ResultCodes.FINISHED)
                {
                    ((TextView) findViewById(R.id.textView)).setText(resultData.getString("FILE_PATH"));
                }
                else if (resultCode == StackService.ResultCodes.ERROR)
                {
                    ((TextView) findViewById(R.id.textView)).setText(resultData.getString("MESSAGE"));
                }
            }
        };

        Intent in = new Intent(this, StackService.class);
        in.setClipData(data.getClipData());
        in.putExtra("RECEIVER", rr);
        startService(in);
    }
}
