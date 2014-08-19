package jp.nyatla.nymmd.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import net.pside.android.sample.cardboard.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Vector;

import jp.nyatla.nymmd.MmdException;
import jp.nyatla.nymmd.MmdMotionPlayer;
import jp.nyatla.nymmd.MmdPmdModel_BasicClass;
import jp.nyatla.nymmd.MmdVmdMotion_BasicClass;
import jp.nyatla.nymmd.types.MmdMatrix;
import jp.nyatla.nymmd.types.MmdTexUV;
import jp.nyatla.nymmd.types.MmdVector3;
import jp.nyatla.nymmd.types.PmdMaterial;
import jp.nyatla.nymmd.types.PmdSkinInfo;

public class AndMmdMotionPlayerGLES20 extends MmdMotionPlayer {
    private class TextureList extends ArrayList<TextureList.Item> {
        private static final long serialVersionUID = 1L;

        /**
         * TextureList items
         */
        class Item {
            public int gl_texture_id;
            public String file_name;

        }

        public int getPow2Size(int i_w, int i_h) {
            int c = i_w > i_h ? i_w : i_h;
            int s = 0x1;
            while (s < c) {
                s = s << 1;
            }
            return s;
        }

        public TextureList() {
        }

        public void clear() {
            for (Item i : this) {
                final int[] ids = {i.gl_texture_id};
                GLES20.glDeleteTextures(1, ids, 0);
            }
            super.clear();
        }

