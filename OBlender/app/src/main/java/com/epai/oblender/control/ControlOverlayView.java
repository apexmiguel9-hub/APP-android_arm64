// FCL-style DrawerLayout controller editor — GPL-3.0
// Based on com.tungsten.fcl.control.GameMenu (FoldCraftLauncher)
package com.epai.oblender.control;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

    /* FCL-style drawer items */
    private Switch editModeSwitch, showBoundarySwitch;
    private Button addButtonBtn;

    public ControlOverlayView(Context context) {
        super(context);
        init();
    }

    private void init() {
        /* ── Content area ── */
        contentFrame = new FrameLayout(getContext());
        contentFrame.setBackgroundColor(0xFF1A1A2E);
        DrawerLayout.LayoutParams clp = new DrawerLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(contentFrame, clp);

        /* ── Left drawer (FCL-style edit panel) ── */
        leftDrawer = new LinearLayout(getContext());
        leftDrawer.setOrientation(LinearLayout.VERTICAL);
        leftDrawer.setBackgroundColor(0xFF2A2A3E);
        leftDrawer.setPadding(24, 48, 24, 24);

        int drawerW = (int) (300 * getResources().getDisplayMetrics().density);
        DrawerLayout.LayoutParams dlp = new DrawerLayout.LayoutParams(
                drawerW, LayoutParams.MATCH_PARENT);
        dlp.gravity = Gravity.LEFT;
        addView(leftDrawer, dlp);

        /* ── Populate left drawer ── */
        addDrawerTitle("CONTROLLER EDITOR");

        addDrawerLabel("Edit Mode");
        editModeSwitch = addDrawerSwitch(false, (v, checked) -> {
            if (menu != null) {
                menu.setEditMode(checked);
                menu.setShowViewBoundaries(checked);
            }
        });

        addDrawerLabel("Show Boundaries");
        showBoundarySwitch = addDrawerSwitch(false, (v, checked) -> {
            if (menu != null) menu.setShowViewBoundaries(checked);
        });

        addDrawerLabel("Add Control");
        addButtonBtn = addDrawerButton("  + Add Button", v -> {
            if (menu != null && !menu.isEditMode()) {
                menu.setEditMode(true);
                editModeSwitch.setChecked(true);
            }
            showAddDialog();
        });

        /* ── GameMenuStub + ViewManager ── */
        menu = new GameMenuStub((android.app.Activity) getContext(), contentFrame, keyListener);
        viewManager = menu.getViewManager();
        viewManager.load(getContext());
        viewManager.initializeController();
    }

    public void setListener(OblSettingFragment.OBLSettingFragmentListener l) {
        keyListener = l;
        if (menu != null) {
            menu.getInput().listener = l;
        }
    }

    /* ── Drawer helpers ── */

    private void addDrawerTitle(String s) {
        TextView tv = new TextView(getContext());
        tv.setText(s);
        tv.setTextColor(0xFF9999BB);
        tv.setTextSize(13);
        tv.setPadding(0, 0, 0, 20);
        leftDrawer.addView(tv);
    }

    private void addDrawerLabel(String s) {
        TextView tv = new TextView(getContext());
        tv.setText(s);
        tv.setTextColor(0xFFCCCCDD);
        tv.setTextSize(14);
        tv.setPadding(0, 12, 0, 4);
        leftDrawer.addView(tv);
    }

    private Switch addDrawerSwitch(boolean def, CompoundButton.OnCheckedChangeListener l) {
        Switch sw = new Switch(getContext());
        sw.setChecked(def);
        sw.setTextColor(Color.WHITE);
        sw.setPadding(0, 4, 0, 12);
        sw.setOnCheckedChangeListener(l);
        leftDrawer.addView(sw);
        return sw;
    }

    private Button addDrawerButton(String text, View.OnClickListener l) {
        Button btn = new Button(getContext());
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14);
        btn.setGravity(Gravity.CENTER_VERTICAL);
        btn.setPadding(16, 12, 16, 12);
        btn.setBackground(drawable(0xFF3A3A5E, 0xFF5A5A7E, 8));
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
        EditViewDialog.show(getContext(), data, true, new EditViewDialog.Callback() {
            public void onSave(ControlButtonData d) {
                viewManager.addView(d);
                viewManager.saveController();
            }
            public void onDelete() {}
            public void onClone(CustomControl view) {
                viewManager.addView(view);
            }
        });
    }

    private static GradientDrawable drawable(int fill, int stroke, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(radius);
        gd.setStroke(2, stroke);
        gd.setColor(fill);
        return gd;
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
}
