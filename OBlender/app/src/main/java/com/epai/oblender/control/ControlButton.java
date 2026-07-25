// Based on FoldCraftLauncher (FCL-Team) ControlButton, GPL-3.0
// https://github.com/FCL-Team/FoldCraftLauncher
package com.epai.oblender.control;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.epai.oblender.OblSettingFragment;

import java.util.List;

public class ControlButton extends View {

    public interface EditCallback {
        void onSave(ControlButtonData data);
        void onDelete(String id);
        void onClone(ControlButtonData data);
    }

    private ControlButtonData data;
    private EditCallback editCallback;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boundaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean parentVisible = true;
    private boolean showBoundary = false;

    /* Touch state */
    private float downX, downY;
    private float posX, posY;
    private long downTime;
    private boolean pressed = false;
    private boolean longPressFired = false;
    private final Handler handler = new Handler();
    private Runnable longPressRunnable;

    private int screenW, screenH;

    private OblSettingFragment.OBLSettingFragmentListener keyListener;

    public ControlButton(Context context, ControlButtonData data, OblSettingFragment.OBLSettingFragmentListener listener) {
        super(context);
        this.data = data;
        this.keyListener = listener;
        boundaryPaint.setStyle(Paint.Style.STROKE);
        boundaryPaint.setStrokeWidth(3);
        boundaryPaint.setColor(0xFFFF4444);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        longPressRunnable = () -> {
            longPressFired = true;
            fireLongPress();
        };
        updateFromData();
    }

    public void setEditCallback(EditCallback cb) { this.editCallback = cb; }
    public ControlButtonData getData() { return data; }
    public String getViewId() { return data.getId(); }
    public void setShowBoundary(boolean v) { showBoundary = v; invalidate(); }
    public void setParentVisible(boolean v) { parentVisible = v; setVisibility(v ? VISIBLE : GONE); }

    public void setScreenSize(int w, int h) {
        screenW = w;
        screenH = h;
    }

    public void updateFromData() {
        ControlButtonStyle style = data.getStyle();
        fillPaint.setColor(style.getFillColor());
        strokePaint.setColor(style.getStrokeColor());
        strokePaint.setStrokeWidth(style.getStrokeWidth());
        strokePaint.setStyle(Paint.Style.STROKE);
        textPaint.setColor(style.getTextColor());
        textPaint.setTextSize(style.getTextSize());

        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp == null) lp = new FrameLayout.LayoutParams(100, 100);
        BaseInfoData bi = data.getBaseInfo();
        lp.width = bi.computeWidth(screenW, screenH);
        lp.height = bi.computeHeight(screenW, screenH);
        setLayoutParams(lp);
        setX(bi.computeX(screenW, lp.width));
        setY(bi.computeY(screenH, lp.height));
        requestLayout();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float r = Math.min(8, Math.min(w, h) * 0.1f);

        /* Fill */
        canvas.drawRoundRect(0, 0, w, h, r, r, fillPaint);

        /* Stroke */
        canvas.drawRoundRect(0, 0, w, h, r, r, strokePaint);

        /* Text */
        String text = data.getText();
        if (!text.isEmpty()) {
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float d = (fm.bottom - fm.top) / 2f - fm.bottom;
            canvas.drawText(text, w / 2f, h / 2f + d, textPaint);
        }

        /* Boundary (edit mode) */
        if (showBoundary) {
            canvas.drawRoundRect(0, 0, w, h, r, r, boundaryPaint);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (editCallback != null) {
            return handleEditTouch(event);
        } else {
            return handlePlayTouch(event);
        }
    }

