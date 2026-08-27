package io.github.alanlaw.vfc.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VideoImportHelperTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void duplicateFilenameGetsStableSuffixWithoutOverwritingExistingFile() throws Exception {
        File directory = temporaryFolder.newFolder("videos");
        File original = new File(directory, "filename.mp4");
        try (FileOutputStream output = new FileOutputStream(original)) {
            output.write("old".getBytes(StandardCharsets.UTF_8));
        }

        File duplicate = VideoImportHelper.reserveUniqueVideoFile(directory, "filename.mp4");
        try (FileOutputStream output = new FileOutputStream(duplicate)) {
            output.write("new".getBytes(StandardCharsets.UTF_8));
        }

        assertEquals("filename_1.mp4", duplicate.getName());
        assertEquals(3L, original.length());
        assertEquals(3L, duplicate.length());
        assertTrue(original.isFile());
        assertTrue(duplicate.isFile());
    }
}
