package com.message.postcard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.EyeTransform;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;


public class VRActivity extends CardboardActivity implements CardboardView.StereoRenderer {
	private static final String TAG = "VRPostcardActivity";

	private int mProgram;
	private int maPosition;
	private int maNormal;
	private int muMVPMatrix;
	private int muAmbient;
	private int muDiffuse;
	private final int[] muLightPos = new int[8];
	private final int[] muLightCol = new int[8];

	private int mQProgram;
	private int maQPosition;
	private int maQTexCoord;
	private int muQTexture;

	private int mGProgram;
	private int maGPosition;
	private int maGTexCoord;
	private int muGTexture;
	private final int[] muGTexCoef = new int[4];
	private final int[] muGTexOffset = new int[4];

	private int mPProgram;
	private int maPPosition;
	private int maPSizeShift;
	private int muPPointSize;
	private int muPTime;
	private int muPTexture;
	private int muPColor;

	private final int mParticles = 1500;
	private int glParticleVB;

	private final float[] mMVMatrix = new float[16];
	private final float[] mMVPMatrix = new float[16];
	private final float[] mVMatrix = new float[16];
	private final float[] mProjMatrix = new float[16];
	private final float[] mCameraMatrix = new float[16];
	private final float[] mHeadMatrix = new float[16];
	private final float[] mCenter = new float[4];
	private float mDist;
	private float ratio = 1;
	private final float[] mAmbient = new float[4];
	private final float[] mDiffuse = new float[4];
	private final float[] mSpecular = new float[4];

	private int filterBuf1;
	private int filterBuf2;
	private int sceneBuf;
	private int renderTex1;
	private int renderTex2;
	private int sceneTex;

	private int particleTex;

	private final float[] mOffsets = new float[4];
	private final float[] pix_mult = new float[4];

	class FilterKernelElement
	{
		public float du;
		public float dv;
		public float coef;
	}
	private final FilterKernelElement[] mvGaussian1D = new FilterKernelElement[44];

	private float mfPerTexelWidth;
	private float mfPerTexelHeight;

	private int glQuadVB;

	private int scrWidth;
	private int scrHeight;

	private int texWidth;
	private int texHeight;

	private final int[] genbuf = new int[1];

	private float mAngle;

	private static Scene3D scene = null;

	public float fps = 0;
	private long start_frame;
	private long frames_drawn;
	public boolean showText = false;

	private float maxPointSize = 0;
	private float curPointSize = 0;
	private long prevTime;

	private int eyeBuffer = 0;
	EyeTransform mEyeTransform = null;

	private Vibrator mVibrator;

	private int createBuffer(float[] buffer)
	{
		FloatBuffer floatBuf = ByteBuffer.allocateDirect(buffer.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
		floatBuf.put(buffer);
		floatBuf.position(0);

		GLES20.glGenBuffers(1, genbuf, 0);
		int glBuf = genbuf[0];
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, glBuf);
		GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, buffer.length * 4, floatBuf, GLES20.GL_STATIC_DRAW);
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

