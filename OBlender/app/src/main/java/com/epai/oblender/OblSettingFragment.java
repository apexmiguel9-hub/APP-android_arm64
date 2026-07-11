package com.epai.oblender;

import android.app.AlertDialog;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;

public class OblSettingFragment extends View {
    private final String TAG = "OBL_Grid";
    private static final String PREFS_NAME = "obl_shortcuts";
    private static final String PREFS_JSON = "shortcuts";

    private int mCols = 4;

    /* Material Dark palette */
    private int mBg = 0xFF1A1A2E;
    private int mSurface = 0xFF222244;
    private int mBtnBg = 0xFF2D2D50;
    private int mBtnBgPress = 0xFF3D3D6C;
    private int mBtnBorder = 0xFF4A4A7A;
    private int mBtnTxt = 0xFFE8E8F0;
    private int mComboTxt = 0xFF9999BB;
    private int mAccent = 0xFF7C4DFF;
    private int mAccentLight = 0xFFB388FF;
    private int mToggleOnBg = 0xFF1B5E20;
    private int mToggleOnBorder = 0xFF4CAF50;
    private int mAddBg = 0xFF2ECC71;
    private int mAddBgPress = 0xFF27AE60;
    private int mHeaderBg = 0xFF0F0F23;
    private int mCloseBg = 0xFF7A2A2A;
    private int mRadius = 12;
    private int mBorderW = 1;

    private Paint mPaint, mBorderPaint;
    private int mW, mH;
    private int mPressedIdx = -1;
    private long mPressTime = 0;

    private ArrayList<ShortcutItem> mShortcuts = new ArrayList<>();
    private HashSet<Integer> mToggleActive = new HashSet<>();
    private OBLSettingFragmentListener mListener;

    private Rect mCloseRect, mMoveRect;
    private int mGridTop, mGridBot;

    /* Scrolling */
    private int mScrollY = 0;
    private int mMaxScrollY = 0;
    private float mLastTouchY = 0;
    private boolean mIsDragging = false;

    private static class ShortcutItem {
        String name;
        ArrayList<Integer> keyOrdinals;
        boolean toggleMode;

        ShortcutItem(String n, ArrayList<Integer> k, boolean t) {
            name = n;
            keyOrdinals = k;
            toggleMode = t;
        }
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
        mBorderPaint.setStrokeWidth(mBorderW);

        loadShortcuts();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mW = getWidth(); mH = getHeight();
        if (mW <= 0 || mH <= 0) return;

        /* Background con esquinas redondeadas */
        mPaint.setColor(mBg);
        canvas.drawRoundRect(0, 0, mW, mH, 16, 16, mPaint);

        int headerH = (int) (mH * 0.07f);
        int ctrlH = (int) (mH * 0.09f);
        mGridTop = headerH;
        mGridBot = mH - ctrlH;
        int gridH = mGridBot - mGridTop;

        drawHeader(canvas, headerH);
        drawGrid(canvas, mGridTop, gridH);
        drawControls(canvas, mGridBot, ctrlH);

        if (mPressedIdx >= 0 && System.currentTimeMillis() - mPressTime > 120) {
            mPressedIdx = -1;
            invalidate();
        }
    }

    private void drawHeader(Canvas c, int h) {
        mPaint.setColor(mHeaderBg);
        c.drawRoundRect(0, 0, mW, h + 8, 12, 12, mPaint);

        /* Línea sutil debajo del header */
        mPaint.setColor(0xFF2A2A4A);
        c.drawRect(0, h - 1, mW, h, mPaint);

        mPaint.setColor(mBtnTxt);
        mPaint.setTextSize(h * 0.38f);
        Paint.FontMetrics fm = mPaint.getFontMetrics();
        float y = h / 2f + (fm.bottom - fm.top) / 2f - fm.bottom;
        c.drawText("⌨ SHORTCUTS  " + mShortcuts.size(), mW / 2f, y, mPaint);

        /* Badge de toggle activos */
        if (!mToggleActive.isEmpty()) {
            String badge = mToggleActive.size() + " held";
            mPaint.setColor(mAccent);
            mPaint.setTextSize(h * 0.28f);
            float bw = mPaint.measureText(badge) + 16;
            float bx = mW - bw - 12;
            float by = (h - mPaint.getTextSize()) / 2f;
            c.drawRoundRect(bx, by, bx + bw, by + mPaint.getTextSize() + 6, 8, 8, mPaint);
            mPaint.setColor(Color.WHITE);
            c.drawText(badge, bx + bw / 2f, by + mPaint.getTextSize() + 2, mPaint);
        }
    }

