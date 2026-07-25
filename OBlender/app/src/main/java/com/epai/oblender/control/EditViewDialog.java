// Adapted from FoldCraftLauncher (FCL-Team) control editor, GPL-3.0
// https://github.com/FCL-Team/FoldCraftLauncher
package com.epai.oblender.control;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.List;

public class EditViewDialog {
    public interface Callback {
        void onSave(ControlButtonData data);
        void onDelete();
        void onClone(ControlButtonData data);
    }

    public static void show(Context ctx, ControlButtonData data, boolean isNew, Callback cb) {
        ControlButtonData working = data.clone();
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 20);

        ScrollView sv = new ScrollView(ctx);
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, 20);

        // ── Text ──
        content.addView(label(ctx, "Text"));
        EditText textInput = new EditText(ctx);
        textInput.setText(working.getText());
        textInput.setTextColor(Color.WHITE);
        textInput.setHint("Button label");
        textInput.setHintTextColor(0xFF666688);
        textInput.setBackground(drawable(0xFF2D2D50, 0xFF4A4A7A, 8));
        textInput.setPadding(12, 8, 12, 8);
        content.addView(textInput);

        // ── Position X ──
        content.addView(label(ctx, "X position (0-1000)"));
        SeekBar xSeek = new SeekBar(ctx);
        xSeek.setMax(1000);
        xSeek.setProgress(working.getBaseInfo().getXPosition());
        TextView xVal = new TextView(ctx);
        xVal.setText(String.valueOf(working.getBaseInfo().getXPosition()));
        xVal.setTextColor(0xFFE8E8F0);
        xSeek.setOnSeekBarChangeListener(seekListener(xVal, v -> working.getBaseInfo().setXPosition(v)));
        content.addView(xSeek);
        content.addView(xVal);

        // ── Position Y ──
        content.addView(label(ctx, "Y position (0-1000)"));
        SeekBar ySeek = new SeekBar(ctx);
        ySeek.setMax(1000);
        ySeek.setProgress(working.getBaseInfo().getYPosition());
        TextView yVal = new TextView(ctx);
        yVal.setText(String.valueOf(working.getBaseInfo().getYPosition()));
        yVal.setTextColor(0xFFE8E8F0);
        ySeek.setOnSeekBarChangeListener(seekListener(yVal, v -> working.getBaseInfo().setYPosition(v)));
        content.addView(ySeek);
        content.addView(yVal);

        // ── Size Type ──
        content.addView(label(ctx, "Size type"));
        LinearLayout sizeTypeRow = new LinearLayout(ctx);
        sizeTypeRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnPercent = new Button(ctx);
        btnPercent.setText("%");
        btnPercent.setTextColor(Color.WHITE);
        Button btnAbsolute = new Button(ctx);
        btnAbsolute.setText("dp");
        btnAbsolute.setTextColor(Color.WHITE);
        sizeTypeRow.addView(btnPercent, lp(0, -2, 1));
        sizeTypeRow.addView(btnAbsolute, lp(0, -2, 1));
        content.addView(sizeTypeRow);

        // ── Size (percentage) ──
        LinearLayout pctLayout = new LinearLayout(ctx);
        pctLayout.setOrientation(LinearLayout.VERTICAL);
        content.addView(pctLayout);

        content.addView(label(ctx, "Width (%)"));
        SeekBar wSeekPct = new SeekBar(ctx);
        wSeekPct.setMax(500);
        wSeekPct.setProgress(working.getBaseInfo().getPercentageWidth());
        TextView wPctVal = new TextView(ctx);
        wPctVal.setText((working.getBaseInfo().getPercentageWidth() / 10f) + "%");
        wPctVal.setTextColor(0xFFE8E8F0);
        wSeekPct.setOnSeekBarChangeListener(seekListener(wPctVal, v -> {
            working.getBaseInfo().setPercentageWidth(v);
            wPctVal.setText((v / 10f) + "%");
        }));
        pctLayout.addView(wSeekPct);
        pctLayout.addView(wPctVal);

        content.addView(label(ctx, "Height (%)"));
        SeekBar hSeekPct = new SeekBar(ctx);
        hSeekPct.setMax(500);
        hSeekPct.setProgress(working.getBaseInfo().getPercentageHeight());
        TextView hPctVal = new TextView(ctx);
        hPctVal.setText((working.getBaseInfo().getPercentageHeight() / 10f) + "%");
        hPctVal.setTextColor(0xFFE8E8F0);
        hSeekPct.setOnSeekBarChangeListener(seekListener(hPctVal, v -> {
            working.getBaseInfo().setPercentageHeight(v);
            hPctVal.setText((v / 10f) + "%");
        }));
        pctLayout.addView(hSeekPct);
        pctLayout.addView(hPctVal);

        // ── Size (absolute dp) ──
        LinearLayout absLayout = new LinearLayout(ctx);
        absLayout.setOrientation(LinearLayout.VERTICAL);
        content.addView(absLayout);

        content.addView(label(ctx, "Width (dp)"));
        SeekBar wSeekAbs = new SeekBar(ctx);
        wSeekAbs.setMax(200);
        wSeekAbs.setProgress(working.getBaseInfo().getAbsoluteWidth());
        TextView wAbsVal = new TextView(ctx);
        wAbsVal.setText(working.getBaseInfo().getAbsoluteWidth() + "dp");
        wAbsVal.setTextColor(0xFFE8E8F0);
        wSeekAbs.setOnSeekBarChangeListener(seekListener(wAbsVal, v -> {
            working.getBaseInfo().setAbsoluteWidth(v);
            wAbsVal.setText(v + "dp");
        }));
        absLayout.addView(wSeekAbs);
        absLayout.addView(wAbsVal);

        content.addView(label(ctx, "Height (dp)"));
        SeekBar hSeekAbs = new SeekBar(ctx);
        hSeekAbs.setMax(200);
        hSeekAbs.setProgress(working.getBaseInfo().getAbsoluteHeight());
        TextView hAbsVal = new TextView(ctx);
        hAbsVal.setText(working.getBaseInfo().getAbsoluteHeight() + "dp");
        hAbsVal.setTextColor(0xFFE8E8F0);
        hSeekAbs.setOnSeekBarChangeListener(seekListener(hAbsVal, v -> {
            working.getBaseInfo().setAbsoluteHeight(v);
            hAbsVal.setText(v + "dp");
        }));
        absLayout.addView(hSeekAbs);
        absLayout.addView(hAbsVal);

        // Toggle visibility of size controls
        Runnable updateSizeMode = () -> {
            boolean isPct = working.getBaseInfo().getSizeType() == BaseInfoData.SizeType.PERCENTAGE;
            pctLayout.setVisibility(isPct ? View.VISIBLE : View.GONE);
            absLayout.setVisibility(isPct ? View.GONE : View.VISIBLE);
        };
        btnPercent.setOnClickListener(v -> { working.getBaseInfo().setSizeType(BaseInfoData.SizeType.PERCENTAGE); updateSizeMode.run(); });
        btnAbsolute.setOnClickListener(v -> { working.getBaseInfo().setSizeType(BaseInfoData.SizeType.ABSOLUTE); updateSizeMode.run(); });
        updateSizeMode.run();

        // ── Events: Keycodes ──
        content.addView(label(ctx, "Keys (tap to choose)"));
        LinearLayout keyList = new LinearLayout(ctx);
        keyList.setOrientation(LinearLayout.VERTICAL);
        content.addView(keyList);

        Runnable refreshKeys = () -> {
            keyList.removeAllViews();
            ButtonEventData.Event ev = working.getEvent().getClickEvent();
            if (ev.getKeycodes().isEmpty()) {
                TextView empty = new TextView(ctx);
                empty.setText("No keys assigned");
                empty.setTextColor(0xFF9999BB);
                empty.setPadding(12, 8, 12, 8);
                keyList.addView(empty);
            } else {
                for (int kc : ev.getKeycodes()) {
                    TextView kv = new TextView(ctx);
                    kv.setText("Key " + kc);
                    kv.setTextColor(0xFFA5D6A7);
                    kv.setPadding(12, 4, 12, 4);
                    keyList.addView(kv);
                }
            }
        };
        refreshKeys.run();

        Button chooseKeys = new Button(ctx);
        chooseKeys.setText("Choose keys...");
        chooseKeys.setTextColor(Color.WHITE);
        chooseKeys.setBackground(drawable(0xFF1B5E20, 0xFF4CAF50, 8));
        chooseKeys.setOnClickListener(v -> {
            SelectKeycodeDialog.show(ctx, working.getEvent().getClickEvent().getKeycodes(), false, result -> {
                working.getEvent().getClickEvent().setKeycodes(result);
                refreshKeys.run();
            });
        });
        content.addView(chooseKeys);

        // ── Auto-keep toggle ──
        content.addView(label(ctx, "Options"));
        LinearLayout optRow = new LinearLayout(ctx);
        optRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnToggle = new Button(ctx);
        btnToggle.setText("Toggle mode");
        btnToggle.setTextColor(Color.WHITE);
        Button btnAuto = new Button(ctx);
        btnAuto.setText("Auto-repeat");
        btnAuto.setTextColor(Color.WHITE);
        optRow.addView(btnToggle, lp(0, -2, 1));
        optRow.addView(btnAuto, lp(0, -2, 1));
        content.addView(optRow);

        Runnable refreshOpts = () -> {
            boolean ak = working.getEvent().getClickEvent().isAutoKeep();
            boolean ac = working.getEvent().getClickEvent().isAutoClick();
            btnToggle.setBackground(drawable(ak ? 0xFF1B5E20 : 0xFF2D2D50, ak ? 0xFF4CAF50 : 0xFF4A4A7A, 8));
            btnAuto.setBackground(drawable(ac ? 0xFF1B5E20 : 0xFF2D2D50, ac ? 0xFF4CAF50 : 0xFF4A4A7A, 8));
        };
        btnToggle.setOnClickListener(v -> { working.getEvent().getClickEvent().setAutoKeep(!working.getEvent().getClickEvent().isAutoKeep()); refreshOpts.run(); });
        btnAuto.setOnClickListener(v -> { working.getEvent().getClickEvent().setAutoClick(!working.getEvent().getClickEvent().isAutoClick()); refreshOpts.run(); });
        refreshOpts.run();

        sv.addView(content);
        root.addView(sv);

        // ── Action buttons ──
        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button saveBtn = new Button(ctx);
        saveBtn.setText("Save");
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setBackground(drawable(0xFF1B5E20, 0xFF4CAF50, 8));
        Button deleteBtn = new Button(ctx);
        deleteBtn.setText("Delete");
        deleteBtn.setTextColor(Color.WHITE);
        deleteBtn.setBackground(drawable(0xFF7A2A2A, 0xFFB71C1C, 8));
        Button cloneBtn = new Button(ctx);
        cloneBtn.setText("Clone");
        cloneBtn.setTextColor(Color.WHITE);
        cloneBtn.setBackground(drawable(0xFF2D2D50, 0xFF4A4A7A, 8));
        actions.addView(saveBtn, lp(0, -2, 1));
        if (!isNew) actions.addView(cloneBtn, lp(0, -2, 1));
        if (!isNew) actions.addView(deleteBtn, lp(0, -2, 1));
        root.addView(actions);

        AlertDialog dlg = new AlertDialog.Builder(ctx)
            .setTitle(isNew ? "Add Button" : "Edit Button")
            .setView(root)
            .setCancelable(true)
            .create();
        dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dlg.getWindow().getDecorView().setBackgroundColor(0xFF1A1A2E);

        saveBtn.setOnClickListener(v -> {
            working.setText(textInput.getText().toString());
            cb.onSave(working);
            dlg.dismiss();
        });
        deleteBtn.setOnClickListener(v -> {
            cb.onDelete();
            dlg.dismiss();
        });
        cloneBtn.setOnClickListener(v -> {
            cb.onClone(working);
            dlg.dismiss();
        });

        dlg.show();
    }

    private static TextView label(Context ctx, String s) {
        TextView tv = new TextView(ctx);
        tv.setText(s);
        tv.setTextColor(0xFF9999BB);
        tv.setTextSize(12);
        tv.setPadding(0, 16, 0, 4);
        return tv;
    }

    private static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            w == 0 ? LinearLayout.LayoutParams.MATCH_PARENT : w,
            h == 0 ? LinearLayout.LayoutParams.WRAP_CONTENT : h);
        p.weight = weight;
        p.setMargins(4, 4, 4, 4);
        return p;
    }

    private static GradientDrawable drawable(int fill, int stroke, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(radius);
        gd.setStroke(2, stroke);
        gd.setColor(fill);
        return gd;
    }

    private static SeekBar.OnSeekBarChangeListener seekListener(TextView tv, java.util.function.Consumer<Integer> setter) {
        return new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int v, boolean u) { setter.accept(v); tv.setText(String.valueOf(v)); }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        };
    }
}
