package io.github.alanlaw.vfc;

import android.net.Uri;

public final class IpcContract {
    public static final String AUTHORITY = "io.github.alanlaw.vfc.provider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    public static final String PATH_CONFIG = "config";
    public static final String PATH_VIDEO = "video";
    public static final String PATH_AUDIO = "audio";
    public static final String PATH_RUNTIME_COMMAND = "runtime_command";

    public static final Uri URI_CONFIG = Uri.withAppendedPath(CONTENT_URI, PATH_CONFIG);
    public static final Uri URI_VIDEO = Uri.withAppendedPath(CONTENT_URI, PATH_VIDEO);
    public static final Uri URI_AUDIO = Uri.withAppendedPath(CONTENT_URI, PATH_AUDIO);
    public static final Uri URI_RUNTIME_COMMAND = Uri.withAppendedPath(CONTENT_URI, PATH_RUNTIME_COMMAND);

    public static final String ACTION_UPDATE_CONFIG = "io.github.alanlaw.vfc.ACTION_UPDATE_CONFIG";
    public static final String ACTION_REQUEST_CONFIG = "io.github.alanlaw.vfc.ACTION_REQUEST_CONFIG";
    public static final String EXTRA_CONFIG_JSON = "config_json";
    public static final String EXTRA_REQUESTER_PACKAGE = "requester_package";
    public static final String EXTRA_VIDEO_BUNDLE = "video_bundle";
    public static final String EXTRA_VIDEO_BINDER = "video_binder";
    public static final String EXTRA_CHANGED = "changed";

    public static final String METHOD_NEXT = "next";
    public static final String METHOD_PREV = "prev";
    public static final String METHOD_RANDOM = "random";
    public static final String METHOD_SELECT = "select";
    public static final String METHOD_SELECT_PRESET_SHORTCUT = "select_preset_shortcut";
    public static final String METHOD_MOVE_VIEWPORT = "move_viewport";
    public static final String METHOD_RESET_VIEWPORT = "reset_viewport";
    public static final String METHOD_ENQUEUE_VIEWPORT_COMMAND = "enqueue_viewport_command";
    public static final String METHOD_GET_PENDING_VIEWPORT_COMMAND = "get_pending_viewport_command";
    public static final String METHOD_ACK_VIEWPORT_COMMAND = "ack_viewport_command";
    public static final String METHOD_REPORT_CAMERA_RUNTIME = "report_camera_runtime";
    public static final String METHOD_HAS_ACTIVE_CAMERA = "has_active_camera";
    public static final String EXTRA_VIDEO_NAME = "video_name";
    public static final String EXTRA_PRESET_ID = "preset_id";
    public static final String EXTRA_SHORTCUT_KEY = "shortcut_key";
    public static final String EXTRA_VIEWPORT_DIRECTION = "viewport_direction";
    public static final String EXTRA_VIEWPORT_COMMAND = "viewport_command";
    public static final String EXTRA_COMMAND_SEQ = "command_seq";
    public static final String EXTRA_LAST_COMMAND_SEQ = "last_command_seq";
    public static final String EXTRA_TARGET_PACKAGE = "target_package";
    public static final String EXTRA_HOST_PACKAGE = "host_package";
    public static final String EXTRA_CAMERA_API = "camera_api";
    public static final String EXTRA_CAMERA_ID = "camera_id";
    public static final String EXTRA_CAMERA_FACING = "facing";
    public static final String EXTRA_SENSOR_ORIENTATION = "sensor_orientation";
    public static final String EXTRA_DISPLAY_ORIENTATION = "display_orientation";
    public static final String EXTRA_PREVIEW_TRANSFORM_FLAGS = "preview_transform_flags";
    public static final String EXTRA_PREVIEW_WIDTH = "preview_width";
    public static final String EXTRA_PREVIEW_HEIGHT = "preview_height";
    public static final String EXTRA_PREVIEW_ACTIVE = "active";
    public static final String EXTRA_GENERATION = "generation";
    public static final String EXTRA_TIMESTAMP = "timestamp";

    public static final String VIEWPORT_COMMAND_UP = "up";
    public static final String VIEWPORT_COMMAND_DOWN = "down";
    public static final String VIEWPORT_COMMAND_LEFT = "left";
    public static final String VIEWPORT_COMMAND_RIGHT = "right";
    public static final String VIEWPORT_COMMAND_ZOOM_IN = "zoom_in";
    public static final String VIEWPORT_COMMAND_ZOOM_OUT = "zoom_out";
    public static final String VIEWPORT_COMMAND_RESET = "reset";

    private IpcContract() {
    }
}
