// FCL-style DrawerLayout controller editor — GPL-3.0
// Based on com.tungsten.fcl.control.GameMenu (FoldCraftLauncher)
package com.epai.oblender.control;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.drawerlayout.widget.DrawerLayout;

import com.epai.oblender.OblSettingFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ControlOverlayView extends DrawerLayout {

    private FrameLayout contentFrame;
    private LinearLayout leftDrawer;
    private List<ControlButton> buttons = new ArrayList<>();

    private OblSettingFragment.OBLSettingFragmentListener keyListener;
    private GameMenuStub menu;
    private ViewManager viewManager;

    private int screenW, screenH;

    public ControlOverlayView(Context context) {
        super(context);
        init();
    }

    private void init() {
        /* ── Content area (game overlay) ── */
        contentFrame = new FrameLayout(getContext());
        contentFrame.setBackgroundColor(0xFF1A1A2E);
        addView(contentFrame, new DrawerLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        /* ── Left drawer (FCL-style controller editor) ── */
        int dp = (int) getResources().getDisplayMetrics().density;

        ScrollView scroll = new ScrollView(getContext());
        scroll.setBackgroundColor(0xFF1E1E2E);

        leftDrawer = new LinearLayout(getContext());
        leftDrawer.setOrientation(LinearLayout.VERTICAL);
        leftDrawer.setPadding(24, 48, 24, 24);

        scroll.addView(leftDrawer, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scroll, drawerParams(320 * dp, Gravity.LEFT));

        /* ── EDIT MODE ── */
        addDrawerTitle("EDIT");

        addDrawerLabel("Edit Mode");
        editModeSwitch = addDrawerSwitch(false, (v, checked) -> {
            if (menu != null) menu.setEditMode(checked);
            menu.setShowViewBoundaries(checked);
            showBoundarySwitch.setChecked(checked);
        });

        addDrawerLabel("Show Boundaries");
        showBoundarySwitch = addDrawerSwitch(false, (v, checked) -> {
            if (menu != null) menu.setShowViewBoundaries(checked);
        });

        addDrawerLabel("Auto Fit");
        Switch autoFitSw = addDrawerSwitch(true, null);

        autoFitSw.setOnCheckedChangeListener((v, checked) -> {
            if (menu != null) menu.getMenuSetting().autoFit = checked;
        });

        /* ── SPACER ── */
        addSpacer();

        /* ── CONTROLLER ── */
        addDrawerTitle("CONTROLLER");

        addDrawerLabel("Current Controller");
        Spinner controllerSpinner = addSpinner(new String[]{"Default"}, 0, null);

        addDrawerLabel("View Group");
        Spinner viewGroupSpinner = addSpinner(new String[]{"(none)"}, 0, null);

        /* ── ACTION BUTTONS (FCL-style row) ── */
        addSpacer();

        addButton("Manage View Groups", v -> {
            Toast.makeText(getContext(), "View Group Manager", Toast.LENGTH_SHORT).show();
        });

        addButton("➕ Add Button", v -> {
            if (menu != null && !menu.isEditMode()) {
                menu.setEditMode(true);
                editModeSwitch.setChecked(true);
            }
            showAddDialog();
        });

        addButton("🔄 Add Direction", v -> {
            Toast.makeText(getContext(), "Add Direction", Toast.LENGTH_SHORT).show();
        });

        addButton("🎨 Manage Button Style", v -> {
            Toast.makeText(getContext(), "Button Styles", Toast.LENGTH_SHORT).show();
        });

        addButton("🎨 Manage Direction Style", v -> {
            Toast.makeText(getContext(), "Direction Styles", Toast.LENGTH_SHORT).show();
        });

        /* ── GameMenuStub + ViewManager ── */
        menu = new GameMenuStub((Activity) getContext(), contentFrame, keyListener);
        viewManager = menu.getViewManager();
        viewManager.load(getContext());
        viewManager.initializeController();

        /* Ensure drawer opens after layout */
        post(() -> {
            try { openDrawer(Gravity.LEFT); } catch (Exception e) { e.printStackTrace(); }
        });
    }

    public void setListener(OblSettingFragment.OBLSettingFragmentListener l) {
        keyListener = l;
        if (menu != null) {
            menu.getInput().listener = l;
        }
    }

    /* ── Drawer helpers ── */

    private DrawerLayout.LayoutParams drawerParams(int w, int gravity) {
        DrawerLayout.LayoutParams p = new DrawerLayout.LayoutParams(w, LayoutParams.MATCH_PARENT);
        p.gravity = gravity;
        return p;
    }

    private void addDrawerTitle(String s) {
        TextView tv = new TextView(getContext());
        tv.setText(s);
        tv.setTextColor(Color.parseColor("#FFBB86FC"));
        tv.setTextSize(13);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(0, 16, 0, 8);
        leftDrawer.addView(tv);
    }

    private void addDrawerLabel(String s) {
        TextView tv = new TextView(getContext());
        tv.setText(s);
        tv.setTextColor(Color.parseColor("#FFE0E0E0"));
        tv.setTextSize(13);
        tv.setPadding(0, 12, 0, 2);
        leftDrawer.addView(tv);
    }

    private Switch addDrawerSwitch(boolean def, CompoundButton.OnCheckedChangeListener l) {
        Switch sw = new Switch(getContext());
        sw.setChecked(def);
        sw.setTextColor(Color.parseColor("#FFE0E0E0"));
        sw.setPadding(0, 2, 0, 8);
        if (l != null) sw.setOnCheckedChangeListener(l);
        leftDrawer.addView(sw);
        return sw;
    }

    private Spinner addSpinner(String[] items, int sel, AdapterView.OnItemSelectedListener l) {
        Spinner sp = new Spinner(getContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_dropdown_item, items);
        sp.setAdapter(adapter);
        sp.setSelection(sel);
        if (l != null) sp.setOnItemSelectedListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 8);
        leftDrawer.addView(sp, lp);
        return sp;
    }

    private void addSpacer() {
        View v = new View(getContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(1, 2));
        leftDrawer.addView(v);
    }

    private void addSpacerBig() {
        View v = new View(getContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(1, 16));
        leftDrawer.addView(v);
    }

    private Button addButton(String text, OnClickListener l) {
        Button btn = new Button(getContext());
        btn.setText(text);
        btn.setTextColor(Color.parseColor("#FFFFFFFF"));
        btn.setTextSize(13);
        btn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        btn.setPadding(16, 12, 16, 12);
        btn.setAllCaps(false);
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(6);
        gd.setColor(0xFF3A3A5E);
        gd.setStroke(1, 0xFF5A5A7E);
        btn.setBackground(gd);
        btn.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 4, 0, 4);
        leftDrawer.addView(btn, lp);
        return btn;
    }

    private void showAddDialog() {
        ControlButtonData data = new ControlButtonData(UUID.randomUUID().toString());
        data.getBaseInfo().setXPosition(100);
        data.getBaseInfo().setYPosition(100);
        data.getBaseInfo().setSizeType(BaseInfoData.SizeType.ABSOLUTE);
        data.getBaseInfo().setAbsoluteWidth(60);
        data.getBaseInfo().setAbsoluteHeight(60);
        data.setText("Btn");
        data.getEvent().getClickEvent().addKeycode(32);  // SPACE
        EditViewDialog.show(getContext(), data, true, new EditViewDialog.Callback() {
            public void onSave(ControlButtonData d) {
                viewManager.addView(d);
                viewManager.saveController();
                Toast.makeText(getContext(), "Button added", Toast.LENGTH_SHORT).show();
            }
            public void onDelete() {}
            public void onClone(CustomControl view) {
                viewManager.addView(view);
            }
        });
    }

    /* ── Lifecycle ── */

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        screenW = right - left;
        screenH = bottom - top;
        if (menu != null) {
            menu.screenWidth = screenW;
            menu.screenHeight = screenH;
        }
    }

    public void setWindowStartPosition(int x, int y) {}

    public void openEditor() {
        post(() -> {
            try { openDrawer(Gravity.LEFT); } catch (Exception ignored) {}
        });
    }
}
