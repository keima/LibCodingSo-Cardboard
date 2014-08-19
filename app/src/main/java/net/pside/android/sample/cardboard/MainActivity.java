package net.pside.android.sample.cardboard;

import android.os.Bundle;
import android.util.Log;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.EyeTransform;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;

import jp.nyatla.nymmd.MmdException;
import jp.nyatla.nymmd.android.AndMmdMotionPlayerGLES20;
import jp.nyatla.nymmd.android.AndMmdPmdModel;
import jp.nyatla.nymmd.android.AndMmdVmdMotion;

public class MainActivity extends CardboardActivity
        implements CardboardView.StereoRenderer {
    private static final String TAG = MainActivity.class.getSimpleName();

    // PMD model / VMD motion
    AndMmdPmdModel mPmdModel;
    AndMmdVmdMotion mVmdMotion;

    AndMmdMotionPlayerGLES20 mPlayer;

    MyRenderer mRenderer;

    private int mGlProgram;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize View
        setContentView(R.layout.activity_main);
        CardboardView cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
        cardboardView.setRenderer(this);
        setCardboardView(cardboardView);

        mRenderer = new MyRenderer();

        try {
            mPmdModel = new AndMmdPmdModel(getAssets(), "model/Miku_Hatsune.pmd");
            mVmdMotion = new AndMmdVmdMotion(getAssets(), "motion/kishimen.vmd");
        } catch (MmdException | IOException e) {
            e.printStackTrace();
        }
    }

    // region CardboardView.StereoRenderer
    @Override
    public void onNewFrame(HeadTransform headTransform) {
        Log.d(TAG, "onNewFrame()");

        mRenderer.onNewFrame(null);
    }

    @Override
    public void onDrawEye(EyeTransform eyeTransform) {
        Log.d(TAG, "onDrawEye()");

        mRenderer.onDrawFrame(null);

//        mPlayer.render(this);
    }

    @Override
    public void onFinishFrame(Viewport viewport) {
        Log.d(TAG, "onFinishFrame()");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.d(TAG, "onSurfaceChanged(" + width + "," + height + ")");

        mRenderer.onSurfaceChanged(null, width / 2, height / 2);
    }

    @Override
    public void onSurfaceCreated(EGLConfig eglConfig) {
        Log.d(TAG, "onSurfaceCreated()");

        mRenderer.onSurfaceCreated(null, eglConfig);

/*        mPlayer = new AndMmdMotionPlayerGLES20();

        try {
            mPlayer.setPmd(mPmdModel);
            mPlayer.setVmd(mVmdMotion);
        } catch (MmdException e) {
            e.printStackTrace();
        }
        */
    }

    @Override
    public void onRendererShutdown() {
        Log.d(TAG, "onRenderShutdown()");
//        mPlayer.dispose();
    }
    // endregion
}
