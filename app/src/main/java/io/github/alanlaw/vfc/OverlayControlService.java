package io.github.alanlaw.vfc;

import android.animation.ValueAnimator;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

public class OverlayControlService extends Service {
    private static final int EDGE_MARGIN_DP = 12;
    private static final float SCALE_FACTOR = 0.8f;
    private static final int INNER_GAP_DP = 6;

    private WindowManager windowManager;
    private LinearLayout contentContainer;
    private FrameLayout rootView;
    private LinearLayout actionPanel;
    private HorizontalScrollView actionScroll;
    private TextView bubbleView;
    private TextView presetButton;
    private TextView rotationButton;
    private TextView viewportButton;
    private PopupWindow presetPopup;
    private PopupWindow viewportPopup;
    private WindowManager.LayoutParams layoutParams;
    private ValueAnimator snapAnimator;
    private boolean isExpanded;
    private boolean snappedToRight = true;
    private final TextView[] shortcutButtons = new TextView[5];
    private ConfigManager configManager;
    private ContentObserver configObserver;

    private static final String[] SHORTCUT_KEYS = {
            ConfigManager.PRESET_SHORTCUT_DOT,
            ConfigManager.PRESET_SHORTCUT_LEFT,
            ConfigManager.PRESET_SHORTCUT_RIGHT,
            ConfigManager.PRESET_SHORTCUT_OPEN,
            ConfigManager.PRESET_SHORTCUT_BLINK
    };
    private static final int[] SHORTCUT_LABEL_RES = {
            R.string.shortcut_dot,
            R.string.shortcut_left,
            R.string.shortcut_right,
            R.string.shortcut_open,
            R.string.shortcut_blink
    };
    private static final int[] SHORTCUT_COLORS = {
            0xFF6650A4,
            0xFF3F637A,
            0xFF8A5A2B,
            0xFF386B52,
            0xFF66557A
    };

