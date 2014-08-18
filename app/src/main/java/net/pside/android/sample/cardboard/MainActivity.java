package net.pside.android.sample.cardboard;

import android.os.Bundle;
import android.util.Log;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.EyeTransform;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;

import javax.microedition.khronos.egl.EGLConfig;

public class MainActivity extends CardboardActivity
        implements CardboardView.StereoRenderer {
    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize View
        setContentView(R.layout.activity_main);
        CardboardView cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
        cardboardView.setRenderer(this);
        setCardboardView(cardboardView);
    }

    // region CardboardView.StereoRenderer
    @Override
    public void onNewFrame(HeadTransform headTransform) {
        Log.d(TAG, "onNewFrame()");
    }

    @Override
    public void onDrawEye(EyeTransform eyeTransform) {
        Log.d(TAG, "onDrawEye()");
    }

    @Override
    public void onFinishFrame(Viewport viewport) {
        Log.d(TAG, "onFinishFrame()");
    }

    @Override
    public void onSurfaceChanged(int i, int i2) {
        Log.d(TAG, "onSurfaceChanged()");
    }

    @Override
    public void onSurfaceCreated(EGLConfig eglConfig) {
        Log.d(TAG, "onSurfaceCreated()");

//        GLES20
    }

    @Override
    public void onRendererShutdown() {
        Log.d(TAG, "onRenderShutdown()");
    }
    // endregion
}
