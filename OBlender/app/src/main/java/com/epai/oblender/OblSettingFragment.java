package com.epai.oblender;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;

public class OblSettingFragment extends View {
    private final String TAG = "OBL_Grid";
    private static final String PREFS_NAME = "obl_shortcuts";
    private static final String PREFS_JSON = "shortcuts";
    private static final String PREFS_CUSTOM = "obl_custom";
    private static final String KB_DIR_NAME = "keyboards";

    private enum Tab { SHORTCUTS, KEYBOARD, NUMPAD }
    private Tab mCurrentTab = Tab.SHORTCUTS;

    /* Colors */
    private int mBg = 0xFF1A1A2E;
    private int mBtnBg = 0xFF2D2D50;
    private int mBtnBgPress = 0xFF3D3D6C;
    private int mBtnBorder = 0xFF4A4A7A;
    private int mBtnTxt = 0xFFE8E8F0;
    private int mComboTxt = 0xFF9999BB;
    private int mToggleOnBg = 0xFF1B5E20;
    private int mToggleOnBorder = 0xFF4CAF50;
    private int mAddBg = 0xFF2ECC71;
    private int mAddBgPress = 0xFF27AE60;
    private int mHeaderBg = 0xFF0F0F23;
    private int mCloseBg = 0xFF7A2A2A;
    private int mRadius = 12;
    private int mTabActiveBg = 0xFF3D3D6C;
    private int mTabInactiveBg = 0xFF1A1A2E;

    private Paint mPaint, mBorderPaint;
    private int mW, mH;
    private int mPressedIdx = -1;
    private long mPressTime = 0;

    /* Shortcuts tab */
    private ArrayList<ShortcutItem> mShortcuts = new ArrayList<>();
    private HashSet<Integer> mToggleActive = new HashSet<>();
    private OBLSettingFragmentListener mListener;

    private Rect mCloseRect, mSettingsRect, mDelRect;
    private int mGridTop, mGridBot;
    private int mScrollY = 0, mMaxScrollY = 0;
    private float mLastTouchY = 0;
    private boolean mIsDragging = false;
    private boolean mDeleteMode = false;
    private int mCols = 4;
    private float mKeySize = 1.0f;
    private String mGridName = "";

    /* Keyboard tab state */
    private boolean mShiftActive = false;

    /* Tab bar rects for touch */
    private Rect mShortcutsTabRect, mKeyboardTabRect, mNumPadTabRect;

    private static class ShortcutItem {
        String name;
        ArrayList<Integer> keyOrdinals;
        boolean toggleMode;
        boolean builtin;
        int customColor; /* 0 = use grid default */
        ShortcutItem(String n, ArrayList<Integer> k, boolean t) { name = n; keyOrdinals = k; toggleMode = t; builtin = false; customColor = 0; }
        ShortcutItem(String n, ArrayList<Integer> k, boolean t, boolean b) { name = n; keyOrdinals = k; toggleMode = t; builtin = b; customColor = 0; }
    }