    @Override
    public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        configManager = new ConfigManager(false);
        configManager.setContext(this);
        configManager.migrateV02Configuration();
        configObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                refreshShortcutButtonStates();
            }
        };
        try {
            getContentResolver().registerContentObserver(IpcContract.URI_CONFIG, true, configObserver);
        } catch (Exception ignored) {
        }
        showOverlay();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(io.github.alanlaw.vfc.utils.LocaleHelper.INSTANCE.onAttach(newBase));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        refreshShortcutButtonStates();
        showOverlay();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (configObserver != null) {
            try {
                getContentResolver().unregisterContentObserver(configObserver);
            } catch (Exception ignored) {
            }
            configObserver = null;
        }
        removeOverlay();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showOverlay() {
        if (windowManager == null || rootView != null) {
            return;
        }

        rootView = new FrameLayout(this);
        rootView.setClipChildren(false);
        rootView.setClipToPadding(false);
        int outerPadding = dpScaled(6);
        rootView.setPadding(outerPadding, outerPadding, outerPadding, outerPadding);

        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.HORIZONTAL);
        contentContainer.setGravity(Gravity.CENTER_VERTICAL);
        contentContainer.setClipChildren(false);
        contentContainer.setClipToPadding(false);

        actionPanel = new LinearLayout(this);
        actionPanel.setOrientation(LinearLayout.HORIZONTAL);
        actionPanel.setGravity(Gravity.CENTER_VERTICAL);
        int panelPaddingH = dpScaled(8);
        int panelPaddingV = dpScaled(8);
        actionPanel.setPadding(panelPaddingH, panelPaddingV, panelPaddingH, panelPaddingV);
        actionPanel.setVisibility(View.GONE);
        actionPanel.setBackground(makePanelBackground());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            actionPanel.setElevation(dpScaled(6));
        }

        presetButton = makeActionButton(
                getString(R.string.overlay_preset),
                resolveMonetColor(0xFF6650A4),
                v -> togglePresetPopup());
        actionPanel.addView(presetButton);
        actionPanel.addView(makeSpacer());

        for (int i = 0; i < SHORTCUT_KEYS.length; i++) {
            final int shortcutIndex = i;
            shortcutButtons[i] = makeActionButton(
                    getString(SHORTCUT_LABEL_RES[i]),
                    resolveMonetColor(SHORTCUT_COLORS[i]),
                    v -> handleShortcut(shortcutIndex));
            actionPanel.addView(shortcutButtons[i]);
            if (i < SHORTCUT_KEYS.length - 1) {
                actionPanel.addView(makeSpacer());
            }
        }

        actionPanel.addView(makeSpacer());
        rotationButton = makeActionButton(
                getString(R.string.overlay_action_rotate),
                resolveMonetColor(0xFF7D5260),
                v -> handleRotation());
        actionPanel.addView(rotationButton);

        actionPanel.addView(makeSpacer());
        viewportButton = makeActionButton(
                getString(R.string.overlay_action_viewport),
                resolveMonetColor(0xFF4F6475),
                v -> toggleViewportPopup());
        actionPanel.addView(viewportButton);

        actionScroll = new HorizontalScrollView(this);
        actionScroll.setHorizontalScrollBarEnabled(false);
        actionScroll.setFillViewport(false);
        actionScroll.setClipChildren(false);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int bubbleSize = dpScaled(45);
        int availableActionWidth = screenWidth - outerPadding * 2 - bubbleSize
                - dpScaled(INNER_GAP_DP) - dp(EDGE_MARGIN_DP) * 2;
        LinearLayout.LayoutParams actionScrollParams = new LinearLayout.LayoutParams(
                Math.max(1, availableActionWidth), LinearLayout.LayoutParams.WRAP_CONTENT);
        actionScroll.setLayoutParams(actionScrollParams);
        actionScroll.addView(actionPanel, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        actionScroll.setVisibility(View.GONE);

        bubbleView = new TextView(this);
        bubbleView.setText("VFC");
        bubbleView.setTextColor(resolveOnAccentColor());
        bubbleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        bubbleView.setGravity(Gravity.CENTER);
        bubbleView.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        bubbleView.setBackground(makeBubbleBackground());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bubbleView.setElevation(dpScaled(6));
        }

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(bubbleSize, bubbleSize);
        bubbleView.setLayoutParams(bubbleParams);
        bubbleView.setOnTouchListener(new BubbleTouchListener());

        rootView.addView(contentContainer);

        applySideLayout();

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = getResources().getDisplayMetrics().widthPixels - dpScaled(60);
        layoutParams.y = dpScaled(176);

        windowManager.addView(rootView, layoutParams);
        refreshShortcutButtonStates();
        rootView.post(() -> updateOverlayPosition(false));
    }

    private void removeOverlay() {
        cancelSnapAnimator();
        dismissPresetPopup();
        dismissViewportPopup();
        if (windowManager != null && rootView != null) {
            try {
                windowManager.removeView(rootView);
            } catch (Exception ignored) {
            }
        }
        contentContainer = null;
        rootView = null;
        actionPanel = null;
        actionScroll = null;
        bubbleView = null;
        presetButton = null;
        rotationButton = null;
        viewportButton = null;
        for (int i = 0; i < shortcutButtons.length; i++) {
            shortcutButtons[i] = null;
        }
    }

    private void toggleExpanded() {
        isExpanded = !isExpanded;
        if (isExpanded) {
            refreshShortcutButtonStates();
        } else {
            dismissPresetPopup();
            dismissViewportPopup();
        }
        if (actionPanel != null) {
            actionPanel.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        }
        if (actionScroll != null) {
            actionScroll.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        }
        if (rootView != null) {
            rootView.post(() -> updateOverlayPosition(true));
        }
    }

    private View makeSpacer() {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dpScaled(6), 1));
        return spacer;
    }

    private TextView makeActionButton(String text, int backgroundColor, View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(resolveOnAccentColor());
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        button.setSingleLine(true);
        button.setIncludeFontPadding(false);
        button.setPadding(dpScaled(4), dpScaled(8), dpScaled(4), dpScaled(8));
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setLayoutParams(new LinearLayout.LayoutParams(dpScaled(36), dpScaled(34)));
        button.setBackground(makeActionBackground(backgroundColor));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setElevation(dpScaled(1));
        }
        button.setOnClickListener(listener);
        return button;
    }

    private void handleShortcut(int shortcutIndex) {
        if (shortcutIndex < 0 || shortcutIndex >= SHORTCUT_KEYS.length) {
            return;
        }
        refreshShortcutButtonStates();
        String shortcutLabel = getString(SHORTCUT_LABEL_RES[shortcutIndex]);
        String videoName = configManager == null
                ? ""
                : configManager.getCurrentPresetShortcutVideo(SHORTCUT_KEYS[shortcutIndex]);
        if (videoName.isEmpty()) {
            android.widget.Toast.makeText(
                    this,
                    getString(R.string.overlay_shortcut_unbound, shortcutLabel),
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        String currentPresetId = configManager == null
                ? ""
                : configManager.getString(ConfigManager.KEY_CURRENT_PRESET_ID, "");
        if (!ControlActionHelper.selectPresetShortcut(
                this, currentPresetId, SHORTCUT_KEYS[shortcutIndex])) {
            android.widget.Toast.makeText(
                    this,
                    getString(R.string.overlay_shortcut_select_failed),
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        refreshShortcutButtonStates();
    }

    private void handleRotation() {
        int rotation = ControlActionHelper.rotateVideo(this);
        android.widget.Toast.makeText(
                this,
                getString(R.string.overlay_rotation_status, rotation),
                android.widget.Toast.LENGTH_SHORT).show();
    }

    private void toggleViewportPopup() {
        if (viewportPopup != null && viewportPopup.isShowing()) {
            dismissViewportPopup();
            return;
        }
        showViewportPopup();
    }

    private void showViewportPopup() {
        if (configManager == null || viewportButton == null || rootView == null) {
            return;
        }
        configManager.forceReload();
        String aspectMode = configManager.getString(
                ConfigManager.KEY_VIDEO_ASPECT_MODE, ConfigManager.ASPECT_MODE_DYNAMIC);
        if (!ConfigManager.ASPECT_MODE_DYNAMIC.equals(aspectMode)) {
            android.widget.Toast.makeText(
                    this,
                    getString(R.string.overlay_viewport_dynamic_only),
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (configManager.getActiveBinding() == null) {
            android.widget.Toast.makeText(
                    this,
                    getString(R.string.overlay_viewport_no_active),
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        dismissPresetPopup();
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER_HORIZONTAL);
        controls.setPadding(dp(8), dp(8), dp(8), dp(8));

        LinearLayout upRow = makeViewportRow();
        upRow.addView(makeViewportControl("↑", ConfigManager.VIEWPORT_DIRECTION_UP));
        controls.addView(upRow);

        LinearLayout middleRow = makeViewportRow();
        middleRow.addView(makeViewportControl("←", ConfigManager.VIEWPORT_DIRECTION_LEFT));
        middleRow.addView(makeViewportControl("中", null));
        middleRow.addView(makeViewportControl("→", ConfigManager.VIEWPORT_DIRECTION_RIGHT));
        controls.addView(middleRow);

        LinearLayout downRow = makeViewportRow();
        downRow.addView(makeViewportControl("↓", ConfigManager.VIEWPORT_DIRECTION_DOWN));
        controls.addView(downRow);

        viewportPopup = new PopupWindow(controls, dp(174), dp(174), true);
        viewportPopup.setBackgroundDrawable(new ColorDrawable(resolvePanelColor()));
        viewportPopup.setOutsideTouchable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            viewportPopup.setElevation(dp(6));
        }
        try {
            viewportPopup.showAsDropDown(viewportButton, 0, dp(4));
        } catch (Exception e) {
            dismissViewportPopup();
        }
    }

    private LinearLayout makeViewportRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50)));
        return row;
    }

    private TextView makeViewportControl(String text, String direction) {
        TextView button = makeActionButton(text, resolveMonetColor(0xFF4F6475),
                v -> handleViewport(direction));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(40)));
        return button;
    }

    private void handleViewport(String direction) {
        if (configManager == null) {
            return;
        }
        configManager.forceReload();
        String aspectMode = configManager.getString(
                ConfigManager.KEY_VIDEO_ASPECT_MODE, ConfigManager.ASPECT_MODE_DYNAMIC);
        if (!ConfigManager.ASPECT_MODE_DYNAMIC.equals(aspectMode)) {
            android.widget.Toast.makeText(
                    this,
                    getString(R.string.overlay_viewport_dynamic_only),
                    android.widget.Toast.LENGTH_SHORT).show();
            dismissViewportPopup();
            return;
        }
        if (configManager.getActiveBinding() == null) {
            android.widget.Toast.makeText(
                    this,
                    getString(R.string.overlay_viewport_no_active),
                    android.widget.Toast.LENGTH_SHORT).show();
            dismissViewportPopup();
            return;
        }
        boolean changed = direction == null
                ? ControlActionHelper.resetViewport(this)
                : ControlActionHelper.moveViewport(this, direction);
        if (changed) {
            refreshShortcutButtonStates();
        }
    }

    private void refreshShortcutButtonStates() {
        if (configManager == null) {
            return;
        }
        configManager.forceReload();
        String selectedVideo = configManager.getString(ConfigManager.KEY_SELECTED_VIDEO, "");
        for (int i = 0; i < shortcutButtons.length; i++) {
            TextView button = shortcutButtons[i];
            if (button == null) {
                continue;
            }
            String boundVideo = configManager.getCurrentPresetShortcutVideo(SHORTCUT_KEYS[i]);
            boolean bound = !boundVideo.isEmpty();
            boolean active = bound && boundVideo.equals(selectedVideo);
            int color = resolveMonetColor(SHORTCUT_COLORS[i]);
            button.setAlpha(bound ? 1.0f : 0.42f);
            button.setTypeface(Typeface.create(Typeface.SANS_SERIF,
                    active ? Typeface.BOLD_ITALIC : Typeface.BOLD));
            button.setBackground(makeActionBackground(active ? lightenColor(color, 0.25f) : color, active));
        }
    }

    private void togglePresetPopup() {
        if (presetPopup != null && presetPopup.isShowing()) {
            dismissPresetPopup();
            return;
        }
        dismissViewportPopup();
        showPresetPopup();
    }

    private void showPresetPopup() {
        if (configManager == null || presetButton == null || rootView == null) {
            return;
        }
        configManager.forceReload();
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));

        java.util.List<ConfigManager.ShortcutPreset> presets = configManager.listPresets();
        if (presets.isEmpty()) {
            list.addView(makePresetPopupText(getString(R.string.overlay_no_presets), false));
        } else {
            String currentId = configManager.getCurrentPreset() == null
                    ? ""
                    : configManager.getCurrentPreset().getId();
            for (ConfigManager.ShortcutPreset preset : presets) {
                final ConfigManager.ShortcutPreset selectedPreset = preset;
                boolean selected = preset.getId().equals(currentId);
                TextView item = makePresetPopupText(
                        (selected ? "✓ " : "") + preset.getName(), selected);
                item.setOnClickListener(v -> {
                    if (configManager.setCurrentPreset(selectedPreset.getId())) {
                        dismissPresetPopup();
                        refreshShortcutButtonStates();
                        android.widget.Toast.makeText(
                                this,
                                getString(R.string.overlay_preset_switched, selectedPreset.getName()),
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
                list.addView(item);
            }
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(list, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        int rowCount = Math.max(1, presets.size());
        int popupHeight = Math.min(dp(360), dp(16 + rowCount * 44));
        if (presets.isEmpty()) {
            popupHeight = dp(72);
        }
        presetPopup = new PopupWindow(scrollView, dp(190), popupHeight, true);
        presetPopup.setBackgroundDrawable(new ColorDrawable(resolvePanelColor()));
        presetPopup.setOutsideTouchable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            presetPopup.setElevation(dp(6));
        }
        try {
            presetPopup.showAsDropDown(presetButton, 0, dp(4));
        } catch (Exception e) {
            dismissPresetPopup();
        }
    }

    private TextView makePresetPopupText(String text, boolean selected) {
        TextView item = new TextView(this);
        item.setText(text);
        item.setSingleLine(true);
        item.setEllipsize(android.text.TextUtils.TruncateAt.END);
        item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        item.setTextColor(resolvePanelTextColor());
        item.setTypeface(Typeface.create(Typeface.SANS_SERIF,
                selected ? Typeface.BOLD : Typeface.NORMAL));
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(10), dp(8), dp(10), dp(8));
        item.setBackground(makePopupItemBackground(selected));
        item.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
        return item;
    }

    private RippleDrawable makePopupItemBackground(boolean selected) {
        GradientDrawable content = new GradientDrawable();
        content.setColor(selected
                ? adjustAlpha(resolveMonetColor(0xFF6650A4), 0.24f)
                : Color.TRANSPARENT);
        content.setCornerRadius(dp(10));
        return new RippleDrawable(
                ColorStateList.valueOf(adjustAlpha(resolveMonetColor(0xFF6650A4), 0.18f)),
                content,
                null);
    }

    private int resolvePanelTextColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return getColorCompat(android.R.color.system_neutral1_900, Color.BLACK);
        }
        return Color.DKGRAY;
    }

    private void dismissPresetPopup() {
        if (presetPopup != null) {
            try {
                presetPopup.dismiss();
            } catch (Exception ignored) {
            }
            presetPopup = null;
        }
    }

    private void dismissViewportPopup() {
        if (viewportPopup != null) {
            try {
                viewportPopup.dismiss();
            } catch (Exception ignored) {
            }
            viewportPopup = null;
        }
    }

    private GradientDrawable makePanelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(resolvePanelColor());
        drawable.setCornerRadius(dpScaled(16));
        drawable.setStroke(Math.max(1, dpScaled(1)), resolvePanelStrokeColor());
        return drawable;
    }

    private RippleDrawable makeActionBackground(int color) {
        return makeActionBackground(color, false);
    }

    private RippleDrawable makeActionBackground(int color, boolean active) {
        GradientDrawable content = new GradientDrawable();
        content.setColor(color);
        content.setCornerRadius(dpScaled(12));
        if (active) {
            content.setStroke(Math.max(1, dpScaled(2)), resolveOnAccentColor());
        }
        return new RippleDrawable(
                ColorStateList.valueOf(adjustAlpha(resolveOnAccentColor(), 0.18f)),
                content,
                null);
    }

    private RippleDrawable makeBubbleBackground() {
        GradientDrawable content = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] {
                        lightenColor(resolveMonetColor(0xFF6650A4), 0.12f),
                        resolveMonetColor(0xFF6650A4)
                });
        content.setShape(GradientDrawable.OVAL);
        content.setStroke(Math.max(1, dpScaled(1)), adjustAlpha(resolveOnAccentColor(), 0.24f));
        return new RippleDrawable(
                ColorStateList.valueOf(adjustAlpha(resolveOnAccentColor(), 0.16f)),
                content,
                null);
    }

    private void applySideLayout() {
        if (contentContainer == null) {
            return;
        }
        contentContainer.removeAllViews();

        int gap = dpScaled(INNER_GAP_DP);

        if (actionScroll != null && actionScroll.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams panelLp = (LinearLayout.LayoutParams) actionScroll.getLayoutParams();
            panelLp.setMargins(0, 0, 0, 0);
            if (snappedToRight) {
                panelLp.rightMargin = gap;
            } else {
                panelLp.leftMargin = gap;
            }
            actionScroll.setLayoutParams(panelLp);
        }

        if (snappedToRight) {
            if (actionScroll != null) {
                contentContainer.addView(actionScroll);
            }
            if (bubbleView != null) {
                contentContainer.addView(bubbleView);
            }
        } else {
            if (bubbleView != null) {
                contentContainer.addView(bubbleView);
            }
            if (actionScroll != null) {
                contentContainer.addView(actionScroll);
            }
        }
    }

    private void updateOverlayPosition(boolean animate) {
        if (layoutParams == null || windowManager == null || rootView == null) {
            return;
        }

        applySideLayout();

        int overlayWidth = getOverlayWidth();
        int overlayHeight = getOverlayHeight();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int edgeMargin = dp(EDGE_MARGIN_DP);
        int minX = edgeMargin;
        int maxX = Math.max(edgeMargin, screenWidth - overlayWidth - edgeMargin);
        int maxY = Math.max(edgeMargin, screenHeight - overlayHeight - edgeMargin);
        int targetX = snappedToRight ? maxX : minX;
        int targetY = clamp(layoutParams.y, edgeMargin, maxY);

        layoutParams.y = targetY;
        if (!animate) {
            cancelSnapAnimator();
            layoutParams.x = targetX;
            safelyUpdateLayout();
            return;
        }

        animateSnapTo(targetX, targetY);
    }

    private void animateSnapTo(int targetX, int targetY) {
        if (layoutParams == null) {
            return;
        }
        cancelSnapAnimator();
        final int startX = layoutParams.x;
        layoutParams.y = targetY;
        snapAnimator = ValueAnimator.ofInt(startX, targetX);
        snapAnimator.setDuration(180L);
        snapAnimator.setInterpolator(new DecelerateInterpolator());
        snapAnimator.addUpdateListener(animation -> {
            if (layoutParams == null) {
                return;
            }
            layoutParams.x = (Integer) animation.getAnimatedValue();
            safelyUpdateLayout();
        });
        snapAnimator.start();
    }

    private void cancelSnapAnimator() {
        if (snapAnimator != null) {
            snapAnimator.cancel();
            snapAnimator = null;
        }
    }

    private void safelyUpdateLayout() {
        if (windowManager == null || rootView == null || layoutParams == null) {
            return;
        }
        try {
            windowManager.updateViewLayout(rootView, layoutParams);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private int getOverlayWidth() {
        if (rootView == null) {
            return 0;
        }
        int width = rootView.getWidth();
        if (width > 0) {
            return width;
        }
        rootView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return rootView.getMeasuredWidth();
    }

    private int getOverlayHeight() {
        if (rootView == null) {
            return 0;
        }
        int height = rootView.getHeight();
        if (height > 0) {
            return height;
        }
        rootView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return rootView.getMeasuredHeight();
    }

    private int resolveMonetColor(int fallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            int resId;
            if (fallback == 0xFF6650A4) {
                resId = android.R.color.system_accent1_600;
            } else if (fallback == 0xFF7D5260) {
                resId = android.R.color.system_accent2_600;
            } else {
                resId = android.R.color.system_accent3_600;
            }
            return getColorCompat(resId, fallback);
        }
        return fallback;
    }

    private int resolveOnAccentColor() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? getColorCompat(android.R.color.system_neutral1_0, Color.WHITE)
                : Color.WHITE;
    }

    private int resolvePanelColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return adjustAlpha(getColorCompat(android.R.color.system_neutral1_10, 0xFFF4EEFF), 0.94f);
        }
        return 0xEEF4EEFF;
    }

    private int resolvePanelStrokeColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return adjustAlpha(getColorCompat(android.R.color.system_accent1_300, 0xFFB9A9E3), 0.38f);
        }
        return 0x40B9A9E3;
    }

    private int getColorCompat(int resId, int fallback) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return getColor(resId);
            }
            Resources resources = getResources();
            return resources.getColor(resId);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int lightenColor(int color, float amount) {
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        r += Math.round((255 - r) * amount);
        g += Math.round((255 - g) * amount);
        b += Math.round((255 - b) * amount);
        return Color.argb(Color.alpha(color), clamp(r, 0, 255), clamp(g, 0, 255), clamp(b, 0, 255));
    }

    private int adjustAlpha(int color, float alpha) {
        return Color.argb(Math.round(255 * alpha), Color.red(color), Color.green(color), Color.blue(color));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics());
    }

    private int dpScaled(int value) {
        return Math.max(1, Math.round(dp(value) * SCALE_FACTOR));
    }

    private final class BubbleTouchListener implements View.OnTouchListener {
        private final int touchSlop = ViewConfiguration.get(OverlayControlService.this).getScaledTouchSlop();
        private int initialX;
        private int initialY;
        private float initialTouchX;
        private float initialTouchY;
        private boolean moved;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (layoutParams == null || windowManager == null) {
                return false;
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    cancelSnapAnimator();
                    initialX = layoutParams.x;
                    initialY = layoutParams.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int deltaX = (int) (event.getRawX() - initialTouchX);
                    int deltaY = (int) (event.getRawY() - initialTouchY);
                    if (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop) {
                        if (!moved) {
                            moved = true;
                            v.setPressed(false);
                        }
                    }
                    int overlayWidth = getOverlayWidth();
                    int overlayHeight = getOverlayHeight();
                    int screenWidth = getResources().getDisplayMetrics().widthPixels;
                    int screenHeight = getResources().getDisplayMetrics().heightPixels;
                    int edgeMargin = dp(EDGE_MARGIN_DP);
                    int maxX = Math.max(edgeMargin, screenWidth - overlayWidth - edgeMargin);
                    int maxY = Math.max(edgeMargin, screenHeight - overlayHeight - edgeMargin);
                    layoutParams.x = clamp(initialX + deltaX, edgeMargin, maxX);
                    layoutParams.y = clamp(initialY + deltaY, edgeMargin, maxY);
                    safelyUpdateLayout();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    v.setPressed(false);
                    if (!moved && event.getAction() == MotionEvent.ACTION_UP) {
                        toggleExpanded();
                        v.performClick();
                    } else if (moved) {
                        snappedToRight = event.getRawX() >= getResources().getDisplayMetrics().widthPixels / 2f;
                        if (rootView != null) {
                            rootView.post(() -> updateOverlayPosition(true));
                        } else {
                            updateOverlayPosition(true);
                        }
                    }
                    return true;
                default:
                    return false;
            }
        }
    }
}
