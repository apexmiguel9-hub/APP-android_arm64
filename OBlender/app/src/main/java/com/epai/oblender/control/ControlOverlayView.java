// Adapted from FoldCraftLauncher (FCL-Team) control editor concept, GPL-3.0
// https://github.com/FCL-Team/FoldCraftLauncher
package com.epai.oblender.control;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import com.epai.oblender.OblSettingFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ControlOverlayView extends FrameLayout {

    /* Bottom bar */
    private Button editBtn, addBtn, closeBtn;
    private boolean editMode = false;
    private boolean initialized = false;

    /* Content area that holds control buttons */
    private FrameLayout gridArea;
    private List<ControlButton> buttons = new ArrayList<>();
    private ViewManager persistence;

    private OblSettingFragment.OBLSettingFragmentListener keyListener;

    private int screenW, screenH;

    /* Drag handle */
    private View dragHandle;
    private boolean dragging = false;
    private float dragStartX, dragStartY;
    private int windowStartX, windowStartY;
    private WindowPositionCallback windowPosCallback;
    private boolean inLayoutUpdate = false;

    public interface WindowPositionCallback {
        void setPosition(int x, int y);
    }
    public void setWindowPosCallback(WindowPositionCallback cb) { windowPosCallback = cb; }

    public ControlOverlayView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(0xFF1A1A2E);

        persistence = new ViewManager();
        persistence.load(context);
    }

    /* Called by OBLNativeActivity */
    public void setListener(OblSettingFragment.OBLSettingFragmentListener l) {
        setKeyListener(l);
    }

    public void setKeyListener(OblSettingFragment.OBLSettingFragmentListener l) {
        keyListener = l;
        persistence.setKeySender(new ViewManager.KeySender() {
            public void enterKeyOn(int[] keys) { if (keyListener != null) keyListener.enterKeyOn(keys); }
            public void enterKeyOff(int[] keys) { if (keyListener != null) keyListener.enterKeyOff(keys); }
            public void enterKey(int[] keys) { if (keyListener != null) keyListener.enterKey(keys); }
        });
    }

    public ViewManager getPersistence() { return persistence; }

    private void ensureChildren() {
        if (initialized) return;
        initialized = true;

        int density = (int) getResources().getDisplayMetrics().density;

        /* ── Drag handle at top ── */
        dragHandle = new View(getContext());
        dragHandle.setBackgroundColor(0x44000000);
        LayoutParams dlp = new LayoutParams(LayoutParams.MATCH_PARENT, 24 * density);
        addView(dragHandle, dlp);
        dragHandle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = true;
                    dragStartX = event.getRawX();
                    dragStartY = event.getRawY();
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (dragging && windowPosCallback != null) {
                        int dx = (int) (event.getRawX() - dragStartX);
                        int dy = (int) (event.getRawY() - dragStartY);
                        windowPosCallback.setPosition(windowStartX + dx, windowStartY + dy);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    break;
            }
            return true;
        });

        /* ── Grid area (scrollable content) ── */
        gridArea = new FrameLayout(getContext());
        gridArea.setBackgroundColor(0xFF2A2A3E);
        LayoutParams glp = new LayoutParams(LayoutParams.MATCH_PARENT, 0);
        glp.topMargin = 24 * density;
        glp.bottomMargin = (int) (40 * density);
        addView(gridArea, glp);

        /* ── Bottom bar buttons ── */
        int btnSize = (int) (100 * density);
        int barH = (int) (40 * density);

        editBtn = new Button(getContext());
        editBtn.setText("Edit");
        editBtn.setTextColor(Color.WHITE);
        editBtn.setTextSize(11);
        editBtn.setBackgroundColor(0xFF4A4A7A);
        editBtn.setOnClickListener(v -> toggleEditMode());

        addBtn = new Button(getContext());
        addBtn.setText("+");
        addBtn.setTextColor(Color.WHITE);
        addBtn.setTextSize(15);
        addBtn.setBackgroundColor(0xFF2ECC71);
        addBtn.setOnClickListener(v -> {
            if (!editMode) toggleEditMode();
            showAddDialog();
        });

        closeBtn = new Button(getContext());
        closeBtn.setText("X");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(15);
        closeBtn.setBackgroundColor(0xFF7A2A2A);
        closeBtn.setOnClickListener(v -> setVisibility(GONE));

        LayoutParams lp;
        lp = new LayoutParams(btnSize, barH);
        lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        lp.setMargins(10, 0, 0, 0);
        addView(editBtn, lp);

        lp = new LayoutParams(btnSize, barH);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.setMargins(0, 0, 0, 0);
        addView(addBtn, lp);

        lp = new LayoutParams(btnSize, barH);
        lp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        lp.setMargins(0, 0, 10, 0);
        addView(closeBtn, lp);

        /* ── Load persisted buttons ── */
        for (ControlButtonData data : persistence.getButtons()) {
            addControlButton(data, false);
        }
    }

    private void toggleEditMode() {
        editMode = !editMode;
        editBtn.setBackgroundColor(editMode ? 0xFF1B5E20 : 0xFF4A4A7A);
        for (ControlButton btn : buttons) {
            btn.setShowBoundary(editMode);
        }
    }

    private void showAddDialog() {
        ControlButtonData data = new ControlButtonData(UUID.randomUUID().toString());
        data.getBaseInfo().setXPosition(100);
        data.getBaseInfo().setYPosition(100);
        data.getBaseInfo().setSizeType(BaseInfoData.SizeType.ABSOLUTE);
        data.getBaseInfo().setAbsoluteWidth(60);
        data.getBaseInfo().setAbsoluteHeight(60);
        EditViewDialog.show(getContext(), data, true, new EditViewDialog.Callback() {
            public void onSave(ControlButtonData d) {
                addControlButton(d, true);
            }
            public void onDelete() {}
            public void onClone(ControlButtonData d) {
                addControlButton(d, true);
            }
        });
    }

    private void addControlButton(ControlButtonData data, boolean persist) {
        final ControlButton btn = new ControlButton(getContext(), data, keyListener);
        btn.setEditCallback(new ControlButton.EditCallback() {
            public void onSave(ControlButtonData d) {
                btn.updateFromData();
                if (persist) persistence.save(getContext());
            }
            public void onDelete(String id) {
                removeControlButton(btn);
            }
            public void onClone(ControlButtonData d) {
                addControlButton(d, true);
            }
        });
        btn.setShowBoundary(editMode);
        btn.setScreenSize(screenW, screenH);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.width = data.getBaseInfo().computeWidth(screenW, screenH);
        lp.height = data.getBaseInfo().computeHeight(screenW, screenH);
        btn.setLayoutParams(lp);
        btn.setX(data.getBaseInfo().computeX(screenW, lp.width));
        btn.setY(data.getBaseInfo().computeY(screenH, lp.height));

        gridArea.addView(btn);
        buttons.add(btn);

        if (persist) {
            persistence.addButton(data);
            persistence.save(getContext());
        }
    }

    private void removeControlButton(ControlButton btn) {
        persistence.removeButton(btn.getViewId());
        persistence.save(getContext());
        buttons.remove(btn);
        gridArea.removeView(btn);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        screenW = right - left;
        screenH = bottom - top;
        ensureChildren();
        /* Update grid area size */
        if (gridArea != null) {
            ViewGroup.LayoutParams glp = gridArea.getLayoutParams();
            glp.height = screenH - (int)(40 * getResources().getDisplayMetrics().density)
                    - (int)(24 * getResources().getDisplayMetrics().density);
            gridArea.setLayoutParams(glp);
        }
        /* Update control button positions (safe guard avoids re-entrant layout loop) */
        if (!inLayoutUpdate) {
            inLayoutUpdate = true;
            for (ControlButton btn : buttons) {
                btn.setScreenSize(screenW, screenH);
                btn.updateFromData();
            }
            inLayoutUpdate = false;
        }
    }

    public void setWindowStartPosition(int x, int y) {
        windowStartX = x;
        windowStartY = y;
    }
}
