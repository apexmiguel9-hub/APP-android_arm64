// Based on FoldCraftLauncher (FCL-Team) control editor concept, GPL-3.0
// https://github.com/FCL-Team/FoldCraftLauncher
package com.epai.oblender.control;

import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;

public class ControlButtonStyle implements Cloneable {
    public static final ControlButtonStyle DEFAULT = new ControlButtonStyle("Default");

    private String name = "";
    private int textColor = Color.WHITE;
    private int textSize = 14; // sp
    private int strokeWidth = 2; // dp
    private int strokeColor = 0xFF555555;
    private int cornerRadius = 8; // dp
    private int fillColor = 0x66000000;

    private int textColorPressed = Color.WHITE;
    private int textSizePressed = 14;
    private int strokeWidthPressed = 2;
    private int strokeColorPressed = 0xFF888888;
    private int cornerRadiusPressed = 8;
    private int fillColorPressed = 0x88444444;

    private List<Runnable> changeListeners = new ArrayList<>();

    public ControlButtonStyle() {}
    public ControlButtonStyle(String name) { this.name = name; }

    public void addChangeListener(Runnable r) { changeListeners.add(r); }
    private void notifyChange() { for (Runnable r : changeListeners) r.run(); }

    public String getName() { return name; }
    public void setName(String v) { name = v; notifyChange(); }

    public int getTextColor() { return textColor; }
    public void setTextColor(int v) { textColor = v; notifyChange(); }

    public int getTextSize() { return textSize; }
    public void setTextSize(int v) { textSize = Math.max(8, Math.min(48, v)); notifyChange(); }

    public int getStrokeWidth() { return strokeWidth; }
    public void setStrokeWidth(int v) { strokeWidth = Math.max(0, v); notifyChange(); }

    public int getStrokeColor() { return strokeColor; }
    public void setStrokeColor(int v) { strokeColor = v; notifyChange(); }

    public int getCornerRadius() { return cornerRadius; }
    public void setCornerRadius(int v) { cornerRadius = Math.max(0, v); notifyChange(); }

    public int getFillColor() { return fillColor; }
    public void setFillColor(int v) { fillColor = v; notifyChange(); }

    public int getTextColorPressed() { return textColorPressed; }
    public void setTextColorPressed(int v) { textColorPressed = v; notifyChange(); }

    public int getTextSizePressed() { return textSizePressed; }
    public void setTextSizePressed(int v) { textSizePressed = Math.max(8, Math.min(48, v)); notifyChange(); }

    public int getStrokeWidthPressed() { return strokeWidthPressed; }
    public void setStrokeWidthPressed(int v) { strokeWidthPressed = Math.max(0, v); notifyChange(); }

    public int getStrokeColorPressed() { return strokeColorPressed; }
    public void setStrokeColorPressed(int v) { strokeColorPressed = v; notifyChange(); }

    public int getCornerRadiusPressed() { return cornerRadiusPressed; }
    public void setCornerRadiusPressed(int v) { cornerRadiusPressed = Math.max(0, v); notifyChange(); }

    public int getFillColorPressed() { return fillColorPressed; }
    public void setFillColorPressed(int v) { fillColorPressed = v; notifyChange(); }

    @Override
    public ControlButtonStyle clone() {
        ControlButtonStyle c = new ControlButtonStyle(name);
        c.textColor = textColor;
        c.textSize = textSize;
        c.strokeWidth = strokeWidth;
        c.strokeColor = strokeColor;
        c.cornerRadius = cornerRadius;
        c.fillColor = fillColor;
        c.textColorPressed = textColorPressed;
        c.textSizePressed = textSizePressed;
        c.strokeWidthPressed = strokeWidthPressed;
        c.strokeColorPressed = strokeColorPressed;
        c.cornerRadiusPressed = cornerRadiusPressed;
        c.fillColorPressed = fillColorPressed;
        return c;
    }
}
