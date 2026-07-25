// Adapted from FoldCraftLauncher (FCL-Team) ViewManager, GPL-3.0
// https://github.com/FCL-Team/FoldCraftLauncher
package com.epai.oblender.control;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class ViewManager {
    private static final String TAG = "ViewManager";
    private static final String PREFS_CONTROLS = "obl_controls";
    private static final String KEY_BUTTONS = "buttons";

    private List<ControlButtonData> buttons = new ArrayList<>();
    private List<ButtonRect> buttonRects = new ArrayList<>();
    private boolean editMode;
    private boolean showBoundaries;
    private int dragIndex = -1;
    private float dragOffX, dragOffY;
    private int screenW, screenH;

    private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint boundaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<Runnable> changeListeners = new ArrayList<>();

    public interface KeySender {
        void enterKeyOn(int[] keys);
        void enterKeyOff(int[] keys);
        void enterKey(int[] keys);
    }

    private KeySender keySender;

    public ViewManager() {
        boundaryPaint.setStyle(Paint.Style.STROKE);
        boundaryPaint.setStrokeWidth(3);
        boundaryPaint.setColor(0xFFFF4444);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    public void setKeySender(KeySender ks) { keySender = ks; }
    public void addChangeListener(Runnable r) { changeListeners.add(r); }
    private void notifyChange() { for (Runnable r : changeListeners) r.run(); }

    public boolean isEditMode() { return editMode; }
    public void setEditMode(boolean v) { editMode = v; notifyChange(); }

    public boolean isShowBoundaries() { return showBoundaries; }
    public void setShowBoundaries(boolean v) { showBoundaries = v; notifyChange(); }

    public List<ControlButtonData> getButtons() { return buttons; }

    public void setScreenSize(int w, int h) { screenW = w; screenH = h; }

    // ── CRUD ──

    public void addButton(ControlButtonData data) {
        buttons.add(data);
        rebuildRects();
        notifyChange();
    }

    public void removeButton(String id) {
        Iterator<ControlButtonData> it = buttons.iterator();
        while (it.hasNext()) {
            if (it.next().getId().equals(id)) { it.remove(); break; }
        }
        rebuildRects();
        notifyChange();
    }

    public void updateButton(String id, ControlButtonData data) {
        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i).getId().equals(id)) {
                buttons.set(i, data);
                break;
            }
        }
        rebuildRects();
        notifyChange();
    }

    // ── Hit testing ──

    public int hitTest(float x, float y) {
        for (int i = buttonRects.size() - 1; i >= 0; i--) {
            if (buttonRects.get(i).rect.contains(x, y)) return i;
        }
        return -1;
    }

    public boolean startDrag(int index, float x, float y) {
        if (index < 0 || index >= buttonRects.size()) return false;
        dragIndex = index;
        RectF r = buttonRects.get(index).rect;
        dragOffX = x - r.left;
        dragOffY = y - r.top;
        return true;
    }

    public void dragTo(float x, float y) {
        if (dragIndex < 0) return;
        ControlButtonData data = buttons.get(dragIndex);
        float bw = buttonRects.get(dragIndex).w;
        float bh = buttonRects.get(dragIndex).h;
        float nx = x - dragOffX;
        float ny = y - dragOffY;
        nx = Math.max(0, Math.min(screenW - bw, nx));
        ny = Math.max(0, Math.min(screenH - bh, ny));
        // Convert pixel position back to 0-1000 fraction
        int px = (int) ((screenW - bw) > 0 ? nx / (screenW - bw) * 1000 : 0);
        int py = (int) ((screenH - bh) > 0 ? ny / (screenH - bh) * 1000 : 0);
        data.getBaseInfo().setXPosition(px);
        data.getBaseInfo().setYPosition(py);
        rebuildRects();
        notifyChange();
    }

    public void endDrag() { dragIndex = -1; }

    // ── Fire key events (play mode) ──

    public boolean fireButton(int index) {
        if (index < 0 || index >= buttons.size() || keySender == null) return false;
        ControlButtonData data = buttons.get(index);
        ButtonEventData.Event ev = data.getEvent().getClickEvent();
        if (ev == null || ev.getKeycodes().isEmpty()) return false;
        int[] keys = new int[ev.getKeycodes().size()];
        for (int i = 0; i < keys.length; i++) keys[i] = ev.getKeycodes().get(i);
        if (ev.isAutoKeep()) {
            keySender.enterKeyOn(keys);
        } else if (ev.isAutoClick()) {
            keySender.enterKey(keys);
        } else {
            keySender.enterKeyOn(keys);
            keySender.enterKeyOff(keys);
        }
        return true;
    }

    // ── Rendering ──

    public void draw(Canvas c) {
        for (int i = 0; i < buttonRects.size(); i++) {
            ButtonRect br = buttonRects.get(i);
            ControlButtonData data = buttons.get(i);
            ControlButtonStyle style = data.getStyle();
            RectF r = br.rect;
            float rX = dp(style.getCornerRadius());

            // Fill
            fillPaint.setColor(style.getFillColor());
            c.drawRoundRect(r, rX, rX, fillPaint);

            // Stroke
            strokePaint.setColor(style.getStrokeColor());
            strokePaint.setStrokeWidth(dp(style.getStrokeWidth()));
            strokePaint.setStyle(Paint.Style.STROKE);
            c.drawRoundRect(r, rX, rX, strokePaint);

            // Text
            textPaint.setColor(style.getTextColor());
            textPaint.setTextSize(dp(style.getTextSize()));
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float d = (fm.bottom - fm.top) / 2f - fm.bottom;
            c.drawText(data.getText(), r.centerX(), r.centerY() + d, textPaint);

            // Boundary (edit mode)
            if (editMode && showBoundaries) {
                c.drawRoundRect(r, rX, rX, boundaryPaint);
            }
        }
    }

    // ── Layout ──

    private void rebuildRects() {
        buttonRects.clear();
        for (ControlButtonData data : buttons) {
            BaseInfoData bi = data.getBaseInfo();
            int bw = bi.computeWidth(screenW, screenH);
            int bh = bi.computeHeight(screenW, screenH);
            int bx = bi.computeX(screenW, bw);
            int by = bi.computeY(screenH, bh);
            buttonRects.add(new ButtonRect(new RectF(bx, by, bx + bw, by + bh), bw, bh));
        }
    }

    // ── Persistence ──

    public void load(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS_CONTROLS, Context.MODE_PRIVATE);
            String json = sp.getString(KEY_BUTTONS, "[]");
            JSONArray arr = new JSONArray(json);
            buttons.clear();
            for (int i = 0; i < arr.length(); i++) {
                buttons.add(fromJson(arr.getJSONObject(i)));
            }
            rebuildRects();
        } catch (Exception e) { Log.e(TAG, "load", e); }
    }

    public void save(Context ctx) {
        try {
            JSONArray arr = new JSONArray();
            for (ControlButtonData data : buttons) {
                arr.put(toJson(data));
            }
            ctx.getSharedPreferences(PREFS_CONTROLS, Context.MODE_PRIVATE)
                .edit().putString(KEY_BUTTONS, arr.toString()).apply();
        } catch (Exception e) { Log.e(TAG, "save", e); }
    }

    // ── JSON serialization ──

    private static JSONObject toJson(ControlButtonData d) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", d.getId());
        o.put("text", d.getText());
        o.put("styleName", d.getStyle().getName());
        JSONObject bi = new JSONObject();
        bi.put("x", d.getBaseInfo().getXPosition());
        bi.put("y", d.getBaseInfo().getYPosition());
        bi.put("sizeType", d.getBaseInfo().getSizeType().name());
        bi.put("absW", d.getBaseInfo().getAbsoluteWidth());
        bi.put("absH", d.getBaseInfo().getAbsoluteHeight());
        bi.put("pctW", d.getBaseInfo().getPercentageWidth());
        bi.put("pctH", d.getBaseInfo().getPercentageHeight());
        o.put("baseInfo", bi);
        JSONObject ev = new JSONObject();
        ev.put("autoKeep", d.getEvent().getClickEvent().isAutoKeep());
        ev.put("autoClick", d.getEvent().getClickEvent().isAutoClick());
        JSONArray ka = new JSONArray();
        for (int k : d.getEvent().getClickEvent().getKeycodes()) ka.put(k);
        ev.put("keycodes", ka);
        o.put("event", ev);
        return o;
    }

    private static ControlButtonData fromJson(JSONObject o) throws Exception {
        ControlButtonData d = new ControlButtonData(o.optString("id", UUID.randomUUID().toString()));
        d.setText(o.optString("text", ""));
        JSONObject bi = o.getJSONObject("baseInfo");
        d.getBaseInfo().setXPosition(bi.optInt("x", 0));
        d.getBaseInfo().setYPosition(bi.optInt("y", 0));
        try { d.getBaseInfo().setSizeType(BaseInfoData.SizeType.valueOf(bi.optString("sizeType", "PERCENTAGE"))); } catch (Exception e) {}
        d.getBaseInfo().setAbsoluteWidth(bi.optInt("absW", 60));
        d.getBaseInfo().setAbsoluteHeight(bi.optInt("absH", 60));
        d.getBaseInfo().setPercentageWidth(bi.optInt("pctW", 80));
        d.getBaseInfo().setPercentageHeight(bi.optInt("pctH", 80));
        if (o.has("event")) {
            JSONObject ev = o.getJSONObject("event");
            d.getEvent().getClickEvent().setAutoKeep(ev.optBoolean("autoKeep", false));
            d.getEvent().getClickEvent().setAutoClick(ev.optBoolean("autoClick", false));
            JSONArray ka = ev.getJSONArray("keycodes");
            for (int i = 0; i < ka.length(); i++) d.getEvent().getClickEvent().addKeycode(ka.getInt(i));
        }
        return d;
    }

    // ── Helpers ──

    private float dp(int val) { return val; } // simplified; real dp conversion needs context

    private static class ButtonRect {
        RectF rect;
        float w, h;
        ButtonRect(RectF r, float w, float h) { this.rect = r; this.w = w; this.h = h; }
    }
}
