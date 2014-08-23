package net.pside.android.sample.cardboard;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;

import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.EyeTransform;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;

import org.apache.commons.lang3.ArrayUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;

public class MyRenderer implements CardboardView.StereoRenderer {
    private float[] mModelMatrix = new float[16];       // ワールド行列
    private float[] mViewMatrix = new float[16];        // ビュー行列
    private float[] mViewMatrixOrigin = new float[16];        // ビュー行列(起点)
    private float[] mProjectionMatrix = new float[16];  // 射影行列
    private float[] mMVPMatrix = new float[16];         // これらの積行列

    private int mNumberOfVertex;
    private final FloatBuffer mVertices;  // 頂点バッファ
    private final FloatBuffer mColors;  // 色バッファ
    private final IntBuffer mIndices; // Indexの複数形

    private int mMVPMatrixHandle;  // u_MVPMatrixのハンドル
    private int mPositionHandle;   // a_Positionのハンドル
    private int mColorHandle;      // a_Colorのハンドル

    private final int mBytesPerFloat = 4;                                  // floatのバイト数
    private final int mStrideBytes = 7 * mBytesPerFloat;                   // ストライドバイト数
    private final int mPositionOffset = 0;                                 // 位置情報の先頭位置
    private final int mPositionDataSize = 3;                               // 位置情報のデータサイズ
    private final int mColorOffset = mPositionOffset + mPositionDataSize;  // 色情報の先頭位置
    private final int mColorDataSize = 4;                                  // 色情報のデータサイズ
    private int mProgramHandle;

    public MyRenderer() {
        // 頂点バッファ生成
        final float[] triangleVerticesData = {
                // 各頂点情報
                // (座標属性) X, Y, Z,
                // (色属性) R, G, B, A

                // 1
                0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f, 1.0f,

                0.5f, 0.86f, 0.0f,
                1.0f, 0.0f, 0.0f, 1.0f,

                0.0f, 1.0f, 0.0f,
                1.0f, 0.0f, 0.0f, 1.0f,

                // 2
                0.0f, 1.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 1.0f,

                0.5f, 0.43f, 0.81f,
                0.0f, 1.0f, 0.0f, 1.0f,

                0.5f, 0.86f, 0.0f,
                0.0f, 1.0f, 0.0f, 1.0f,

                // 3
                0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 1.0f,

                0.5f, 0.43f, 0.81f,
                0.0f, 0.0f, 1.0f, 1.0f,

                0.5f, 0.86f, 0.0f,
                0.0f, 0.0f, 1.0f, 1.0f,

                // 4
                0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f, 1.0f,

                0.0f, 1.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 1.0f,

                0.5f, 0.43f, 0.81f,
                0.0f, 0.0f, 1.0f, 1.0f,
        };

        Torus torus = generateTorus(40, 128, 10, 20);

        mNumberOfVertex = torus.index.length;
        // バッファを確保し、バイトオーダーをネイティブに合わせる(Javaとネイティブではバイトオーダーが異なる)
        mVertices = ByteBuffer.allocateDirect(torus.position.length * mBytesPerFloat)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mVertices.put(torus.position).position(0);  // データをバッファへ

        mColors = ByteBuffer.allocateDirect(torus.color.length * mBytesPerFloat)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mColors.put(torus.color).position(0);

        mIndices = ByteBuffer.allocateDirect(torus.index.length * 4)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();
        mIndices.put(torus.index).position(0);
    }

    private class Torus {
        float[] position;
        float[] color;
        int[] index;

        private Torus(float[] position, float[] color, int[] index) {
            this.position = position;
            this.color = color;
            this.index = index;
        }
    }