        private Item createTexture(String szFileName, InputStream i_st) throws MmdException {
            Bitmap img = BitmapFactory.decodeStream(i_st);
            IntBuffer texId = IntBuffer.allocate(1);
            if (img == null) {
                throw new MmdException();
            }
            //2^nに変更
            Bitmap img2;
            int s = getPow2Size(img.getWidth(), img.getHeight());
            if (s != img.getWidth() || s != img.getHeight()) {
                img2 = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(img2);
                canvas.drawBitmap(img, new Rect(0, 0, img.getWidth(), img.getHeight()), new Rect(0, 0, img2.getWidth(), img2.getHeight()), new Paint());
            } else {
                img2 = img;
            }

            GLES20.glGenTextures(1, texId);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId.get(0));
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4);

            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
            // 転写

            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, img2, 0);

            GLES20.glEnable(GLES20.GL_TEXTURE_2D);

            Item ret = new Item();
            ret.file_name = szFileName;
            ret.gl_texture_id = texId.get(0);
            return ret;
        }

        public int getTexture(String i_name, MmdPmdModel_BasicClass.IResourceProvider i_io) throws MmdException {
            try {
                for (Item i : this) {
                    if (i.file_name.equalsIgnoreCase(i_name)) {
                        // 読み込み済みのテクスチャを発見
                        return i.gl_texture_id;
                    }
                }
                // なければファイルを読み込んでテクスチャ作成
                Item ret = this.createTexture(i_name, i_io.getTextureStream(i_name));
                if (ret != null) {
                    this.add(ret);
                    return ret.gl_texture_id;
                }
            } catch (Exception e) {
                throw new MmdException(e);
            }
            // テクスチャ読み込みか作成失敗
            throw new MmdException();
        }
    }

    private class Material {
        public final float[] color = new float[12];// Diffuse,Specular,Ambientの順
        public float fShininess;
        public ShortBuffer indices;
        public int ulNumIndices;
        public int texture_id;
        public int unknown;
    }

    private Context mContext;

    private TextureList mTextureList;
    private final MmdMatrix __tmp_matrix = new MmdMatrix();
    private Material[] mMaterials;
    private float[] _fbuf;
    private FloatBuffer mVertexArray;
    private FloatBuffer mNormalVectorArray;
    private FloatBuffer mTextureCoordsArray;

    private int mGlProgram;
    private int mPositionParam;
    private int mNormalParam;
    private int mColorParam;

    private int mModelViewProjectionParam;
    private int mLightPosParam;
    private int mModelViewParam;
    private int mModelParam;


    public AndMmdMotionPlayerGLES20() {
        super();
        mTextureList = new TextureList();
    }

    public void dispose() {
        mContext = null;
        mTextureList.clear();
        GLES20.glDeleteProgram(mGlProgram);
    }

    /**
     * PMDクラスをセットする
     * @param i_pmd_model
     * @throws MmdException
     */
    public void setPmd(MmdPmdModel_BasicClass i_pmd_model) throws MmdException {
        super.setPmd(i_pmd_model);

        //確保済みリソースのリセット
        mTextureList.clear();

        //OpenGLResourceの生成
        final int number_of_vertex = i_pmd_model.getNumberOfVertex();
        mVertexArray = makeFloatBuffer(number_of_vertex * 3);
        mNormalVectorArray = makeFloatBuffer(number_of_vertex * 3);
        mTextureCoordsArray = makeFloatBuffer(this._ref_pmd_model.getUvArray().length * 2);
        _fbuf = new float[number_of_vertex * 3 * 2];

        MmdPmdModel_BasicClass.IResourceProvider tp = i_pmd_model.getResourceProvider();

        //Material配列の作成
        PmdMaterial[] m = i_pmd_model.getMaterials();// this._ref_materials;
        Vector<Material> materials = new Vector<Material>();
        for (int i = 0; i < m.length; i++) {
            final Material new_material = new Material();

            new_material.unknown = m[i].unknown;

            // D,A,S[rgba]
            m[i].col4Diffuse.getValue(new_material.color, 0);
            m[i].col4Ambient.getValue(new_material.color, 4);
            m[i].col4Specular.getValue(new_material.color, 8);
            new_material.fShininess = m[i].fShininess;

            if (m[i].texture_name != null) {
                new_material.texture_id = this.mTextureList.getTexture(m[i].texture_name, tp);
            } else {
                new_material.texture_id = 0;
            }

            new_material.indices = ShortBuffer.wrap(m[i].indices);
            new_material.ulNumIndices = m[i].indices.length;

            materials.add(new_material);
        }
        mMaterials = materials.toArray(new Material[materials.size()]);

        FloatBuffer tex_array = this.mTextureCoordsArray;
        tex_array.position(0);
        final MmdTexUV[] texture_uv = this._ref_pmd_model.getUvArray();
        for (int i = 0; i < number_of_vertex; i++) {
            tex_array.put(texture_uv[i].u);
            tex_array.put(texture_uv[i].v);
        }
        return;
    }

    /**
     * VMDクラスをセットする
     * @param i_vmd_model
     * @throws MmdException
     */
    public void setVmd(MmdVmdMotion_BasicClass i_vmd_model) throws MmdException {
        super.setVmd(i_vmd_model);
    }

    /**
     * この関数はupdateMotionがskinning_matを更新するを呼び出します。
     */
    protected void onUpdateSkinningMatrix(MmdMatrix[] i_skinning_mat) throws MmdException {
        MmdVector3 vp;
        MmdMatrix mat;

        MmdVector3[] org_pos_array = this._ref_pmd_model.getPositionArray();
        MmdVector3[] org_normal_array = this._ref_pmd_model.getNormatArray();
        PmdSkinInfo[] org_skin_info = this._ref_pmd_model.getSkinInfoArray();
        int number_of_vertex = this._ref_pmd_model.getNumberOfVertex();

        float[] ft = this._fbuf;
        int p1 = 0;
        int p2 = number_of_vertex * 3;

        for (int i = 0; i < number_of_vertex; i++) {
            PmdSkinInfo info_ptr = org_skin_info[i];
            if (info_ptr.fWeight == 0.0f) {
                mat = i_skinning_mat[info_ptr.unBoneNo_1];
            } else if (info_ptr.fWeight >= 0.9999f) {
                mat = i_skinning_mat[info_ptr.unBoneNo_0];
            } else {
                final MmdMatrix mat0 = i_skinning_mat[info_ptr.unBoneNo_0];
                final MmdMatrix mat1 = i_skinning_mat[info_ptr.unBoneNo_1];
                mat = this.__tmp_matrix;
                mat.MatrixLerp(mat0, mat1, info_ptr.fWeight);
            }
            vp = org_pos_array[i];
            ft[p1++] = ((float) (vp.x * mat.m00 + vp.y * mat.m10 + vp.z * mat.m20 + mat.m30));
            ft[p1++] = ((float) (vp.x * mat.m01 + vp.y * mat.m11 + vp.z * mat.m21 + mat.m31));
            ft[p1++] = ((float) (vp.x * mat.m02 + vp.y * mat.m12 + vp.z * mat.m22 + mat.m32));

            vp = org_normal_array[i];
            ft[p2++] = ((float) (vp.x * mat.m00 + vp.y * mat.m10 + vp.z * mat.m20));
            ft[p2++] = ((float) (vp.x * mat.m01 + vp.y * mat.m11 + vp.z * mat.m21));
            ft[p2++] = ((float) (vp.x * mat.m02 + vp.y * mat.m12 + vp.z * mat.m22));
        }
        this.mVertexArray.position(0);
        this.mVertexArray.put(ft, 0, number_of_vertex * 3);
        this.mNormalVectorArray.position(0);
        this.mNormalVectorArray.put(ft, number_of_vertex * 3, number_of_vertex * 3);
        return;
    }

    public void initBuffers() {

    }

    /**
     * 描画する
     */
    public void render(Context context) {
        // Initialize
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_FRONT);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // とりあえずbufferに変換しよう
        this.mVertexArray.position(0);
        this.mNormalVectorArray.position(0);
        this.mTextureCoordsArray.position(0);
        // とりあえず転写用

