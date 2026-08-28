package io.github.alanlaw.vfc;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import io.github.alanlaw.vfc.utils.LogUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import android.graphics.Bitmap;

/**
 * OpenGL ES 旋转渲染器。
 * 在 MediaPlayer 输出和目标 Surface 之间插入 GL 旋转层，
 * 实现 GPU 加速的实时画面旋转。
 *
 * 使用流程：
 * 1. new GLVideoRenderer(targetSurface, tag)
 * 2. mediaPlayer.setSurface(renderer.getInputSurface())
 * 3. renderer.setRotation(90) // 实时调整
 * 4. renderer.release() // 释放资源
 */
public class GLVideoRenderer implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "GLVideoRenderer";

    // EGL
    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;

    // GL
    private int mProgram;
    private int mTextureId;
    private int maPositionHandle;
    private int maTextureHandle;
    private int muSTMatrixHandle;
    private int muCropMatrixHandle;
    private int muDynamicTextureMatrixHandle;
    private int muRotMatrixHandle;
    private int muAmbientColorHandle;
    private int muAmbientIntensityHandle;

    // Input/Output
    private SurfaceTexture mInputSurfaceTexture;
    private Surface mInputSurface;
    private Surface mTargetSurface; // keep reference for validity checks

    // Matrices
    private final float[] mSTMatrix = new float[16];
    private final float[] mCropMatrix = new float[16];
    private final float[] mDynamicTextureMatrix = new float[16];
    private final float[] mRotMatrix = new float[16];

    // State. A frame reads one immutable snapshot for its entire render pass.
    private final RenderTargetRole mRenderTargetRole;
    private final AtomicReference<RendererState> mState;
    private final Object mStateLock = new Object();
    private VirtualSensorGeometry.Calculation mDynamicGeometry;
    private RenderTargetGeometry.Calculation mTargetGeometry;
    private volatile boolean mReleased = false;
    private volatile boolean mReleaseRequested = false;
    private boolean mInitialized = false;
    private volatile int mSurfaceWidth = 0;
    private volatile int mSurfaceHeight = 0;
    private long mFrameCount = 0;
    private long mCachedGeometryGeneration = -1L;
    private long mCachedTargetGeometryGeneration = -1L;
    private long mCachedAspectLayoutGeneration = -1L;
    private int mCachedRawTargetWidth = -1;
    private int mCachedRawTargetHeight = -1;
    private int mCachedAspectTargetWidth = -1;
    private int mCachedAspectTargetHeight = -1;
    private VideoAspectLayout.Layout mAspectLayout;
    private RenderTargetRole mCachedTargetRole;
    private int mFramesSinceSurfaceSizeCheck;
    private int mConsecutiveSwapFailures;
    private static final int SURFACE_SIZE_RECHECK_INTERVAL = 30;

    // Thread
    private HandlerThread mGLThread;
    private Handler mGLHandler;

    // Tag for logging
    private final String mTag;

    // Geometry buffers
    private FloatBuffer mVertexBuffer;
    private FloatBuffer mTexCoordBuffer;

    // Reusable capture buffer (avoid per-frame allocation)
    private ByteBuffer mCaptureBuffer;
    private int mCaptureBufferSize;
    private String mLastAspectDiagnostic;
    private long mLastAspectStateGeneration = -1L;
    private int mLastAspectRawTargetWidth = -1;
    private int mLastAspectRawTargetHeight = -1;
    private RenderTargetRole mLastAspectRole;
    private long mLastRenderStateDiagnosticGeneration = -1L;
    private final int[] mEglWidth = new int[1];
    private final int[] mEglHeight = new int[1];
    private final FrameTaskCoalescer mFrameCoalescer = new FrameTaskCoalescer();
    private final Runnable mRenderRunnable = this::runCoalescedFrame;

    // Shader sources, vertices, and tex coords shared via GLHelper

    /**
     * 创建 GL 旋转渲染器。
     *
     * @param targetSurface 渲染目标 Surface（预览或 ImageReader 的 Surface）
     * @param tag           日志标识
     */
    public GLVideoRenderer(Surface targetSurface, String tag) {
        this(targetSurface, tag, RenderTargetRole.PREVIEW);
    }

    /** Create a renderer with an explicit consumer geometry role. */
    public GLVideoRenderer(Surface targetSurface, String tag, RenderTargetRole role) {
        mTag = tag;
        mRenderTargetRole = role == null ? RenderTargetRole.PREVIEW : role;
        mState = new AtomicReference<>(RendererState.initial(mRenderTargetRole));
        mTargetSurface = targetSurface;
        Matrix.setIdentityM(mRotMatrix, 0);
        Matrix.setIdentityM(mSTMatrix, 0);
        Matrix.setIdentityM(mCropMatrix, 0);
        Matrix.setIdentityM(mDynamicTextureMatrix, 0);

        mGLThread = new HandlerThread("GLRenderer-" + tag);
        mGLThread.start();
        mGLHandler = new Handler(mGLThread.getLooper());

        CountDownLatch latch = new CountDownLatch(1);
        mGLHandler.post(() -> {
            try {
                initEGL(targetSurface);
                initGL();
                mInitialized = true;
                LogUtil.log("【CS】【GL】" + mTag + " 初始化成功");
            } catch (Exception e) {
                LogUtil.log("【CS】【GL】" + mTag + " 初始化失败: " + e);
                mInitialized = false;
            }
            latch.countDown();
        });

        try {
            if (!latch.await(3000, TimeUnit.MILLISECONDS)) {
                LogUtil.log("【CS】【GL】" + mTag + " 初始化超时");
            }
        } catch (InterruptedException e) {
            LogUtil.log("【CS】【GL】" + mTag + " 初始化被中断");
        }
    }

    public boolean isInitialized() {
        return mInitialized && !mReleased && !mReleaseRequested;
    }

    /**
     * 获取输入 Surface，供 MediaPlayer.setSurface() 使用。
     */
    public Surface getInputSurface() {
        return mInputSurface;
    }

    public int getSurfaceWidth() {
        return mSurfaceWidth;
    }

    public int getSurfaceHeight() {
        return mSurfaceHeight;
    }

    /**
     * 设置旋转角度（0/90/180/270），实时生效。
     */
    public void setRotation(int degrees) {
        int normalized = ((degrees % 360) + 360) % 360;
        synchronized (mStateLock) {
            mState.set(mState.get().withRotation(normalized));
        }
    }

    public int getRotation() {
        return mState.get().rotationDegrees;
    }

    public RendererState getState() {
        return mState.get();
    }

    /** Atomically publish all renderer inputs for the next frame. */
    public void applyState(RendererState state) {
        if (state == null || state.role != mRenderTargetRole) {
            return;
        }
        synchronized (mStateLock) {
            RendererState current = mState.get();
            // A candidate is derived from a snapshot read outside this lock.
            // Strict monotonicity prevents a stale config candidate with the
            // same generation from overwriting a concurrent source-size update.
            if (state.generation > current.generation) {
                mState.set(state);
            }
        }
    }

    /** Set the dimensions reported by the active media playback chain. */
    public void setSourceSize(int width, int height) {
        if (width > 0 && height > 0) {
            synchronized (mStateLock) {
                mState.set(mState.get().withSourceSize(width, height));
            }
        }
    }

    public int getSourceWidth() {
        return mState.get().sourceWidth;
    }

    public int getSourceHeight() {
        return mState.get().sourceHeight;
    }

    /** Set Dynamic (default), FIT, or CROP rendering. */
    public void setAspectMode(String aspectMode) {
        synchronized (mStateLock) {
            mState.set(mState.get().withAspectMode(aspectMode));
        }
    }

    public String getAspectMode() {
        return mState.get().aspectMode;
    }

    public RenderTargetRole getRenderTargetRole() {
        return mRenderTargetRole;
    }

    /** Update host geometry used for preview logical-target orientation. */
    public void setHostWindowGeometry(HostWindowGeometry.Snapshot geometry) {
        synchronized (mStateLock) {
            mState.set(mState.get().withHostWindowGeometry(geometry));
        }
    }

    /** Set the requested camera footprint; zero means use the raw EGL target. */
    public void setRequestedFootprint(int width, int height) {
        synchronized (mStateLock) {
            mState.set(mState.get().withRequestedFootprint(width, height));
        }
    }

    /** Record the Camera1 producer transform for low-noise diagnostics. */
    public void setProducerTransformFlags(int transformFlags) {
        synchronized (mStateLock) {
            mState.set(mState.get().withProducerTransformFlags(transformFlags));
        }
    }

    /** Apply the active preset shortcut's decoded-source viewport anchor. */
    public void setViewportState(ConfigManager.ActiveBinding activeBinding, int stepPercent) {
        synchronized (mStateLock) {
            mState.set(mState.get().withViewportState(activeBinding, stepPercent));
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        Handler handler = mGLHandler;
        if (mReleased || mReleaseRequested || !mInitialized || handler == null)
            return;
        if (mFrameCoalescer.onFrameAvailable() && !handler.post(mRenderRunnable)) {
            mFrameCoalescer.cancel();
        }
    }

    private void runCoalescedFrame() {
        mFrameCoalescer.beginTask();
        if (!mReleased && !mReleaseRequested && mInitialized) {
            drawFrame();
        }
        boolean followUp = mFrameCoalescer.finishTask();
        if (followUp && !mReleased && !mReleaseRequested && mInitialized) {
            Handler handler = mGLHandler;
            if (handler == null || !handler.post(mRenderRunnable)) {
                mFrameCoalescer.cancel();
            }
        } else if (mReleased || mReleaseRequested || !mInitialized) {
            mFrameCoalescer.cancel();
        }
    }

    private void drawFrame() {
        if (mReleased || mReleaseRequested || !mInitialized)
            return;
        // Bail out if the target surface has been destroyed to avoid native crash
        if (mTargetSurface != null && !mTargetSurface.isValid()) {
            LogUtil.log("【CS】【GL】" + mTag + " target surface 已失效，停止渲染");
            mReleaseRequested = true;
            return;
        }
        long startNanos = System.nanoTime();
        try {
            RendererState state = mState.get();
            logRenderStateIfChanged(state);
            renderToBackBuffer(mRenderTargetRole, state);
            if (!EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface)) {
                int err = EGL14.eglGetError();
                mConsecutiveSwapFailures++;
                LogUtil.log("【CS】【GL】" + mTag + " eglSwapBuffers 失败, err=" + err);
                if (err == EGL14.EGL_BAD_SURFACE || err == EGL14.EGL_BAD_NATIVE_WINDOW) {
                    mReleaseRequested = true;
                } else if (mConsecutiveSwapFailures >= 3) {
                    LogUtil.log("【CS】【GL】" + mTag
                            + " eglSwapBuffers 连续失败，停止后续渲染");
                    mReleaseRequested = true;
                }
            } else {
                mConsecutiveSwapFailures = 0;
            }
        } catch (Exception e) {
            LogUtil.log("【CS】【GL】" + mTag + " drawFrame 异常: " + e);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            if (durationMs > 50L) {
                LogUtil.log("[VFC][GLSlowFrame] renderer=" + mTag
                        + " durationMs=" + durationMs
                        + " surface=" + mSurfaceWidth + "x" + mSurfaceHeight
                        + " stateGeneration=" + mState.get().generation
                        + " severe=" + (durationMs > 200L));
            }
        }
    }

    /**
     * 渲染一帧到后缓冲（不调用 eglSwapBuffers）。
     * drawFrame() 和 captureFrameRaw() 共用此方法。
     */
    private void renderToBackBuffer(RenderTargetRole renderTargetRole) {
        renderToBackBuffer(renderTargetRole, mState.get());
    }

    private void renderToBackBuffer(RenderTargetRole renderTargetRole, RendererState state) {
        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            return;
        }

        RendererState frameState = state == null ? mState.get() : state;
        RenderTargetRole effectiveRole = renderTargetRole == null
                ? mRenderTargetRole : renderTargetRole;
        mInputSurfaceTexture.updateTexImage();
        mInputSurfaceTexture.getTransformMatrix(mSTMatrix);

        refreshSurfaceSizeIfNeeded();
        int rawTargetWidth = mSurfaceWidth;
        int rawTargetHeight = mSurfaceHeight;
        if (rawTargetWidth > 0 && rawTargetHeight > 0) {
            // The viewport always remains the actual EGL buffer dimensions.
            GLES20.glViewport(0, 0, rawTargetWidth, rawTargetHeight);
        }

        RenderTargetGeometry.Calculation targetGeometry = ensureTargetGeometry(
                rawTargetWidth, rawTargetHeight, effectiveRole, frameState);
        Matrix.setIdentityM(mRotMatrix, 0);
        Matrix.setIdentityM(mCropMatrix, 0);
        if (!ConfigManager.ASPECT_MODE_DYNAMIC.equals(frameState.aspectMode)) {
            Matrix.setIdentityM(mDynamicTextureMatrix, 0);
        }
        if (ConfigManager.ASPECT_MODE_DYNAMIC.equals(frameState.aspectMode)) {
            ensureDynamicGeometry(rawTargetWidth, rawTargetHeight, targetGeometry,
                    effectiveRole, frameState);
        } else if (rawTargetWidth > 0 && rawTargetHeight > 0
                && frameState.sourceWidth > 0 && frameState.sourceHeight > 0) {
            Matrix.setIdentityM(mDynamicTextureMatrix, 0);
            int logicalTargetWidth = targetGeometry == null
                    ? rawTargetWidth : targetGeometry.logicalTargetWidth;
            int logicalTargetHeight = targetGeometry == null
                    ? rawTargetHeight : targetGeometry.logicalTargetHeight;
            VideoAspectLayout.Layout layout = ensureAspectLayout(
                    frameState, logicalTargetWidth, logicalTargetHeight);
            Matrix.scaleM(mRotMatrix, 0, layout.scaleX, layout.scaleY, 1.0f);
            if (frameState.rotationDegrees != 0) {
                Matrix.rotateM(mRotMatrix, 0, -frameState.rotationDegrees, 0, 0, 1.0f);
            }
            logAspectIfChanged(targetGeometry, layout, null, null, frameState);
        } else if (frameState.rotationDegrees != 0) {
            Matrix.rotateM(mRotMatrix, 0, -frameState.rotationDegrees, 0, 0, 1.0f);
        }

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(mProgram);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId);

        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);
        if (muDynamicTextureMatrixHandle >= 0) {
            GLES20.glUniformMatrix4fv(muDynamicTextureMatrixHandle, 1, false,
                    mDynamicTextureMatrix, 0);
        }
        GLES20.glUniformMatrix4fv(muCropMatrixHandle, 1, false, mCropMatrix, 0);
        GLES20.glUniformMatrix4fv(muRotMatrixHandle, 1, false, mRotMatrix, 0);

        io.github.alanlaw.vfc.utils.ScreenColorDetector detector =
                io.github.alanlaw.vfc.utils.ScreenColorDetector.INSTANCE;
        float ambientRed = detector.getAmbientRed();
        float ambientGreen = detector.getAmbientGreen();
        float ambientBlue = detector.getAmbientBlue();
        float ambientIntensity = detector.getAmbientIntensity();
        if (muAmbientColorHandle >= 0) {
            GLES20.glUniform3f(muAmbientColorHandle, ambientRed, ambientGreen, ambientBlue);
        }
        if (muAmbientIntensityHandle >= 0) {
            GLES20.glUniform1f(muAmbientIntensityHandle, ambientIntensity);
        }

        mVertexBuffer.position(0);
        GLES20.glEnableVertexAttribArray(maPositionHandle);
        GLES20.glVertexAttribPointer(maPositionHandle, 2, GLES20.GL_FLOAT, false, 0, mVertexBuffer);

        mTexCoordBuffer.position(0);
        GLES20.glEnableVertexAttribArray(maTextureHandle);
        GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false, 0, mTexCoordBuffer);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        mFrameCount++;
    }

    private void refreshSurfaceSizeIfNeeded() {
        if (mSurfaceWidth > 0 && mSurfaceHeight > 0
                && ++mFramesSinceSurfaceSizeCheck < SURFACE_SIZE_RECHECK_INTERVAL) {
            return;
        }
        mFramesSinceSurfaceSizeCheck = 0;
        EGL14.eglQuerySurface(mEGLDisplay, mEGLSurface, EGL14.EGL_WIDTH, mEglWidth, 0);
        EGL14.eglQuerySurface(mEGLDisplay, mEGLSurface, EGL14.EGL_HEIGHT, mEglHeight, 0);
        int nextWidth = mEglWidth[0];
        int nextHeight = mEglHeight[0];
        if (nextWidth > 0 && nextHeight > 0
                && (mSurfaceWidth != nextWidth || mSurfaceHeight != nextHeight)) {
            mSurfaceWidth = nextWidth;
            mSurfaceHeight = nextHeight;
            mCachedRawTargetWidth = -1;
            mCachedRawTargetHeight = -1;
            mCachedGeometryGeneration = -1L;
            mCachedTargetGeometryGeneration = -1L;
            mCachedAspectLayoutGeneration = -1L;
            mDynamicGeometry = null;
            mTargetGeometry = null;
            mAspectLayout = null;
        }
    }

    private VideoAspectLayout.Layout ensureAspectLayout(RendererState state,
            int logicalTargetWidth, int logicalTargetHeight) {
        if (mAspectLayout == null
                || mCachedAspectLayoutGeneration != state.generation
                || mCachedAspectTargetWidth != logicalTargetWidth
                || mCachedAspectTargetHeight != logicalTargetHeight) {
            mAspectLayout = VideoAspectLayout.calculate(
                    state.sourceWidth, state.sourceHeight,
                    logicalTargetWidth, logicalTargetHeight,
                    state.rotationDegrees, state.aspectMode);
            mCachedAspectLayoutGeneration = state.generation;
            mCachedAspectTargetWidth = logicalTargetWidth;
            mCachedAspectTargetHeight = logicalTargetHeight;
        }
        return mAspectLayout;
    }

    private RenderTargetGeometry.Calculation ensureTargetGeometry(int rawTargetWidth,
            int rawTargetHeight, RenderTargetRole role, RendererState state) {
        if (mTargetGeometry == null
                || mCachedTargetGeometryGeneration != state.generation
                || mCachedRawTargetWidth != rawTargetWidth
                || mCachedRawTargetHeight != rawTargetHeight
                || mCachedTargetRole != role) {
            mTargetGeometry = RenderTargetGeometry.calculate(rawTargetWidth, rawTargetHeight,
                    state.hostWindowGeometry, role);
            mCachedTargetGeometryGeneration = state.generation;
            mCachedRawTargetWidth = rawTargetWidth;
            mCachedRawTargetHeight = rawTargetHeight;
            mCachedTargetRole = role;
        }
        return mTargetGeometry;
    }

    private void ensureDynamicGeometry(int rawTargetWidth, int rawTargetHeight,
            RenderTargetGeometry.Calculation targetGeometry, RenderTargetRole role,
            RendererState state) {
        if (mDynamicGeometry != null
                && mCachedGeometryGeneration == state.generation
                && mCachedRawTargetWidth == rawTargetWidth
                && mCachedRawTargetHeight == rawTargetHeight
                && mDynamicGeometry.role == role) {
            return;
        }

        // Dynamic v3 uses the actual requested output aspect. Host-window
        // compensation is deliberately kept in VideoAspectLayout's FIT/CROP
        // target calculation; it must not swap the producer's requested
        // Camera buffer dimensions for Dynamic sampling.
        int requestedWidth = role == RenderTargetRole.PREVIEW
                && state.requestedWidth > 0 ? state.requestedWidth : rawTargetWidth;
        int requestedHeight = role == RenderTargetRole.PREVIEW
                && state.requestedHeight > 0 ? state.requestedHeight : rawTargetHeight;
        mDynamicGeometry = VirtualSensorGeometry.calculate(
                state.sourceWidth, state.sourceHeight,
                requestedWidth, requestedHeight, state.rotationDegrees,
                state.anchorU, state.anchorV, state.zoom, role);
        VirtualSensorGeometry.buildDynamicTextureMatrix(mDynamicGeometry,
                mDynamicTextureMatrix);
        Matrix.setIdentityM(mCropMatrix, 0);
        Matrix.setIdentityM(mRotMatrix, 0);
        mCachedGeometryGeneration = state.generation;
        mCachedRawTargetWidth = rawTargetWidth;
        mCachedRawTargetHeight = rawTargetHeight;
        logDynamicAspectIfChanged(rawTargetWidth, rawTargetHeight, targetGeometry, role, state);
    }

    private void logDynamicAspectIfChanged(int rawTargetWidth, int rawTargetHeight,
            RenderTargetGeometry.Calculation targetGeometry, RenderTargetRole role,
            RendererState state) {
        VirtualSensorGeometry.Calculation geometry = mDynamicGeometry;
        if (geometry == null) {
            return;
        }
        HostWindowGeometry.Snapshot host = state.hostWindowGeometry;
        int hostWidth = host == null ? 0 : host.getWidth();
        int hostHeight = host == null ? 0 : host.getHeight();
        int displayRotation = host == null
                ? HostWindowGeometry.UNKNOWN_ROTATION : host.getDisplayRotation();
        int logicalTargetWidth = targetGeometry == null
                ? rawTargetWidth : targetGeometry.logicalTargetWidth;
        int logicalTargetHeight = targetGeometry == null
                ? rawTargetHeight : targetGeometry.logicalTargetHeight;
        boolean compensated = targetGeometry != null && targetGeometry.orientationCompensated;
        String diagnostic = String.format(Locale.US,
                "role=%s|renderer=%s|source=%dx%d|rawTarget=%dx%d|host=%dx%d"
                        + "|logicalTarget=%dx%d|displayRotation=%d|videoRotation=%d|mode=%s"
                        + "|sourceAspect=%.5f|targetAspect=%.5f|scaleX=1.00000|scaleY=1.00000"
                        + "|baseViewport=%.2fx%.2f|effectiveViewport=%.2fx%.2f"
                        + "|zoom=%.4f|sourceAnchor=%.5f,%.5f|effectiveAnchor=%.5f,%.5f"
                        + "|transformFlags=0x%02x|orientationCompensated=%s",
                role, mTag, state.sourceWidth, state.sourceHeight,
                rawTargetWidth, rawTargetHeight, hostWidth, hostHeight,
                logicalTargetWidth, logicalTargetHeight, displayRotation, state.rotationDegrees,
                state.aspectMode, geometry.sourceAspect, geometry.targetAspect,
                geometry.baseViewportWidth, geometry.baseViewportHeight,
                geometry.viewportWidth, geometry.viewportHeight, geometry.zoom,
                geometry.sourceAnchorU, geometry.sourceAnchorV,
                geometry.logicalAnchorU, geometry.logicalAnchorV,
                state.producerTransformFlags, compensated);
        if (!diagnostic.equals(mLastAspectDiagnostic)) {
            mLastAspectDiagnostic = diagnostic;
            LogUtil.log("[VFC][Aspect] " + diagnostic);
        }
    }

    private void logRenderStateIfChanged(RendererState state) {
        if (state == null || state.generation == mLastRenderStateDiagnosticGeneration) {
            return;
        }
        mLastRenderStateDiagnosticGeneration = state.generation;
        LogUtil.log("[VFC][RenderState] renderer=" + mTag
                + " generation=" + state.generation
                + " source=" + state.sourceWidth + "x" + state.sourceHeight
                + " rotation=" + state.rotationDegrees
                + " aspect=" + state.aspectMode
                + " anchor=" + state.anchorU + "," + state.anchorV
                + " zoom=" + state.zoom
                + " requestedOutput=" + state.requestedWidth + "x" + state.requestedHeight);
    }

    /**
     * 截取当前渲染帧为 Bitmap（使用渲染器当前旋转角度）。
     */
    public Bitmap captureFrame(int width, int height) {
        return captureFrameWithRotation(width, height, -1);
    }

    /**
     * 截取当前帧并应用指定旋转角度（不影响预览 Surface）。
     * 用于 WhatsApp YUV 帧生成：预览渲染器旋转为 0°（本机自拍正确），
     * 但 YUV 截帧需要应用 video_rotation_offset 才能让对方看到正确方向。
     *
     * @param rotationDegrees 要应用的旋转角度，-1 表示使用渲染器当前旋转
     */
    public Bitmap captureFrameWithRotation(int width, int height, int rotationDegrees) {
        if (!isInitialized() || mReleased)
            return null;
        final Bitmap[] result = { null };
        CountDownLatch latch = new CountDownLatch(1);
        mGLHandler.post(() -> {
            try {
                RendererState captureState = mState.get();
                if (rotationDegrees >= 0) {
                    int normalized = ((rotationDegrees % 360) + 360) % 360;
                    captureState = captureState.withRotation(normalized);
                }

                // 渲染到后缓冲（不 swap，不影响预览显示）
                renderToBackBuffer(RenderTargetRole.CAPTURE, captureState);

                int bufSize = width * height * 4;
                if (mCaptureBuffer == null || mCaptureBufferSize != bufSize) {
                    mCaptureBuffer = ByteBuffer.allocateDirect(bufSize);
                    mCaptureBuffer.order(ByteOrder.nativeOrder());
                    mCaptureBufferSize = bufSize;
                }
                mCaptureBuffer.clear();
                GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mCaptureBuffer);
                mCaptureBuffer.rewind();

                Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bmp.copyPixelsFromBuffer(mCaptureBuffer);

                // glReadPixels 输出的图像是上下颠倒的, 翻转
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.postScale(1, -1);
                result[0] = Bitmap.createBitmap(bmp, 0, 0, width, height, matrix, true);

                bmp.recycle();
                // 不 swap — 不影响预览 Surface 的显示内容
            } catch (Exception e) {
                LogUtil.log("【CS】【GL】captureFrame 失败: " + e);
            }
            latch.countDown();
        });
        try {
            latch.await(2000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
        }
        return result[0];
    }

    private void initEGL(Surface targetSurface) {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("eglGetDisplay failed");
        }

        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            throw new RuntimeException("eglInitialize failed");
        }

        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_NONE
        };

        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)) {
            throw new RuntimeException("eglChooseConfig failed");
        }
        if (numConfigs[0] == 0) {
            throw new RuntimeException("No matching EGL config");
        }

        int[] contextAttribs = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0);
        if (mEGLContext == EGL14.EGL_NO_CONTEXT) {
            throw new RuntimeException("eglCreateContext failed");
        }

        int[] surfaceAttribs = { EGL14.EGL_NONE };
        mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, configs[0], targetSurface, surfaceAttribs, 0);
        if (mEGLSurface == EGL14.EGL_NO_SURFACE) {
            int eglError = EGL14.eglGetError();
            throw new RuntimeException("eglCreateWindowSurface failed with error: " + eglError);
        }

        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }

        int[] width = new int[1];
        int[] height = new int[1];
        EGL14.eglQuerySurface(mEGLDisplay, mEGLSurface, EGL14.EGL_WIDTH, width, 0);
        EGL14.eglQuerySurface(mEGLDisplay, mEGLSurface, EGL14.EGL_HEIGHT, height, 0);
        mSurfaceWidth = width[0];
        mSurfaceHeight = height[0];
        LogUtil.log("【CS】【GL】EGL Surface dimensions initialized: " + width[0] + "x" + height[0]);

        // 如果 EGL Surface 尺寸太小（例如 SurfaceHolder buffer 尚未分配），视为初始化失败
        if (width[0] <= 1 && height[0] <= 1) {
            throw new RuntimeException(
                    "EGL Surface too small (" + width[0] + "x" + height[0] + "), skipping GL renderer");
        }
    }

    private void initGL() {
        int vertexShader = GLHelper.loadShader(GLES20.GL_VERTEX_SHADER, GLHelper.VERTEX_SHADER);
        int fragmentShader = GLHelper.loadShader(GLES20.GL_FRAGMENT_SHADER, GLHelper.FRAGMENT_SHADER);
        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, vertexShader);
        GLES20.glAttachShader(mProgram, fragmentShader);
        GLES20.glLinkProgram(mProgram);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(mProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            String error = GLES20.glGetProgramInfoLog(mProgram);
            GLES20.glDeleteProgram(mProgram);
            throw new RuntimeException("Program link failed: " + error);
        }

        maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
        muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
        muDynamicTextureMatrixHandle = GLES20.glGetUniformLocation(mProgram,
                "uDynamicTextureMatrix");
        muCropMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uCropMatrix");
        muRotMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uRotMatrix");
        muAmbientColorHandle = GLES20.glGetUniformLocation(mProgram, "uAmbientColor");
        muAmbientIntensityHandle = GLES20.glGetUniformLocation(mProgram, "uAmbientIntensity");

        // Create external OES texture
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        mTextureId = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        // Create input SurfaceTexture bound to the external texture
        mInputSurfaceTexture = new SurfaceTexture(mTextureId);
        mInputSurfaceTexture.setOnFrameAvailableListener(this);
        mInputSurface = new Surface(mInputSurfaceTexture);

        mVertexBuffer = GLHelper.createFloatBuffer(GLHelper.VERTICES);
        mTexCoordBuffer = GLHelper.createFloatBuffer(GLHelper.TEX_COORDS);
    }

    /**
     * 释放所有 GL/EGL 资源。调用后该渲染器不可再使用。
     */
    public void release() {
        if (mReleased)
            return;
        mReleaseRequested = true;
        mFrameCoalescer.cancel();
        CountDownLatch releaseLatch = new CountDownLatch(1);
        if (mGLHandler != null) {
            mGLHandler.post(() -> {
                try {
                    releaseInternal();
                } finally {
                    releaseLatch.countDown();
                }
            });
        } else {
            releaseLatch.countDown();
        }

        try {
            releaseLatch.await(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        if (mGLThread != null) {
            mGLThread.quitSafely();
            try {
                mGLThread.join(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        mGLHandler = null;
        mGLThread = null;
    }

    private void releaseInternal() {
        if (mReleased) {
            return;
        }
        mReleased = true;
        mInitialized = false;
        mFrameCoalescer.cancel();
        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }
        if (mInputSurfaceTexture != null) {
            mInputSurfaceTexture.release();
            mInputSurfaceTexture = null;
        }
        if (mProgram != 0) {
            GLES20.glDeleteProgram(mProgram);
            mProgram = 0;
        }
        if (mTextureId != 0) {
            GLES20.glDeleteTextures(1, new int[] { mTextureId }, 0);
            mTextureId = 0;
        }
        if (mEGLSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
            mEGLSurface = EGL14.EGL_NO_SURFACE;
        }
        if (mEGLContext != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
            mEGLContext = EGL14.EGL_NO_CONTEXT;
        }
        if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglTerminate(mEGLDisplay);
            mEGLDisplay = EGL14.EGL_NO_DISPLAY;
        }
    }

    private void logAspectIfChanged(RenderTargetGeometry.Calculation geometry,
            VideoAspectLayout.Layout layout,
            VirtualSensorGeometry.Calculation dynamicGeometry,
            VirtualSensorGeometry.NormalizedRect dynamicCrop,
            RendererState state) {
        if (geometry == null || layout == null || state == null) {
            return;
        }
        if (mLastAspectStateGeneration == state.generation
                && mLastAspectRawTargetWidth == geometry.rawTargetWidth
                && mLastAspectRawTargetHeight == geometry.rawTargetHeight
                && mLastAspectRole == geometry.role) {
            return;
        }
        float sourceAspect = dynamicGeometry == null
                ? layout.sourceAspect : dynamicGeometry.sourceAspect;
        float targetAspect = dynamicGeometry == null
                ? layout.targetAspect : dynamicGeometry.targetAspect;
        float scaleX = dynamicGeometry == null ? layout.scaleX : 1.0f;
        float scaleY = dynamicGeometry == null ? layout.scaleY : 1.0f;
        String logicalAnchor = dynamicGeometry == null
                ? "-"
                : String.format(Locale.US, "%.5f,%.5f",
                        dynamicGeometry.logicalAnchorU, dynamicGeometry.logicalAnchorV);
        String sourceAnchor = String.format(Locale.US, "%.5f,%.5f",
                state.anchorU, state.anchorV);
        String crop = dynamicCrop == null ? "-" : dynamicCrop.toString();
        String diagnostic = String.format(Locale.US,
                "role=%s|renderer=%s|source=%dx%d|rawTarget=%dx%d|host=%dx%d|logicalTarget=%dx%d"
                        + "|displayRotation=%d|videoRotation=%d|mode=%s|sourceAspect=%.5f"
                        + "|targetAspect=%.5f|scaleX=%.5f|scaleY=%.5f|orientationCompensated=%s"
                        + "|activePreset=%s|activeShortcut=%s"
                        + "|sourceAnchor=%s|logicalAnchor=%s|crop=%s|step=%d",
                geometry.role,
                mTag,
                state.sourceWidth, state.sourceHeight,
                geometry.rawTargetWidth, geometry.rawTargetHeight,
                geometry.hostWidth, geometry.hostHeight,
                geometry.logicalTargetWidth, geometry.logicalTargetHeight,
                geometry.displayRotation, state.rotationDegrees, state.aspectMode,
                sourceAspect, targetAspect,
                scaleX, scaleY,
                geometry.orientationCompensated,
                state.activePresetId, state.activeShortcutKey,
                sourceAnchor, logicalAnchor, crop, state.viewportMoveStepPercent);
        if (!diagnostic.equals(mLastAspectDiagnostic)) {
            mLastAspectDiagnostic = diagnostic;
            LogUtil.log("[VFC][Aspect] " + diagnostic);
        }
        mLastAspectStateGeneration = state.generation;
        mLastAspectRawTargetWidth = geometry.rawTargetWidth;
        mLastAspectRawTargetHeight = geometry.rawTargetHeight;
        mLastAspectRole = geometry.role;
    }

    // ---- Static helpers for managing multiple renderers ----

    /**
     * 安全创建渲染器，失败时返回 null 而非抛异常。
     */
    public static GLVideoRenderer createSafely(Surface targetSurface, String tag) {
        return createSafely(targetSurface, tag, RenderTargetRole.PREVIEW);
    }

    public static GLVideoRenderer createSafely(Surface targetSurface, String tag,
            RenderTargetRole role) {
        if (targetSurface == null || !targetSurface.isValid()) {
            LogUtil.log("【CS】【GL】" + tag + " 目标 Surface 无效，跳过创建");
            return null;
        }
        try {
            GLVideoRenderer renderer = new GLVideoRenderer(targetSurface, tag, role);
            if (renderer.isInitialized()) {
                return renderer;
            } else {
                renderer.release();
                LogUtil.log("【CS】【GL】" + tag + " 初始化失败，回退到直接播放");
                return null;
            }
        } catch (Exception e) {
            LogUtil.log("【CS】【GL】" + tag + " 创建异常: " + e);
            return null;
        }
    }

    /**
     * 安全释放渲染器。
     */
    public static void releaseSafely(GLVideoRenderer renderer) {
        if (renderer != null) {
            try {
                renderer.release();
            } catch (Exception e) {
                LogUtil.log("【CS】【GL】释放渲染器异常: " + e);
            }
        }
    }
}
