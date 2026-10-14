package com.folddesk.app;

import android.inputmethodservice.InputMethodService;
import android.graphics.Color;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;

public class FoldDeskImeService extends InputMethodService {
    private static volatile FoldDeskImeService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public View onCreateInputView() {
        View spacer = new View(this);
        spacer.setBackgroundColor(Color.TRANSPARENT);
        spacer.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return spacer;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean sendKey(String key) {
        FoldDeskImeService s = instance;
        if (s == null) return false;
        InputConnection ic = s.getCurrentInputConnection();
        if (ic == null) return false;

        switch (key) {
            case "⌫":
                return ic.deleteSurroundingText(1, 0);
            case "ENTER":
                boolean down = ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                boolean up = ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
                return down || up;
            case "SPACE":
                return ic.commitText(" ", 1);
            case "TAB":
                boolean td = ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB));
                boolean tu = ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB));
                return td || tu;
            default:
                return ic.commitText(key, 1);
        }
    }
}