    private void drawGrid(Canvas c, int top, int gridH) {
        int rows = Math.max(1, (int) Math.ceil((float) mShortcuts.size() / mCols) + 1);
        float cellW = (float) mW / mCols;
        float cellH = 66f;
        float pad = 5f;

        int totalH = (int) (rows * cellH);
        mMaxScrollY = Math.max(0, totalH - gridH + 10);
        if (mScrollY > mMaxScrollY) mScrollY = mMaxScrollY;
        if (mScrollY < 0) mScrollY = 0;

        int idx = 0;
        for (int i = 0; i < mShortcuts.size(); i++, idx++) {
            int r = idx / mCols, col = idx % mCols;
            float yy = top + r * cellH - mScrollY;
            if (yy + cellH < top || yy > mGridBot) continue;
            RectF rect = new RectF(col * cellW + pad, yy + pad,
                                   (col + 1) * cellW - pad, yy + cellH - pad);
            drawBtn(c, rect, i, mShortcuts.get(i));
        }

        /* +Add button */
        int r = idx / mCols, col = idx % mCols;
        float yy = top + r * cellH - mScrollY;
        if (yy + cellH >= top && yy <= mGridBot) {
            RectF rect = new RectF(col * cellW + pad, yy + pad,
                                   (col + 1) * cellW - pad, yy + cellH - pad);
            boolean pressed = (mPressedIdx == -2);
            mPaint.setColor(pressed ? mAddBgPress : mAddBg);
            c.drawRoundRect(rect, mRadius, mRadius, mPaint);
            mPaint.setColor(Color.WHITE);
            mPaint.setTextSize(Math.min(28f, rect.height() * 0.35f));
            Paint.FontMetrics fm = mPaint.getFontMetrics();
            float y1 = rect.centerY() + (fm.bottom - fm.top) / 2f - fm.bottom;
            c.drawText("+ Add", rect.centerX(), y1, mPaint);
        }
    }

