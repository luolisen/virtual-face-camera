package io.github.alanlaw.vfc;

import android.net.Uri;

public final class IpcContract {
    public static final String AUTHORITY = "io.github.alanlaw.vfc.provider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    public static final String PATH_CONFIG = "config";
    public static final String PATH_VIDEO = "video";
    public static final String PATH_AUDIO = "audio";

    public static final Uri URI_CONFIG = Uri.withAppendedPath(CONTENT_URI, PATH_CONFIG);
    public static final Uri URI_VIDEO = Uri.withAppendedPath(CONTENT_URI, PATH_VIDEO);
    public static final Uri URI_AUDIO = Uri.withAppendedPath(CONTENT_URI, PATH_AUDIO);

    public static final String ACTION_UPDATE_CONFIG = "io.github.alanlaw.vfc.ACTION_UPDATE_CONFIG";
    public static final String ACTION_REQUEST_CONFIG = "io.github.alanlaw.vfc.ACTION_REQUEST_CONFIG";
    public static final String ACTION_NEXT = "io.github.alanlaw.vfc.ACTION_CAMSWAP_NEXT";
    public static final String ACTION_ROTATE = "io.github.alanlaw.vfc.ACTION_CAMSWAP_ROTATE";
    public static final String ACTION_EXIT = "io.github.alanlaw.vfc.ACTION_CAMSWAP_EXIT";
    public static final String ACTION_NOTIFICATION_PREV = "io.github.alanlaw.vfc.action.PREV_INTERNAL";
    public static final String ACTION_NOTIFICATION_NEXT = "io.github.alanlaw.vfc.action.NEXT_INTERNAL";
    public static final String ACTION_NOTIFICATION_ROTATE = "io.github.alanlaw.vfc.action.ROTATE_INTERNAL";
    public static final String ACTION_NOTIFICATION_EXIT = "io.github.alanlaw.vfc.action.EXIT_INTERNAL";

    public static final String EXTRA_CONFIG_JSON = "config_json";
    public static final String EXTRA_REQUESTER_PACKAGE = "requester_package";
    public static final String EXTRA_VIDEO_BUNDLE = "video_bundle";
    public static final String EXTRA_VIDEO_BINDER = "video_binder";
    public static final String EXTRA_CHANGED = "changed";

    public static final String METHOD_NEXT = "next";
    public static final String METHOD_PREV = "prev";
    public static final String METHOD_RANDOM = "random";
    public static final String METHOD_SELECT = "select";
    public static final String EXTRA_VIDEO_NAME = "video_name";

    private IpcContract() {
    }
}
