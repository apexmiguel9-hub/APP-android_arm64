package com.epai.oblender;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;

public class OblSettingFragment extends View {
    private final String TAG = "OBL_Grid";
    private static final String PREFS_NAME = "obl_shortcuts";
    private static final String PREFS_JSON = "shortcuts";

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

    /* Keyboard tab state */
    private boolean mShiftActive = false;

    /* Tab bar rects for touch */
    private Rect mShortcutsTabRect, mKeyboardTabRect, mNumPadTabRect;

    private static class ShortcutItem {
        String name;
        ArrayList<Integer> keyOrdinals;
        boolean toggleMode;
        boolean builtin;
        ShortcutItem(String n, ArrayList<Integer> k, boolean t) { name = n; keyOrdinals = k; toggleMode = t; builtin = false; }
        ShortcutItem(String n, ArrayList<Integer> k, boolean t, boolean b) { name = n; keyOrdinals = k; toggleMode = t; builtin = b; }
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
        float cellH = 66f;
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
        else bg = mBtnBg;

        mPaint.setColor(0x11000000);
        c.drawRoundRect(r.left + 2, r.top + 2, r.right + 2, r.bottom + 2, mRadius, mRadius, mPaint);
        mPaint.setColor(bg);
        c.drawRoundRect(r, mRadius, mRadius, mPaint);
        mBorderPaint.setColor(borderCol);
        mBorderPaint.setStrokeWidth(sc.toggleMode ? 2 : 1);
        c.drawRoundRect(r, mRadius, mRadius, mBorderPaint);

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
        float rowH = Math.min(52f, (gridH - pad * (rows + 1)) / rows);
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
        float cellH = Math.min(64f, (gridH - 10f) / rows);
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
        float cellH = 66f;
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

        String[] items = {"Export shortcuts", "Import shortcuts", "Cancel"};
        new AlertDialog.Builder(ctx)
            .setCustomTitle(makeTitle(ctx, "Grid Settings"))
            .setItems(items, (d, w) -> {
                if (w == 0) exportShortcuts();
                else if (w == 1) importShortcuts();
            })
            .show();
    }

    private void exportShortcuts() {
        Context ctx = getContext();
        if (ctx == null) return;
        try {
            JSONArray arr = new JSONArray();
            for (ShortcutItem s : mShortcuts) {
                JSONObject o = new JSONObject();
                o.put("n", s.name);
                JSONArray ka = new JSONArray();
                for (int k : s.keyOrdinals) ka.put(k);
                o.put("k", ka);
                o.put("t", s.toggleMode);
                arr.put(o);
            }
            String json = arr.toString(2);
            ClipboardManager clip = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            clip.setPrimaryClip(ClipData.newPlainText("obl_shortcuts", json));
            Toast.makeText(ctx, "Exported " + mShortcuts.size() + " shortcuts to clipboard", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(ctx, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void importShortcuts() {
        Context ctx = getContext();
        if (ctx == null) return;
        try {
            ClipboardManager clip = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (!clip.hasPrimaryClip()) { Toast.makeText(ctx, "Clipboard is empty", Toast.LENGTH_SHORT).show(); return; }
            String json = clip.getPrimaryClip().getItemAt(0).getText().toString();
            JSONArray arr = new JSONArray(json);
            int imported = 0;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String n = o.getString("n");
                JSONArray ka = o.getJSONArray("k");
                ArrayList<Integer> k = new ArrayList<>();
                for (int j = 0; j < ka.length(); j++) k.add(ka.getInt(j));
                boolean t = o.has("t") && o.getBoolean("t");
                /* Skip if it matches a builtin */
                if (isBuiltinKeySet(k)) continue;
                mShortcuts.add(new ShortcutItem(n, k, t));
                imported++;
            }
            persistShortcuts();
            invalidate();
            Toast.makeText(ctx, "Imported " + imported + " shortcuts", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(ctx, "Import failed: invalid JSON", Toast.LENGTH_SHORT).show();
        }
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
            mShortcuts.add(new ShortcutItem(name, keys, toggleCheck.isChecked()));
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
        dlg.getWindow().getDecorView().setBackgroundColor(0xFF1A1A2E);
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
                mShortcuts.add(new ShortcutItem(n, k, t));
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
                arr.put(o);
            }
            getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(PREFS_JSON, arr.toString()).apply();
        } catch (Exception e) { Log.e(TAG, "persist", e); }
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
