package net.pside.android.sample.cardboard;

/**
 * http://www7.plala.or.jp/kfb/program/framerate.html
 */
public class FrameRate {
    private static final String TAG = FrameRate.class.getSimpleName();

    /**
     * 測定基準時間
     */
    private long mBaseTime;

    /**
     * フレーム数
     */
    private int mCount;

    /**
     * フレームレート
     */
    private float mFrameRate;

    /**
     * コンストラクタ
     */
    public FrameRate() {
        mBaseTime = System.currentTimeMillis();  //基準時間をセット
    }

    /**
     * 計測されたフレームレートを取得する
     * @return フレームレート
     */
    public float getFrameRate() {
        return mFrameRate;
    }

    /**
     * 描画時にコールすることでフレームレート計測を行う
     */
    public void count() {
        ++mCount;        //フレーム数をインクリメント
        long now = System.currentTimeMillis();      //現在時刻を取得
        if (now - mBaseTime >= 1000) {       //１秒以上経過していれば
            mFrameRate = (float) (mCount * 1000) / (float) (now - mBaseTime);        //フレームレートを計算
            mBaseTime = now;     //現在時刻を基準時間に
            mCount = 0;          //フレーム数をリセット
        }
    }
}
