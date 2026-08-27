package io.github.alanlaw.vfc;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VideoProviderSelectionTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rejectsTraversalAndAbsoluteVideoNames() {
        assertFalse(VideoProvider.isSafeVideoName("../outside.mp4"));
        assertFalse(VideoProvider.isSafeVideoName("nested/video.mp4"));
        assertFalse(VideoProvider.isSafeVideoName("/tmp/outside.mp4"));
        assertFalse(VideoProvider.isSafeVideoName("..\\outside.mp4"));
        assertTrue(VideoProvider.isSafeVideoName("face_a.mp4"));
    }

    @Test
    public void selectionMustBelongToTheVideoManagerLibrary() throws Exception {
        File dir = temporaryFolder.newFolder("library");
        File available = temporaryFolder.newFile("library/available.mp4");
        temporaryFolder.newFile("library/not-a-video.txt");

        assertTrue(VideoProvider.isVideoInLibrary(dir, available.getName()));
        assertFalse(VideoProvider.isVideoInLibrary(dir, "not-a-video.txt"));
        assertFalse(VideoProvider.isVideoInLibrary(dir, "missing.mp4"));
        assertFalse(VideoProvider.isVideoInLibrary(dir, "../available.mp4"));
    }
}
