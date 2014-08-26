package net.pside.android.sample.cardboard;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.EyeTransform;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;

import java.util.Timer;
import java.util.TimerTask;

import javax.microedition.khronos.egl.EGLConfig;

public class MainActivity extends CardboardActivity implements CardboardView.StereoRenderer {
    private static final String TAG = MainActivity.class.getSimpleName();

    MyRenderer mRenderer;
    FrameRate mFrameRate;
    Timer mTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRenderer = new MyRenderer();
        mFrameRate = new FrameRate();
        mTimer = new Timer();

        mTimer.scheduleAtFixedRate(mTimerTask, 1000, 1000);

        // Initialize View
        setContentView(R.layout.activity_main);
        CardboardView cardboardView = (CardboardView) findViewById(R.id.cardboard_view);

        // フレームレート計測をしたいのでthis指定してるけどメインロジックはMyRendererに書いてある
        cardboardView.setRenderer(this);

        cardboardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Trigger when tap.");
            }
        });
        setCardboardView(cardboardView);
    }

    @Override
    public void onCardboardTrigger() {
        super.onCardboardTrigger();
        Log.d(TAG, "Trigger");
    }

    // region CardboardView.StereoRenderer
    public void onNewFrame(HeadTransform headTransform) {
        mRenderer.onNewFrame(headTransform);
        mFrameRate.count();
    }

    public void onDrawEye(EyeTransform eyeTransform) {
        mRenderer.onDrawEye(eyeTransform);
    }

    public void onFinishFrame(Viewport viewport) {
        mRenderer.onFinishFrame(viewport);
    }

    public void onSurfaceChanged(int width, int height) {
        mRenderer.onSurfaceChanged(width, height);
    }

    public void onSurfaceCreated(EGLConfig eglConfig) {
        mRenderer.onSurfaceCreated(eglConfig);
    }

    public void onRendererShutdown() {
        mRenderer.onRendererShutdown();
    }
    // endregion

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimer();
    }

    private void cancelTimer() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }

    private TimerTask mTimerTask = new TimerTask() {
        @Override
        public void run() {
            Log.d(TAG, "fps:" + mFrameRate.getFrameRate());
        }
    };

}
