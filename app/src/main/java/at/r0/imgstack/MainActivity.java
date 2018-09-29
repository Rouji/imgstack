package at.r0.imgstack;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;


import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;


public class MainActivity extends AppCompatActivity
{
    private static final int PICK_IMAGES = 1;
    private List<Uri> imageUris = new LinkedList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent in = getIntent();
        onNewIntent(in);
    }

    @Override
    protected void onNewIntent(Intent intent)
    {
        if (intent != null && intent.getAction().equals(Intent.ACTION_SEND_MULTIPLE))
        {
            imageUris.addAll(intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM));
        }
        updateLabel();
    }

    @Override
    public void onActivityResult(int reqCode, int resCode, Intent data)
    {
        onNewIntent(null);
        if (reqCode != PICK_IMAGES)
            return;

        TextView tv = findViewById(R.id.debugText);
        ClipData cd = data.getClipData();
        if (cd != null)
        {
            for (int i = 0; i < cd.getItemCount(); ++i)
            {
                Uri u = cd.getItemAt(i).getUri();
                imageUris.add(u);
            }
        }
        Uri uri = data.getData();
        if (uri != null)
        {
            imageUris.add(uri);
        }

        updateLabel();
    }

    private void updateLabel()
    {
        TextView tv = findViewById(R.id.debugText);
        if (imageUris.isEmpty())
        {
            tv.setText("No files selected");
        }
        else
        {
            tv.setText(String.format("%d files selected:\n", imageUris.size()));
            for (Uri u : imageUris)
            {
                tv.setText(tv.getText() + u.getPath() + "\n");
            }
        }
    }

    public void onSettingsClick(View view)
    {
        Intent in = new Intent(this, SettingsActivity.class);
        startActivity(in);
    }

    public void onClearClick(View view)
    {
        imageUris.clear();
        updateLabel();
    }

    public void onAddClick(View view)
    {
        Intent in = new Intent();
        in.setType("image/*");
        in.setAction(Intent.ACTION_GET_CONTENT);
        in.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(in, "Select Images"), PICK_IMAGES);
    }

    public void onStartClick(View view)
    {
        Intent in = new Intent(getApplicationContext(), StackService.class);
        in.putParcelableArrayListExtra("IMAGE_URIS", new ArrayList<>(imageUris));
        in.setAction(StackService.ACTION_STACK);
        startService(in);
    }
}