    public OblSettingFragment(Context context) { super(context); init(); }
    public OblSettingFragment(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public OblSettingFragment(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }
    public OblSettingFragment(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) { super(context, attrs, defStyleAttr, defStyleRes); init(); }

    private void init() {
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(1);
        loadCustomization();
        loadShortcuts();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mW = getWidth(); mH = getHeight();
        if (mW <= 0 || mH <= 0) return;

        mPaint.setColor(mBg);
        canvas.drawRoundRect(0, 0, mW, mH, 16, 16, mPaint);

        int tabH = (int) (mH * 0.08f);
        int headerH = (int) (mH * 0.065f);
        int ctrlH = (mCurrentTab == Tab.SHORTCUTS) ? (int) (mH * 0.09f) : 0;

        int topBarH = tabH + headerH;
        mGridTop = topBarH;
        mGridBot = mH - ctrlH;

        drawTabBar(canvas, tabH);
        drawHeader(canvas, tabH, headerH);

        switch (mCurrentTab) {
            case SHORTCUTS:
                drawShortcutsGrid(canvas, mGridTop, mGridBot - mGridTop);
                drawControls(canvas, mGridBot, ctrlH);
                break;
            case KEYBOARD:
                drawKeyboard(canvas, mGridTop, mGridBot - mGridTop);
                break;
            case NUMPAD:
                drawNumPad(canvas, mGridTop, mGridBot - mGridTop);
                break;
        }

        if (mPressedIdx >= 0 && System.currentTimeMillis() - mPressTime > 120) {
            mPressedIdx = -1; invalidate();
        }
    }

    /* ─── Tab Bar ─── */

    private void drawTabBar(Canvas c, int h) {
        mPaint.setColor(mHeaderBg);
        c.drawRect(0, 0, mW, h, mPaint);

        String[] tabs = { "Shortcuts", "Keyboard", "#NumPad" };
        Tab[] values = { Tab.SHORTCUTS, Tab.KEYBOARD, Tab.NUMPAD };
        float tw = mW / 3f;
        mPaint.setTextSize(h * 0.42f);
        Paint.FontMetrics fm = mPaint.getFontMetrics();
        float ty = h / 2f + (fm.bottom - fm.top) / 2f - fm.bottom;

        for (int i = 0; i < 3; i++) {
            float lx = i * tw, rx = (i + 1) * tw;
            boolean active = (mCurrentTab == values[i]);
            mPaint.setColor(active ? mTabActiveBg : mTabInactiveBg);
            c.drawRect(lx, 0, rx, h, mPaint);

            if (active) {
                mPaint.setColor(0xFF7C4DFF);
                c.drawRect(lx, h - 3, rx, h, mPaint);
            }

            mPaint.setColor(active ? mBtnTxt : mComboTxt);
            c.drawText(tabs[i], (lx + rx) / 2f, ty, mPaint);
        }

        /* Store tab hit rects */
        mShortcutsTabRect = new Rect(0, 0, (int)(mW / 3f), h);
        mKeyboardTabRect = new Rect((int)(mW / 3f), 0, (int)(2 * mW / 3f), h);
        mNumPadTabRect = new Rect((int)(2 * mW / 3f), 0, mW, h);
    }

    /* ─── Header (gear + toggle badge) ─── */

    private void drawHeader(Canvas c, int top, int h) {
        float gy = top + 2;
        mPaint.setColor(mHeaderBg);
        c.drawRect(0, gy, mW, gy + h, mPaint);

        mPaint.setColor(mBtnTxt);
        mPaint.setTextSize(h * 0.4f);
        Paint.FontMetrics fm = mPaint.getFontMetrics();
        float y = gy + h / 2f + (fm.bottom - fm.top) / 2f - fm.bottom;

        String title;
        switch (mCurrentTab) {
            case KEYBOARD: title = "QWERTY"; break;
            case NUMPAD: title = "Numpad"; break;
            default:
                title = "Shortcuts  " + mShortcuts.size();
                break;
        }
        c.drawText(title, mW / 2f, y, mPaint);

        /* Settings gear */
        float gearSize = h * 0.6f;
        float gx = mW - gearSize - 16;
        float gtx = gx + gearSize / 2f;
        float gty = gy + h / 2f + (fm.bottom - fm.top) / 2f - fm.bottom;
        mPaint.setTextSize(gearSize * 0.85f);
        mPaint.setColor(0xFF9999BB);
        c.drawText("\u2699", gtx, gty, mPaint);
        mSettingsRect = new Rect((int)gx, (int)gy, (int)(gx + gearSize + 16), (int)(gy + h));

        /* Toggle active badge for shortcuts tab */
        if (mCurrentTab == Tab.SHORTCUTS && !mToggleActive.isEmpty()) {
            mPaint.setColor(0xFF7C4DFF);
            mPaint.setTextSize(h * 0.3f);
            String badge = mToggleActive.size() + " held";
            float bw = mPaint.measureText(badge) + 12;
            float bx = mW - bw - gearSize - 32;
            float by = gy + (h - mPaint.getTextSize()) / 2f;
            c.drawRoundRect(bx, by, bx + bw, by + mPaint.getTextSize() + 6, 8, 8, mPaint);
            mPaint.setColor(Color.WHITE);
            c.drawText(badge, bx + bw / 2f, by + mPaint.getTextSize() + 2, mPaint);
        }
    }

    /* ════════════════════════════════════════
     * SHORTCUTS TAB
     * ════════════════════════════════════════ */

    private void drawShortcutsGrid(Canvas c, int top, int gridH) {
        int rows = Math.max(1, (int) Math.ceil((float) mShortcuts.size() / mCols) + 1);
        float cellW = (float) mW / mCols;
        float cellH = 66f * mKeySize;
        int totalH = (int) (rows * cellH);
        mMaxScrollY = Math.max(0, totalH - gridH + 10);
        if (mScrollY > mMaxScrollY) mScrollY = mMaxScrollY;
        if (mScrollY < 0) mScrollY = 0;

        int idx = 0;
        for (int i = 0; i < mShortcuts.size(); i++, idx++) {
            int r = idx / mCols, col = idx % mCols;
            float yy = top + r * cellH - mScrollY;
            if (yy + cellH < top || yy > mGridBot) continue;
            RectF rect = new RectF(col * cellW + 5, yy + 5, (col + 1) * cellW - 5, yy + cellH - 5);
            drawShortcutBtn(c, rect, i, mShortcuts.get(i));
        }
        int r = idx / mCols, col = idx % mCols;
        float yy = top + r * cellH - mScrollY;
        if (yy + cellH >= top && yy <= mGridBot) {
            RectF rect = new RectF(col * cellW + 5, yy + 5, (col + 1) * cellW - 5, yy + cellH - 5);
            boolean pressed = (mPressedIdx == -2);
            mPaint.setColor(pressed ? mAddBgPress : mAddBg);
            c.drawRoundRect(rect, mRadius, mRadius, mPaint);
            mPaint.setColor(Color.WHITE);
            mPaint.setTextSize(Math.min(28f, rect.height() * 0.35f));
            Paint.FontMetrics fm = mPaint.getFontMetrics();
            c.drawText("+ Add", rect.centerX(), rect.centerY() + (fm.bottom - fm.top) / 2f - fm.bottom, mPaint);
        }
    }

    private void drawShortcutBtn(Canvas c, RectF r, int idx, ShortcutItem sc) {
        boolean pressed = (mPressedIdx == idx);
        boolean toggleActive = mToggleActive.contains(idx);
        int bg, txtCol = mBtnTxt, borderCol = mBtnBorder;
        if (toggleActive) { bg = mToggleOnBg; borderCol = mToggleOnBorder; txtCol = 0xFFA5D6A7; }
        else if (pressed) bg = mBtnBgPress;
        else if (sc.customColor != 0) { bg = sc.customColor; borderCol = sc.customColor; }
        else bg = mBtnBg;

        /* Scale down slightly when pressed (pulse animation) */
        float sx = 1f, sy = 1f;
        if (pressed) { sx = 0.92f; sy = 0.92f; }
        float cx = r.centerX(), cy = r.centerY();
        float l = cx + (r.left - cx) * sx;
        float t = cy + (r.top - cy) * sy;
        float r2 = cx + (r.right - cx) * sx;
        float b = cy + (r.bottom - cy) * sy;

        mPaint.setColor(0x11000000);
        c.drawRoundRect(l + 2, t + 2, r2 + 2, b + 2, mRadius, mRadius, mPaint);
        mPaint.setColor(bg);
        c.drawRoundRect(l, t, r2, b, mRadius, mRadius, mPaint);
        mBorderPaint.setColor(borderCol);
        mBorderPaint.setStrokeWidth(sc.toggleMode ? 2 : 1);
        c.drawRoundRect(l, t, r2, b, mRadius, mRadius, mBorderPaint);

        if (mDeleteMode) {
            mPaint.setColor(0xCCFF1744);
            mPaint.setTextSize(22);
            c.drawText("\u2716", r.right - 26, r.top + 22, mPaint);
        }
        if (toggleActive) {
            mPaint.setColor(mToggleOnBorder);
            mPaint.setTextSize(18);
            c.drawText("\u2713", r.right - 22, r.top + 20, mPaint);
        }
        float h = r.height();
        String line2 = comboDisplay(sc);
        float sz1 = Math.min(30f, h * 0.28f);
        float sz2 = Math.min(13f, h * 0.14f);
        mPaint.setColor(txtCol);
        mPaint.setTextSize(sz1);
        Paint.FontMetrics fm = mPaint.getFontMetrics();
        float y1 = r.centerY() - (line2.isEmpty() ? sz1 * 0.2f : sz1 * 0.1f);
        c.drawText(sc.name, r.centerX(), y1, mPaint);
        if (!line2.isEmpty()) {
            mPaint.setTextSize(sz2);
            mPaint.setColor(toggleActive ? 0xFFA5D6A7 : mComboTxt);
            c.drawText(line2, r.centerX(), y1 + sz1 * 0.55f + sz2 * 0.2f, mPaint);
        }
    }

    private void drawControls(Canvas c, int top, int h) {
        if (mCurrentTab != Tab.SHORTCUTS) return;
        mPaint.setColor(mHeaderBg);
        c.drawRoundRect(0, top, mW, top + h + 8, 0, 0, mPaint);
        float bw = mW / 4f;
        mCloseRect = new Rect((int) (mW - bw * 1.2f), top, mW, top + h);
        float textSize = h * 0.38f;

        mPaint.setColor(mCloseBg);
        c.drawRoundRect(new RectF(mCloseRect), 8, 8, mPaint);
        mPaint.setColor(Color.WHITE);
        mPaint.setTextSize(textSize);
        Paint.FontMetrics fm = mPaint.getFontMetrics();
        float d = (fm.bottom - fm.top) / 2f - fm.bottom;
        c.drawText("\u2716 Close", mCloseRect.exactCenterX(), mCloseRect.exactCenterY() + d, mPaint);

        mPaint.setColor(mDeleteMode ? 0xFFB71C1C : mBtnBorder);
        mDelRect = new Rect((int) (bw * 1.1f), top, (int) (bw * 1.9f), top + h);
        c.drawRoundRect(new RectF(mDelRect), 8, 8, mPaint);
        mPaint.setColor(mDeleteMode ? Color.WHITE : 0xFF9999AA);
        mPaint.setTextSize(textSize);
        c.drawText(mDeleteMode ? "\u2716 Del" : "Del", mDelRect.exactCenterX(), mDelRect.exactCenterY() + d, mPaint);
    }

    /* ════════════════════════════════════════
     * KEYBOARD TAB
     * ════════════════════════════════════════ */

    private static class KbKey {
        String label;
        int ordinal;
        float w; /* relative width weight (1.0 = standard) */
        boolean isSpecial;
        KbKey(String lbl, int ord, float w) { this(lbl, ord, w, false); }
        KbKey(String lbl, int ord, float w, boolean sp) { label = lbl; ordinal = ord; this.w = w; isSpecial = sp; }
    }

    /* Row definitions: each element = {label, ordinal, width_weight} */
    private static final KbKey[][] KB_ROWS = {
        /* Row 0: Q W E R T Y U I O P */
        new KbKey[]{
            new KbKey("Q",20,1), new KbKey("W",21,1), new KbKey("E",51,1), new KbKey("R",43,1),
            new KbKey("T",22,1), new KbKey("Y",28,1), new KbKey("U",74,1), new KbKey("I",23,1),
            new KbKey("O",24,1), new KbKey("P",73,1)
        },
        /* Row 1: A S D F G H J K L \n */
        new KbKey[]{
            new KbKey("A",25,1), new KbKey("S",44,1), new KbKey("D",48,1), new KbKey("F",53,1),
            new KbKey("G",45,1), new KbKey("H",46,1), new KbKey("J",49,1), new KbKey("K",71,1),
            new KbKey("L",72,1), new KbKey("\u232B",42,1.3f,true) /* Backspace */
        },
        /* Row 2: Z X C V B N M , . */
        new KbKey[]{
            new KbKey("Z",26,1), new KbKey("X",27,1), new KbKey("C",29,1), new KbKey("V",50,1),
            new KbKey("B",52,1), new KbKey("N",30,1), new KbKey("M",31,1),
            new KbKey(",",32,1), new KbKey(".",33,1)
        },
        /* Row 3: Shift Ctrl Alt Space Enter */
        new KbKey[]{
            new KbKey("\u21E7",0,1.5f,true), /* Shift */
            new KbKey("Ctrl",1,1.2f,true),
            new KbKey("Alt",2,1.2f,true),
            new KbKey("Space",34,4f),
            new KbKey("\u23CE",13,1.5f,true) /* Enter */
        }
    };

    private RectF[][] mKbHitRects = null; /* lazily created on draw */

    private void drawKeyboard(Canvas c, int top, int gridH) {
        float cellW = mW;
        float pad = 4;
        float totalPad = pad * 2;
        float usableW = cellW - totalPad;

        /* Calculate rows sizes */
        int rows = KB_ROWS.length;
        float rowH = Math.min(52f * mKeySize, (gridH - pad * (rows + 1)) / rows);
        float ky0 = top + pad;
        if (mKbHitRects == null || mKbHitRects.length != rows) {
            mKbHitRects = new RectF[rows][];
        }

        for (int ri = 0; ri < rows; ri++) {
            KbKey[] row = KB_ROWS[ri];
            float totalW = 0;
            for (KbKey k : row) totalW += k.w;
            float availW = usableW - (row.length - 1) * pad;
            float kx0 = pad;
            float ky = ky0 + ri * (rowH + pad);

            if (mKbHitRects[ri] == null || mKbHitRects[ri].length != row.length) {
                mKbHitRects[ri] = new RectF[row.length];
            }

            for (int ki = 0; ki < row.length; ki++) {
                KbKey k = row[ki];
                float kw = (availW * k.w / totalW);
                RectF kr = new RectF(kx0, ky, kx0 + kw, ky + rowH);

                /* Draw key */
                mPaint.setColor(mBtnBg);
                c.drawRoundRect(kr, 8, 8, mPaint);
                mBorderPaint.setColor(mBtnBorder);
                mBorderPaint.setStrokeWidth(1);
                c.drawRoundRect(kr, 8, 8, mBorderPaint);

                /* Label */
                mPaint.setColor(k.isSpecial ? 0xFFB388FF : mBtnTxt);
                float sz = Math.min(20f, rowH * 0.45f);
                if (k.isSpecial && k.label.length() > 3) sz *= 0.85f;
                mPaint.setTextSize(sz);
                Paint.FontMetrics fm = mPaint.getFontMetrics();
                float lx = (kr.left + kr.right) / 2f;
                float ly = (kr.top + kr.bottom) / 2f + (fm.bottom - fm.top) / 2f - fm.bottom;

                /* Shift highlight */
                if (k.ordinal == 0 && mShiftActive) {
                    mPaint.setColor(mToggleOnBg);
                    c.drawRoundRect(kr, 8, 8, mPaint);
                    mPaint.setColor(0xFFA5D6A7);
                }

                c.drawText(k.label, lx, ly, mPaint);

                mKbHitRects[ri][ki] = kr;
                kx0 += kw + pad;
            }
        }
    }

    /* ════════════════════════════════════════
     * NUMPAD TAB
     * ════════════════════════════════════════ */

    private static final String[] NP_LABELS = {
        "7","8","9","/",
        "4","5","6","*",
        "1","2","3","-",
        "0",".","Ent","+"
    };
    /* ordinals for numpad keys — use Num_* where available, regular keys for 6-9 */
    private static final int[] NP_ORDS = {
        68,69,70,63,  /* 7, 8, 9, / */
        58,59,67,62,  /* 4, 5, 6, * */
        55,56,57,61,  /* 1, 2, 3, - */
        54,64,65,60   /* 0, ., Ent, + */
    };
    private RectF[] mNpHitRects = null;

    private void drawNumPad(Canvas c, int top, int gridH) {
        int cols = 4;
        int rows = 4;
        float cellW = (float) mW / cols;
        float cellH = Math.min(64f * mKeySize, (gridH - 10f) / rows);
        float startY = top + (gridH - rows * cellH) / 2f;

        if (mNpHitRects == null) mNpHitRects = new RectF[NP_LABELS.length];

        for (int i = 0; i < NP_LABELS.length; i++) {
            int r = i / cols, col = i % cols;
            RectF kr = new RectF(col * cellW + 5, startY + r * cellH + 5,
                                  (col + 1) * cellW - 5, startY + (r + 1) * cellH - 5);
            mNpHitRects[i] = kr;

            boolean isOp = NP_LABELS[i].equals("/") || NP_LABELS[i].equals("*") ||
                           NP_LABELS[i].equals("-") || NP_LABELS[i].equals("+");
            mPaint.setColor(isOp ? 0xFF3D3D6C : mBtnBg);
            c.drawRoundRect(kr, mRadius, mRadius, mPaint);
            mBorderPaint.setColor(mBtnBorder);
            mBorderPaint.setStrokeWidth(1);
            c.drawRoundRect(kr, mRadius, mRadius, mBorderPaint);

            mPaint.setColor(isOp ? 0xFFB388FF : mBtnTxt);
            float sz = Math.min(26f, cellH * 0.4f);
            mPaint.setTextSize(sz);
            Paint.FontMetrics fm = mPaint.getFontMetrics();
            float lx = (kr.left + kr.right) / 2f;
            float ly = (kr.top + kr.bottom) / 2f + (fm.bottom - fm.top) / 2f - fm.bottom;
            c.drawText(NP_LABELS[i], lx, ly, mPaint);
        }
    }

    /* ════════════════════════════════════════
     * TOUCH HANDLING
     * ════════════════════════════════════════ */

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX(), y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                mLastTouchY = y; mIsDragging = false;

                /* Tab bar */
                if (mShortcutsTabRect != null && mShortcutsTabRect.contains((int)x, (int)y)) {
                    if (mCurrentTab != Tab.SHORTCUTS) { mCurrentTab = Tab.SHORTCUTS; invalidate(); }
                    return true;
                }
                if (mKeyboardTabRect != null && mKeyboardTabRect.contains((int)x, (int)y)) {
                    if (mCurrentTab != Tab.KEYBOARD) { mCurrentTab = Tab.KEYBOARD; invalidate(); }
                    return true;
                }
                if (mNumPadTabRect != null && mNumPadTabRect.contains((int)x, (int)y)) {
                    if (mCurrentTab != Tab.NUMPAD) { mCurrentTab = Tab.NUMPAD; invalidate(); }
                    return true;
                }

                /* Settings gear */
                if (mSettingsRect != null && mSettingsRect.contains((int)x, (int)y)) {
                    showSettings();
                    return true;
                }

                switch (mCurrentTab) {
                    case SHORTCUTS: return onShortcutsTouch(x, y);
                    case KEYBOARD: return onKeyboardTouch(x, y);
                    case NUMPAD: return onNumPadTouch(x, y);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mCurrentTab == Tab.SHORTCUTS) {
                    float dy = y - mLastTouchY;
                    if (Math.abs(dy) > 10f) mIsDragging = true;
                    if (mIsDragging) {
                        mScrollY -= (int) dy;
                        if (mScrollY < 0) mScrollY = 0;
                        if (mScrollY > mMaxScrollY) mScrollY = mMaxScrollY;
                        mLastTouchY = y;
                        invalidate();
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: {
                mPressedIdx = -1; mIsDragging = false; invalidate(); performClick();
                break;
            }
        }
        return true;
    }

    /* ─── Shortcuts touch ─── */

    private boolean onShortcutsTouch(float x, float y) {
        if (mCloseRect != null && mCloseRect.contains((int) x, (int) y)) {
            if (mListener != null) { clearAllToggles(); mListener.closeFragment(); }
            return true;
        }
        if (mDelRect != null && mDelRect.contains((int)x, (int)y)) {
            mDeleteMode = !mDeleteMode; invalidate(); return true;
        }
        int hitIdx = hitTest(x, y);
        if (hitIdx >= 0) {
            if (mDeleteMode) {
                deleteShortcut(hitIdx);
            } else {
                mPressedIdx = hitIdx; mPressTime = System.currentTimeMillis(); invalidate();
                performHapticFeedback(0);
                executeShortcut(hitIdx);
                postDelayed(() -> { mPressedIdx = -1; invalidate(); }, 80);
            }
            return true;
        }
        if (hitIdx == -99) {
            mPressedIdx = -2; invalidate();
            startAddShortcut();
            return true;
        }
        return false;
    }

    /* ─── Keyboard touch ─── */

    private boolean onKeyboardTouch(float x, float y) {
        if (mKbHitRects == null) return false;
        for (int ri = 0; ri < mKbHitRects.length; ri++) {
            if (mKbHitRects[ri] == null) continue;
            for (int ki = 0; ki < mKbHitRects[ri].length; ki++) {
                RectF kr = mKbHitRects[ri][ki];
                if (kr != null && kr.contains(x, y)) {
                    KbKey k = KB_ROWS[ri][ki];
                    performHapticFeedback(0);

                    if (k.ordinal == 0) {
                        /* Shift toggle */
                        mShiftActive = !mShiftActive;
                        invalidate();
                    } else if (k.ordinal == 1) {
                        /* Ctrl toggle */
                        toggleModifier(1);
                    } else if (k.ordinal == 2) {
                        /* Alt toggle */
                        toggleModifier(2);
                    } else if (k.ordinal == 42) {
                        /* Backspace via Godot input system (KEYCODE_DEL) */
                        if (mListener != null) mListener.backspace();
                    } else if (k.ordinal == 13) {
                        /* Enter via Godot input system (KEYCODE_ENTER) */
                        if (mListener != null) mListener.enter();
                    } else {
                        /* Send key */
                        if (mListener != null) {
                            int[] keys = {k.ordinal};
                            mListener.enterKey(keys);
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /* ─── Numpad touch ─── */

    private boolean onNumPadTouch(float x, float y) {
        if (mNpHitRects == null) return false;
        for (int i = 0; i < mNpHitRects.length; i++) {
            if (mNpHitRects[i] != null && mNpHitRects[i].contains(x, y)) {
                performHapticFeedback(0);
                if (mListener != null) {
                    mListener.enterKey(new int[]{NP_ORDS[i]});
                }
                return true;
            }
        }
        return false;
    }

    /* ─── Hit test for shortcuts ─── */

    private int hitTest(float x, float y) {
        if (mCols <= 0) return -1;
        float cellW = (float) mW / mCols;
        float cellH = 66f * mKeySize;
        int maxRows = Math.max(1, (int) Math.ceil((float) mShortcuts.size() / mCols) + 1);
        int col = (int) (x / cellW);
        if (col < 0 || col >= mCols) return -1;
        for (int r = 0; r < maxRows; r++) {
            float yy = mGridTop + r * cellH - mScrollY;
            if (y >= yy && y <= yy + cellH) {
                int idx = r * mCols + col;
                if (idx < mShortcuts.size()) return idx;
                if (idx == mShortcuts.size()) return -99;
                return -1;
            }
        }
        return -1;
    }

    /* ════════════════════════════════════════
     * EXECUTION
     * ════════════════════════════════════════ */

    private void toggleModifier(int ord) {
        /* Find if any toggle shortcut exists for this modifier */
        for (int idx = 0; idx < mShortcuts.size(); idx++) {
            ShortcutItem sc = mShortcuts.get(idx);
            if (sc.toggleMode && sc.keyOrdinals.size() == 1 && sc.keyOrdinals.get(0) == ord) {
                executeShortcut(idx);
                return;
            }
        }
        /* No toggle shortcut found — send a normal press */
        if (mListener != null) mListener.enterKey(new int[]{ord});
    }

    private void executeShortcut(int index) {
        if (index < 0 || index >= mShortcuts.size() || mListener == null) return;
        ShortcutItem sc = mShortcuts.get(index);
        ArrayList<Integer> mods = new ArrayList<>();
        ArrayList<Integer> actionKeys = new ArrayList<>();
        for (int ord : sc.keyOrdinals) {
            if (ord == 0 || ord == 1 || ord == 2) mods.add(ord);
            else actionKeys.add(ord);
        }

        if (sc.toggleMode) {
            /* Toggle mode: press on tap, release on next tap (works for ANY combo) */
            if (mToggleActive.contains(index)) {
                /* Release all */
                for (int ord : sc.keyOrdinals) mListener.enterKeyOff(new int[]{ord});
                mToggleActive.remove(index);
            } else {
                /* Press all */
                for (int ord : sc.keyOrdinals) mListener.enterKeyOn(new int[]{ord});
                mToggleActive.add(index);
            }
        } else {
            /* Normal chord */
            if (mods.size() > 0) {
                int[] modArr = new int[mods.size()];
                for (int i = 0; i < mods.size(); i++) modArr[i] = mods.get(i);
                mListener.enterKeyOn(modArr);
            }
            if (actionKeys.size() > 0) {
                int[] actArr = new int[actionKeys.size()];
                for (int i = 0; i < actionKeys.size(); i++) actArr[i] = actionKeys.get(i);
                mListener.enterKey(actArr);
            }
            if (mods.size() > 0) {
                int[] modArr = new int[mods.size()];
                for (int i = 0; i < mods.size(); i++) modArr[i] = mods.get(i);
                mListener.enterKeyOff(modArr);
            }
        }
        invalidate();
    }

    private void deleteShortcut(int index) {
        if (index < 0 || index >= mShortcuts.size()) return;
        Context ctx = getContext();
        if (ctx == null) return;
        String name = mShortcuts.get(index).name;
        new AlertDialog.Builder(ctx)
            .setTitle("Delete \"" + name + "\"?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete", (d, w) -> {
                mShortcuts.remove(index);
                persistShortcuts();
                invalidate();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    public void clearAllToggles() {
        for (int idx : mToggleActive) {
            if (idx < 0 || idx >= mShortcuts.size()) continue;
            ShortcutItem sc = mShortcuts.get(idx);
            if (mListener != null)
                for (int ord : sc.keyOrdinals) mListener.enterKeyOff(new int[]{ord});
        }
        mToggleActive.clear();
        mShiftActive = false;
    }

    /* ─── Display helpers ─── */

    private String comboDisplay(ShortcutItem sc) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sc.keyOrdinals.size(); i++) {
            if (i > 0) sb.append("+");
            sb.append(keyName(sc.keyOrdinals.get(i)));
        }
        if (sc.toggleMode) sb.append(" \u21BB");
        return sb.toString();
    }

    private String keyName(int ord) {
        if (ord >= 0 && ord < OBLButtonID.values().length) {
            String raw = OBLButtonID.values()[ord].name().replace("OBLButtonID_", "");
            if (raw.equals("Space")) return "Spc";
            if (raw.equals("Enter")) return "Ent";
            if (raw.equals("Esc")) return "Esc";
            if (raw.equals("Tab")) return "Tab";
            if (raw.equals("Delete")) return "Del";
            if (raw.startsWith("Num_")) return raw.substring(4);
            if (raw.equals("UpArrow")) return "\u2191";
            if (raw.equals("DownArrow")) return "\u2193";
            if (raw.equals("LeftArrow")) return "\u2190";
            if (raw.equals("RightArrow")) return "\u2192";
            return raw;
        }
        if (ord == 10000) return "LClick";
        if (ord == 10001) return "RClick";
        if (ord == 10002) return "ScUp";
        if (ord == 10003) return "ScDn";
        if (ord == 10004) return "Undo";
        if (ord == 10005) return "Redo";
        if (ord == 10006) return "Scl";
        return "?";
    }

    /* ─── Settings dialog ─── */

    private void showSettings() {
        Context ctx = getContext();
        if (ctx == null) return;

        String[] items = {"Export to file", "Import from file", "Customize appearance", "Cancel"};
        new AlertDialog.Builder(ctx)
            .setCustomTitle(makeTitle(ctx, "Grid Settings"))
            .setItems(items, (d, w) -> {
                if (w == 0) showExportDialog();
                else if (w == 1) showFilePicker();
                else if (w == 2) showCustomizeDialog();
            })
            .show();
    }

    /* ─── Export ─── */

    private void showExportDialog() {
        Context ctx = getContext();
        if (ctx == null) return;
        final EditText input = new EditText(ctx);
        input.setText(mGridName.isEmpty() ? "my_keyboard" : mGridName);
        input.setHint("File name (without .json)");
        input.setTextColor(0xFFE8E8F0);
        input.setHintTextColor(0xFF666688);
        input.setBackgroundColor(0xFF2D2D50);
        input.setPadding(12, 8, 12, 8);
        new AlertDialog.Builder(ctx)
            .setCustomTitle(makeTitle(ctx, "Export to file"))
            .setView(input)
            .setPositiveButton("Export", (d, w) -> exportToFile(input.getText().toString().trim()))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void exportToFile(String name) {
        Context ctx = getContext();
        if (ctx == null) return;
        if (name.isEmpty()) name = "my_keyboard";
        if (!name.endsWith(".json")) name += ".json";
        try {
            JSONObject root = buildFullExportJSON();
            if (root == null) { Toast.makeText(ctx, "Export failed", Toast.LENGTH_SHORT).show(); return; }
            root.put("name", name.replace(".json", ""));
            File dir = getKeyboardsDir();
            File file = new File(dir, name);
            FileWriter fw = new FileWriter(file);
            fw.write(root.toString(2));
            fw.close();
            Toast.makeText(ctx, "Exported to " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(ctx, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /* ─── Import ─── */

    private void showFilePicker() {
        Context ctx = getContext();
        if (ctx == null) return;
        File dir = getKeyboardsDir();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null || files.length == 0) {
            Toast.makeText(ctx, "No .json files in " + dir.getAbsolutePath(), Toast.LENGTH_LONG).show();
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        final String[] names = new String[files.length];
        final String[] paths = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            names[i] = files[i].getName();
            paths[i] = files[i].getAbsolutePath();
        }
        new AlertDialog.Builder(ctx)
            .setCustomTitle(makeTitle(ctx, "Select file to import"))
            .setItems(names, (d, w) -> importFromFile(paths[w]))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void importFromFile(String path) {
        Context ctx = getContext();
        if (ctx == null) return;
        try {
            BufferedReader br = new BufferedReader(new FileReader(path));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            JSONObject root = new JSONObject(sb.toString());

            /* Load customization if present */
            if (root.has("customization")) {
                JSONObject cust = root.getJSONObject("customization");
                mBg = cust.optInt("bg", mBg);
                mBtnBg = cust.optInt("btnBg", mBtnBg);
                mBtnBgPress = cust.optInt("btnBgPress", mBtnBgPress);
                mBtnBorder = cust.optInt("btnBorder", mBtnBorder);
                mBtnTxt = cust.optInt("btnTxt", mBtnTxt);
                mComboTxt = cust.optInt("comboTxt", mComboTxt);
                mToggleOnBg = cust.optInt("toggleOnBg", mToggleOnBg);
                mToggleOnBorder = cust.optInt("toggleOnBorder", mToggleOnBorder);
                mAddBg = cust.optInt("addBg", mAddBg);
                mAddBgPress = cust.optInt("addBgPress", mAddBgPress);
                mHeaderBg = cust.optInt("headerBg", mHeaderBg);
                mCloseBg = cust.optInt("closeBg", mCloseBg);
                mTabActiveBg = cust.optInt("tabActiveBg", mTabActiveBg);
                mTabInactiveBg = cust.optInt("tabInactiveBg", mTabInactiveBg);
                mRadius = cust.optInt("radius", mRadius);
                mCols = cust.optInt("cols", mCols);
                mKeySize = (float) cust.optDouble("keySize", mKeySize);
                mGridName = root.optString("name", mGridName);
                saveCustomization();
            }

            /* Load shortcuts */
            if (root.has("shortcuts")) {
                JSONArray arr = root.getJSONArray("shortcuts");
                int imported = 0;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String n = o.getString("n");
                    JSONArray ka = o.getJSONArray("k");
                    ArrayList<Integer> k = new ArrayList<>();
                    for (int j = 0; j < ka.length(); j++) k.add(ka.getInt(j));
                    if (isBuiltinKeySet(k)) continue;
                    boolean t = o.optBoolean("t", false);
                    ShortcutItem si = new ShortcutItem(n, k, t);
                    si.customColor = o.optInt("c", 0);
                    mShortcuts.add(si);
                    imported++;
                }
                persistShortcuts();
            }

            invalidate();
            Toast.makeText(ctx, "Imported " + path.substring(path.lastIndexOf('/') + 1), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(ctx, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /* ─── Customize dialog ─── */

    private void showCustomizeDialog() {
        Context ctx = getContext();
        if (ctx == null) return;
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 10, 20, 10);

        final EditText nameInput = makeEditField(ctx, "Grid name", mGridName);
        layout.addView(catLabel(ctx, "Name"));
        layout.addView(nameInput);

        final EditText colsInput = makeEditField(ctx, "2-6", String.valueOf(mCols));
        layout.addView(catLabel(ctx, "Columns (2-6)"));
        layout.addView(colsInput);

        final EditText sizeInput = makeEditField(ctx, "0.5-2.0", String.valueOf(mKeySize));
        layout.addView(catLabel(ctx, "Key size (0.5-2.0)"));
        layout.addView(sizeInput);

        final EditText radiusInput = makeEditField(ctx, "4-24", String.valueOf(mRadius));
        layout.addView(catLabel(ctx, "Corner radius (4-24)"));
        layout.addView(radiusInput);

        layout.addView(catLabel(ctx, "Colors (tap to change)"));

        /* Color fields: label + colored preview that opens picker */
        final int[] colorVals = { mBg, mBtnBg, mBtnTxt, mBtnBorder, mHeaderBg };
        String[] colorLabels = { "Background", "Button bg", "Button text", "Border", "Header bg" };
        final TextView[] colorPreviews = new TextView[colorVals.length];

        for (int i = 0; i < colorVals.length; i++) {
            final int fi = i;
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 4, 0, 0);
            TextView lbl = new TextView(ctx);
            lbl.setText(colorLabels[i]);
            lbl.setTextColor(0xFF8888AA);
            lbl.setTextSize(13);
            lbl.setPadding(0, 8, 8, 8);
            final TextView preview = new TextView(ctx);
            preview.setText("      ");
            preview.setBackgroundColor(colorVals[i]);
            preview.setPadding(16, 10, 16, 10);
            preview.setClickable(true);
            preview.setOnClickListener(v -> showColorPicker(ctx, colorLabels[fi], fi, colorVals, colorPreviews));
            row.addView(lbl);
            row.addView(preview);
            colorPreviews[i] = preview;
            layout.addView(row);
        }

        ScrollView sv = new ScrollView(ctx);
        sv.addView(layout);
        AlertDialog dlg = new AlertDialog.Builder(ctx)
            .setCustomTitle(makeTitle(ctx, "Customize Appearance"))
            .setView(sv)
            .setPositiveButton("Apply", (d, w) -> {
                try {
                    int c = Integer.parseInt(colsInput.getText().toString().trim());
                    if (c >= 2 && c <= 6) mCols = c;
                } catch (Exception e) {}
                try {
                    float s = Float.parseFloat(sizeInput.getText().toString().trim());
                    if (s >= 0.5f && s <= 2.0f) mKeySize = s;
                } catch (Exception e) {}
                mGridName = nameInput.getText().toString().trim();
                mBg = colorVals[0];
                mBtnBg = colorVals[1];
                mBtnTxt = colorVals[2];
                mBtnBorder = colorVals[3];
                mHeaderBg = colorVals[4];
                try {
                    int r = Integer.parseInt(radiusInput.getText().toString().trim());
                    if (r >= 4 && r <= 24) mRadius = r;
                } catch (Exception e) {}
                saveCustomization();
                invalidate();
                Toast.makeText(ctx, "Customization applied", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .create();
        dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dlg.getWindow().getDecorView().setBackgroundColor(mBg);
        dlg.show();
    }

    /* ─── HSV Color picker ─── */

    private void showHsvColorPicker(final Context ctx, final String label,
                                     final int initialColor, final ColorCallback callback) {
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(12, 8, 12, 8);

        /* HSV picker View */
        final int pickerSize = Math.min(280, (int)(getResources().getDisplayMetrics().widthPixels * 0.7f));
        final HsvPickerView picker = new HsvPickerView(ctx, initialColor, pickerSize);
        layout.addView(picker);

        /* Preview + hex row */
        LinearLayout previewRow = new LinearLayout(ctx);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        previewRow.setPadding(0, 6, 0, 2);
        final View previewSwatch = new View(ctx);
        previewSwatch.setBackgroundColor(initialColor);
        previewSwatch.setLayoutParams(new LinearLayout.LayoutParams(50, 50));
        previewRow.addView(previewSwatch);
        final EditText hexInput = new EditText(ctx);
        hexInput.setText(String.format("%08X", initialColor));
        hexInput.setTextColor(0xFFE8E8F0);
        hexInput.setHintTextColor(0xFF666688);
        hexInput.setBackgroundColor(0xFF2D2D50);
        hexInput.setPadding(8, 6, 8, 6);
        hexInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        previewRow.addView(hexInput);
        layout.addView(previewRow);

        /* RGB inputs */
        LinearLayout rgbRow = new LinearLayout(ctx);
        rgbRow.setOrientation(LinearLayout.HORIZONTAL);
        int[] rgb = { (initialColor >> 16) & 0xFF, (initialColor >> 8) & 0xFF, initialColor & 0xFF };
        String[] rgbLabels = {"R", "G", "B"};
        final EditText[] rgbInputs = new EditText[3];
        for (int i = 0; i < 3; i++) {
            final int fi = i;
            TextView l = new TextView(ctx);
            l.setText(rgbLabels[i]);
            l.setTextColor(0xFFB388FF);
            l.setTextSize(13);
            l.setPadding(4, 6, 2, 6);
            rgbRow.addView(l);
            EditText et = new EditText(ctx);
            et.setText(String.valueOf(rgb[i]));
            et.setTextColor(0xFFE8E8F0);
            et.setBackgroundColor(0xFF2D2D50);
            et.setGravity(Gravity.CENTER);
            et.setPadding(4, 4, 4, 4);
            et.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            final EditText inputRef = et;
            et.setOnEditorActionListener((v, actionId, event) -> {
                try {
                    int val = Integer.parseInt(inputRef.getText().toString().trim());
                    if (val < 0) val = 0; if (val > 255) val = 255;
                    int nc = (fi == 0 ? (val << 16) | (initialColor & 0xFF00FFFF) :
                              fi == 1 ? (val << 8) | (initialColor & 0xFFFF00FF) :
                              val | (initialColor & 0xFFFFFF00));
                    picker.setColor(nc);
                    updateColorDisplay(nc, picker, previewSwatch, hexInput, rgbInputs);
                } catch (Exception e) {}
                return false;
            });
            rgbRow.addView(et);
            rgbInputs[i] = et;
        }
        layout.addView(rgbRow);

        /* Preset row (small swatches) */
        LinearLayout presetRow = new LinearLayout(ctx);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetRow.setPadding(0, 6, 0, 0);
        int[] presets = {0xFFFF0000, 0xFFFFA500, 0xFFFFFF00, 0xFF00FF00, 0xFF0000FF, 0xFF800080};
        for (int pc : presets) {
            View sw = new View(ctx);
            sw.setBackgroundColor(pc);
            sw.setLayoutParams(new LinearLayout.LayoutParams(0, 36, 1));
            sw.setPadding(2, 2, 2, 2);
            final int fp = pc;
            sw.setOnClickListener(v -> {
                picker.setColor(fp);
                updateColorDisplay(fp, picker, previewSwatch, hexInput, rgbInputs);
            });
            presetRow.addView(sw);
        }
        /* Clear button */
        TextView clearBtn = new TextView(ctx);
        clearBtn.setText(" \u2716 ");
        clearBtn.setTextColor(0xFF8888AA);
        clearBtn.setTextSize(16);
        clearBtn.setGravity(Gravity.CENTER);
        clearBtn.setPadding(6, 6, 6, 6);
        clearBtn.setOnClickListener(v -> {
            int nc = 0;
            callback.onColor(nc);
            previewSwatch.setBackgroundColor(0xFF2D2D50);
        });
        presetRow.addView(clearBtn);
        layout.addView(presetRow);

        /* Update callback from picker */
        picker.mCallback = (nc) -> updateColorDisplay(nc, picker, previewSwatch, hexInput, rgbInputs);

        AlertDialog dlg = new AlertDialog.Builder(ctx)
            .setCustomTitle(makeTitle(ctx, label))
            .setView(layout)
            .setPositiveButton("OK", (d, w) -> callback.onColor(picker.getColor()))
            .setNegativeButton("Cancel", null)
            .create();
        dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dlg.getWindow().getDecorView().setBackgroundColor(mBg);
        dlg.show();
    }

    private void updateColorDisplay(int nc, HsvPickerView picker, View swatch, EditText hex, EditText[] rgb) {
        swatch.setBackgroundColor(nc);
        hex.setText(String.format("%08X", nc));
        if (rgb != null) {
            rgb[0].setText(String.valueOf((nc >> 16) & 0xFF));
            rgb[1].setText(String.valueOf((nc >> 8) & 0xFF));
            rgb[2].setText(String.valueOf(nc & 0xFF));
        }
    }

    private interface ColorCallback { void onColor(int color); }

    /* ─── HSV Picker View ─── */

    private static class HsvPickerView extends View {
        private int mSize;
        private float mHue = 0, mSat = 1, mVal = 1;
        private int mColor = 0xFFFF0000;
        private RectF mSvRect, mHueRect;
        private Bitmap mSvBmp, mHueBmp;
        private Paint mBmpPaint, mBorderPaint;
        private boolean mDragSv = false, mDragHue = false;
        private ColorCallback mCallback;

        HsvPickerView(Context ctx, int initialColor, int size) {
            super(ctx);
            mSize = size;
            mBmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
            mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mBorderPaint.setStyle(Paint.Style.STROKE);
            mBorderPaint.setColor(0xFF666688);
            mBorderPaint.setStrokeWidth(1);
            setColor(initialColor);
        }

        void setColor(int c) {
            mColor = c;
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            float[] hsv = new float[3];
            android.graphics.Color.RGBToHSV(r, g, b, hsv);
            mHue = hsv[0]; mSat = hsv[1]; mVal = hsv[2];
            invalidate();
        }

        int getColor() { return mColor; }

        @Override
        protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            float pad = 8;
            float svSize = mSize;
            float hueH = 28;
            mSvRect = new RectF(pad, pad, pad + svSize, pad + svSize);
            mHueRect = new RectF(pad, pad + svSize + 8, pad + svSize, pad + svSize + 8 + hueH);
            buildSvBitmap();
            buildHueBitmap();
        }

        private void buildSvBitmap() {
            int res = 64;
            mSvBmp = Bitmap.createBitmap(res, res, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < res; y++) {
                for (int x = 0; x < res; x++) {
                    float sat = x / (float)(res - 1);
                    float val = 1f - y / (float)(res - 1);
                    mSvBmp.setPixel(x, y, android.graphics.Color.HSVToColor(new float[]{mHue, sat, val}));
                }
            }
        }

        private void buildHueBitmap() {
            int w = (int) mHueRect.width();
            int h = (int) mHueRect.height();
            if (w <= 0 || h <= 0) return;
            mHueBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < w; x++) {
                float hue = 360f * x / (float)(w - 1);
                int col = android.graphics.Color.HSVToColor(new float[]{hue, 1f, 1f});
                for (int y = 0; y < h; y++) mHueBmp.setPixel(x, y, col);
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            if (mSvBmp != null && mSvRect != null) {
                c.drawBitmap(mSvBmp, null, mSvRect, mBmpPaint);
                c.drawRoundRect(mSvRect, 4, 4, mBorderPaint);
                /* Indicator dot */
                float dx = mSvRect.left + mSat * mSvRect.width();
                float dy = mSvRect.top + (1f - mVal) * mSvRect.height();
                mBorderPaint.setColor(0xFFFFFFFF);
                mBorderPaint.setStrokeWidth(2);
                c.drawCircle(dx, dy, 6, mBorderPaint);
                mBorderPaint.setColor(0xFF666688);
                mBorderPaint.setStrokeWidth(1);
            }
            if (mHueBmp != null && mHueRect != null) {
                c.drawBitmap(mHueBmp, null, mHueRect, mBmpPaint);
                c.drawRoundRect(mHueRect, 4, 4, mBorderPaint);
                /* Indicator */
                float hx = mHueRect.left + (mHue / 360f) * mHueRect.width();
                mBorderPaint.setColor(0xFFFFFFFF);
                mBorderPaint.setStrokeWidth(3);
                c.drawRect(hx - 3, mHueRect.top - 2, hx + 3, mHueRect.bottom + 2, mBorderPaint);
                mBorderPaint.setColor(0xFF666688);
                mBorderPaint.setStrokeWidth(1);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    if (mSvRect != null && mSvRect.contains(x, y)) { mDragSv = true; pickSv(x, y); return true; }
                    if (mHueRect != null && mHueRect.contains(x, y)) { mDragHue = true; pickHue(x, y); return true; }
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (mDragSv) { pickSv(x, y); return true; }
                    if (mDragHue) { pickHue(x, y); return true; }
                    break;
                }
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: {
                    mDragSv = false; mDragHue = false; break;
                }
            }
            return false;
        }

        private void pickSv(float x, float y) {
            if (mSvRect == null) return;
            mSat = Math.max(0, Math.min(1, (x - mSvRect.left) / mSvRect.width()));
            mVal = Math.max(0, Math.min(1, 1f - (y - mSvRect.top) / mSvRect.height()));
            updateColor();
        }

        private void pickHue(float x, float y) {
            if (mHueRect == null) return;
            mHue = Math.max(0, Math.min(360, (x - mHueRect.left) / mHueRect.width() * 360f));
            buildSvBitmap();
            updateColor();
            invalidate();
        }

        private void updateColor() {
            mColor = android.graphics.Color.HSVToColor(new float[]{mHue, mSat, mVal});
            if (mCallback != null) mCallback.onColor(mColor);
            invalidate();
        }
    }

    /* ─── Shortcut color picker (wraps HSV picker) ─── */

    private void showShortcutColorPicker(final Context ctx, final int[] result, final TextView preview) {
        showHsvColorPicker(ctx, "Pick key color", result[0] != 0 ? result[0] : 0xFF2D2D50, (nc) -> {
            if (nc == 0) {
                result[0] = 0;
                preview.setBackgroundColor(0xFF2D2D50);
                preview.setText("default");
                preview.setTextColor(0xFF8888AA);
            } else {
                result[0] = nc;
                preview.setBackgroundColor(nc);
                preview.setText("");
                preview.setTextColor(0xFFE8E8F0);
            }
        });
    }

    /* ─── Grid color picker (wraps HSV picker) ─── */

    private void showColorPicker(final Context ctx, final String label,
                                  final int slotIdx, final int[] colorVals,
                                  final TextView[] colorPreviews) {
        showHsvColorPicker(ctx, label, colorVals[slotIdx], (nc) -> {
            colorVals[slotIdx] = nc;
            colorPreviews[slotIdx].setBackgroundColor(nc);
        });
    }

    private int parseHex(String s, int def) {
        try {
            if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
            if (s.startsWith("#")) s = s.substring(1);
            if (s.length() == 6) s = "FF" + s;
            return (int) Long.parseLong(s, 16);
        } catch (Exception e) { return def; }
    }

    private View catLabel(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(0xFFB388FF);
        tv.setTextSize(13);
        tv.setPadding(0, 10, 0, 2);
        return tv;
    }

    private EditText makeEditField(Context ctx, String hint, String value) {
        EditText et = new EditText(ctx);
        et.setText(value);
        et.setHint(hint);
        et.setTextColor(0xFFE8E8F0);
        et.setHintTextColor(0xFF666688);
        et.setBackgroundColor(0xFF2D2D50);
        et.setPadding(12, 8, 12, 8);
        return et;
    }

    /* ════════════════════════════════════════
     * ADD SHORTCUT DIALOG
     * ════════════════════════════════════════ */

    private void startAddShortcut() {
        Context ctx = getContext();
        if (ctx == null) return;
        showShortcutEditor(ctx);
    }

    private void showShortcutEditor(final Context ctx) {
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 10, 30, 10);

        final EditText nameInput = new EditText(ctx);
        nameInput.setHint("Name (e.g. Subdivide)");
        nameInput.setTextColor(0xFFE8E8F0);
        nameInput.setHintTextColor(0xFF666688);
        nameInput.setBackgroundColor(0xFF2D2D50);
        nameInput.setPadding(12, 8, 12, 8);
        layout.addView(nameInput);

        final int[] slotOrds = {-1, -1, -1};
        final TextView[] slotViews = new TextView[3];
        LinearLayout slotsRow = new LinearLayout(ctx);
        slotsRow.setOrientation(LinearLayout.HORIZONTAL);
        slotsRow.setPadding(0, 10, 0, 10);

        for (int i = 0; i < 3; i++) {
            final int fi = i;
            TextView tv = new TextView(ctx);
            tv.setText("Empty");
            tv.setTextColor(0xFF8888AA);
            tv.setTextSize(13);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundColor(0xFF2D2D50);
            tv.setPadding(8, 16, 8, 16);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.setMargins(3, 0, 3, 0);
            tv.setLayoutParams(lp);
            tv.setOnClickListener(v -> showSlotCategoryPicker(ctx, fi, slotOrds, slotViews));
            slotsRow.addView(tv);
            slotViews[i] = tv;
        }
        layout.addView(slotsRow);

        final CheckBox toggleCheck = new CheckBox(ctx);
        toggleCheck.setText("\u21BB Hold mode (stays pressed)");
        toggleCheck.setTextColor(0xFFB388FF);
        layout.addView(toggleCheck);

        /* Custom color picker row */
        final int[] customColor = {0};
        LinearLayout colorRow = new LinearLayout(ctx);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setPadding(0, 8, 0, 4);
        TextView colorLbl = new TextView(ctx);
        colorLbl.setText("Color: ");
        colorLbl.setTextColor(0xFF8888AA);
        colorLbl.setTextSize(13);
        colorLbl.setPadding(0, 8, 8, 8);
        final TextView colorPreview = new TextView(ctx);
        colorPreview.setText("default");
        colorPreview.setTextColor(0xFF8888AA);
        colorPreview.setTextSize(13);
        colorPreview.setPadding(16, 10, 16, 10);
        colorPreview.setBackgroundColor(0xFF2D2D50);
        colorPreview.setClickable(true);
        colorPreview.setOnClickListener(v -> {
            /* Show simple color picker, store result in customColor[0] */
            showShortcutColorPicker(ctx, customColor, colorPreview);
        });
        colorRow.addView(colorLbl);
        colorRow.addView(colorPreview);
        layout.addView(colorRow);

        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setCustomTitle(makeTitle(ctx, "Name + keys"));
        b.setView(layout);
        b.setPositiveButton("Confirm", (d, w) -> {
            String name = nameInput.getText().toString().trim();
            ArrayList<Integer> keys = new ArrayList<>();
            for (int ord : slotOrds) {
                if (ord >= 0) keys.add(ord);
            }
            if (keys.isEmpty()) return;
            if (name.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < keys.size(); i++) {
                    if (i > 0) sb.append("+");
                    sb.append(keyName(keys.get(i)));
                }
                name = sb.toString();
            }
            ShortcutItem si = new ShortcutItem(name, keys, toggleCheck.isChecked());
            si.customColor = customColor[0];
            mShortcuts.add(si);
            persistShortcuts(); invalidate();
        });
        b.setNegativeButton("Cancel", null);
        styleDialog(b.show());
    }

    private void showSlotCategoryPicker(final Context ctx, final int slotIdx, final int[] slotOrds, final TextView[] slotViews) {
        String[] cats = {"Letters", "Numbers", "F-Keys", "Special", "Mouse / Virtual"};
        String[] descs = {"A-Z", "0-9", "F1-F12", "Tab Spc Ent Esc \u2191\u2193\u2190\u2192 Shift Ctrl Alt ...", "Mouse, Scroll, Undo, Redo"};
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 10, 20, 10);
        final AlertDialog[] catDlg = new AlertDialog[1];
        for (int i = 0; i < cats.length; i++) {
            final int fi = i;
            View card = makeCard(ctx, cats[i], descs[i], v -> {
                if (catDlg[0] != null) catDlg[0].dismiss();
                switch (fi) {
                    case 0: showSlotKeyPicker(ctx, slotIdx, slotOrds, slotViews,
                        new String[]{"A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z"},
                        new int[]{25,52,29,48,51,53,45,46,23,49,71,72,31,30,24,73,20,43,44,22,74,50,21,27,28,26}); break;
                    case 1: showSlotKeyPicker(ctx, slotIdx, slotOrds, slotViews,
                        new String[]{"1","2","3","4","5","6","7","8","9","0"},
                        new int[]{15,16,17,18,19,67,68,69,70,66}); break;
                    case 2: showSlotKeyPicker(ctx, slotIdx, slotOrds, slotViews,
                        new String[]{"F1","F2","F3","F4","F5","F6","F7","F8","F9","F10","F11","F12"},
                        new int[]{75,8,9,10,76,77,78,79,80,81,82,11}); break;
                    case 3: showSlotKeyPicker(ctx, slotIdx, slotOrds, slotViews,
                        new String[]{"Esc","Tab","Space","Enter","Delete","Home","End","Ins.","PgUp.","PgDn.","[","]","-","=",";","'","`",",",".","/","\u2191","\u2193","\u2190","\u2192","Shift","Ctrl","Alt"},
                        new int[]{7,41,34,13,42,12,90,89,35,36,85,86,83,84,87,88,14,32,33,47,37,38,39,40,0,1,2}); break;
                    case 4: showSlotKeyPicker(ctx, slotIdx, slotOrds, slotViews,
                        new String[]{"Left Mouse","Right Mouse","Scroll Up","Scroll Down","Undo","Redo","Scroll Toggle"},
                        new int[]{10000,10001,10002,10003,10004,10005,10006}); break;
                }
            });
            layout.addView(card);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setCustomTitle(makeTitle(ctx, "Pick a key for slot " + (slotIdx + 1)));
        b.setView(layout);
        b.setNegativeButton("Back", null);
        catDlg[0] = b.show();
        styleDialog(catDlg[0]);
    }

    private void showSlotKeyPicker(final Context ctx, final int slotIdx, final int[] slotOrds, final TextView[] slotViews, final String[] keys, final int[] ords) {
        final AlertDialog[] self = new AlertDialog[1];
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 10, 20, 10);
        LinearLayout row = null;
        int cols = 4;
        for (int i = 0; i < keys.length; i++) {
            if (i % cols == 0) { row = new LinearLayout(ctx); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER); layout.addView(row); }
            final int ord = ords[i];
            TextView tv = new TextView(ctx);
            tv.setText(keys[i]);
            tv.setTextColor(0xFFE8E8F0);
            tv.setTextSize(14);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundResource(android.R.drawable.editbox_background);
            tv.setPadding(12, 8, 12, 8);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.setMargins(3, 3, 3, 3);
            row.addView(tv, lp);
            tv.setOnClickListener(v -> {
                slotOrds[slotIdx] = ord;
                slotViews[slotIdx].setText(keyName(ord));
                slotViews[slotIdx].setTextColor(0xFFE8E8F0);
                if (self[0] != null) self[0].dismiss();
            });
        }
        ScrollView sv = new ScrollView(ctx);
        sv.addView(layout);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setCustomTitle(makeTitle(ctx, "Slot " + (slotIdx + 1)));
        b.setView(sv);
        b.setNegativeButton("Back", null);
        self[0] = b.show();
        styleDialog(self[0]);
    }

    /* ─── UI helpers ─── */

    private View makeTitle(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(0xFFB388FF);
        tv.setTextSize(18);
        tv.setPadding(30, 20, 30, 10);
        return tv;
    }

    private View makeCard(Context ctx, String title, String desc, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(16, 10, 16, 10);
        card.setBackgroundColor(0xFF2D2D50);
        card.setClickable(true);
        card.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 4, 0, 4);
        card.setLayoutParams(lp);
        TextView titleTv = new TextView(ctx);
        titleTv.setText(title);
        titleTv.setTextColor(0xFFE8E8F0);
        titleTv.setTextSize(17);
        card.addView(titleTv);
        TextView descTv = new TextView(ctx);
        descTv.setText(desc);
        descTv.setTextColor(0xFF8888AA);
        descTv.setTextSize(12);
        card.addView(descTv);
        card.setOnClickListener(listener);
        return card;
    }

    private void styleDialog(AlertDialog dlg) {
        if (dlg == null || dlg.getWindow() == null) return;
        dlg.getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.85f), ViewGroup.LayoutParams.WRAP_CONTENT);
        dlg.getWindow().setGravity(Gravity.CENTER);
        dlg.getWindow().setDimAmount(0.6f);
        dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dlg.getWindow().getDecorView().setBackgroundColor(mBg);
    }

    /* ════════════════════════════════════════
     * PERSISTENCE
     * ════════════════════════════════════════ */

    private static final int[][] BUILTIN_KEYS = {
        {10004}, {10005}, {10006}, {10001}, {0}, {1}, {2}
    };

    private boolean isBuiltinKeySet(ArrayList<Integer> keys) {
        for (int[] bk : BUILTIN_KEYS) {
            if (keys.size() == bk.length) {
                boolean match = true;
                for (int i = 0; i < bk.length; i++) {
                    if (keys.get(i) != bk[i]) { match = false; break; }
                }
                if (match) return true;
            }
        }
        return false;
    }

    private void loadShortcuts() {
        mShortcuts.clear();
        mToggleActive.clear();
        /* Load saved shortcuts from SharedPreferences */
        try {
            Context ctx = getContext();
            if (ctx == null) return;
            String json = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREFS_JSON, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String n = o.getString("n");
                JSONArray ka = o.getJSONArray("k");
                ArrayList<Integer> k = new ArrayList<>();
                for (int j = 0; j < ka.length(); j++) k.add(ka.getInt(j));
                if (isBuiltinKeySet(k)) continue;
                boolean t = o.has("t") && o.getBoolean("t");
                ShortcutItem si = new ShortcutItem(n, k, t);
                si.customColor = o.optInt("c", 0);
                mShortcuts.add(si);
            }
        } catch (Exception e) { Log.e(TAG, "load", e); }
        /* Add builtins FIRST so saved shortcuts always appear AFTER them (fix:
         * new shortcuts that were last before save appear above defaults after restart). */
        addBuiltin("\u21A9 Undo", new int[]{10004});
        addBuiltin("\u21AA Redo", new int[]{10005});
        addBuiltin("Scroll \u21C5", new int[]{10006});
        addBuiltin("Right", new int[]{10001});
        addBuiltin("Shift", new int[]{0});
        addBuiltin("Ctrl", new int[]{1});
        addBuiltin("Alt", new int[]{2});
    }

    private void addBuiltin(String name, int[] ords) {
        ArrayList<Integer> k = new ArrayList<>();
        for (int o : ords) k.add(o);
        mShortcuts.add(0, new ShortcutItem(name, k, false, true));
    }

    private void persistShortcuts() {
        try {
            JSONArray arr = new JSONArray();
            for (ShortcutItem s : mShortcuts) {
                if (s.builtin) continue;
                JSONObject o = new JSONObject();
                o.put("n", s.name);
                JSONArray ka = new JSONArray();
                for (int k : s.keyOrdinals) ka.put(k);
                o.put("k", ka);
                o.put("t", s.toggleMode);
                if (s.customColor != 0) o.put("c", s.customColor);
                arr.put(o);
            }
            getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(PREFS_JSON, arr.toString()).apply();
        } catch (Exception e) { Log.e(TAG, "persist", e); }
    }

    /* ─── Customization persistence ─── */

    private void loadCustomization() {
        try {
            Context ctx = getContext();
            if (ctx == null) return;
            SharedPreferences sp = ctx.getSharedPreferences(PREFS_CUSTOM, Context.MODE_PRIVATE);
            mBg = sp.getInt("bg", 0xFF1A1A2E);
            mBtnBg = sp.getInt("btnBg", 0xFF2D2D50);
            mBtnBgPress = sp.getInt("btnBgPress", 0xFF3D3D6C);
            mBtnBorder = sp.getInt("btnBorder", 0xFF4A4A7A);
            mBtnTxt = sp.getInt("btnTxt", 0xFFE8E8F0);
            mComboTxt = sp.getInt("comboTxt", 0xFF9999BB);
            mToggleOnBg = sp.getInt("toggleOnBg", 0xFF1B5E20);
            mToggleOnBorder = sp.getInt("toggleOnBorder", 0xFF4CAF50);
            mAddBg = sp.getInt("addBg", 0xFF2ECC71);
            mAddBgPress = sp.getInt("addBgPress", 0xFF27AE60);
            mHeaderBg = sp.getInt("headerBg", 0xFF0F0F23);
            mCloseBg = sp.getInt("closeBg", 0xFF7A2A2A);
            mTabActiveBg = sp.getInt("tabActiveBg", 0xFF3D3D6C);
            mTabInactiveBg = sp.getInt("tabInactiveBg", 0xFF1A1A2E);
            mRadius = sp.getInt("radius", 12);
            mCols = sp.getInt("cols", 4);
            mKeySize = sp.getFloat("keySize", 1.0f);
            mGridName = sp.getString("gridName", "");
        } catch (Exception e) { Log.e(TAG, "loadCustomization", e); }
    }

    private void saveCustomization() {
        try {
            Context ctx = getContext();
            if (ctx == null) return;
            ctx.getSharedPreferences(PREFS_CUSTOM, Context.MODE_PRIVATE).edit()
                .putInt("bg", mBg)
                .putInt("btnBg", mBtnBg)
                .putInt("btnBgPress", mBtnBgPress)
                .putInt("btnBorder", mBtnBorder)
                .putInt("btnTxt", mBtnTxt)
                .putInt("comboTxt", mComboTxt)
                .putInt("toggleOnBg", mToggleOnBg)
                .putInt("toggleOnBorder", mToggleOnBorder)
                .putInt("addBg", mAddBg)
                .putInt("addBgPress", mAddBgPress)
                .putInt("headerBg", mHeaderBg)
                .putInt("closeBg", mCloseBg)
                .putInt("tabActiveBg", mTabActiveBg)
                .putInt("tabInactiveBg", mTabInactiveBg)
                .putInt("radius", mRadius)
                .putInt("cols", mCols)
                .putFloat("keySize", mKeySize)
                .putString("gridName", mGridName)
                .apply();
        } catch (Exception e) { Log.e(TAG, "saveCustomization", e); }
    }

    private File getKeyboardsDir() {
        File dir = new File(Environment.getExternalStorageDirectory(), "com.epai.oblender/" + KB_DIR_NAME);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private JSONObject buildFullExportJSON() {
        try {
            JSONObject root = new JSONObject();
            root.put("format_version", 1);
            root.put("name", mGridName.isEmpty() ? "My Grid" : mGridName);

            JSONObject cust = new JSONObject();
            cust.put("bg", mBg);
            cust.put("btnBg", mBtnBg);
            cust.put("btnBgPress", mBtnBgPress);
            cust.put("btnBorder", mBtnBorder);
            cust.put("btnTxt", mBtnTxt);
            cust.put("comboTxt", mComboTxt);
            cust.put("toggleOnBg", mToggleOnBg);
            cust.put("toggleOnBorder", mToggleOnBorder);
            cust.put("addBg", mAddBg);
            cust.put("addBgPress", mAddBgPress);
            cust.put("headerBg", mHeaderBg);
            cust.put("closeBg", mCloseBg);
            cust.put("tabActiveBg", mTabActiveBg);
            cust.put("tabInactiveBg", mTabInactiveBg);
            cust.put("radius", mRadius);
            cust.put("cols", mCols);
            cust.put("keySize", mKeySize);
            root.put("customization", cust);

            JSONArray arr = new JSONArray();
            for (ShortcutItem s : mShortcuts) {
                if (s.builtin) continue;
                JSONObject o = new JSONObject();
                o.put("n", s.name);
                JSONArray ka = new JSONArray();
                for (int k : s.keyOrdinals) ka.put(k);
                o.put("k", ka);
                o.put("t", s.toggleMode);
                if (s.customColor != 0) o.put("c", s.customColor);
                arr.put(o);
            }
            root.put("shortcuts", arr);
            return root;
        } catch (Exception e) { return null; }
    }

    void setOBLSettingFragmentListener(OBLSettingFragmentListener l) { mListener = l; }
    void SetValue(int type, int value) {}
    int GetAsyncKeyState(int type) {
        if (type == 100) return getVisibility() == VISIBLE ? 1 : 0;
        return 0;
    }

    public interface OBLSettingFragmentListener {
        void enterKeyOn(int keys[]);
        void enterKeyOff(int keys[]);
        void enterKey(int keys[]);
        void backspace();
        void enter();
        void closeFragment();
    }
}
