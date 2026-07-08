package com.epai.oblender;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class OblSettingFragment extends View {
    private final String TAG = "设置页面";

    private int mColorBG = Color.valueOf(0.64f, 0.64f, 0.64f).toArgb();
    private int mColorControlBtnTxt = Color.valueOf(1.0f, 1.0f, 1.0f).toArgb();
    private int mColorControlBtnBG = Color.valueOf(0.6f, 0.6f, 0.6f).toArgb();
    private int mColorSelectedPageBG = Color.valueOf(0.4f, 0.0f, 0.0f).toArgb();
    private int mColorBtnTxt = Color.valueOf(1.0f, 1.0f, 1.0f).toArgb();
    private int mColorBtnBG = Color.valueOf(0.45f, 0.45f, 0.45f).toArgb();
    private int mColorSelectedBtnBG = Color.valueOf(0.55f, 0.0f, 0.0f).toArgb();
    private int mColorClickEffect = Color.valueOf(0.0f, 0.55f, 0.0f).toArgb();
    private int mIntOffset = 5;
    private Paint mPaint;
    private int mIntWidth = 0;
    private int mIntHeight = 0;
    private int mIntCurrentPage = 0;
    private int mIntTotalRow = 5;
    private int mIntTotalColumn = 6;
    private ArrayList<OBLBtn> mOBLBtns = null;
    OBLControlBtn mOBLControlBtnPage1 = null;
    OBLControlBtn mOBLControlBtnPage2 = null;
    OBLControlBtn mOBLControlBtnPage3 = null;
    OBLControlBtn mOBLControlBtnPage4 = null;
    OBLControlBtn mOBLControlBtnMove=null;
    OBLControlBtn mOBLControlBtnClose = null;
    private OBLBtn mOBLBtnCtrl = null;
    private OBLBtn mOBLBtnAlt = null;
    private OBLBtn mOBLBtnShift = null;
    private OBLBtn mOBLBtnMMB = null;
    private OBLBtn mOBLBtnRMB = null;

    private static final int PAGE_SHORTCUTS = 3;
    private static final int MODE_NORMAL = 0;
    private static final int MODE_ADD_SHORTCUT = 1;
    private static final String PREFS_NAME = "obl_shortcuts";
    private static final String PREFS_JSON = "shortcuts";

    private int mMode = MODE_NORMAL;
    private ArrayList<ShortcutItem> mShortcuts = new ArrayList<>();
    private ArrayList<Integer> mSelModifiers = new ArrayList<>();
    private String mSelDisplay = "";

    private class ShortcutItem {
        String name;
        ArrayList<Integer> keyOrdinals;
        ShortcutItem(String n, ArrayList<Integer> k) { name = n; keyOrdinals = k; }
    }

    private enum OBLControlBtnID {
        OBL_CONTROL_BTN_ID_PAGE1,
        OBL_CONTROL_BTN_ID_PAGE2,
        OBL_CONTROL_BTN_ID_PAGE3,
        OBL_CONTROL_BTN_ID_PAGE4,
        OBL_CONTROL_BTN_ID_MOVE,
        OBL_CONTROL_BTN_ID_CLOSE
    }

    private class OBLBtnBase {
        private boolean mBooleanEffect = false;

        public boolean isBooleanEffect() {
            return mBooleanEffect;
        }

        public void addClickEffect() {
            mBooleanEffect = true;
        }

        public void clearClickEffect() {
            mBooleanEffect = false;
        }
    }

    private class OBLControlBtn extends OBLBtnBase {
        private String mStringText = "";
        private Rect mRect = null;
        private Rect mRectInner = null;
        private OBLControlBtnID mOBLControlBtnID;
        private boolean mBooleanSelected = false;

        OBLControlBtn(OBLControlBtnID oblControlBtnID, String stringText) {
            mStringText = stringText;
            mOBLControlBtnID = oblControlBtnID;
        }

        public void draw(Canvas canvas, Paint paint) {
            if (mRectInner != null) {
                if (isBooleanEffect()) {
                    mPaint.setColor(mColorClickEffect);
                } else {
                    if (mBooleanSelected) {
                        mPaint.setColor(mColorSelectedPageBG);
                    } else {
                        mPaint.setColor(mColorControlBtnBG);
                    }
                }
                canvas.drawRect(mRectInner, paint);
                mPaint.setColor(mColorControlBtnTxt);
                Paint.FontMetrics fontMetrics = paint.getFontMetrics();
                float distance = (fontMetrics.bottom - fontMetrics.top) / 2 - fontMetrics.bottom;
                canvas.drawText(mStringText, mRectInner.centerX(), mRectInner.centerY() + distance, paint);
            }
        }

        public void setGeometry(int left, int top, int right, int bottom) {
            if (mRect == null) {
                mRect = new Rect();
                mRect.set(left, top, right, bottom);
                mRectInner = new Rect();
                mRectInner.set(left + mIntOffset, top + mIntOffset, right - mIntOffset, bottom - mIntOffset);
            }
        }

        public boolean hitTest(float x, float y) {
            return mRectInner.contains((int) x, (int) y);
        }

        public void setSelected(boolean booleanSelected) {
            mBooleanSelected = booleanSelected;
        }

        public boolean isBooleanSelected() {
            return mBooleanSelected;
        }
    }

    private class OBLBtn extends OBLBtnBase {
        private boolean mBooleanIsSelected = false;
        private int mIntSelected = 0;
        private OBLButtonID mOBLButtonID;
        private String mStringText = "";
        private int mIntPageIndex = 0;
        private int mIntRowIndex = 0;
        private int mIntColIndex = 0;
        private Rect mRect = null;
        private Rect mRectInner = null;

        public OBLBtn(OBLButtonID oblButtonID, String stringText, int intSelected) {
            mStringText = stringText;
            mOBLButtonID = oblButtonID;
            mIntSelected = intSelected;
        }

        public void setPosition(int intRowIndex, int intColIndex, int intPageIndex) {
            mIntRowIndex = intRowIndex;
            mIntColIndex = intColIndex;
            mIntPageIndex = intPageIndex;
        }

        public int getIntPageIndex() {
            return mIntPageIndex;
        }

        public int getIntSelected() {
            return mIntSelected;
        }

        public void setSelected(boolean booleanIsSelected) {
            mBooleanIsSelected = booleanIsSelected;
        }

        public boolean isSelected() {
            return mBooleanIsSelected;
        }

        public OBLButtonID getOBLButtonID() {
            return mOBLButtonID;
        }

        public void draw(Canvas canvas, Paint paint) {
            if (mRectInner != null) {
                if (isBooleanEffect()) {
                    mPaint.setColor(mColorClickEffect);
                } else {
                    if (mBooleanIsSelected) {
                        mPaint.setColor(mColorSelectedBtnBG);
                    } else {
                        mPaint.setColor(mColorBtnBG);
                    }
                }
                canvas.drawRect(mRectInner, paint);
                mPaint.setColor(mColorBtnTxt);
                Paint.FontMetrics fontMetrics = paint.getFontMetrics();
                float distance = (fontMetrics.bottom - fontMetrics.top) / 2 - fontMetrics.bottom;
                canvas.drawText(mStringText, mRectInner.centerX(), mRectInner.centerY() + distance, paint);
            }
        }

        public void setGeometry(Rect rect) {
            mRect = rect;
        }

        public void setGeometry(int totalWidth, int totalHeight, int totalRow, int totalColumn) {
            if (mIntPageIndex < 0) {
                return;
            }
            if (mRect == null) {
                mRect = new Rect();
                int x1 = totalWidth / totalColumn * mIntColIndex;
                int x2 = totalWidth / totalColumn * (mIntColIndex + 1);
                int y1 = totalHeight / totalRow * mIntRowIndex;
                int y2 = totalHeight / totalRow * (mIntRowIndex + 1);
                mRect.set(x1, y1, x2, y2);

                mRectInner = new Rect();
                mRectInner.set(x1 + mIntOffset, y1 + mIntOffset, x2 - mIntOffset, y2 - mIntOffset);
            }
        }

        public boolean hitTest(float x, float y) {
            return mRectInner.contains((int) x, (int) y);
        }
    }

    public OblSettingFragment(Context context) {
        super(context);
        initial();
    }

    public OblSettingFragment(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initial();
    }

    public OblSettingFragment(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initial();
    }

    public OblSettingFragment(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initial();
    }

    public void initial() {
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setStrokeWidth(1);
        mPaint.setTextSize(mPaint.getTextSize() * 1.5f);

        mOBLBtns = new ArrayList<>();

        // Page 0: Modifiers + QWERTY top half
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Esc, "Esc", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Tab, "Tab", 0));
        mOBLBtnShift = new OBLBtn(OBLButtonID.OBLButtonID_Shift, "Shift", 1);
        mOBLBtns.add(mOBLBtnShift);
        mOBLBtnCtrl = new OBLBtn(OBLButtonID.OBLButtonID_Ctrl, "Ctrl", 1);
        mOBLBtns.add(mOBLBtnCtrl);
        mOBLBtnAlt = new OBLBtn(OBLButtonID.OBLButtonID_Alt, "Alt", 1);
        mOBLBtns.add(mOBLBtnAlt);
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Space, "Spac.", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Q, "Q", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_W, "W", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_E, "E", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_R, "R", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_T, "T", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Y, "Y", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_U, "U", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_I, "I", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_O, "O", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_P, "P", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_LeftBracket, "[", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_RightBracket, "]", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_A, "A", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_S, "S", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_D, "D", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_F, "F", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_G, "G", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_H, "H", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_J, "J", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_K, "K", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_L, "L", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Z, "Z", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_X, "X", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_C, "C", 0));

        // Page 1: QWERTY bottom half + Numbers + Punctuation
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_V, "V", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_B, "B", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_N, "N", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_M, "M", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Tilde, "`", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_COMMA, ",", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_PEROID, ".", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_LeftSlash, "/", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_1, "1", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_2, "2", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_3, "3", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_4, "4", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_5, "5", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_6, "6", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_7, "7", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_8, "8", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_9, "9", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_0, "0", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Minus, "-", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Equal, "=", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Semicolon, ";", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Apostrophe, "'", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Insert, "Ins.", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Home, "Home", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_End, "End", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_PgUp, "PgUp.", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_PgDn, "PgDn.", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Delete, "Del.", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Enter, "Enter", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_UpArrow, "↑", 0));

        // Page 2: F-keys + Numpad + Navigation
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_F1, "F1", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_F2, "F2", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_F3, "F3", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_F4, "F4", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_F5, "F5", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_F6, "F6", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_F7, "F7", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_F8, "F8", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_F9, "F9", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_F10, "F10", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_F11, "F11", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_F12, "F12", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Num_0, "N.0", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Num_1, "N.1", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Num_2, "N.2", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Num_3, "N.3", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Num_4, "N.4", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Num_5, "N.5", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Num_Plus, "N.+", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Num_Minus, "N.-", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Num_Asterisk, "N.*", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Num_Slash, "N./", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Num_Period, "N..", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_Num_Enter, "N.Ent", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_LeftArrow, "←", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_RightArrow, "→", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_DownArrow, "↓", 0));
        mOBLBtns.add(new OBLBtn(OBLButtonID.OBLButtonID_ScrollDown, "Scr.⬇", 0));
        mOBLBtnMMB = new OBLBtn(OBLButtonID.OBLButtonID_Scroll, "MMB", 1);
        mOBLBtns.add(mOBLBtnMMB);
        mOBLBtnRMB = new OBLBtn(OBLButtonID.OBLButtonID_RightBtn, "RMB", 1);
        mOBLBtns.add(mOBLBtnRMB);

        int pageItemNum = mIntTotalRow * mIntTotalColumn;
        for (int i = 0; i < mOBLBtns.size(); i++) {
            int index = i % pageItemNum;
            int pageIndex=i/pageItemNum;
            mOBLBtns.get(i).setPosition(index / mIntTotalColumn, index % mIntTotalColumn,pageIndex);
        }

        mOBLControlBtnPage1 = new OBLControlBtn(OBLControlBtnID.OBL_CONTROL_BTN_ID_PAGE1, "P1");
        mOBLControlBtnPage2 = new OBLControlBtn(OBLControlBtnID.OBL_CONTROL_BTN_ID_PAGE2, "P2");
        mOBLControlBtnPage3 = new OBLControlBtn(OBLControlBtnID.OBL_CONTROL_BTN_ID_PAGE3, "P3");
        mOBLControlBtnPage4 = new OBLControlBtn(OBLControlBtnID.OBL_CONTROL_BTN_ID_PAGE4, "SC");
        mOBLControlBtnMove=new OBLControlBtn(OBLControlBtnID.OBL_CONTROL_BTN_ID_MOVE,"Move");
        mOBLControlBtnClose = new OBLControlBtn(OBLControlBtnID.OBL_CONTROL_BTN_ID_CLOSE, "Close");

        mOBLControlBtnPage1.setSelected(true);

        loadShortcuts();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Log.i(TAG, "绘制 1");
        super.onDraw(canvas);
        Log.i(TAG, "绘制 2");
        int getWidthValue = getWidth();
        Log.i(TAG, "绘制 3");
        if ((mIntWidth <= 0) && (getWidthValue > 0)) {
            Log.i(TAG, "绘制 4");
            mIntWidth = getWidthValue;
            Log.i(TAG, "绘制 5");
            mIntHeight = getHeight();
            Log.i(TAG, "绘制 6");
            initialOBLBtnGeometry();
            Log.i(TAG, "绘制 7");
        }
        if (mIntWidth > 0) {
            mPaint.setColor(mColorBG);
            canvas.drawRect(0, 0, mIntWidth, mIntHeight, mPaint);
            Log.i(TAG, "绘制 9");
            renderOBLBtns(canvas, mPaint);
            Log.i(TAG, "绘制 10");
        }
        Log.i(TAG, "绘制 12");
    }

    private void initialOBLBtnGeometry() {
        int topPos = mIntHeight * mIntTotalRow / (mIntTotalRow + 1);
        int widthInternal = mIntWidth / mIntTotalColumn;
        mOBLControlBtnPage1.setGeometry(0 * widthInternal, topPos, 1 * widthInternal, mIntHeight);
        mOBLControlBtnPage2.setGeometry(1 * widthInternal, topPos, 2 * widthInternal, mIntHeight);
        mOBLControlBtnPage3.setGeometry(2 * widthInternal, topPos, 3 * widthInternal, mIntHeight);
        mOBLControlBtnPage4.setGeometry(3 * widthInternal, topPos, 4 * widthInternal, mIntHeight);
        mOBLControlBtnMove.setGeometry((mIntTotalColumn - 2) * widthInternal, topPos, (mIntTotalColumn - 1) * widthInternal, mIntHeight);
        mOBLControlBtnClose.setGeometry((mIntTotalColumn - 1) * widthInternal, topPos, mIntTotalColumn * widthInternal, mIntHeight);
        for (OBLBtn btn : mOBLBtns) {
            btn.setGeometry(mIntWidth, mIntHeight * mIntTotalRow / (mIntTotalRow + 1), mIntTotalRow, mIntTotalColumn);
        }
    }

    private void renderOBLBtns(Canvas canvas, Paint paint) {
        if (mIntCurrentPage == PAGE_SHORTCUTS) {
            renderShortcutsPage(canvas, paint);
        } else {
            for (OBLBtn btn : mOBLBtns) {
                if (btn.getIntPageIndex() == mIntCurrentPage) {
                    btn.draw(canvas, paint);
                }
            }
        }
        mOBLControlBtnPage1.draw(canvas, paint);
        mOBLControlBtnPage2.draw(canvas, paint);
        mOBLControlBtnPage3.draw(canvas, paint);
        mOBLControlBtnPage4.draw(canvas, paint);
        mOBLControlBtnMove.draw(canvas, paint);
        mOBLControlBtnClose.draw(canvas, paint);
    }

    private void renderShortcutsPage(Canvas canvas, Paint paint) {
        int totalCells = mIntTotalRow * mIntTotalColumn;
        int cellW = mIntWidth / mIntTotalColumn;
        int cellH = (mIntHeight * mIntTotalRow / (mIntTotalRow + 1)) / mIntTotalRow;

        int idx = 0;
        for (ShortcutItem sc : mShortcuts) {
            if (idx >= totalCells) break;
            int row = idx / mIntTotalColumn;
            int col = idx % mIntTotalColumn;
            int x1 = col * cellW;
            int y1 = row * cellH;
            int x2 = (col + 1) * cellW;
            int y2 = (row + 1) * cellH;

            mPaint.setColor(mColorBtnBG);
            canvas.drawRect(x1 + mIntOffset, y1 + mIntOffset, x2 - mIntOffset, y2 - mIntOffset, paint);
            mPaint.setColor(mColorBtnTxt);
            float fontSize = Math.min(cellW, cellH) * 0.22f;
            mPaint.setTextSize(fontSize);
            Paint.FontMetrics fm = mPaint.getFontMetrics();
            float dist = (fm.bottom - fm.top) / 2 - fm.bottom;
            String label = sc.name.length() > 8 ? sc.name.substring(0, 7) + "…" : sc.name;
            canvas.drawText(label, (x1 + x2) / 2f, (y1 + y2) / 2f - fontSize * 0.3f + dist, mPaint);
            // Draw combo hint below name
            String combo = comboDisplay(sc);
            mPaint.setTextSize(fontSize * 0.6f);
            canvas.drawText(combo, (x1 + x2) / 2f, (y1 + y2) / 2f + fontSize * 0.5f, mPaint);
            idx++;
        }

        if (idx < totalCells) {
            int row = idx / mIntTotalColumn;
            int col = idx % mIntTotalColumn;
            int x1 = col * cellW;
            int y1 = row * cellH;
            int x2 = (col + 1) * cellW;
            int y2 = (row + 1) * cellH;
            mPaint.setColor(mColorClickEffect);
            canvas.drawRect(x1 + mIntOffset, y1 + mIntOffset, x2 - mIntOffset, y2 - mIntOffset, paint);
            mPaint.setColor(mColorBtnTxt);
            float fontSize = Math.min(cellW, cellH) * 0.25f;
            mPaint.setTextSize(fontSize);
            Paint.FontMetrics fm = mPaint.getFontMetrics();
            float dist = (fm.bottom - fm.top) / 2 - fm.bottom;
            canvas.drawText("+", (x1 + x2) / 2f, (y1 + y2) / 2f + dist, mPaint);
            mPaint.setTextSize(fontSize * 0.5f);
            canvas.drawText("Add", (x1 + x2) / 2f, (y1 + y2) / 2f + fontSize * 0.6f, mPaint);
        }
    }

    private String comboDisplay(ShortcutItem sc) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sc.keyOrdinals.size(); i++) {
            if (i > 0) sb.append("+");
            sb.append(keyDisplayName(sc.keyOrdinals.get(i)));
        }
        return sb.toString();
    }

    private String keyDisplayName(int ordinal) {
        if (ordinal >= 0 && ordinal < OBLButtonID.values().length) {
            String raw = OBLButtonID.values()[ordinal].name();
            String name = raw.replace("OBLButtonID_", "");
            if (name.equals("Shift") || name.equals("Ctrl") || name.equals("Alt")) return name;
            if (name.equals("Space")) return "Space";
            if (name.equals("Enter")) return "Enter";
            if (name.equals("Esc")) return "Esc";
            if (name.equals("Tab")) return "Tab";
            if (name.equals("Delete")) return "Del";
            if (name.startsWith("Num_")) return name.substring(4);
            if (name.startsWith("UpArrow")) return "↑";
            if (name.startsWith("DownArrow")) return "↓";
            if (name.startsWith("LeftArrow")) return "←";
            if (name.startsWith("RightArrow")) return "→";
            return name;
        }
        return "?";
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            float posx = event.getX();
            float posy = event.getY();
            boolean hasHit = false;

            if (mIntCurrentPage == PAGE_SHORTCUTS && mMode == MODE_NORMAL) {
                if (handleShortcutsPageTouch(posx, posy)) return true;
            }

            if (mMode == MODE_NORMAL) {
                for (OBLBtn oblBtn : mOBLBtns) {
                    if (oblBtn.hitTest(posx, posy) && (oblBtn.getIntPageIndex() == mIntCurrentPage)) {
                        clearClickEffect();
                        oblBtn.addClickEffect();
                        clickOBLBtn(oblBtn);
                        hasHit = true;
                        break;
                    }
                }
            }
            if (!hasHit) {
                if (mOBLControlBtnClose.hitTest(posx, posy)) {
                    clearClickEffect();
                    mOBLControlBtnClose.addClickEffect();
                    mOBLSettingFragmentListener.closeFragment();
                    mOBLBtnShift.setSelected(false);
                    mOBLBtnCtrl.setSelected(false);
                    mOBLBtnAlt.setSelected(false);
                    mOBLBtnMMB.setSelected(false);
                    mOBLBtnRMB.setSelected(false);
                    mOBLControlBtnMove.setSelected(false);
                }else if(mOBLControlBtnMove.hitTest(posx, posy)){
                    clearClickEffect();
                    mOBLControlBtnMove.addClickEffect();
                    mOBLControlBtnMove.setSelected(!mOBLControlBtnMove.isBooleanSelected());
                    if (!mOBLControlBtnMove.isBooleanSelected()){
                        int []values=new int[1];
                        values[0]=10000;
                        mOBLSettingFragmentListener.enterKey(values);
                    }
                }else if (mOBLControlBtnPage1.hitTest(posx, posy)) {
                    if (mIntCurrentPage != 0) {
                        clearClickEffect();
                        mOBLControlBtnPage1.addClickEffect();
                        mIntCurrentPage = 0;
                        mMode = MODE_NORMAL;
                        mOBLControlBtnPage1.setSelected(true);
                        mOBLControlBtnPage2.setSelected(false);
                        mOBLControlBtnPage3.setSelected(false);
                        mOBLControlBtnPage4.setSelected(false);
                        invalidate();
                    }
                } else if (mOBLControlBtnPage2.hitTest(posx, posy)) {
                    if (mIntCurrentPage != 1) {
                        clearClickEffect();
                        mOBLControlBtnPage2.addClickEffect();
                        mIntCurrentPage = 1;
                        mMode = MODE_NORMAL;
                        mOBLControlBtnPage1.setSelected(false);
                        mOBLControlBtnPage2.setSelected(true);
                        mOBLControlBtnPage3.setSelected(false);
                        mOBLControlBtnPage4.setSelected(false);
                        invalidate();
                    }
                } else if (mOBLControlBtnPage3.hitTest(posx, posy)) {
                    if (mIntCurrentPage != 2) {
                        clearClickEffect();
                        mOBLControlBtnPage3.addClickEffect();
                        mIntCurrentPage = 2;
                        mMode = MODE_NORMAL;
                        mOBLControlBtnPage1.setSelected(false);
                        mOBLControlBtnPage2.setSelected(false);
                        mOBLControlBtnPage3.setSelected(true);
                        mOBLControlBtnPage4.setSelected(false);
                        invalidate();
                    }
                } else if (mOBLControlBtnPage4.hitTest(posx, posy)) {
                    if (mIntCurrentPage != PAGE_SHORTCUTS) {
                        clearClickEffect();
                        mOBLControlBtnPage4.addClickEffect();
                        mIntCurrentPage = PAGE_SHORTCUTS;
                        mMode = MODE_NORMAL;
                        mOBLControlBtnPage1.setSelected(false);
                        mOBLControlBtnPage2.setSelected(false);
                        mOBLControlBtnPage3.setSelected(false);
                        mOBLControlBtnPage4.setSelected(true);
                        invalidate();
                    }
                }
            }
        }
        if (action == MotionEvent.ACTION_UP) {
            performClick();
            clearClickEffect();
        }
        return super.onTouchEvent(event);
    }

    private boolean handleShortcutsPageTouch(float x, float y) {
        int totalCells = mIntTotalRow * mIntTotalColumn;
        int cellW = mIntWidth / mIntTotalColumn;
        int cellH = (mIntHeight * mIntTotalRow / (mIntTotalRow + 1)) / mIntTotalRow;

        // Check shortcut buttons
        int idx = 0;
        for (int i = 0; i < mShortcuts.size(); i++) {
            if (idx >= totalCells) break;
            int row = idx / mIntTotalColumn;
            int col = idx % mIntTotalColumn;
            int x1 = col * cellW;
            int y1 = row * cellH;
            int x2 = (col + 1) * cellW;
            int y2 = (row + 1) * cellH;
            Rect r = new Rect(x1 + mIntOffset, y1 + mIntOffset, x2 - mIntOffset, y2 - mIntOffset);
            if (r.contains((int) x, (int) y)) {
                executeShortcut(i);
                return true;
            }
            idx++;
        }

        // Check "Add Shortcut" button
        if (idx < totalCells) {
            int row = idx / mIntTotalColumn;
            int col = idx % mIntTotalColumn;
            int x1 = col * cellW;
            int y1 = row * cellH;
            int x2 = (col + 1) * cellW;
            int y2 = (row + 1) * cellH;
            Rect r = new Rect(x1 + mIntOffset, y1 + mIntOffset, x2 - mIntOffset, y2 - mIntOffset);
            if (r.contains((int) x, (int) y)) {
                startAddShortcut();
                return true;
            }
        }
        return false;
    }

    private void startAddShortcut() {
        Context ctx = getContext();
        if (ctx == null) return;
        showModifierDialog(ctx);
    }

    private void showModifierDialog(final Context ctx) {
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle("Step 1/3: Hold keys (optional)");
        final String[] items = {"Shift", "Ctrl", "Alt"};
        final boolean[] checked = new boolean[3];
        b.setMultiChoiceItems(items, null, (d, w, isChecked) -> checked[w] = isChecked);
        b.setPositiveButton("Next", (d, w) -> showKeyCategoryDialog(ctx, checked));
        b.setNegativeButton("Cancel", null);
        b.show();
    }

    private void showKeyCategoryDialog(final Context ctx, final boolean[] mods) {
        final String[] cats = {"Letters", "Numbers", "F-Keys", "Special"};
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle("Step 2/3: Pick a key");
        b.setItems(cats, (d, w) -> {
            switch (w) {
                case 0: showKeyListDialog(ctx, mods, new String[]{"A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z"}, new int[]{25,52,29,48,51,53,45,46,23,49,71,72,31,30,24,73,20,43,44,22,74,50,21,27,28,26}); break;
                case 1: showKeyListDialog(ctx, mods, new String[]{"1","2","3","4","5","6","7","8","9","0"}, new int[]{15,16,17,18,19,67,68,69,70,66}); break;
                case 2: showKeyListDialog(ctx, mods, new String[]{"F1","F2","F3","F4","F5","F6","F7","F8","F9","F10","F11","F12"}, new int[]{75,8,9,10,76,77,78,79,80,81,82,11}); break;
                case 3: showKeyListDialog(ctx, mods, new String[]{"Esc","Tab","Space","Enter","Delete","Home","End","Ins.","PgUp.","PgDn.","[","]","-","=",";","'","`",",",".","/","↑","↓","←","→"}, new int[]{7,41,34,13,42,12,90,89,35,36,85,86,83,84,87,88,14,32,33,47,37,38,39,40}); break;
            }
        });
        b.setNegativeButton("Cancel", null);
        b.show();
    }

    private void showKeyListDialog(final Context ctx, final boolean[] mods, String[] keys, int[] ordinals) {
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle("Select key");
        b.setItems(keys, (d, w) -> showNameDialog(ctx, mods, ordinals[w]));
        b.setNegativeButton("Back", (d, w) -> showKeyCategoryDialog(ctx, mods));
        b.show();
    }

    private void showNameDialog(Context ctx, boolean[] modSelected, int actionKeyOrdinal) {
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle("Step 3/3: Name your shortcut");

        final EditText input = new EditText(ctx);
        input.setHint("e.g. Subdivide");
        b.setView(input);

        StringBuilder preview = new StringBuilder();
        if (modSelected[0]) preview.append("Shift+");
        if (modSelected[1]) preview.append("Ctrl+");
        if (modSelected[2]) preview.append("Alt+");
        preview.append(keyDisplayName(actionKeyOrdinal));

        b.setMessage("Combo: " + preview);

        b.setPositiveButton("Confirm", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) name = preview.toString();
            saveShortcut(name, modSelected, actionKeyOrdinal);
        });
        b.setNegativeButton("Cancel", null);
        b.show();
    }

    private void saveShortcut(String name, boolean[] modSelected, int actionKeyOrdinal) {
        ArrayList<Integer> keys = new ArrayList<>();
        if (modSelected[0]) keys.add(0); // Shift
        if (modSelected[1]) keys.add(1); // Ctrl
        if (modSelected[2]) keys.add(2); // Alt
        keys.add(actionKeyOrdinal);

        mShortcuts.add(new ShortcutItem(name, keys));
        persistShortcuts();
        invalidate();
    }

    private void executeShortcut(int index) {
        if (index < 0 || index >= mShortcuts.size()) return;
        ShortcutItem sc = mShortcuts.get(index);
        if (mOBLSettingFragmentListener == null) return;

        ArrayList<Integer> mods = new ArrayList<>();
        int actionKey = -1;
        for (int ord : sc.keyOrdinals) {
            if (ord == 0 || ord == 1 || ord == 2) {
                mods.add(ord);
            } else {
                actionKey = ord;
            }
        }

        for (int m : mods) {
            mOBLSettingFragmentListener.enterKeyOn(new int[]{m});
        }
        if (actionKey >= 0) {
            mOBLSettingFragmentListener.enterKey(new int[]{actionKey});
        }
        for (int m : mods) {
            mOBLSettingFragmentListener.enterKeyOff(new int[]{m});
        }

        // Close keyboard after executing shortcut
        mOBLSettingFragmentListener.closeFragment();
        mOBLBtnShift.setSelected(false);
        mOBLBtnCtrl.setSelected(false);
        mOBLBtnAlt.setSelected(false);
        mOBLBtnMMB.setSelected(false);
        mOBLBtnRMB.setSelected(false);
        mOBLControlBtnMove.setSelected(false);
    }

    private void loadShortcuts() {
        mShortcuts.clear();
        try {
            Context ctx = getContext();
            if (ctx == null) return;
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(PREFS_JSON, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String name = obj.getString("n");
                JSONArray keyArr = obj.getJSONArray("k");
                ArrayList<Integer> keys = new ArrayList<>();
                for (int j = 0; j < keyArr.length(); j++) {
                    keys.add(keyArr.getInt(j));
                }
                mShortcuts.add(new ShortcutItem(name, keys));
            }
        } catch (Exception e) {
            Log.e(TAG, "loadShortcuts failed", e);
        }
    }

    private void persistShortcuts() {
        try {
            Context ctx = getContext();
            if (ctx == null) return;
            JSONArray arr = new JSONArray();
            for (ShortcutItem sc : mShortcuts) {
                JSONObject obj = new JSONObject();
                obj.put("n", sc.name);
                JSONArray keyArr = new JSONArray();
                for (int k : sc.keyOrdinals) {
                    keyArr.put(k);
                }
                obj.put("k", keyArr);
                arr.put(obj);
            }
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(PREFS_JSON, arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "persistShortcuts failed", e);
        }
    }

    private void clearClickEffect() {
        for (OBLBtn oblBtn : mOBLBtns) {
            oblBtn.clearClickEffect();
        }
        mOBLControlBtnPage1.clearClickEffect();
        mOBLControlBtnPage2.clearClickEffect();
        mOBLControlBtnPage3.clearClickEffect();
        mOBLControlBtnPage4.clearClickEffect();
        mOBLControlBtnClose.clearClickEffect();
        mOBLControlBtnMove.clearClickEffect();
        invalidate();
    }

    void clickOBLBtn(OBLBtn oblBtn) {
        if (oblBtn.getIntSelected() != 0) {
            oblBtn.setSelected(!oblBtn.isSelected());
        }
        int[] keys = new int[1];
        keys[0] = oblBtn.getOBLButtonID().ordinal();
        if (oblBtn.getIntSelected()!=0){
            if (oblBtn.isSelected()){
                mOBLSettingFragmentListener.enterKeyOn(keys);
            }else{
                mOBLSettingFragmentListener.enterKeyOff(keys);
            }
        }else{
            mOBLSettingFragmentListener.enterKey(keys);
        }
        invalidate();
    }

    void setOBLSettingFragmentListener(OBLSettingFragmentListener oblSettingFragmentListener) {
        mOBLSettingFragmentListener = oblSettingFragmentListener;
    }

    void SetValue(int type,int value){
        if (type==0){
            mOBLBtnShift.setSelected(value==1);
        }
        if (type==1){
            mOBLBtnCtrl.setSelected(value==1);
        }
        if (type==2){
            mOBLBtnAlt.setSelected(value==1);
        }
        if (type==3){
            mOBLBtnMMB.setSelected(value==1);
        }
        if (type==4){
            mOBLBtnRMB.setSelected(value==1);
        }
        invalidate();
    }

    int GetAsyncKeyState(int type) {
        if (type == 0) {
            return mOBLBtnShift.isSelected() ? 1 : 0;
        } else if (type == 1) {
            return mOBLBtnAlt.isSelected() ? 1 : 0;
        } else if (type == 2) {
            return mOBLBtnCtrl.isSelected() ? 1 : 0;
        } else if (type == 3) {
            return mOBLBtnMMB.isSelected() ? 1 : 0;
        } else if (type == 4) {
            return mOBLBtnRMB.isSelected() ? 1 : 0;
        } else if(type==100) {
            return getVisibility()==VISIBLE?1:0;
        }else if(type==101) {
            return mOBLControlBtnMove.isBooleanSelected()?1:0;
        }else{
            return 0;
        }
    }

    private OBLSettingFragmentListener mOBLSettingFragmentListener;

    public interface OBLSettingFragmentListener {
        public void enterKeyOn(int keys[]);
        public void enterKeyOff(int keys[]);
        public void enterKey(int keys[]);
        public void closeFragment();
    }
}