//        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, );

        // VertexShaderの引数ポインタを取り出す
        mPositionParam = GLES20.glGetAttribLocation(mGlProgram, "a_Position");
        mNormalParam = GLES20.glGetAttribLocation(mGlProgram, "a_Normal");
//         = GLES20.glGetAttribLocation(mGlProgram, "a_Color");

        GLES20Shader.initShader(context, mGlProgram);
/*
        // 頂点座標、法線、テクスチャ座標の各配列をセット
        GLES20.glVertexAttribPointer();

        GLES20.glVertexPointer(3, GL.GL_FLOAT, 0, this.mVertexArray);
        GLES20.glNormalPointer(GL.GL_FLOAT, 0, this.mNormalVectorArray);
        GLES20.glTexCoordPointer(2, GL.GL_FLOAT, 0, this.mTextureCoordsArray);
        for (int i = this.mMaterials.length - 1; i >= 0; i--) {
            final Material mt_ptr = this.mMaterials[i];
            // マテリアル設定
            gl.glMaterialfv(GL.GL_FRONT_AND_BACK, GL.GL_DIFFUSE, mt_ptr.color, 0);
            gl.glMaterialfv(GL.GL_FRONT_AND_BACK, GL.GL_AMBIENT, mt_ptr.color, 4);
            gl.glMaterialfv(GL.GL_FRONT_AND_BACK, GL.GL_SPECULAR, mt_ptr.color, 8);
            gl.glMaterialf(GL.GL_FRONT_AND_BACK, GL.GL_SHININESS, mt_ptr.fShininess);

            //カリング判定：何となくうまくいったから
            if ((0x100 & mt_ptr.unknown) == 0x100) {
                gl.glDisable(GL.GL_CULL_FACE);
            } else {
                gl.glEnable(GL.GL_CULL_FACE);
            }

            if (mt_ptr.texture_id != 0) {
                // テクスチャありならBindする
                gl.glEnable(GL.GL_TEXTURE_2D);
                gl.glBindTexture(GL.GL_TEXTURE_2D, mt_ptr.texture_id);
            } else {
                // テクスチャなし
                gl.glDisable(GL.GL_TEXTURE_2D);
            }
            // 頂点インデックスを指定してポリゴン描画
            gl.glDrawElements(GL.GL_TRIANGLES, mt_ptr.ulNumIndices, GL.GL_UNSIGNED_SHORT, mt_ptr.indices);
        }
        gl.glDisableClientState(GL.GL_VERTEX_ARRAY);
        gl.glDisableClientState(GL.GL_NORMAL_ARRAY);
        gl.glDisableClientState(GL.GL_TEXTURE_COORD_ARRAY);
        return;*/
    }

    private static FloatBuffer makeFloatBuffer(int i_size) {
        ByteBuffer bb = ByteBuffer.allocateDirect(i_size * 4);
        bb.order(ByteOrder.nativeOrder());
        return bb.asFloatBuffer();
    }

    private static class GLES20Shader {
        private static final String TAG = "GLES20Shader";

        private static int loadShader(String code, int shaderType) {
            int shader = GLES20.glCreateShader(shaderType);
            GLES20.glShaderSource(shader, code);
            GLES20.glCompileShader(shader);

            // Get the compilation status.
            final int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

            // If the compilation failed, delete the shader.
            if (compileStatus[0] == 0) {
                Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }

            if (shader == 0) {
                throw new RuntimeException("Error creating shader.");
            }

            return shader;
        }

        public static void initShader(Context context, int glProgram) {
            String VERTEX_CODE = readRawTextFile(context, R.raw.vertex);
            String FRAGMENT_CODE = readRawTextFile(context, R.raw.fragment);

            int vertexShader = loadShader(VERTEX_CODE, GLES20.GL_VERTEX_SHADER);
            int fragmentShader = loadShader(FRAGMENT_CODE, GLES20.GL_FRAGMENT_SHADER);

            GLES20.glAttachShader(glProgram, vertexShader);
            GLES20.glAttachShader(glProgram, fragmentShader);
            GLES20.glLinkProgram(glProgram);

            int[] programStatus = new int[1];
            GLES20.glGetProgramiv(glProgram, GLES20.GL_LINK_STATUS, programStatus, 0);
            if (programStatus[0] == 0) {
                Log.e(TAG, "Error Linking Program:" + GLES20.glGetProgramInfoLog(glProgram), new RuntimeException());
            }

            GLES20.glUseProgram(glProgram);
        }

        // region LoadShader

        /**
         * Converts a raw text file into a string.
         *
         * @param resId The resource ID of the raw text file about to be turned into a shader.
         * @return
         */
        private static String readRawTextFile(Context context, int resId) {
            InputStream inputStream = context.getResources().openRawResource(resId);
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                return sb.toString();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return "";
        }

        // endregion
    }
}