    private boolean handleEditTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                posX = getX();
                posY = getY();
                downTime = System.currentTimeMillis();
                pressed = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (pressed) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    float tx = Math.max(0, Math.min(screenW - getWidth(), getX() + dx));
                    float ty = Math.max(0, Math.min(screenH - getHeight(), getY() + dy));
                    setX(tx);
                    setY(ty);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                boolean isTap = System.currentTimeMillis() - downTime <= 150
                        && Math.abs(event.getX() - downX) <= 15
                        && Math.abs(event.getY() - downY) <= 15;
                if (isTap) {
                    /* Revert position and open editor */
                    setX(posX);
                    setY(posY);
                    EditViewDialog.show(getContext(), data, false, new EditViewDialog.Callback() {
                        public void onSave(ControlButtonData d) {
                            if (editCallback != null) editCallback.onSave(d);
                        }
                        public void onDelete() {
                            if (editCallback != null) editCallback.onDelete(data.getId());
                        }
                        public void onClone(ControlButtonData d) {
                            if (editCallback != null) editCallback.onClone(d);
                        }
                    });
                } else {
                    /* Save new position */
                    int bw = getWidth();
                    int bh = getHeight();
                    int nx = (int) ((1000 * getX()) / Math.max(1, screenW - bw));
                    int ny = (int) ((1000 * getY()) / Math.max(1, screenH - bh));
                    data.getBaseInfo().setXPosition(nx);
                    data.getBaseInfo().setYPosition(ny);
                    if (editCallback != null) editCallback.onSave(data);
                }
                break;
        }
        return true;
    }

    private boolean handlePlayTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                downTime = System.currentTimeMillis();
                longPressFired = false;
                handler.postDelayed(longPressRunnable, 400);
                firePress(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - downX) > 15 || Math.abs(event.getY() - downY) > 15) {
                    handler.removeCallbacks(longPressRunnable);
                }
                if (data.getEvent().isPointerFollow() && getParent() != null) {
                    /* Pointer follow mode — handled by container */
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressRunnable);
                firePress(false);
                if (longPressFired) {
                    fireLongPress(false);
                }
                if (System.currentTimeMillis() - downTime <= 150
                        && Math.abs(event.getX() - downX) <= 15
                        && Math.abs(event.getY() - downY) <= 15) {
                    fireClick();
                }
                break;
        }
        return true;
    }

    private void firePress(boolean on) {
        if (keyListener == null) return;
        ButtonEventData.Event ev = data.getEvent().getPressEvent();
        if (ev == null || ev.getKeycodes().isEmpty()) return;
        int[] keys = toIntArray(ev.getKeycodes());
        if (on) {
            keyListener.enterKeyOn(keys);
        } else if (!ev.isAutoKeep()) {
            keyListener.enterKeyOff(keys);
        }
    }

    private void fireLongPress() {
        longPressFired = true;
        if (keyListener == null) return;
        ButtonEventData.Event ev = data.getEvent().getLongPressEvent();
        if (ev == null || ev.getKeycodes().isEmpty()) return;
        int[] keys = toIntArray(ev.getKeycodes());
        if (ev.isAutoKeep()) keyListener.enterKeyOn(keys);
        else keyListener.enterKey(keys);
    }

    private void fireLongPress(boolean on) {
        if (keyListener == null) return;
        ButtonEventData.Event ev = data.getEvent().getLongPressEvent();
        if (ev == null || ev.getKeycodes().isEmpty()) return;
        int[] keys = toIntArray(ev.getKeycodes());
        if (!on && !ev.isAutoKeep()) keyListener.enterKeyOff(keys);
    }

    private void fireClick() {
        if (keyListener == null) return;
        ButtonEventData.Event ev = data.getEvent().getClickEvent();
        if (ev == null || ev.getKeycodes().isEmpty()) return;
        int[] keys = toIntArray(ev.getKeycodes());
        if (ev.isAutoKeep()) {
            keyListener.enterKeyOn(keys);
        } else if (ev.isAutoClick()) {
            keyListener.enterKey(keys);
        } else {
            keyListener.enterKeyOn(keys);
            keyListener.enterKeyOff(keys);
        }
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }
}
