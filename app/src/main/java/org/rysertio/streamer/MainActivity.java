package org.rysertio.streamer;

import android.app.Activity;
import android.os.Bundle;

import org.rysertio.streamer.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.EditText;
import android.widget.ToggleButton;

public class MainActivity extends Activity {

    private static final int REQUEST_MEDIA_PROJECTION = 1;

    private ToggleButton mToggleButton;
    private EditText mRtmpUrlEditText;
    private ScreenStreamer mScreenStreamer;
    private MediaProjectionManager mMediaProjectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mToggleButton = findViewById(R.id.toggle_stream);
        mRtmpUrlEditText = findViewById(R.id.edit_rtmp_url);

        mMediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        mToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mToggleButton.isChecked()) {
                    // Start the permission request
                    startActivityForResult(
                        mMediaProjectionManager.createScreenCaptureIntent(),
                        REQUEST_MEDIA_PROJECTION
                    );
                } else {
                    // Stop the stream
                    if (mScreenStreamer != null) {
                        mScreenStreamer.stop();
                    }
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                // User denied permission
                mToggleButton.setChecked(false);
                return;
            }
            
            // Permission granted. Get screen metrics.
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;
            int screenDpi = metrics.densityDpi;

            String rtmpUrl = mRtmpUrlEditText.getText().toString();

            // Start the streamer
            mScreenStreamer = new ScreenStreamer();
            mScreenStreamer.start(
                rtmpUrl,
                mMediaProjectionManager,
                resultCode,
                data,
                screenWidth,
                screenHeight,
                screenDpi
            );
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mScreenStreamer != null) {
            mScreenStreamer.stop();
        }
    }
}
