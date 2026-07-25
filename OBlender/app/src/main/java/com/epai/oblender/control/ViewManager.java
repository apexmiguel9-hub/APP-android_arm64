// Adapted from FoldCraftLauncher (FCL-Team) ViewManager, GPL-3.0
package com.epai.oblender.control;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class ViewManager {

    private static final String PREFS_CONTROLS = "obl_controls";
    private static final String KEY_BUTTONS = "buttons";

    private List<ControlButtonData> buttons = new ArrayList<>();
    private GameMenuStub menu;

    public ViewManager(GameMenuStub menu) { this.menu = menu; }

    public List<ControlButtonData> getButtons() { return buttons; }

    public void addView(CustomControl control) {
        if (control instanceof ControlButtonData) {
            buttons.add((ControlButtonData) control);
            saveController();
            loadView(control, true);
        }
    }

    public void removeView(CustomControl control) {
        Iterator<ControlButtonData> it = buttons.iterator();
        while (it.hasNext()) {
            if (it.next().getId().equals(control.getViewId())) {
                it.remove();
                break;
            }
        }
        for (int i = 0; i < menu.getBaseLayout().getChildCount(); i++) {
            View view = menu.getBaseLayout().getChildAt(i);
            if (view instanceof CustomView && control.getViewId().equals(((CustomView) view).getViewId())) {
                ((CustomView) view).removeListener();
                menu.getBaseLayout().removeView(view);
                break;
            }
        }
        saveController();
    }

    private void loadView(CustomControl control, boolean parentVisibility) {
        if (control instanceof ControlButtonData) {
            ControlButtonData data = (ControlButtonData) control;
            ControlButton btn = new ControlButton(menu.getActivity(), menu, view -> {
                ((ControlButton) view).setParentVisibility(parentVisibility);
                ((ControlButton) view).setData(data);
            });
            menu.getBaseLayout().addView(btn);
        }
    }

    public void saveController() {
        save(menu.getActivity());
    }

    public void initializeController() {
        removeAllCustomViews();
        for (ControlButtonData data : buttons) {
            loadView(data, true);
        }
    }

    private void removeAllCustomViews() {
        ArrayList<View> views = new ArrayList<>();
        for (int i = 0; i < menu.getBaseLayout().getChildCount(); i++) {
            if (menu.getBaseLayout().getChildAt(i) instanceof CustomView) {
                views.add(menu.getBaseLayout().getChildAt(i));
            }
        }
        for (View v : views) {
            ((CustomView) v).removeListener();
            menu.getBaseLayout().removeView(v);
        }
    }

    public void switchViewGroupVisibility(ControlViewGroup viewGroup) {}

    /* ── Persistence ── */

    public void load(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS_CONTROLS, Context.MODE_PRIVATE);
            String json = sp.getString(KEY_BUTTONS, "[]");
            JSONArray arr = new JSONArray(json);
            buttons.clear();
            for (int i = 0; i < arr.length(); i++) {
                buttons.add(fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void save(Context ctx) {
        try {
            JSONArray arr = new JSONArray();
            for (ControlButtonData data : buttons) {
                arr.put(toJson(data));
            }
            ctx.getSharedPreferences(PREFS_CONTROLS, Context.MODE_PRIVATE)
                .edit().putString(KEY_BUTTONS, arr.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

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
}
