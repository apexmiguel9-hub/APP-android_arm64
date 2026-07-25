// Adapted from FoldCraftLauncher (FCL-Team) keycode selector concept, GPL-3.0
// https://github.com/FCL-Team/FoldCraftLauncher
package com.epai.oblender.control;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.ArrayList;
import java.util.List;

public class SelectKeycodeDialog {
    public interface Callback {
        void onResult(List<Integer> keycodes);
    }

    private static class KeyDef {
        String label;
        int ordinal;
        float w;
        KeyDef(String label, int ordinal, float w) { this.label = label; this.ordinal = ordinal; this.w = w; }
    }

    private static final KeyDef[][] ROWS = {
        { new KeyDef("Esc", 41, 1.5f), new KeyDef("Tab", 15, 1.5f) },
        { new KeyDef("Q",16,1), new KeyDef("W",17,1), new KeyDef("E",18,1), new KeyDef("R",19,1),
          new KeyDef("T",20,1), new KeyDef("Y",21,1), new KeyDef("U",22,1), new KeyDef("I",23,1),
          new KeyDef("O",24,1), new KeyDef("P",25,1) },
        { new KeyDef("A",4,1), new KeyDef("S",5,1), new KeyDef("D",6,1), new KeyDef("F",7,1),
          new KeyDef("G",8,1), new KeyDef("H",9,1), new KeyDef("J",10,1), new KeyDef("K",11,1),
          new KeyDef("L",12,1) },
        { new KeyDef("Z",29,1), new KeyDef("X",27,1), new KeyDef("C",28,1), new KeyDef("V",26,1),
          new KeyDef("B",30,1), new KeyDef("N",31,1), new KeyDef("M",32,1) },
        { new KeyDef("Shift", 0, 2), new KeyDef("Ctrl", 1, 2), new KeyDef("Alt", 2, 2) },
        { new KeyDef("Space",34,4), new KeyDef("Enter",13,1.5f) },
        { new KeyDef("0",44,1), new KeyDef("1",36,1), new KeyDef("2",37,1), new KeyDef("3",38,1),
          new KeyDef("4",39,1), new KeyDef("5",40,1), new KeyDef("6",41,1), new KeyDef("7",42,1),
          new KeyDef("8",43,1), new KeyDef("9",45,1) },
        { new KeyDef("F1",58,1), new KeyDef("F2",59,1), new KeyDef("F3",60,1), new KeyDef("F4",61,1),
          new KeyDef("F5",62,1), new KeyDef("F6",63,1), new KeyDef("F7",64,1), new KeyDef("F8",65,1) },
        { new KeyDef("Del", 46, 1.5f), new KeyDef("Bksp", 14, 1.5f) },
    };

    public static void show(Context ctx, List<Integer> initial, boolean single, Callback cb) {
        List<Integer> selected = new ArrayList<>(initial);
        KeyPadView pad = new KeyPadView(ctx, selected, single);
        int padH = ROWS.length * 56 + 20;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, padH);
        pad.setLayoutParams(lp);

        ScrollView sv = new ScrollView(ctx);
        sv.addView(pad);

        AlertDialog dlg = new AlertDialog.Builder(ctx)
            .setTitle(single ? "Select a key" : "Select keys")
            .setView(sv)
            .setPositiveButton("OK", (d, w) -> cb.onResult(selected))
            .setNegativeButton("Cancel", null)
            .create();
        dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dlg.getWindow().getDecorView().setBackgroundColor(0xFF1A1A2E);
        dlg.show();
    }

    private static class KeyPadView extends View {
        private List<Integer> selected;
        private boolean single;
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float cellW, cellH;
        private RectF[][] hitRects;
        private int rows, maxCols;

        KeyPadView(Context ctx, List<Integer> selected, boolean single) {
            super(ctx);
            this.selected = selected;
            this.single = single;
            this.rows = ROWS.length;
            int max = 0;
            for (KeyDef[] r : ROWS) {
                int total = 0;
                for (KeyDef k : r) total += k.w;
                if (total > max) max = total;
            }
            this.maxCols = max;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            cellW = w / (float) maxCols;
            cellH = 50;
            hitRects = new RectF[rows][];
            for (int ri = 0; ri < rows; ri++) {
                KeyDef[] row = ROWS[ri];
                hitRects[ri] = new RectF[row.length];
                float x = 4;
                float y = ri * cellH + 4;
                for (int ki = 0; ki < row.length; ki++) {
                    float kw = cellW * row[ki].w - 4;
                    float kh = cellH - 4;
                    hitRects[ri][ki] = new RectF(x, y, x + kw, y + kh);
                    x += kw + 4;
                }
            }
        }

        @Override
        protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws);
            int h = (int) (rows * cellH + 20);
            setMeasuredDimension(w, h);
        }

        @Override
        protected void onDraw(Canvas c) {
            paint.setTextAlign(Paint.Align.CENTER);
            for (int ri = 0; ri < rows; ri++) {
                KeyDef[] row = ROWS[ri];
                for (int ki = 0; ki < row.length; ki++) {
                    RectF r = hitRects[ri][ki];
                    boolean sel = selected.contains(row[ki].ordinal);
                    paint.setColor(sel ? 0xFF1B5E20 : 0xFF2D2D50);
                    c.drawRoundRect(r, 6, 6, paint);
                    paint.setColor(sel ? 0xFFA5D6A7 : 0xFFE8E8F0);
                    paint.setTextSize(Math.min(20, r.height() * 0.5f));
                    paint.setFakeBoldText(true);
                    Paint.FontMetrics fm = paint.getFontMetrics();
                    float d = (fm.bottom - fm.top) / 2f - fm.bottom;
                    c.drawText(row[ki].label, r.centerX(), r.centerY() + d, paint);
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_DOWN) return true;
            float x = e.getX(), y = e.getY();
            for (int ri = 0; ri < rows; ri++) {
                for (int ki = 0; ki < hitRects[ri].length; ki++) {
                    if (hitRects[ri][ki] != null && hitRects[ri][ki].contains(x, y)) {
                        int ord = ROWS[ri][ki].ordinal;
                        if (single) {
                            selected.clear();
                            selected.add(ord);
                        } else {
                            if (selected.contains(ord)) selected.remove((Integer) ord);
                            else selected.add(ord);
                        }
                        invalidate();
                        return true;
                    }
                }
            }
            return true;
        }
    }
}