    private void drawBtn(Canvas c, RectF r, int idx, ShortcutItem sc) {
        boolean pressed = (mPressedIdx == idx);
        boolean toggleActive = mToggleActive.contains(idx);
        boolean isToggle = sc.toggleMode;

        int bg;
        int txtCol = mBtnTxt;
        int borderCol = mBtnBorder;

        if (toggleActive) {
            bg = mToggleOnBg;
            borderCol = mToggleOnBorder;
            txtCol = 0xFFA5D6A7;
        } else if (pressed) {
            bg = mBtnBgPress;
        } else {
            bg = mBtnBg;
        }

        /* Sombra sutil */
        mPaint.setColor(0x11000000);
        c.drawRoundRect(r.left + 2, r.top + 2, r.right + 2, r.bottom + 2, mRadius, mRadius, mPaint);

        mPaint.setColor(bg);
        c.drawRoundRect(r, mRadius, mRadius, mPaint);

        mBorderPaint.setColor(borderCol);
        mBorderPaint.setStrokeWidth(isToggle ? 2 : 1);
        c.drawRoundRect(r, mRadius, mRadius, mBorderPaint);

        /* Indicador de toggle activo */
        if (toggleActive) {
            mPaint.setColor(mToggleOnBorder);
            mPaint.setTextSize(18f);
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
        mPaint.setColor(mHeaderBg);
        c.drawRoundRect(0, top, mW, top + h + 8, 0, 0, mPaint);

        float bw = mW / 5f;
        mCloseRect = new Rect((int) (mW - bw * 1.5f), top, mW, top + h);
        mMoveRect = new Rect(0, top, (int) (bw * 1.5f), top + h);

        float textSize = h * 0.4f;

        mPaint.setColor(mCloseBg);
        c.drawRoundRect(new RectF(mCloseRect), 8, 8, mPaint);
        mPaint.setColor(Color.WHITE);
        mPaint.setTextSize(textSize);
        Paint.FontMetrics fm = mPaint.getFontMetrics();
        float d = (fm.bottom - fm.top) / 2f - fm.bottom;
        c.drawText("✕  Close", mCloseRect.exactCenterX(), mCloseRect.exactCenterY() + d, mPaint);

        mPaint.setColor(mBtnBorder);
        c.drawRoundRect(new RectF(mMoveRect), 8, 8, mPaint);
        mPaint.setColor(Color.parseColor("#9999AA"));
        mPaint.setTextSize(textSize);
        c.drawText("↕  Move", mMoveRect.exactCenterX(), mMoveRect.exactCenterY() + d, mPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX(), y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                mLastTouchY = y;
                mIsDragging = false;

                if (mCloseRect != null && mCloseRect.contains((int) x, (int) y)) {
                    if (mListener != null) mListener.closeFragment();
                    return true;
                }
                if (mMoveRect != null && mMoveRect.contains((int) x, (int) y)) {
                    if (mListener != null) mListener.enterKey(new int[]{10000});
                    return true;
                }

                int hitIdx = hitTest(x, y);
                if (hitIdx >= 0) {
                    mPressedIdx = hitIdx;
                    mPressTime = System.currentTimeMillis();
                    invalidate();
                    performHapticFeedback(0);
                    executeShortcut(hitIdx);
                    postDelayed(() -> { mPressedIdx = -1; invalidate(); }, 80);
                    return true;
                }
                if (hitIdx == -99) {
                    mPressedIdx = -2;
                    mPressTime = System.currentTimeMillis();
                    invalidate();
                    startAddShortcut();
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                float dy = y - mLastTouchY;
                if (Math.abs(dy) > 10f) {
                    mIsDragging = true;
                }
                if (mIsDragging) {
                    mScrollY -= (int) dy;
                    if (mScrollY < 0) mScrollY = 0;
                    if (mScrollY > mMaxScrollY) mScrollY = mMaxScrollY;
                    mLastTouchY = y;
                    invalidate();
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                mPressedIdx = -1;
                mIsDragging = false;
                invalidate();
                performClick();
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                mPressedIdx = -1;
                mIsDragging = false;
                invalidate();
                break;
            }
        }
        return true;
    }

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

    private void executeShortcut(int index) {
        if (index < 0 || index >= mShortcuts.size() || mListener == null) return;
        ShortcutItem sc = mShortcuts.get(index);
        ArrayList<Integer> mods = new ArrayList<>();
        int actionKey = -1;
        for (int ord : sc.keyOrdinals) {
            if (ord == 0 || ord == 1 || ord == 2) mods.add(ord);
            else actionKey = ord;
        }

        if (sc.toggleMode && mods.size() > 0 && actionKey == -1) {
            /* Pure modifier toggle: toggle held/released */
            if (mToggleActive.contains(index)) {
                /* Release */
                for (int m : mods) mListener.enterKeyOff(new int[]{m});
                mToggleActive.remove(index);
            } else {
                /* Hold */
                for (int m : mods) mListener.enterKeyOn(new int[]{m});
                mToggleActive.add(index);
            }
        } else if (sc.toggleMode && actionKey >= 0) {
            /* Key combo with toggle: treat as momentary press but show visual */
            for (int m : mods) mListener.enterKeyOn(new int[]{m});
            mListener.enterKey(new int[]{actionKey});
            for (int m : mods) mListener.enterKeyOff(new int[]{m});
        } else {
            /* Normal momentary press */
            for (int m : mods) mListener.enterKeyOn(new int[]{m});
            if (actionKey >= 0) mListener.enterKey(new int[]{actionKey});
            for (int m : mods) mListener.enterKeyOff(new int[]{m});
        }
        invalidate();
    }

    private void clearAllToggles() {
        for (int idx : mToggleActive) {
            if (idx < 0 || idx >= mShortcuts.size()) continue;
            ShortcutItem sc = mShortcuts.get(idx);
            ArrayList<Integer> mods = new ArrayList<>();
            for (int ord : sc.keyOrdinals) {
                if (ord == 0 || ord == 1 || ord == 2) mods.add(ord);
            }
            if (mListener != null) {
                for (int m : mods) mListener.enterKeyOff(new int[]{m});
            }
        }
        mToggleActive.clear();
        invalidate();
    }

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
            if (raw.equals("Space")) return "Space";
            if (raw.equals("Enter")) return "Enter";
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
        if (ord == 10001) return "Right";
        if (ord == 10004) return "Undo";
        if (ord == 10005) return "Redo";
        if (ord == 10006) return "Scroll";
        return "?";
    }

    private void startAddShortcut() {
        Context ctx = getContext();
        if (ctx == null) return;
        showModifierDialog(ctx);
    }

    private void showModifierDialog(final Context ctx) {
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 10, 30, 10);

        final boolean[] checked = new boolean[3];
        String[] labels = {"Shift", "Ctrl", "Alt"};
        for (int i = 0; i < 3; i++) {
            CheckBox cb = new CheckBox(ctx);
            cb.setText(labels[i]);
            cb.setTextColor(0xFFE8E8F0);
            final int fi = i;
            cb.setOnCheckedChangeListener((b, is) -> checked[fi] = is);
            layout.addView(cb);
        }

        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setCustomTitle(createTitleView(ctx, "Hold modifiers (optional)"));
        b.setView(layout);
        b.setPositiveButton("Next", (d, w) -> showCategoryDialog(ctx, checked));
        b.setNegativeButton("Cancel", null);
        AlertDialog dlg = b.show();
        styleDialog(dlg);
    }

