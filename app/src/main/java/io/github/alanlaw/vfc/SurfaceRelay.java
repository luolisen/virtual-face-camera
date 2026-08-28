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

/**
 * A robust fallback for when targetSurface (usually SurfaceTexture-backed)
 * rejects EGL Window creation. This creates an intermediate SurfaceTexture
 * for MediaPlayer, then blits it to targetSurface using an internal Pbuffer
 * EGL context + eglSwapBuffers (if possible) or Canvas.
 */
public class SurfaceRelay implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "SurfaceRelay";

    // EGL
    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface mEGLPbufferSurface = EGL14.EGL_NO_SURFACE;
    private EGLSurface mEGLWindowSurface = EGL14.EGL_NO_SURFACE;

    // GL
    private int mProgram;
    private int mTextureId;
    private int maPositionHandle;
    private int maTextureHandle;
    private int muSTMatrixHandle;
    private int muDynamicTextureMatrixHandle;
    private int muCropMatrixHandle;
    private int muRotMatrixHandle;

    // Input/Output
    private SurfaceTexture mInputSurfaceTexture;
    private Surface mInputSurface;
    private Surface mTargetSurface;

    // Matrices
    private final float[] mSTMatrix = new float[16];
    private final float[] mDynamicTextureMatrix = new float[16];
    private final float[] mCropMatrix = new float[16];
    private final float[] mRotMatrix = new float[16];

    // State. One immutable snapshot is consumed per frame.
    private final RenderTargetRole mRenderTargetRole;
    private final AtomicReference<RendererState> mState;
    private final Object mStateLock = new Object();
    private VirtualSensorGeometry.Calculation mDynamicGeometry;
    private RenderTargetGeometry.Calculation mTargetGeometry;
    private volatile boolean mReleased = false;
    private volatile boolean mReleaseRequested = false;
    private boolean mInitialized = false;
    private String mLastAspectDiagnostic;
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
    private final int[] mSurfaceWidth = new int[1];
    private final int[] mSurfaceHeight = new int[1];
    private long mLastAspectStateGeneration = -1L;
    private int mLastAspectRawTargetWidth = -1;
    private int mLastAspectRawTargetHeight = -1;
    private RenderTargetRole mLastAspectRole;
    private long mLastRenderStateDiagnosticGeneration = -1L;
    private final FrameTaskCoalescer mFrameCoalescer = new FrameTaskCoalescer();
    private final Runnable mRenderRunnable = this::runCoalescedFrame;

    // Shader sources, vertices, and tex coords shared via GLHelper

    public SurfaceRelay(Surface targetSurface, String tag) {
        this(targetSurface, tag, RenderTargetRole.PREVIEW);
    }

    public SurfaceRelay(Surface targetSurface, String tag, RenderTargetRole role) {
        mTag = tag;
        mRenderTargetRole = role == null ? RenderTargetRole.PREVIEW : role;
        mState = new AtomicReference<>(RendererState.initial(mRenderTargetRole));
        mTargetSurface = targetSurface;
        Matrix.setIdentityM(mRotMatrix, 0);
        Matrix.setIdentityM(mSTMatrix, 0);
        Matrix.setIdentityM(mDynamicTextureMatrix, 0);
        Matrix.setIdentityM(mCropMatrix, 0);

        mGLThread = new HandlerThread("GLRelay-" + tag);
        mGLThread.start();
        mGLHandler = new Handler(mGLThread.getLooper());

        CountDownLatch latch = new CountDownLatch(1);
        mGLHandler.post(() -> {
            try {
                initEGL();
                initGL();
                mInitialized = true;
                LogUtil.log("【CS】【Relay】" + mTag + " 初始化成功，提供中间 Surface");
            } catch (Exception e) {
                LogUtil.log("【CS】【Relay】" + mTag + " 初始化失败: " + e);
                mInitialized = false;
            }
            latch.countDown();
        });

        try {
            if (!latch.await(3000, TimeUnit.MILLISECONDS)) {
                LogUtil.log("【CS】【Relay】" + mTag + " 初始化超时");
            }
        } catch (InterruptedException e) {
            LogUtil.log("【CS】【Relay】" + mTag + " 初始化被中断");
        }
    }

    public boolean isInitialized() {
        return mInitialized && !mReleased && !mReleaseRequested;
    }

    public Surface getInputSurface() {
        return mInputSurface;
    }

    public void setRotation(int degrees) {
        int normalized = ((degrees % 360) + 360) % 360;
        synchronized (mStateLock) {
            mState.set(mState.get().withRotation(normalized));
        }
    }

    public void setSourceSize(int width, int height) {
        if (width > 0 && height > 0) {
            synchronized (mStateLock) {
                mState.set(mState.get().withSourceSize(width, height));
            }
        }
    }

    public void setAspectMode(String aspectMode) {
        synchronized (mStateLock) {
            mState.set(mState.get().withAspectMode(aspectMode));
        }
    }

    public void setHostWindowGeometry(HostWindowGeometry.Snapshot geometry) {
        synchronized (mStateLock) {
            mState.set(mState.get().withHostWindowGeometry(geometry));
        }
    }

    /** Set the requested camera footprint; zero means use the raw target. */
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
        if (mTargetSurface != null && !mTargetSurface.isValid()) {
            LogUtil.log("【CS】【Relay】" + mTag + " target surface 已失效，停止渲染");
            mReleaseRequested = true;
            return;
        }
        long startNanos = System.nanoTime();
        try {
            if (!EGL14.eglMakeCurrent(mEGLDisplay,
                    mEGLWindowSurface != EGL14.EGL_NO_SURFACE ? mEGLWindowSurface : mEGLPbufferSurface,
                    mEGLWindowSurface != EGL14.EGL_NO_SURFACE ? mEGLWindowSurface : mEGLPbufferSurface, mEGLContext)) {
                return;
            }

            mInputSurfaceTexture.updateTexImage();
            mInputSurfaceTexture.getTransformMatrix(mSTMatrix);

            if (mEGLWindowSurface == EGL14.EGL_NO_SURFACE) {
                // Try to attach to target Surface
                int[] surfaceAttribs = { EGL14.EGL_NONE };
                EGLConfig[] configs = getEglConfigs();
                if (configs != null && configs.length > 0) {
                    mEGLWindowSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, configs[0], mTargetSurface,
                            surfaceAttribs, 0);
                    if (mEGLWindowSurface != EGL14.EGL_NO_SURFACE) {
                        mFramesSinceSurfaceSizeCheck = SURFACE_SIZE_RECHECK_INTERVAL;
                        EGL14.eglMakeCurrent(mEGLDisplay, mEGLWindowSurface, mEGLWindowSurface, mEGLContext);
                        LogUtil.log("【CS】【Relay】" + mTag + " late eglCreateWindowSurface 成功！");
                    } else {
                        int err = EGL14.eglGetError();
                        // LogUtil.log("【CS】【Relay】" + mTag + " eglCreateWindowSurface(late) 失败: " +
                        // err);
                        // Keep using PBuffer... No output to target though
                    }
                }
            }

            EGLSurface activeSurface = mEGLWindowSurface != EGL14.EGL_NO_SURFACE
                    ? mEGLWindowSurface : mEGLPbufferSurface;
            refreshSurfaceSizeIfNeeded(activeSurface);
            int rawTargetWidth = mSurfaceWidth[0];
            int rawTargetHeight = mSurfaceHeight[0];
            if (rawTargetWidth > 0 && rawTargetHeight > 0) {
                GLES20.glViewport(0, 0, rawTargetWidth, rawTargetHeight);
            }

            RendererState state = mState.get();
            logRenderStateIfChanged(state);
            RenderTargetGeometry.Calculation targetGeometry = ensureTargetGeometry(
                    rawTargetWidth, rawTargetHeight, mRenderTargetRole, state);
            Matrix.setIdentityM(mRotMatrix, 0);
            Matrix.setIdentityM(mCropMatrix, 0);
            if (!ConfigManager.ASPECT_MODE_DYNAMIC.equals(state.aspectMode)) {
                Matrix.setIdentityM(mDynamicTextureMatrix, 0);
            }
            if (ConfigManager.ASPECT_MODE_DYNAMIC.equals(state.aspectMode)) {
                ensureDynamicGeometry(rawTargetWidth, rawTargetHeight, targetGeometry,
                        mRenderTargetRole, state);
            } else if (rawTargetWidth > 0 && rawTargetHeight > 0
                    && state.sourceWidth > 0 && state.sourceHeight > 0) {
                Matrix.setIdentityM(mDynamicTextureMatrix, 0);
                int logicalTargetWidth = targetGeometry == null
                        ? rawTargetWidth : targetGeometry.logicalTargetWidth;
                int logicalTargetHeight = targetGeometry == null
                        ? rawTargetHeight : targetGeometry.logicalTargetHeight;
                VideoAspectLayout.Layout layout = ensureAspectLayout(
                        state, logicalTargetWidth, logicalTargetHeight);
                Matrix.scaleM(mRotMatrix, 0, layout.scaleX, layout.scaleY, 1.0f);
                if (state.rotationDegrees != 0) {
                    Matrix.rotateM(mRotMatrix, 0, -state.rotationDegrees, 0, 0, 1.0f);
                }
                logAspectIfChanged(targetGeometry, layout, null, null, state);
            } else if (state.rotationDegrees != 0) {
                Matrix.rotateM(mRotMatrix, 0, -state.rotationDegrees, 0, 0, 1.0f);
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

            mVertexBuffer.position(0);
            GLES20.glEnableVertexAttribArray(maPositionHandle);
            GLES20.glVertexAttribPointer(maPositionHandle, 2, GLES20.GL_FLOAT, false, 0, mVertexBuffer);

            mTexCoordBuffer.position(0);
            GLES20.glEnableVertexAttribArray(maTextureHandle);
            GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false, 0, mTexCoordBuffer);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            if (mEGLWindowSurface != EGL14.EGL_NO_SURFACE) {
                if (!EGL14.eglSwapBuffers(mEGLDisplay, mEGLWindowSurface)) {
                    int err = EGL14.eglGetError();
                    mConsecutiveSwapFailures++;
                    LogUtil.log("【CS】【Relay】" + mTag
                            + " eglSwapBuffers 失败, err=" + err);
                    if (err == EGL14.EGL_BAD_SURFACE
                            || err == EGL14.EGL_BAD_NATIVE_WINDOW
                            || mConsecutiveSwapFailures >= 3) {
                        mReleaseRequested = true;
                    }
                } else {
                    mConsecutiveSwapFailures = 0;
                }
            }
        } catch (Exception e) {
            LogUtil.log("【CS】【Relay】" + mTag + " drawFrame 异常: " + e);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            if (durationMs > 50L) {
                LogUtil.log("[VFC][GLSlowFrame] renderer=" + mTag
                        + " durationMs=" + durationMs
                        + " surface=" + mSurfaceWidth[0] + "x" + mSurfaceHeight[0]
                        + " stateGeneration=" + mState.get().generation
                        + " severe=" + (durationMs > 200L));
            }
        }
    }

    private void refreshSurfaceSizeIfNeeded(EGLSurface activeSurface) {
        if (mSurfaceWidth[0] > 0 && mSurfaceHeight[0] > 0
                && ++mFramesSinceSurfaceSizeCheck < SURFACE_SIZE_RECHECK_INTERVAL) {
            return;
        }
        mFramesSinceSurfaceSizeCheck = 0;
        EGL14.eglQuerySurface(mEGLDisplay, activeSurface, EGL14.EGL_WIDTH, mSurfaceWidth, 0);
        EGL14.eglQuerySurface(mEGLDisplay, activeSurface, EGL14.EGL_HEIGHT, mSurfaceHeight, 0);
        if (mSurfaceWidth[0] != mCachedRawTargetWidth
                || mSurfaceHeight[0] != mCachedRawTargetHeight) {
            mCachedRawTargetWidth = mSurfaceWidth[0];
            mCachedRawTargetHeight = mSurfaceHeight[0];
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
                || mCachedTargetRole != role) {
            mTargetGeometry = RenderTargetGeometry.calculate(rawTargetWidth, rawTargetHeight,
                    state.hostWindowGeometry, role);
            mCachedTargetGeometryGeneration = state.generation;
            mCachedTargetRole = role;
        }
        return mTargetGeometry;
    }

    private void ensureDynamicGeometry(int rawTargetWidth, int rawTargetHeight,
            RenderTargetGeometry.Calculation targetGeometry, RenderTargetRole role,
            RendererState state) {
        if (mDynamicGeometry != null
                && mCachedGeometryGeneration == state.generation
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

    private EGLConfig[] getEglConfigs() {
        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT | EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)) {
            return null;
        }
        return configs;
    }

    private void initEGL() {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY)
            throw new RuntimeException("eglGetDisplay failed");

        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1))
            throw new RuntimeException("eglInitialize failed");

        EGLConfig[] configs = getEglConfigs();
        if (configs == null || configs.length == 0)
            throw new RuntimeException("No matching EGL config");

        int[] contextAttribs = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0);
        if (mEGLContext == EGL14.EGL_NO_CONTEXT)
            throw new RuntimeException("eglCreateContext failed");

        int[] pbufferAttribs = {
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
        };
        // Create a standalone PBuffer context so MediaPlayer can at least push frames
        mEGLPbufferSurface = EGL14.eglCreatePbufferSurface(mEGLDisplay, configs[0], pbufferAttribs, 0);
        if (mEGLPbufferSurface == EGL14.EGL_NO_SURFACE)
            throw new RuntimeException("eglCreatePbufferSurface failed");

        // Try Window surface immediately
        int[] surfaceAttribs = { EGL14.EGL_NONE };
        mEGLWindowSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, configs[0], mTargetSurface, surfaceAttribs, 0);
        if (mEGLWindowSurface == EGL14.EGL_NO_SURFACE) {
            LogUtil.log("【CS】【Relay】eglCreateWindowSurface initial fail (Expected). Proceeding with PBuffer.");
        }

        if (!EGL14.eglMakeCurrent(mEGLDisplay,
                mEGLWindowSurface != EGL14.EGL_NO_SURFACE ? mEGLWindowSurface : mEGLPbufferSurface,
                mEGLWindowSurface != EGL14.EGL_NO_SURFACE ? mEGLWindowSurface : mEGLPbufferSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
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

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        mTextureId = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        mInputSurfaceTexture = new SurfaceTexture(mTextureId);
        mInputSurfaceTexture.setOnFrameAvailableListener(this);
        mInputSurface = new Surface(mInputSurfaceTexture);

        mVertexBuffer = GLHelper.createFloatBuffer(GLHelper.VERTICES);
        mTexCoordBuffer = GLHelper.createFloatBuffer(GLHelper.TEX_COORDS);
    }

    public void release() {
        if (mReleased)
            return;
        mReleaseRequested = true;
        mFrameCoalescer.cancel();
        if (mGLHandler != null)
            mGLHandler.post(this::releaseInternal);
        if (mGLThread != null) {
            mGLThread.quitSafely();
            try {
                mGLThread.join(1000);
            } catch (InterruptedException ignored) {
            }
        }
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
        if (mEGLWindowSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(mEGLDisplay, mEGLWindowSurface);
            mEGLWindowSurface = EGL14.EGL_NO_SURFACE;
        }
        if (mEGLPbufferSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(mEGLDisplay, mEGLPbufferSurface);
            mEGLPbufferSurface = EGL14.EGL_NO_SURFACE;
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

    public static SurfaceRelay createSafely(Surface targetSurface, String tag) {
        return createSafely(targetSurface, tag, RenderTargetRole.PREVIEW);
    }

    public static SurfaceRelay createSafely(Surface targetSurface, String tag,
            RenderTargetRole role) {
        if (targetSurface == null || !targetSurface.isValid())
            return null;
        try {
            SurfaceRelay relay = new SurfaceRelay(targetSurface, tag, role);
            if (relay.isInitialized())
                return relay;
            relay.release();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void releaseSafely(SurfaceRelay relay) {
        if (relay != null) {
            try {
                relay.release();
            } catch (Exception e) {
            }
        }
    }
}
