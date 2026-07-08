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
import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class OblSettingFragment extends View {
    private final String TAG = "OBL_Grid";
    private static final String PREFS_NAME = "obl_shortcuts";
    private static final String PREFS_JSON = "shortcuts";

    private int mBg = Color.parseColor("#2B2B2B");
    private int mBtnBg = Color.parseColor("#505050");
    private int mBtnTxt = Color.WHITE;
    private int mAccent = Color.parseColor("#E67E22");
    private int mAddBg = Color.parseColor("#27AE60");
    private int mCtrlBg = Color.parseColor("#444444");
    private int mCtrlTxt = Color.parseColor("#AAAAAA");
    private float mRadius = 8f;

    private Paint mPaint;
    private int mW, mH;
    private int mGridRows = 5, mGridCols = 6;

    private ArrayList<ShortcutItem> mShortcuts = new ArrayList<>();
    private OBLSettingFragmentListener mListener;

    private Rect mCloseRect = null, mMoveRect = null;
    private RectF[] mBtnRects = null;
    private RectF mAddRect = null;

    private class ShortcutItem {
        String name;
        ArrayList<Integer> keyOrdinals;
        ShortcutItem(String n, ArrayList<Integer> k) { name = n; keyOrdinals = k; }
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
        loadShortcuts();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mW = getWidth(); mH = getHeight();
        if (mW <= 0 || mH <= 0) return;

        mPaint.setColor(mBg);
        canvas.drawRect(0, 0, mW, mH, mPaint);

        recomputeLayout();
        drawShortcuts(canvas);
        drawControls(canvas);
    }

    private void recomputeLayout() {
        int cells = mGridRows * mGridCols;
        int botH = mH / (mGridRows + 1);
        int gridH = mH - botH;
        float cw = (float) mW / mGridCols;
        float ch = (float) gridH / mGridRows;

        mBtnRects = new RectF[cells];
        for (int i = 0; i < cells; i++) {
            int r = i / mGridCols, c = i % mGridCols;
            mBtnRects[i] = new RectF(c * cw + 3, r * ch + 3, (c + 1) * cw - 3, (r + 1) * ch - 3);
        }

        int scCount = mShortcuts.size();
        int addIdx = Math.min(scCount, cells - 1);
        if (addIdx < cells) {
            int r = addIdx / mGridCols, c = addIdx % mGridCols;
            mAddRect = new RectF(c * cw + 3, r * ch + 3, (c + 1) * cw - 3, (r + 1) * ch - 3);
        } else {
            mAddRect = null;
        }

        float ctrlW = mW / 3f;
        mCloseRect = new Rect(mW - (int) ctrlW, gridH, mW, mH);
        mMoveRect = new Rect(0, gridH, (int) ctrlW, mH);
    }

    private void drawShortcuts(Canvas canvas) {
        int cells = mGridRows * mGridCols;
        if (mBtnRects == null) return;

        int idx = 0;
        for (int i = 0; i < mShortcuts.size() && idx < cells; i++, idx++) {
            drawRoundedBtn(canvas, mBtnRects[idx], mBtnBg, mBtnTxt, mShortcuts.get(i).name,
                comboDisplay(mShortcuts.get(i)));
        }

        if (idx < cells && mAddRect != null) {
            drawRoundedBtn(canvas, mAddRect, mAddBg, Color.WHITE, "+", "Add");
        }
    }

    private void drawRoundedBtn(Canvas c, RectF r, int bg, int txtColor, String line1, String line2) {
        mPaint.setColor(bg);
        c.drawRoundRect(r, mRadius, mRadius, mPaint);

        float h = r.height();
        float sz1 = Math.min(36f, h * 0.32f);
        float sz2 = Math.min(18f, h * 0.18f);

        mPaint.setColor(txtColor);
        mPaint.setTextSize(sz1);
        Paint.FontMetrics fm = mPaint.getFontMetrics();
        float y1 = r.centerY() - sz1 * 0.15f;
        c.drawText(line1, r.centerX(), y1, mPaint);

        mPaint.setTextSize(sz2);
        mPaint.setColor(Color.parseColor("#CCCCCC"));
        c.drawText(line2, r.centerX(), y1 + sz1 * 0.5f + sz2 * 0.2f, mPaint);
    }

    private void drawControls(Canvas canvas) {
        if (mMoveRect != null) {
            mPaint.setColor(mCtrlBg);
            canvas.drawRect(mMoveRect, mPaint);
            mPaint.setColor(mCtrlTxt);
            mPaint.setTextSize(28f);
            Paint.FontMetrics fm = mPaint.getFontMetrics();
            float d = (fm.bottom - fm.top) / 2f - fm.bottom;
            canvas.drawText("↕", mMoveRect.exactCenterX(), mMoveRect.exactCenterY() + d, mPaint);
        }
        if (mCloseRect != null) {
            mPaint.setColor(0xFF7A2A2A);
            canvas.drawRect(mCloseRect, mPaint);
            mPaint.setColor(Color.WHITE);
            mPaint.setTextSize(30f);
            Paint.FontMetrics fm = mPaint.getFontMetrics();
            float d = (fm.bottom - fm.top) / 2f - fm.bottom;
            canvas.drawText("✕", mCloseRect.exactCenterX(), mCloseRect.exactCenterY() + d, mPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) return super.onTouchEvent(event);
        float x = event.getX(), y = event.getY();

        if (mCloseRect != null && mCloseRect.contains((int) x, (int) y)) {
            if (mListener != null) mListener.closeFragment();
            return true;
        }
        if (mMoveRect != null && mMoveRect.contains((int) x, (int) y)) {
            if (mListener != null) mListener.enterKey(new int[]{10000});
            return true;
        }

        if (mBtnRects != null) {
            int idx = 0;
            for (int i = 0; i < mShortcuts.size() && idx < mBtnRects.length; i++, idx++) {
                if (mBtnRects[idx].contains(x, y)) {
                    executeShortcut(i);
                    return true;
                }
            }
            if (mAddRect != null && mAddRect.contains(x, y)) {
                startAddShortcut();
                return true;
            }
        }
        return super.onTouchEvent(event);
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
        for (int m : mods) mListener.enterKeyOn(new int[]{m});
        if (actionKey >= 0) mListener.enterKey(new int[]{actionKey});
        for (int m : mods) mListener.enterKeyOff(new int[]{m});
        mListener.closeFragment();
    }

    private String comboDisplay(ShortcutItem sc) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sc.keyOrdinals.size(); i++) {
            if (i > 0) sb.append("+");
            sb.append(keyName(sc.keyOrdinals.get(i)));
        }
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
        return "?";
    }

    private void startAddShortcut() {
        Context ctx = getContext();
        if (ctx == null) return;
        showModifierDialog(ctx);
    }

    private void showModifierDialog(final Context ctx) {
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle("Hold keys (optional)");
        final String[] items = {"Shift", "Ctrl", "Alt"};
        final boolean[] checked = new boolean[3];
        b.setMultiChoiceItems(items, null, (d, w, is) -> checked[w] = is);
        b.setPositiveButton("Next", (d, w) -> showCategoryDialog(ctx, checked));
        b.setNegativeButton("Cancel", null);
        b.show();
    }

    private void showCategoryDialog(final Context ctx, final boolean[] mods) {
        final String[] cats = {"Letters", "Numbers", "F-Keys", "Special"};
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle("Pick a key");
        b.setItems(cats, (d, w) -> {
            switch (w) {
                case 0: showKeyDialog(ctx, mods, new String[]{"A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z"}, new int[]{25,52,29,48,51,53,45,46,23,49,71,72,31,30,24,73,20,43,44,22,74,50,21,27,28,26}); break;
                case 1: showKeyDialog(ctx, mods, new String[]{"1","2","3","4","5","6","7","8","9","0"}, new int[]{15,16,17,18,19,67,68,69,70,66}); break;
                case 2: showKeyDialog(ctx, mods, new String[]{"F1","F2","F3","F4","F5","F6","F7","F8","F9","F10","F11","F12"}, new int[]{75,8,9,10,76,77,78,79,80,81,82,11}); break;
                case 3: showKeyDialog(ctx, mods, new String[]{"Esc","Tab","Space","Enter","Delete","Home","End","Ins.","PgUp.","PgDn.","[","]","-","=",";","'","`",",",".","/","↑","↓","←","→"}, new int[]{7,41,34,13,42,12,90,89,35,36,85,86,83,84,87,88,14,32,33,47,37,38,39,40}); break;
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
        b.setTitle("Name your shortcut");
        EditText input = new EditText(ctx);
        input.setHint("e.g. Subdivide");
        b.setView(input);

        StringBuilder preview = new StringBuilder();
        if (modSelected[0]) preview.append("Shift+");
        if (modSelected[1]) preview.append("Ctrl+");
        if (modSelected[2]) preview.append("Alt+");
        preview.append(keyName(actionOrd));

        b.setMessage("Combo: " + preview);
        b.setPositiveButton("Confirm", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) name = preview.toString();
            saveShortcut(name, modSelected, actionOrd);
        });
        b.setNegativeButton("Cancel", null);
        b.show();
    }

    private void saveShortcut(String name, boolean[] mods, int actionOrd) {
        ArrayList<Integer> keys = new ArrayList<>();
        if (mods[0]) keys.add(0);
        if (mods[1]) keys.add(1);
        if (mods[2]) keys.add(2);
        keys.add(actionOrd);
        mShortcuts.add(new ShortcutItem(name, keys));
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
                mShortcuts.add(new ShortcutItem(n, k));
            }
        } catch (Exception e) {
            Log.e(TAG, "load", e);
        }
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