    private Torus generateTorus(int row, int column, int irad, int orad) {
        ArrayList<Float> pos = new ArrayList<>(), col = new ArrayList<>();

        for (int i = 0; i <= row; i++) {
            double r, rr, ry;
            r = Math.PI * 2 / row * i;
            rr = Math.cos(r);
            ry = Math.sin(r);

            for (int j = 0; j <= column; j++) {
                double tr, tx, ty, tz;
                tr = Math.PI * 2 / column * j;
                tx = (rr * irad + orad) * Math.cos(tr);
                ty = ry * irad;
                tz = (rr * irad + orad) * Math.sin(tr);

                pos.add(new Float(tx));
                pos.add(new Float(ty));
                pos.add(new Float(tz));

                int[] tc = ColorUtil.HSVtoRGB(360 / column * j, 1, 1);

//                col.add((float) tc[0]);
//                col.add((float) tc[1]);
//                col.add((float) tc[2]);
                col.add(new Float(tx));
                col.add(new Float(ty));
                col.add(new Float(tz));
                col.add(1.0f); // alpha
            }
        }

        ArrayList<Integer> idx = new ArrayList<>();
        for (int i = 0; i < row; i++) {
            for (int j = 0; j < column; j++) {
                int r = (column + 1) * i + j;

                idx.add(r);
                idx.add((r + column + 1));
                idx.add((r + 1));

                idx.add((r + column + 1));
                idx.add((r + column + 2));
                idx.add((r + 1));

            }
        }

        return new Torus(
                ArrayUtils.toPrimitive(pos.toArray(new Float[0])),
                ArrayUtils.toPrimitive(col.toArray(new Float[0])),
                ArrayUtils.toPrimitive(idx.toArray(new Integer[0]))
        );
    }

    @Override
    public void onNewFrame(HeadTransform headTransform) {
        // シェーダプログラム適用
        GLES20.glUseProgram(mProgramHandle);

        // ハンドル(ポインタ)の取得
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_MVPMatrix");
        mPositionHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Position");
        mColorHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Color");

        // プリミティブをアニメーション
        // 経過秒から回転角度を求める(10秒/周)
        long time = SystemClock.uptimeMillis() % 10000L;
        float angleInDegrees = (360.0f / 10000.0f) * ((int) time);

        // ワールド行列に対して回転をかける
        Matrix.setIdentityM(mModelMatrix, 0);  // 単位行列でリセット
        Matrix.rotateM(mModelMatrix, 0, angleInDegrees, 1.0f, 0.0f, 0.0f);  // 回転行列
    }

    // 三角形を描画する
    private void drawTriangle() {
        // OpenGLに頂点バッファを渡す
//        aTriangleBuffer.position(mPositionOffset);  // 頂点バッファを座標属性にセット
        mVertices.position(0);
        GLES20.glVertexAttribPointer(mPositionHandle, mPositionDataSize, GLES20.GL_FLOAT, false, 0, mVertices);  // ポインタと座標属性を結び付ける
        GLES20.glEnableVertexAttribArray(mPositionHandle);  // 座標属性有効

//        aTriangleBuffer.position(mColorOffset);  // 頂点バッファを色属性にセット
        mColors.position(0);
        GLES20.glVertexAttribPointer(mColorHandle, mColorDataSize, GLES20.GL_FLOAT, false, 0, mColors);  // ポインタと色属性を結び付ける
        GLES20.glEnableVertexAttribArray(mColorHandle);  // 色属性有効

        // ワールド行列×ビュー行列×射影行列をセット
        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);

//        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 12);  // 三角形を描画
        mIndices.position(0);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mNumberOfVertex, GLES20.GL_UNSIGNED_INT, mIndices);
    }

    @Override
    public void onDrawEye(EyeTransform eyeTransform) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f); // 背景色
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);  // バッファのクリア

        Matrix.multiplyMM(mViewMatrix, 0, eyeTransform.getEyeView(), 0, mViewMatrixOrigin, 0);
