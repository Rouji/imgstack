package at.r0.imgstack;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.design.internal.BottomNavigationItemView;
import android.support.design.internal.BottomNavigationMenuView;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;


public class MainActivity extends AppCompatActivity
{
    private static final int PICK_IMAGES = 1;
    private List<Uri> imageUris = new LinkedList<>();

    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public void onActivityResult(int reqCode, int resCode, Intent data)
    {
        if (reqCode != PICK_IMAGES)
            return;

        ClipData cd = data.getClipData();
        for (int i=0; i<cd.getItemCount(); ++i)
        {
            imageUris.add(cd.getItemAt(i).getUri());
        }

    }

    public void onSettingsClick(View view)
    {
        Toast.makeText(this, "NOT IMPLEMENTED", Toast.LENGTH_SHORT).show();
    }

    public void onClearClick(View view)
    {
        imageUris.clear();
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
