// Adapted from FoldCraftLauncher (FCL-Team) control editor concept, GPL-3.0
// https://github.com/FCL-Team/FoldCraftLauncher
package com.epai.oblender.control;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.epai.oblender.OblSettingFragment;
import java.util.UUID;

public class ControlOverlayView extends View {
    private static final String TAG = "ControlOverlay";

    private ViewManager mViewManager = new ViewManager();
    private boolean mEditMode = false;
    private int mControlsDragIndex = -1;
    private boolean mControlsMoved = false;
    private float mDragStartX, mDragStartY;
    private boolean mHitButton = false;

    private Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mW, mH;
    private int mBg = 0xFF1A1A2E;

    /* Bottom bar rects */
    private Rect mCloseRect, mEditRect, mAddRect;

    private OblSettingFragment.OBLSettingFragmentListener mListener;

    public ControlOverlayView(Context context) { super(context); init(); }
    public ControlOverlayView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public ControlOverlayView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        mViewManager.setKeySender(new ViewManager.KeySender() {
            public void enterKeyOn(int[] keys) { if (mListener != null) mListener.enterKeyOn(keys); }
            public void enterKeyOff(int[] keys) { if (mListener != null) mListener.enterKeyOff(keys); }
            public void enterKey(int[] keys) { if (mListener != null) mListener.enterKey(keys); }
        });
        mViewManager.load(getContext());
    }

    public void setListener(OblSettingFragment.OBLSettingFragmentListener l) { mListener = l; }
    public ViewManager getViewManager() { return mViewManager; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mW = getWidth(); mH = getHeight();
        if (mW <= 0 || mH <= 0) return;

        mPaint.setColor(mBg);
        canvas.drawRoundRect(0, 0, mW, mH, 16, 16, mPaint);

        int gridH = mH - (int)(mH * 0.09f);

        /* Bottom bar */
        float tabBarH = mH * 0.09f;
        float btw = mW / 4f;

        mPaint.setColor(0xFF0F0F23);
        canvas.drawRect(0, gridH, mW, mH, mPaint);

        /* Edit toggle */
        mEditRect = new Rect(0, gridH, (int)btw, mH);
        mPaint.setColor(mEditMode ? 0xFF1B5E20 : 0xFF4A4A7A);
        canvas.drawRoundRect(new RectF(mEditRect), 8, 8, mPaint);
        mPaint.setColor(mEditMode ? 0xFFA5D6A7 : 0xFF9999AA);
        mPaint.setTextSize(tabBarH * 0.38f);
        Paint.FontMetrics fm = mPaint.getFontMetrics();
        float d = (fm.bottom - fm.top) / 2f - fm.bottom;
        canvas.drawText("Edit", mEditRect.exactCenterX(), mEditRect.exactCenterY() + d, mPaint);

        /* Add button */
        mAddRect = new Rect((int)btw, gridH, (int)(btw * 2), mH);
        mPaint.setColor(mEditMode ? 0xFF2ECC71 : 0xFF4A4A7A);
        canvas.drawRoundRect(new RectF(mAddRect), 8, 8, mPaint);
        mPaint.setColor(Color.WHITE);
        canvas.drawText("+ Add", mAddRect.exactCenterX(), mAddRect.exactCenterY() + d, mPaint);

        /* Close button */
        mCloseRect = new Rect((int)(btw * 3), gridH, mW, mH);
        mPaint.setColor(0xFF7A2A2A);
        canvas.drawRoundRect(new RectF(mCloseRect), 8, 8, mPaint);
        mPaint.setColor(Color.WHITE);
        canvas.drawText("\u2716 Close", mCloseRect.exactCenterX(), mCloseRect.exactCenterY() + d, mPaint);

        /* Draw control buttons in the grid area */
        mViewManager.setScreenSize(mW, gridH);
        int save = canvas.save();
        canvas.clipRect(0, 0, mW, gridH);
        mViewManager.draw(canvas);
        canvas.restoreToCount(save);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX(), y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                mDragStartX = x; mDragStartY = y;
                mHitButton = false;

                /* Bottom bar */
                if (mCloseRect != null && mCloseRect.contains((int)x, (int)y)) {
                    setVisibility(INVISIBLE);
                    mHitButton = true;
                    return true;
                }
                if (mEditRect != null && mEditRect.contains((int)x, (int)y)) {
                    mEditMode = !mEditMode;
                    mViewManager.setShowBoundaries(mEditMode);
                    mHitButton = true;
                    invalidate();
                    return true;
                }
                if (mAddRect != null && mAddRect.contains((int)x, (int)y)) {
                    mHitButton = true;
                    if (mEditMode) showAddButton();
                    return true;
                }

                /* Hit test buttons */
                mViewManager.setScreenSize(mW, mH - (int)(mH * 0.09f));
                int hit = mViewManager.hitTest(x, y);
                if (hit >= 0) {
                    mHitButton = true;
                    if (mEditMode) {
                        mControlsDragIndex = hit;
                        mControlsMoved = false;
                        mViewManager.startDrag(hit, x, y);
                    } else {
                        mViewManager.fireButton(hit);
                    }
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mEditMode && mControlsDragIndex >= 0) {
                    float cdx = Math.abs(x - mDragStartX);
                    float cdy = Math.abs(y - mDragStartY);
                    if (!mControlsMoved && (cdx > 15f || cdy > 15f)) {
                        mControlsMoved = true;
                    }
                    if (mControlsMoved) {
                        mViewManager.dragTo(x, y);
                        invalidate();
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: {
                if (mControlsDragIndex >= 0) {
                    mViewManager.endDrag();
                    if (!mControlsMoved && mEditMode) {
                        /* Tap → open editor */
                        ControlButtonData data = mViewManager.getButtons().get(mControlsDragIndex);
                        EditViewDialog.show(getContext(), data, false, new EditViewDialog.Callback() {
                            public void onSave(ControlButtonData d) {
                                mViewManager.updateButton(data.getId(), d);
                                mViewManager.save(getContext());
                                invalidate();
                            }
                            public void onDelete() {
                                mViewManager.removeButton(data.getId());
                                mViewManager.save(getContext());
                                invalidate();
                            }
                            public void onClone(ControlButtonData d) {
                                mViewManager.addButton(d);
                                mViewManager.save(getContext());
                                invalidate();
                            }
                        });
                    }
                    mControlsDragIndex = -1;
                    mViewManager.save(getContext());
                }
                invalidate(); performClick();
                break;
            }
        }
        return true;
    }

    private void showAddButton() {
        ControlButtonData data = new ControlButtonData(UUID.randomUUID().toString());
        EditViewDialog.show(getContext(), data, true, new EditViewDialog.Callback() {
            public void onSave(ControlButtonData d) {
                mViewManager.addButton(d);
                mViewManager.save(getContext());
                invalidate();
            }
            public void onDelete() {}
            public void onClone(ControlButtonData d) {
                mViewManager.addButton(d);
                mViewManager.save(getContext());
                invalidate();
            }
        });
    }
}
