package io.github.alanlaw.vfc.utils;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Shared, duplicate-safe importer for videos selected through the system picker. */
public final class VideoImportHelper {
    private static final String DEFAULT_VIDEO_NAME = "video.mp4";

    private VideoImportHelper() {
    }

    /**
     * Reserve a new filename without ever replacing an existing asset. The
     * returned file is created empty, which also makes concurrent imports safe.
     */
    public static File reserveUniqueVideoFile(File mediaDir, String requestedName) throws IOException {
        if (mediaDir == null) {
            throw new IOException("Media directory is null");
        }
        if (!mediaDir.exists() && !mediaDir.mkdirs()) {
            throw new IOException("Unable to create media directory: " + mediaDir);
        }
        if (!mediaDir.isDirectory()) {
            throw new IOException("Media path is not a directory: " + mediaDir);
        }

        String safeName = sanitizeVideoName(requestedName);
        File candidate = new File(mediaDir, safeName);
        if (candidate.createNewFile()) {
            return candidate;
        }

        String base = safeName;
        String extension = "";
        int dot = safeName.lastIndexOf('.');
        if (dot > 0) {
            base = safeName.substring(0, dot);
            extension = safeName.substring(dot);
        }

        for (int suffix = 1; suffix < Integer.MAX_VALUE; suffix++) {
            candidate = new File(mediaDir, base + "_" + suffix + extension);
            if (candidate.createNewFile()) {
                return candidate;
            }
        }
        throw new IOException("Unable to allocate a unique video filename");
    }

    /** Copy one picker URI into the managed media directory and return its final filename. */
    public static File importVideo(Context context, Uri uri, File mediaDir, String requestedName)
            throws IOException {
        if (context == null || uri == null) {
            throw new IOException("Video import context or URI is null");
        }

        File destination = reserveUniqueVideoFile(mediaDir, requestedName);
        boolean completed = false;
        try (InputStream input = context.getContentResolver().openInputStream(uri);
                FileOutputStream output = new FileOutputStream(destination)) {
            if (input == null) {
                throw new IOException("Unable to open selected video");
            }
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            output.flush();
            completed = destination.length() > 0L;
        } finally {
            if (!completed && destination.exists()) {
                // The empty reservation is never left as a selectable video.
                destination.delete();
            }
        }

        if (!completed) {
            throw new IOException("Selected video is empty");
        }
        try {
            destination.setReadable(true, false);
            Runtime.getRuntime().exec(new String[] { "chmod", "644", destination.getAbsolutePath() });
        } catch (Exception ignored) {
            // Best effort; ConfigManager's existing directory permissions remain the fallback.
        }
        return destination;
    }

    /** Keep only a plain managed filename; never allow a picker display name to escape the directory. */
    public static String sanitizeVideoName(String requestedName) {
        String name = requestedName == null ? "" : requestedName.trim();
        if (name.isEmpty()) {
            return DEFAULT_VIDEO_NAME;
        }
        name = name.replace('/', '_').replace('\\', '_');
        name = name.replaceAll("[\\p{Cntrl}]", "_");
        if (name.equals(".") || name.equals("..")) {
            return DEFAULT_VIDEO_NAME;
        }
        File basename = new File(name);
        name = basename.getName();
        if (name.isEmpty()) {
            return DEFAULT_VIDEO_NAME;
        }
        if (!VideoManager.isVideoFileName(name)) {
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            name = base + ".mp4";
        }
        return name;
    }
}