//        mViewMatrix = mViewMatrixOrigin.clone();

        drawTriangle();
    }

    @Override
    public void onFinishFrame(Viewport viewport) {

    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        // スクリーンが変わり画角を変更する場合、射影行列を作り直す
        GLES20.glViewport(0, 0, width, height);

        final float ratio = (float) width / height;
        final float left = -ratio;
        final float right = ratio;
        final float bottom = -1.0f;
        final float top = 1.0f;
        final float near = 1.0f;
        final float far = 100.0f;

        Log.d("MyRenderer", "(left/right/bottom/top/near/far): " + left + "/" + right + "/" + bottom + "/" + top + "/" + near + "/" + far);
        Matrix.frustumM(mProjectionMatrix, 0, left, right, bottom, top, near, far);
    }

    @Override
    public void onSurfaceCreated(EGLConfig eglConfig) {
        // カメラ(ビュー行列)を設定
        final float[] eye = {0.0f, 0.0f, -50.0f};
        final float[] look = {0.0f, 0.0f, 0.0f};
        final float[] up = {0.0f, 1.0f, 0.0f};
        Matrix.setLookAtM(mViewMatrixOrigin, 0, eye[0], eye[1], eye[2], look[0], look[1], look[2], up[0], up[1], up[2]);

        // バーテックスシェーダ
        final String vertexShader =
                "uniform mat4 u_MVPMatrix;      \n"
                        + "attribute vec4 a_Position;     \n"
                        + "attribute vec4 a_Color;        \n"
                        + "varying vec4 v_Color;          \n"

                        + "void main()                    \n"
                        + "{                              \n"
                        + "   v_Color = a_Color;          \n"
                        + "   gl_Position = u_MVPMatrix   \n"
                        + "               * a_Position;   \n"
                        + "}                              \n";

        // フラグメントシェーダ
        final String fragmentShader =
                "precision mediump float;       \n"
                        + "varying vec4 v_Color;          \n"

                        + "void main()                    \n"
                        + "{                              \n"
                        + "   gl_FragColor = v_Color;     \n"
                        + "}                              \n";

        // バーテックスシェーダをコンパイル
        int vertexShaderHandle = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        if (vertexShaderHandle != 0) {
            GLES20.glShaderSource(vertexShaderHandle, vertexShader);  // シェーダソースを送信し
            GLES20.glCompileShader(vertexShaderHandle);  // コンパイル

            // コンパイル結果のチェック
            final int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(vertexShaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

            if (compileStatus[0] == 0) {
                // コンパイル失敗
                GLES20.glDeleteShader(vertexShaderHandle);
                vertexShaderHandle = 0;
            }
        }
        if (vertexShaderHandle == 0) {
            throw new RuntimeException("Error creating vertex shader.");
        }

        // フラグメントシェーダをコンパイル
        int fragmentShaderHandle = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        if (fragmentShaderHandle != 0) {
            GLES20.glShaderSource(fragmentShaderHandle, fragmentShader);
            GLES20.glCompileShader(fragmentShaderHandle);

            final int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(fragmentShaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

            if (compileStatus[0] == 0) {
                GLES20.glDeleteShader(fragmentShaderHandle);
                fragmentShaderHandle = 0;
            }
        }
        if (fragmentShaderHandle == 0) {
            throw new RuntimeException("Error creating fragment shader.");
        }

        // シェーダプログラムをリンク
        mProgramHandle = GLES20.glCreateProgram();
        if (mProgramHandle != 0) {
            GLES20.glAttachShader(mProgramHandle, vertexShaderHandle);  // バーテックスシェーダをアタッチ
            GLES20.glAttachShader(mProgramHandle, fragmentShaderHandle);  // フラグメントシェーダをアタッチ
            GLES20.glBindAttribLocation(mProgramHandle, 0, "a_Position");  // attributeのindexを設定
            GLES20.glBindAttribLocation(mProgramHandle, 1, "a_Color");  // attributeのindexを設定
            GLES20.glLinkProgram(mProgramHandle);  // バーテックスシェーダとフラグメントシェーダをプログラムへリンク

            // リンク結果のチェック
            final int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(mProgramHandle, GLES20.GL_LINK_STATUS, linkStatus, 0);

            if (linkStatus[0] == 0) {
                // リンク失敗
                GLES20.glDeleteProgram(mProgramHandle);
                mProgramHandle = 0;
            }
        }
        if (mProgramHandle == 0) {
            throw new RuntimeException("Error creating program.");
        }

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    @Override
    public void onRendererShutdown() {

    }
}