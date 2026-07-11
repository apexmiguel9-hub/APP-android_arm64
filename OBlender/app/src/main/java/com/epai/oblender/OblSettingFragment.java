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
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class OblSettingFragment extends View {
    private final String TAG = "OBL_Grid";
    private static final String PREFS_NAME = "obl_shortcuts";
    private static final String PREFS_JSON = "shortcuts";

    private int mCols = 4;
    private int mBg = Color.parseColor("#1A1A2E");
    private int mBtnBg = Color.parseColor("#2D2D44");
    private int mBtnBgPress = Color.parseColor("#3D3D5C");
    private int mBtnBorder = Color.parseColor("#4A4A6A");
    private int mBtnTxt = Color.parseColor("#E8E8F0");
    private int mComboTxt = Color.parseColor("#8888AA");
    private int mAddBg = Color.parseColor("#2ECC71");
    private int mAddBgPress = Color.parseColor("#27AE60");
    private int mHeaderBg = Color.parseColor("#16162A");
    private int mCloseBg = Color.parseColor("#7A2A2A");
    private int mRadius = 10;
    private int mBorderW = 1;

    private Paint mPaint, mBorderPaint;
    private int mW, mH;
    private int mPressedIdx = -1;
    private long mPressTime = 0;

    private ArrayList<ShortcutItem> mShortcuts = new ArrayList<>();
    private OBLSettingFragmentListener mListener;

    private Rect mCloseRect, mMoveRect;
    private int mGridTop, mGridBot;

    /* Scrolling */
    private int mScrollY = 0;
    private int mMaxScrollY = 0;
    private float mLastTouchY = 0;
    private boolean mIsDragging = false;

    private class ShortcutItem {
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

        mPaint.setColor(mBg);
        canvas.drawRect(0, 0, mW, mH, mPaint);

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
        c.drawRect(0, 0, mW, h, mPaint);
        mPaint.setColor(mBtnTxt);
        mPaint.setTextSize(h * 0.4f);
        Paint.FontMetrics fm = mPaint.getFontMetrics();
        float y = h / 2f + (fm.bottom - fm.top) / 2f - fm.bottom;
        c.drawText("⌨ SHORTCUTS  (" + mShortcuts.size() + ")", mW / 2f, y, mPaint);
    }

    private void drawGrid(Canvas c, int top, int gridH) {
        int rows = Math.max(1, (int) Math.ceil((float) mShortcuts.size() / mCols) + 1);
        float cellW = (float) mW / mCols;
        float cellH = 64f;
        float pad = 4f;

        int totalH = (int) (rows * cellH);
        mMaxScrollY = Math.max(0, totalH - gridH);
        if (mScrollY > mMaxScrollY) mScrollY = mMaxScrollY;
        if (mScrollY < 0) mScrollY = 0;

        int idx = 0;
        for (int i = 0; i < mShortcuts.size(); i++, idx++) {
            int r = idx / mCols, col = idx % mCols;
            float yy = top + r * cellH - mScrollY;
            if (yy + cellH < top || yy > mGridBot) continue;
            RectF rect = new RectF(col * cellW + pad, yy + pad,
                                   (col + 1) * cellW - pad, yy + cellH - pad);
            boolean pressed = (mPressedIdx == i);
            drawBtn(c, rect, i, pressed ? mBtnBgPress : mBtnBg, mBtnTxt,
                    mShortcuts.get(i).name, comboDisplay(mShortcuts.get(i)));
        }

        /* +Add button */
        int r = idx / mCols, col = idx % mCols;
        float yy = top + r * cellH - mScrollY;
        if (yy + cellH >= top && yy <= mGridBot) {
            RectF rect = new RectF(col * cellW + pad, yy + pad,
                                   (col + 1) * cellW - pad, yy + cellH - pad);
            boolean pressed = (mPressedIdx == -2);
            drawBtn(c, rect, -2, pressed ? mAddBgPress : mAddBg, Color.WHITE, "+ Add", "");
        }
    }

    private int mExtraRows = 0;

    private void drawBtn(Canvas c, RectF r, int idx, int bg, int txtCol, String line1, String line2) {
        mPaint.setColor(bg);
        c.drawRoundRect(r, mRadius, mRadius, mPaint);
        mBorderPaint.setColor(mBtnBorder);
        c.drawRoundRect(r, mRadius, mRadius, mBorderPaint);

        float h = r.height();
        float sz1 = Math.min(32f, h * 0.3f);
        float sz2 = Math.min(15f, h * 0.16f);

        mPaint.setColor(txtCol);
        mPaint.setTextSize(sz1);
        Paint.FontMetrics fm = mPaint.getFontMetrics();
        float y1 = r.centerY() - (line2.isEmpty() ? 0 : sz1 * 0.08f);
        c.drawText(line1, r.centerX(), y1, mPaint);

        if (!line2.isEmpty()) {
            mPaint.setTextSize(sz2);
            mPaint.setColor(mComboTxt);
            c.drawText(line2, r.centerX(), y1 + sz1 * 0.5f + sz2 * 0.2f, mPaint);
        }
    }

    private void drawControls(Canvas c, int top, int h) {
        mPaint.setColor(mHeaderBg);
        c.drawRect(0, top, mW, top + h, mPaint);

        float bw = mW / 5f;
        mCloseRect = new Rect((int) (mW - bw * 1.5f), top, mW, top + h);
        mMoveRect = new Rect(0, top, (int) (bw * 1.5f), top + h);

        float textSize = h * 0.4f;

        mPaint.setColor(mCloseBg);
        c.drawRoundRect(new RectF(mCloseRect), 6, 6, mPaint);
        mPaint.setColor(Color.WHITE);
        mPaint.setTextSize(textSize);
        Paint.FontMetrics fm = mPaint.getFontMetrics();
        float d = (fm.bottom - fm.top) / 2f - fm.bottom;
        c.drawText("✕  Close", mCloseRect.exactCenterX(), mCloseRect.exactCenterY() + d, mPaint);

        mPaint.setColor(mBtnBorder);
        c.drawRoundRect(new RectF(mMoveRect), 6, 6, mPaint);
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

                /* Check grid taps */
                int hitIdx = hitTest(x, y);
                if (hitIdx >= 0) {
                    mPressedIdx = hitIdx;
                    mPressTime = System.currentTimeMillis();
                    invalidate();
                    performHapticFeedback(0);
                    postDelayed(() -> { executeShortcut(hitIdx); mPressedIdx = -1; }, 80);
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
                if (!mIsDragging) {
                    int hitIdx = hitTest(x, y);
                    if (hitIdx >= 0 && mPressedIdx == hitIdx) {
                        /* Already handled by postDelayed */
                    }
                }
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
        float cellH = 64f;
        float pad = 4f;

        int maxRows = Math.max(1, (int) Math.ceil((float) mShortcuts.size() / mCols) + 1);

        int col = (int) (x / cellW);
        if (col < 0 || col >= mCols) return -1;

        for (int r = 0; r < maxRows; r++) {
            float yy = mGridTop + r * cellH - mScrollY;
            if (y >= yy && y <= yy + cellH) {
                int idx = r * mCols + col;
                if (idx < mShortcuts.size()) {
                    return idx;
                }
                if (idx == mShortcuts.size()) {
                    return -99; /* +Add button */
                }
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
        if (sc.toggleMode) {
            /* Toggle: send modifier on, leave it on.
             * For basic key (no modifier), just send key down+up. */
            if (actionKey >= 0) {
                /* Key with toggle: send ON then OFF (short press) since toggling a regular key
                 * doesn't have a "held" concept. Use as momentary. */
                for (int m : mods) mListener.enterKeyOn(new int[]{m});
                mListener.enterKey(new int[]{actionKey});
                for (int m : mods) mListener.enterKeyOff(new int[]{m});
            } else {
                /* Pure modifier toggle: hold it. The system tracks modifier state. */
                for (int m : mods) mListener.enterKeyOn(new int[]{m});
            }
        } else {
            /* Normal momentary press. */
            for (int m : mods) mListener.enterKeyOn(new int[]{m});
            if (actionKey >= 0) mListener.enterKey(new int[]{actionKey});
            for (int m : mods) mListener.enterKeyOff(new int[]{m});
        }
        /* DO NOT close the fragment — let user keep tapping. */
    }

    private String comboDisplay(ShortcutItem sc) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sc.keyOrdinals.size(); i++) {
            if (i > 0) sb.append("+");
            sb.append(keyName(sc.keyOrdinals.get(i)));
        }
        if (sc.toggleMode) sb.append(" [T]");
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
            if (raw.equals("UpArrow")) return "↑";
            if (raw.equals("DownArrow")) return "↓";
            if (raw.equals("LeftArrow")) return "←";
            if (raw.equals("RightArrow")) return "→";
            return raw;
        }
        /* Special virtual keys for built-in functions */
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
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle("Hold modifiers (optional)");
        final String[] items = {"Shift", "Ctrl", "Alt"};
        final boolean[] checked = new boolean[3];
        b.setMultiChoiceItems(items, null, (d, w, is) -> checked[w] = is);
        b.setPositiveButton("Next", (d, w) -> showCategoryDialog(ctx, checked));
        b.setNegativeButton("Cancel", null);
        b.show();
    }

    private void showCategoryDialog(final Context ctx, final boolean[] mods) {
        final String[] cats = {"Letters", "Numbers", "F-Keys", "Special", "Mouse / Virtual"};
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle("Pick a key");
        b.setItems(cats, (d, w) -> {
            switch (w) {
                case 0: showKeyDialog(ctx, mods, new String[]{"A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z"}, new int[]{25,52,29,48,51,53,45,46,23,49,71,72,31,30,24,73,20,43,44,22,74,50,21,27,28,26}); break;
                case 1: showKeyDialog(ctx, mods, new String[]{"1","2","3","4","5","6","7","8","9","0"}, new int[]{15,16,17,18,19,67,68,69,70,66}); break;
                case 2: showKeyDialog(ctx, mods, new String[]{"F1","F2","F3","F4","F5","F6","F7","F8","F9","F10","F11","F12"}, new int[]{75,8,9,10,76,77,78,79,80,81,82,11}); break;
                case 3: showKeyDialog(ctx, mods, new String[]{"Esc","Tab","Space","Enter","Delete","Home","End","Ins.","PgUp.","PgDn.","[","]","-","=",";","'","`",",",".","/","↑","↓","←","→","Shift","Ctrl","Alt"}, new int[]{7,41,34,13,42,12,90,89,35,36,85,86,83,84,87,88,14,32,33,47,37,38,39,40,0,1,2}); break;
                case 4: showKeyDialog(ctx, mods, new String[]{"Left Mouse","Right Mouse","Middle Mouse","Scroll Up","Scroll Down","Undo","Redo","Scroll Toggle"}, new int[]{10000,10001,10002,10003,10004,10005,10006,10007}); break;
            }
        });
        b.setNegativeButton("Cancel", null);
        b.show();
    }

    private void showKeyDialog(final Context ctx, final boolean[] mods, String[] keys, int[] ords) {
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle("Select key");
        b.setItems(keys, (d, w) -> showNameDialog(ctx, mods, ords[w]));
        b.setNegativeButton("Back", (d, w) -> showCategoryDialog(ctx, mods));
        b.show();
    }

    private void showNameDialog(Context ctx, boolean[] modSelected, int actionOrd) {
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);

        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText input = new EditText(ctx);
        input.setHint("e.g. Subdivide");
        layout.addView(input);

        final CheckBox toggleCheck = new CheckBox(ctx);
        toggleCheck.setText("Toggle mode (stays held until pressed again)");
        layout.addView(toggleCheck);

        StringBuilder preview = new StringBuilder();
        if (modSelected[0]) preview.append("Shift+");
        if (modSelected[1]) preview.append("Ctrl+");
        if (modSelected[2]) preview.append("Alt+");
        preview.append(keyName(actionOrd));

        b.setView(layout);
        b.setTitle("Name your shortcut");
        b.setMessage("Combo: " + preview);
        b.setPositiveButton("Confirm", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) name = preview.toString();
            saveShortcut(name, modSelected, actionOrd, toggleCheck.isChecked());
        });
        b.setNegativeButton("Cancel", null);
        b.show();
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
        /* Add built-in virtual shortcuts. */
        addBuiltin("\u21A9 Undo", new int[]{10004});
        addBuiltin("\u21AA Redo", new int[]{10005});
        addBuiltin("Scroll\u21C5", new int[]{10006});
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