    private void showCategoryDialog(final Context ctx, final boolean[] mods) {
        String[] cats = {"Letters", "Numbers", "F-Keys", "Special", "Mouse / Virtual"};
        String[] descs = {
            "A B C D E F G H I J K L M N O P Q R S T U V W X Y Z",
            "0 1 2 3 4 5 6 7 8 9",
            "F1 - F12",
            "Tab Space Enter Esc \u2191 \u2193 \u2190 \u2192 Shift Ctrl Alt ...",
            "Left Right Middle Mouse, Scroll, Undo, Redo"
        };

        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 10, 20, 10);

        for (int i = 0; i < cats.length; i++) {
            final int fi = i;
            View card = createCardItem(ctx, cats[i], descs[i], v -> {
                switch (fi) {
                    case 0: showKeyDialog(ctx, mods, new String[]{"A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z"}, new int[]{25,52,29,48,51,53,45,46,23,49,71,72,31,30,24,73,20,43,44,22,74,50,21,27,28,26}); break;
                    case 1: showKeyDialog(ctx, mods, new String[]{"1","2","3","4","5","6","7","8","9","0"}, new int[]{15,16,17,18,19,67,68,69,70,66}); break;
                    case 2: showKeyDialog(ctx, mods, new String[]{"F1","F2","F3","F4","F5","F6","F7","F8","F9","F10","F11","F12"}, new int[]{75,8,9,10,76,77,78,79,80,81,82,11}); break;
                    case 3: showKeyDialog(ctx, mods, new String[]{"Esc","Tab","Space","Enter","Delete","Home","End","Ins.","PgUp.","PgDn.","[","]","-","=",";","'","`",",",".","/","\u2191","\u2193","\u2190","\u2192","Shift","Ctrl","Alt"}, new int[]{7,41,34,13,42,12,90,89,35,36,85,86,83,84,87,88,14,32,33,47,37,38,39,40,0,1,2}); break;
                    case 4: showKeyDialog(ctx, mods, new String[]{"Left Mouse","Right Mouse","Middle Mouse","Scroll Up","Scroll Down","Undo","Redo","Scroll Toggle"}, new int[]{10000,10001,10002,10003,10004,10005,10006,10007}); break;
                }
            });
            layout.addView(card);
        }

        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setCustomTitle(createTitleView(ctx, "Pick a key category"));
        b.setView(layout);
        b.setNegativeButton("Cancel", null);
        AlertDialog dlg = b.show();
        styleDialog(dlg);
    }

    private void showKeyDialog(final Context ctx, final boolean[] mods, String[] keys, int[] ords) {
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 10, 20, 10);

        /* Grid de teclas */
        LinearLayout row = null;
        int cols = 4;
        for (int i = 0; i < keys.length; i++) {
            if (i % cols == 0) {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                layout.addView(row);
            }
            final int fi = i;
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
            tv.setOnClickListener(v -> showNameDialog(ctx, mods, ord));
        }

        ScrollView sv = new ScrollView(ctx);
        sv.addView(layout);

        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setCustomTitle(createTitleView(ctx, "Select a key"));
        b.setView(sv);
        b.setNegativeButton("Back", (d, w) -> showCategoryDialog(ctx, mods));
        AlertDialog dlg = b.show();
        styleDialog(dlg);
    }

    private void showNameDialog(Context ctx, boolean[] modSelected, int actionOrd) {
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 15, 30, 15);

        final EditText input = new EditText(ctx);
        input.setHint("e.g. Subdivide");
        input.setTextColor(0xFFE8E8F0);
        input.setHintTextColor(0xFF666688);
        input.setBackgroundColor(0xFF2D2D50);
        input.setPadding(12, 8, 12, 8);
        layout.addView(input);

        final CheckBox toggleCheck = new CheckBox(ctx);
        toggleCheck.setText("\u21BB Hold mode (stays pressed until tapped again)");
        toggleCheck.setTextColor(0xFFB388FF);
        layout.addView(toggleCheck);

        StringBuilder preview = new StringBuilder();
        if (modSelected[0]) preview.append("Shift+");
        if (modSelected[1]) preview.append("Ctrl+");
        if (modSelected[2]) preview.append("Alt+");
        preview.append(keyName(actionOrd));

        TextView comboView = new TextView(ctx);
        comboView.setText("Combo: " + preview.toString());
        comboView.setTextColor(0xFF9999BB);
        comboView.setTextSize(16);
        comboView.setPadding(0, 8, 0, 0);
        layout.addView(comboView);

        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setCustomTitle(createTitleView(ctx, "Name your shortcut"));
        b.setView(layout);
        b.setPositiveButton("Confirm", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) name = preview.toString();
            saveShortcut(name, modSelected, actionOrd, toggleCheck.isChecked());
        });
        b.setNegativeButton("Cancel", null);
        AlertDialog dlg = b.show();
        styleDialog(dlg);
    }

    private View createTitleView(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(0xFFB388FF);
        tv.setTextSize(18);
        tv.setPadding(30, 20, 30, 10);
        return tv;
    }

    private View createCardItem(Context ctx, String title, String desc, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(16, 10, 16, 10);
        card.setBackgroundColor(0xFF2D2D50);
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
        dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dlg.getWindow().setLayout(
            (int) (getResources().getDisplayMetrics().widthPixels * 0.85f),
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dlg.getWindow().setGravity(Gravity.CENTER);
    }

    private void saveShortcut(String name, boolean[] mods, int actionOrd, boolean toggleMode) {
        ArrayList<Integer> keys = new ArrayList<>();
        if (mods[0]) keys.add(0);
        if (mods[1]) keys.add(1);
        if (mods[2]) keys.add(2);
        keys.add(actionOrd);
        mShortcuts.add(new ShortcutItem(name, keys, toggleMode));
        persistShortcuts();
        invalidate();
    }

    private void loadShortcuts() {
        mShortcuts.clear();
        mToggleActive.clear();
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
                boolean t = false;
                if (o.has("t")) t = o.getBoolean("t");
                mShortcuts.add(new ShortcutItem(n, k, t));
            }
        } catch (Exception e) {
            Log.e(TAG, "load", e);
        }
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
        mShortcuts.add(new ShortcutItem(name, k, false));
    }

    private void persistShortcuts() {
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
            getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(PREFS_JSON, arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "persist", e);
        }
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
        void closeFragment();
    }
}
