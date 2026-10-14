package com.folddesk.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class FoldDeskAccessibilityService extends AccessibilityService {
    private static volatile FoldDeskAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkgCs = event.getPackageName();
        CharSequence clsCs = event.getClassName();
        String pkg = pkgCs == null ? "" : pkgCs.toString();
        String cls = clsCs == null ? "" : clsCs.toString();

        if (getPackageName().equals(pkg) && cls.contains("MainActivity")) {
            try { stopService(new Intent(this, OverlayControlService.class)); } catch (Throwable ignored) { }
            return;
        }

        if (!pkg.isEmpty() && !getPackageName().equals(pkg) && Settings.canDrawOverlays(this)) {
            try { startService(new Intent(this, OverlayControlService.class)); } catch (Throwable ignored) { }
        }
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean tap(float x, float y) {
        FoldDeskAccessibilityService s = instance;
        if (s == null || Build.VERSION.SDK_INT < 24) return false;
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(p, 0, 60);
        return s.dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    public static boolean longPress(float x, float y) {
        FoldDeskAccessibilityService s = instance;
        if (s == null || Build.VERSION.SDK_INT < 24) return false;
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(p, 0, 650);
        return s.dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    public static boolean scroll(float x, float y, float deltaY) {
        FoldDeskAccessibilityService s = instance;
        if (s == null || Build.VERSION.SDK_INT < 24) return false;
        float distance = Math.max(90f, Math.min(260f, Math.abs(deltaY) * 3.2f));
        float endY = deltaY < 0 ? y - distance : y + distance;
        if (endY < 80f) endY = 80f;
        Path p = new Path();
        p.moveTo(x, y);
        p.lineTo(x, endY);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(p, 0, 180);
        return s.dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    public static boolean setTextFallback(String text) {
        FoldDeskAccessibilityService s = instance;
        if (s == null) return false;
        AccessibilityNodeInfo root = s.getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null || !focused.isEditable()) return false;
        CharSequence old = focused.getText();
        String next = (old == null ? "" : old.toString()) + text;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, next);
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    public static boolean backspaceFallback() {
        FoldDeskAccessibilityService s = instance;
        if (s == null) return false;
        AccessibilityNodeInfo root = s.getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null || !focused.isEditable()) return false;
        String old = focused.getText() == null ? "" : focused.getText().toString();
        if (old.isEmpty()) return true;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, old.substring(0, old.length() - 1));
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }
}
