package io.github.alanlaw.vfc;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VfcIdentityTest {
    @Test
    public void releaseIdentityIsFixedAtV023() {
        assertEquals("io.github.alanlaw.vfc", BuildConfig.APPLICATION_ID);
        assertEquals("0.2.3", BuildConfig.VERSION_NAME);
        assertEquals(5, BuildConfig.VERSION_CODE);
    }

    @Test
    public void ipcContractUsesMigratedAuthorityAndSelectMethod() {
        Uri contentUri = Mockito.mock(Uri.class);
        try (MockedStatic<Uri> uriMock = Mockito.mockStatic(Uri.class)) {
            stubIpcUris(uriMock, contentUri);
            assertEquals("io.github.alanlaw.vfc.provider", IpcContract.AUTHORITY);
            assertEquals("io.github.alanlaw.vfc.ACTION_UPDATE_CONFIG", IpcContract.ACTION_UPDATE_CONFIG);
            assertEquals("select", IpcContract.METHOD_SELECT);
            assertEquals("select_preset_shortcut", IpcContract.METHOD_SELECT_PRESET_SHORTCUT);
            assertEquals("video_name", IpcContract.EXTRA_VIDEO_NAME);
            assertEquals("preset_id", IpcContract.EXTRA_PRESET_ID);
            assertEquals("shortcut_key", IpcContract.EXTRA_SHORTCUT_KEY);
        }
    }

    @Test
    public void controlActionHelperSendsSelectThroughProvider() {
        Context context = Mockito.mock(Context.class);
        ContentResolver resolver = Mockito.mock(ContentResolver.class);
        Bundle result = Mockito.mock(Bundle.class);
        Mockito.when(result.getBoolean(IpcContract.EXTRA_CHANGED, false)).thenReturn(true);
        Mockito.when(context.getContentResolver()).thenReturn(resolver);
        AtomicReference<String> capturedVideoName = new AtomicReference<>();
        Uri contentUri = Mockito.mock(Uri.class);
        try (MockedStatic<Uri> uriMock = Mockito.mockStatic(Uri.class)) {
            stubIpcUris(uriMock, contentUri);
            try (MockedConstruction<Bundle> construction = Mockito.mockConstruction(Bundle.class,
                    (bundle, context1) -> Mockito.doAnswer(invocation -> {
                        capturedVideoName.set(invocation.getArgument(1));
                        return null;
                    }).when(bundle).putString(
                            Mockito.eq(IpcContract.EXTRA_VIDEO_NAME), Mockito.anyString()))) {
                Mockito.when(resolver.call(
                        Mockito.eq(IpcContract.CONTENT_URI),
                        Mockito.eq(IpcContract.METHOD_SELECT),
                        Mockito.isNull(String.class),
                        Mockito.any(Bundle.class))).thenReturn(result);

                assertTrue(ControlActionHelper.selectVideo(context, "face.mp4"));
            }
        }
        assertEquals("face.mp4", capturedVideoName.get());
        Mockito.verify(resolver).call(
                Mockito.eq(IpcContract.CONTENT_URI),
                Mockito.eq(IpcContract.METHOD_SELECT),
                Mockito.isNull(String.class),
                Mockito.any(Bundle.class));
    }

    private static void stubIpcUris(MockedStatic<Uri> uriMock, Uri contentUri) {
        uriMock.when(() -> Uri.parse("content://io.github.alanlaw.vfc.provider"))
                .thenReturn(contentUri);
        uriMock.when(() -> Uri.withAppendedPath(contentUri, "config")).thenReturn(contentUri);
        uriMock.when(() -> Uri.withAppendedPath(contentUri, "video")).thenReturn(contentUri);
        uriMock.when(() -> Uri.withAppendedPath(contentUri, "audio")).thenReturn(contentUri);
        uriMock.when(() -> Uri.withAppendedPath(contentUri, "runtime_command")).thenReturn(contentUri);
    }
}