		return glBuf;
	}

	private void initShapes()
	{
		mAmbient[0] = 0.587f;
		mAmbient[1] = 0.587f;
		mAmbient[2] = 0.587f;
		mAmbient[3] = 1.0f;
		mDiffuse[0] = 0.587f;
		mDiffuse[1] = 0.587f;
		mDiffuse[2] = 0.587f;
		mDiffuse[3] = 1.0f;
		mSpecular[0] = 0.896f;
		mSpecular[1] = 0.896f;
		mSpecular[2] = 0.896f;
		mSpecular[3] = 1.0f;

		if (scene == null) {
			try {
				AssetManager am = getAssets();
				InputStream rose = am.open("rose.3ds");
				scene = (new Load3DS()).Load(rose);
				rose.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			assert scene != null;
			int i, num = scene.lights.size();
			for (i = 0; i < num; i++) {
				Light3D light = scene.lights.get(i);
				light.color[0] /= 4.5;
				light.color[1] /= 4.5;
				light.color[2] /= 4.5;
			}
		}

		float[] minpos = new float[3];
		float[] maxpos = new float[3];
		minpos[0] = minpos[1] = minpos[2] = Float.MAX_VALUE;
		maxpos[0] = maxpos[1] = maxpos[2] = -Float.MAX_VALUE;

		int i, num;
		final float ver[] = new float[4];
		final float vec[] = new float[4];
		ver[3] = 1;

		num = scene.objects.size();
		for (i = 0; i < num; i++) {
			Object3D obj = scene.objects.get(i);
			obj.glVertices = createBuffer(obj.vertexBuffer);

			GLES20.glGenBuffers(1, genbuf, 0);
			obj.glIndices = genbuf[0];
			GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, obj.glIndices);
			GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, obj.indCount * 2, null, GLES20.GL_STATIC_DRAW);

			int k, mats = obj.faceMats.size();
			for (k = 0; k < mats; k++) {
				FaceMat mat = obj.faceMats.get(k);
				ShortBuffer indBuf = ByteBuffer.allocateDirect(mat.indexBuffer.length * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
				indBuf.put(mat.indexBuffer);
				indBuf.position(0);

				GLES20.glBufferSubData(GLES20.GL_ELEMENT_ARRAY_BUFFER, mat.bufOffset * 2, mat.indexBuffer.length * 2, indBuf);
			}
			GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
		}

		num = scene.animations.size();
		for (i = 0; i < num; i++) {
			Animation anim = scene.animations.get(i);
			Object3D obj = anim.object;
			if (obj == null) continue;

			int j, verts = obj.vertCount;
			for (j = 0; j < verts; j++) {
				for (int k = 0; k < 3; k++)
					ver[k] = obj.vertexBuffer[j*8 + k];

				Matrix.multiplyMV(vec, 0, anim.world, 0, ver, 0);

				for (int k = 0; k < 3; k++) {
					vec[k] *= 0.002f;
					if (minpos[k] > vec[k]) minpos[k] = vec[k];
					if (maxpos[k] < vec[k]) maxpos[k] = vec[k];
				}
			}
		}

		for (int k = 0; k < 3; k++)
			mCenter[k] = (minpos[k] + maxpos[k]) / 2;
		mCenter[3] = 1;

		mDist = max(max(maxpos[0] - minpos[0], maxpos[1] - minpos[1]), maxpos[2] - minpos[2]);

		num = scene.animations.size();
		for (i = 0; i < num; i++) {
			Animation anim = scene.animations.get(i);
			Object3D obj = anim.object;
			if (obj == null) continue;

			int j, verts = obj.vertCount;
			for (j = 0; j < verts; j++) {
				for (int k = 0; k < 3; k++)
					obj.vertexBuffer[j * 8 + k] -= mCenter[k];
				obj.vertexBuffer[j * 8 + 1] -= mDist * 0.385f;
			}
		}

		final float quadv[] = {
				-1,  1, 0, 0, 1,
				-1, -1, 0, 0, 0,
				1,  1, 0, 1, 1,
				1, -1, 0, 1, 0
		};

		glQuadVB = createBuffer(quadv);
	}

	private static float max(float f, float g)
	{
		return (f > g ? f : g);
	}

	private static int min(int size, int i)
	{
		return (size < i ? size : i);
	}

	private int makeRenderTarget(int width, int height, int[] handles)
	{
		GLES20.glGenTextures(1, genbuf, 0);
		int renderTex = genbuf[0];
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTex);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);

		IntBuffer texBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
		GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, texBuffer);

		GLES20.glGenRenderbuffers(1, genbuf, 0);
		int depthBuf = genbuf[0];
		GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depthBuf);
		GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, width, height);

		GLES20.glGenFramebuffers(1, genbuf, 0);
		int frameBuf = genbuf[0];
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuf);
		GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTex, 0);
		GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, depthBuf);

		int res = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);

		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

		handles[0] = frameBuf;
		handles[1] = renderTex;

		return res;
	}

	/**
	 * Converts a raw text file into a string.
	 * @param resId The resource ID of the raw text file about to be turned into a shader.
	 * @return
	 */
	private String readRawTextFile(int resId)
	{
		InputStream inputStream = getResources().openRawResource(resId);
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

	/**
	 * Converts a raw text file, saved as a resource, into an OpenGL ES shader
	 * @param type The type of shader we will be creating.
	 * @param resId The resource ID of the raw text file about to be turned into a shader.
	 * @return
	 */
	private int loadGLShader(int type, int resId)
	{
		String code = readRawTextFile(resId);
		int shader = GLES20.glCreateShader(type);
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

	/**
	 * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
	 * @param func
	 */
	private static void checkGLError(String func)
	{
		int error;
		while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
			Log.e(TAG, func + ": glError " + error);
			throw new RuntimeException(func + ": glError " + error);
		}
	}

	private int Compile(int vsId, int fsId)
	{
		int vertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, vsId);
		int fragmentShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, fsId);

		int prog = GLES20.glCreateProgram();			 // create empty OpenGL Program
		GLES20.glAttachShader(prog, vertexShader);   // add the vertex shader to program
		GLES20.glAttachShader(prog, fragmentShader); // add the fragment shader to program
		GLES20.glLinkProgram(prog);				  // creates OpenGL program executables

		return prog;
	}

	private static int loadTexture(final Context context, final int resourceId)
	{
		final int[] textureHandle = new int[1];

		GLES20.glGenTextures(1, textureHandle, 0);
		if (textureHandle[0] != 0)
		{
			final BitmapFactory.Options options = new BitmapFactory.Options();
			options.inScaled = false;   // No pre-scaling
			// Read in the resource
			final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resourceId, options);

			// Bind to the texture in OpenGL
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);
			// Set filtering
			GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
			GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

			// Load the bitmap into the bound texture.
			GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

			// Recycle the bitmap, since its data has been loaded into OpenGL.
			bitmap.recycle();
		}

		return textureHandle[0];
	}

	private void initParticles()
	{
		int width = scrWidth, height = scrHeight, fontSize = (int) (scrHeight / 12.5); // 7.84);

		// Create an empty, mutable bitmap
		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_4444);
		// get a canvas to paint over the bitmap
		Canvas canvas = new Canvas(bitmap);
		bitmap.eraseColor(Color.BLACK);

		// Draw the text
		Paint textPaint = new Paint();
		textPaint.setTextSize(fontSize);
		textPaint.setAntiAlias(false);
		textPaint.setARGB(0xff, 0xff, 0xff, 0xff);
		textPaint.setTextAlign(Paint.Align.CENTER);
		textPaint.setTypeface(Typeface.SANS_SERIF);

		int fontHeight = (fontSize * 3) / 4;
		int pad = (int)(fontHeight * 5.5f);
		int gap = (height - fontHeight * 3 - pad * 2) / 2;
		int hc = width / 2;

		// draw the text centered
		canvas.drawText("KR", hc, pad + fontHeight, textPaint);
		canvas.drawText("Google ", hc, pad + fontHeight * 2 + gap, textPaint);
		canvas.drawText("Cardboard", hc, pad + fontHeight * 3 + gap * 2, textPaint);
     

		int[] pixels = new int[width * height];
		bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
		bitmap.recycle();

		int colored = 0;
		float[] cx = new float[width * height];
		float[] cy = new float[width * height];

		for (int y = 0, idx = 0; y < height; y++)
			for (int x = 0; x < width; x++)
				if ((pixels[idx++] & 0xffffff) != 0) {
					cx[colored] = x / (float)width;
					cy[colored] = y / (float)height;
					colored++;
				}

		float[] particleBuf = new float[3 * mParticles];
		for (int i = 0, idx = 0; i < mParticles; i++, idx += 3) {
			int n = (int) (Math.random() * colored);
			particleBuf[idx + 0] = cx[n] * 2 - 1;
			particleBuf[idx + 1] = 1 - cy[n] * 2;
			particleBuf[idx + 2] = (float) Math.random();
		}

		curPointSize = 0;
		maxPointSize = scrHeight / 69.0f;
		prevTime = SystemClock.uptimeMillis();

		glParticleVB = createBuffer(particleBuf);

		mPProgram = Compile(R.raw.particle_vertex, R.raw.particle_fragment);
		maPPosition = GLES20.glGetAttribLocation(mPProgram, "vPosition");
		maPSizeShift = GLES20.glGetAttribLocation(mPProgram, "vSizeShift");
		muPPointSize = GLES20.glGetUniformLocation(mPProgram, "uPointSize");
		muPTime = GLES20.glGetUniformLocation(mPProgram, "uTime");
		muPTexture = GLES20.glGetUniformLocation(mPProgram, "uTexture0");
		muPColor = GLES20.glGetUniformLocation(mPProgram, "uColor");

		particleTex = loadTexture(this, R.drawable.particle);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_activity);
		CardboardView cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
		cardboardView.setRenderer(this);
		setCardboardView(cardboardView);
		mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
	}

	private void DrawGauss(boolean invert)
	{
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

		GLES20.glUseProgram(mGProgram);
		GLES20.glDisable(GLES20.GL_DEPTH_TEST);

		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, glQuadVB);
		GLES20.glEnableVertexAttribArray(maGPosition);
		GLES20.glVertexAttribPointer(maGPosition, 3, GLES20.GL_FLOAT, false, 20, 0);
		GLES20.glEnableVertexAttribArray(maGTexCoord);
		GLES20.glVertexAttribPointer(maGTexCoord, 2, GLES20.GL_FLOAT, false, 20, 12);
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

		GLES20.glUniform1i(muGTexture, 0);

		int i, n, k;
		for (i = 0; i < mvGaussian1D.length; i += 4) {
			for (n = 0; n < 4; n++) {
				FilterKernelElement pE = mvGaussian1D[i + n];

				for (k = 0; k < 4; k++)
					pix_mult[k] = pE.coef * 0.11f;
				GLES20.glUniform4fv(muGTexCoef[n], 1, pix_mult, 0);

				mOffsets[0] = mfPerTexelWidth * (invert ? pE.dv : pE.du);
				mOffsets[1] = mfPerTexelHeight * (invert ? pE.du : pE.dv);
				GLES20.glUniform4fv(muGTexOffset[n], 1, mOffsets, 0);
			}

			GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
		}

		GLES20.glDisableVertexAttribArray(maGPosition);
		GLES20.glDisableVertexAttribArray(maGTexCoord);
	}

	private void DrawQuad()
	{
		GLES20.glUseProgram(mQProgram);
		GLES20.glDisable(GLES20.GL_DEPTH_TEST);

		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, glQuadVB);
		GLES20.glEnableVertexAttribArray(maQPosition);
		GLES20.glVertexAttribPointer(maQPosition, 3, GLES20.GL_FLOAT, false, 20, 0);
		GLES20.glEnableVertexAttribArray(maQTexCoord);
		GLES20.glVertexAttribPointer(maQTexCoord, 2, GLES20.GL_FLOAT, false, 20, 12);
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

		GLES20.glUniform1i(muQTexture, 0);

		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

		GLES20.glDisableVertexAttribArray(maQPosition);
		GLES20.glDisableVertexAttribArray(maQTexCoord);
	}

	private void DrawText()
	{
		GLES20.glUseProgram(mPProgram);
		GLES20.glDisable(GLES20.GL_DEPTH_TEST);

		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, glParticleVB);
		GLES20.glEnableVertexAttribArray(maPPosition);
		GLES20.glVertexAttribPointer(maPPosition, 2, GLES20.GL_FLOAT, false, 12, 0);
		GLES20.glEnableVertexAttribArray(maPSizeShift);
		GLES20.glVertexAttribPointer(maPSizeShift, 1, GLES20.GL_FLOAT, false, 12, 8);
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

		GLES20.glUniform1f(muPPointSize, curPointSize);
		GLES20.glUniform4f(muPColor, 1, 1, 0, 1);
		GLES20.glUniform1i(muPTexture, 0);
		GLES20.glUniform1f(muPTime, (SystemClock.uptimeMillis() % 1000) / 1000.0f);

		GLES20.glDrawArrays(GLES20.GL_POINTS, 0, mParticles);

		GLES20.glDisableVertexAttribArray(maPPosition);
		GLES20.glDisableVertexAttribArray(maPSizeShift);
	}

	private void DrawScene(EyeTransform eyeTransform)
	{
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

		GLES20.glUseProgram(mProgram);
		GLES20.glEnable(GLES20.GL_CULL_FACE);
		GLES20.glEnable(GLES20.GL_DEPTH_TEST);

		Matrix.setLookAtM(mCameraMatrix, 0, 0, 0, mDist, 0, 0, 0, 0f, 1.0f, 0.0f);
		Matrix.multiplyMM(mVMatrix, 0, mCameraMatrix, 0, eyeTransform.getEyeView(), 0);
		Matrix.translateM(mVMatrix, 0, -mCenter[0], -mCenter[1] - mDist * 0.385f, -mCenter[2]);
		Matrix.scaleM(mVMatrix, 0, 0.002f, 0.002f, 0.002f);

		int i, j, k, num;
		num = min(scene.lights.size(), 8);

		for (i = 0; i < num; i++) {
			Light3D light = scene.lights.get(i);
			GLES20.glUniform3fv(muLightPos[i], 1, light.pos, 0);
			GLES20.glUniform4fv(muLightCol[i], 1, light.color, 0);
		}

		// Prepare the triangle data
		GLES20.glEnableVertexAttribArray(maPosition);
		GLES20.glEnableVertexAttribArray(maNormal);

		num = scene.animations.size();
		for (i = 0; i < num; i++) {
			Animation anim = scene.animations.get(i);
			Object3D obj = anim.object;
			if (obj == null) continue;

			Matrix.multiplyMM(mMVMatrix, 0, mVMatrix, 0, anim.world, 0);
			Matrix.multiplyMM(mMVPMatrix, 0, eyeTransform.getPerspective(), 0, mMVMatrix, 0);

			// Apply a ModelView Projection transformation
			GLES20.glUniformMatrix4fv(muMVPMatrix, 1, false, mMVPMatrix, 0);

			GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, obj.glVertices);
			GLES20.glVertexAttribPointer(maPosition, 3, GLES20.GL_FLOAT, false, 32, 0);
			GLES20.glVertexAttribPointer(maNormal, 3, GLES20.GL_FLOAT, false, 32, 12);
			GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

			GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, obj.glIndices);

			int mats = obj.faceMats.size();
			for (j = 0; j < mats; j++) {
				FaceMat mat = obj.faceMats.get(j);

				if (mat.material != null) {
					if (mat.material.ambient != null && scene.ambient != null) {
						for (k = 0; k < 3; k++)
							mAmbient[k] = mat.material.ambient[k] * scene.ambient[k];
						GLES20.glUniform4fv(muAmbient, 1, mAmbient, 0);
					}
					else
						GLES20.glUniform4f(muAmbient, 0, 0, 0, 1);

					if (mat.material.diffuse != null)
						GLES20.glUniform4fv(muDiffuse, 1, mat.material.diffuse, 0);
					else
						GLES20.glUniform4fv(muDiffuse, 1, mDiffuse, 0);
				}
				else {
					GLES20.glUniform4f(muAmbient, 0, 0, 0, 1);
					GLES20.glUniform4fv(muDiffuse, 1, mDiffuse, 0);
				}

				GLES20.glDrawElements(GLES20.GL_TRIANGLES, mat.indexBuffer.length, GLES20.GL_UNSIGNED_SHORT, mat.bufOffset * 2);
			}

			GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
		}

		GLES20.glDisableVertexAttribArray(maPosition);
		GLES20.glDisableVertexAttribArray(maNormal);
	}

	@Override
	public void onNewFrame(HeadTransform headTransform)
	{
		long curTime = SystemClock.uptimeMillis();
		long time = curTime % 4000L;
		mAngle = 0.090f * ((int) time);

		if (curTime > start_frame + 1000) {
			fps = frames_drawn * 1000.0f / (curTime - start_frame);
			start_frame = curTime;
			frames_drawn = 0;
		}

		if (showText && curPointSize < maxPointSize) {
			// fade in
			double delta = (curTime - prevTime) / 1000.0;
			curPointSize += maxPointSize * delta;
			if (curPointSize > maxPointSize)
				curPointSize = maxPointSize;
		}
		if (!showText && curPointSize > 0) {
			// fade out
			double delta = (curTime - prevTime) / 1000.0;
			curPointSize -= maxPointSize * delta;
			if (curPointSize < 0)
				curPointSize = 0;
		}

		prevTime = curTime;

		headTransform.getHeadView(mHeadMatrix, 0);

		frames_drawn++;
	}

	private void setRenderTexture(int frameBuf, int texture)
	{
		if (frameBuf == eyeBuffer) {
			mEyeTransform.getParams().getViewport().setGLViewport();
			GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
		}
		else if (frameBuf == sceneBuf || frameBuf == 0) {
			GLES20.glViewport(0, 0, scrWidth, scrHeight);
			GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
		}
		else {
			GLES20.glViewport(0, 0, texWidth, texHeight);
			GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
		}

		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuf);
		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
	}

	@Override
	public void onDrawEye(EyeTransform eyeTransform)
	{
		final int[] eyeBuf = new int[1];
		GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, eyeBuf, 0);
		eyeBuffer = eyeBuf[0];

		mEyeTransform = eyeTransform;

		setRenderTexture(filterBuf1, 0);
		DrawScene(eyeTransform);

		GLES20.glEnable(GLES20.GL_BLEND);
		GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);

		setRenderTexture(filterBuf2, renderTex1);
		DrawGauss(false);

		setRenderTexture(filterBuf1, renderTex2);
		DrawGauss(true);

		GLES20.glDisable(GLES20.GL_BLEND);

		setRenderTexture(eyeBuffer, 0);
		DrawScene(eyeTransform);

		GLES20.glEnable(GLES20.GL_BLEND);
		GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);

		setRenderTexture(eyeBuffer, renderTex1);
		DrawQuad();

		if (curPointSize > 0) {
			setRenderTexture(eyeBuffer, particleTex);
			DrawText();
		}

		GLES20.glDisable(GLES20.GL_BLEND);
	}

	@Override
	public void onCardboardTrigger() {
		showText = !showText;

		// Always give user feedback
		mVibrator.vibrate(50);
	}

	@Override
	public void onFinishFrame(Viewport viewport)
	{
	}

	@Override
	public void onSurfaceChanged(int width, int height)
	{
		ratio = (float) width / height;
		Matrix.frustumM(mProjMatrix, 0, -ratio, ratio, -1, 1, 1, 100);

		int[] handles = new int[2];

		scrWidth = width;
		scrHeight = height;

		texWidth = 256;
		texHeight = 256;

		makeRenderTarget(texWidth, texHeight, handles);
		filterBuf1 = handles[0];
		renderTex1 = handles[1];

		makeRenderTarget(texWidth, texHeight, handles);
		filterBuf2 = handles[0];
		renderTex2 = handles[1];

		makeRenderTarget(scrWidth, scrHeight, handles);
		sceneBuf = handles[0];
		sceneTex = handles[1];

		float cent = (mvGaussian1D.length - 1.0f) / 2.0f, radi;
		for (int u = 0; u < mvGaussian1D.length; u++)
		{
			FilterKernelElement el = mvGaussian1D[u] = new FilterKernelElement();
			el.du = ((float)u) - cent - 0.1f;
			el.dv = 0.0f;
			radi = (el.du * el.du) / (cent * cent);
			el.coef = (float)((0.24/Math.exp(radi*0.18)) + 0.41/Math.exp(radi*4.5));
		}

		float rr = texWidth / (float) texHeight;
		float rs = rr / ratio;

		mfPerTexelWidth = rs / texWidth;
		mfPerTexelHeight = 1.0f / texHeight;

		initParticles();

		start_frame = SystemClock.uptimeMillis();
		frames_drawn = 0;
		fps = 0;
	}

	@Override
	public void onSurfaceCreated(EGLConfig eglConfig)
	{
		// Set the background frame color
		GLES20.glClearColor(0, 0, 0, 1);

		mProgram = Compile(R.raw.lighting_vertex, R.raw.lighting_fragment);

		// get handle to the vertex shader's vPosition member
		maPosition = GLES20.glGetAttribLocation(mProgram, "vPosition");
		maNormal = GLES20.glGetAttribLocation(mProgram, "vNormal");
		muMVPMatrix = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
		muAmbient = GLES20.glGetUniformLocation(mProgram, "uAmbient");
		muDiffuse = GLES20.glGetUniformLocation(mProgram, "uDiffuse");

		int i;
		for (i = 0; i < 8; i++) {
			muLightPos[i] = GLES20.glGetUniformLocation(mProgram, String.format("uLight[%d].position", i));
			muLightCol[i] = GLES20.glGetUniformLocation(mProgram, String.format("uLight[%d].color", i));
		}

		mQProgram = Compile(R.raw.quad_vertex, R.raw.quad_fragment);
		maQPosition = GLES20.glGetAttribLocation(mQProgram, "vPosition");
		maQTexCoord = GLES20.glGetAttribLocation(mQProgram, "vTexCoord0");
		muQTexture = GLES20.glGetUniformLocation(mQProgram, "uTexture0");

		mGProgram = Compile(R.raw.gauss_vertex, R.raw.gauss_fragment);
		maGPosition = GLES20.glGetAttribLocation(mGProgram, "vPosition");
		maGTexCoord = GLES20.glGetAttribLocation(mGProgram, "vTexCoord0");
		muGTexture = GLES20.glGetUniformLocation(mGProgram, "uTexture0");

		for (i = 0; i < 4; i++) {
			muGTexOffset[i] = GLES20.glGetUniformLocation(mGProgram, String.format("uTexOffset%d", i));
			muGTexCoef[i] = GLES20.glGetUniformLocation(mGProgram, String.format("uTexCoef%d", i));
		}

		initShapes();
	}

	@Override
	public void onRendererShutdown()
	{
	}
}
