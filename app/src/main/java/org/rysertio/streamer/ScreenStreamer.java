package org.rysertio.streamer;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import net.ossrs.rtmp.ConnectCheckerRtmp;
import net.ossrs.rtmp.SrsFlvMuxer;


import java.io.IOException;
import java.nio.ByteBuffer;

public class ScreenStreamer implements ConnectCheckerRtmp {

    private static final String TAG = "ScreenStreamer";
    private static final String VIDEO_MIME_TYPE = "video/avc"; // H.264
    private static final int FRAME_RATE = 30;
    private static final int I_FRAME_INTERVAL = 5; // 5 seconds
    private static final int BIT_RATE = 2000000; // 2 Mbps

    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaCodec mVideoEncoder;
    private Surface mInputSurface;
    private HandlerThread mEncoderThread;
    private Handler mEncoderHandler;
    private SrsFlvMuxer srsFlvMuxer;
    private boolean streaming;
  
    private String mRtmpUrl;

    public void start(String rtmpUrl, MediaProjectionManager manager, int resultCode, Intent data,
                      int width, int height, int dpi) {

        this.mRtmpUrl = rtmpUrl;
        srsFlvMuxer = new SrsFlvMuxer(this);
        streaming = false;
        srsFlvMuxer.start(rtmpUrl);
        srsFlvMuxer.setVideoResolution(width, height);
  

        // Get MediaProjection
        mMediaProjection = manager.getMediaProjection(resultCode, data);

        // Configure Video Encoder
        try {
            MediaFormat videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, width, height);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

            mVideoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
            mVideoEncoder.setCallback(new VideoEncoderCallback());
            mVideoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            
            // This is the magic. We create a Surface that is the input to the encoder.
            mInputSurface = mVideoEncoder.createInputSurface();
            
            mVideoEncoder.start();

        } catch (IOException e) {
            Log.e(TAG, "Failed to create video encoder", e);
            release();
            return;
        }

        // Create Virtual Display
        mVirtualDisplay = mMediaProjection.createVirtualDisplay(
            "ScreenStreamer",
            width,
            height,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mInputSurface, // We render the screen directly to the encoder's input
            null,
            null
        );
        
        Log.i(TAG, "Streamer started. Target URL: " + rtmpUrl);
    }
    public void setAuthorization(String user, String password) {
      srsFlvMuxer.setAuthorization(user, password);
    }
    public void stop() {
    srsFlvMuxer.stop();
        Log.i(TAG, "Stopping streamer");
        release();
        
    }

    private void release() {
        if (mVideoEncoder != null) {
            mVideoEncoder.stop();
            mVideoEncoder.release();
            mVideoEncoder = null;
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }
    @Override
    public void onConnectionSuccessRtmp() {
        Log.i(TAG, "✅ RTMP Connection successful");
    }

    @Override
    public void onConnectionFailedRtmp() {
        Log.e(TAG, "❌ RTMP Connection failed: ");
        stop();
    }

    @Override
    public void onDisconnectRtmp() {
        Log.w(TAG, "RTMP disconnected");
    }

    @Override
    public void onAuthErrorRtmp() {
        Log.e(TAG, "RTMP auth error");
    }

    @Override
    public void onAuthSuccessRtmp() {
        Log.i(TAG, "RTMP auth success");
    }
    // This callback is where we get the encoded data
    private class VideoEncoderCallback extends MediaCodec.Callback {
        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
            // Not used when using an input surface
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
            ByteBuffer outputBuffer = codec.getOutputBuffer(index);
            if (outputBuffer != null && info.size > 0) {

                // Create a byte array to hold the encoded data
                byte[] encodedData = new byte[info.size];
                outputBuffer.get(encodedData);
                srsFlvMuxer.sendVideo(outputBuffer, info);

                // =================================================================
                // 
                //    TODO: THIS IS THE ENCODED H.264 DATA (NALU)
                // 
                // This is where you must implement your RTMP client.
                // You CANNOT do this without a library.
                // 
                // The steps required here are:
                // 1. (One-time): Create a TCP socket to mRtmpUrl.
                // 2. (One-time): Perform the RTMP handshake.
                // 3. (One-time): Send AMF 'connect', 'createStream', and 'publish' commands.
                // 4. (First-time): Send the H.264 "SPS" and "PPS" headers (you get these
                //    from the `MediaCodec.BufferInfo.FLAG_CODEC_CONFIG` flag).
                // 5. (Every time): Take this 'encodedData' byte array.
                // 6. Wrap it in an FLV video tag with the correct timestamp (from info.presentationTimeUs).
                // 7. Wrap that FLV tag in an RTMP message (chunked).
                // 8. Write the RTMP message to the socket.
                // 
                // =================================================================

                // For now, we just log that we got a frame
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                     Log.d(TAG, "Got H.264 Config frame (" + encodedData.length + " bytes)");
                } else {
                     Log.v(TAG, "Got H.264 Data frame (" + encodedData.length + " bytes)");
                }

            }
            
            // Release the buffer back to the encoder
            codec.releaseOutputBuffer(index, false);
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
            Log.e(TAG, "MediaCodec error", e);
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            Log.i(TAG, "Video encoder format changed: " + format);
        }
    }
    

}