// Based on FoldCraftLauncher (FCL-Team) control editor concept, GPL-3.0
// https://github.com/FCL-Team/FoldCraftLauncher
package com.epai.oblender.control;

import java.util.ArrayList;
import java.util.List;

public class BaseInfoData implements Cloneable {
    public enum SizeType { PERCENTAGE, ABSOLUTE }
    public enum VisibilityType { ALWAYS, IN_GAME, MENU }

    private VisibilityType visibilityType = VisibilityType.ALWAYS;
    private int xPosition; // 0-1000 (thousandths of screen width)
    private int yPosition; // 0-1000 (thousandths of screen height)
    private SizeType sizeType = SizeType.PERCENTAGE;
    private int absoluteWidth = 60;  // dp
    private int absoluteHeight = 60; // dp
    private int percentageWidth = 80;  // 0-1000
    private int percentageHeight = 80; // 0-1000
    private boolean useScreenWidth = true; // true = % of screen width, false = % of screen height

    private List<Runnable> changeListeners = new ArrayList<>();

    public void addChangeListener(Runnable r) { changeListeners.add(r); }
    private void notifyChange() { for (Runnable r : changeListeners) r.run(); }

    public VisibilityType getVisibilityType() { return visibilityType; }
    public void setVisibilityType(VisibilityType v) { visibilityType = v; notifyChange(); }

    public int getXPosition() { return xPosition; }
    public void setXPosition(int v) { xPosition = Math.max(0, Math.min(1000, v)); notifyChange(); }

    public int getYPosition() { return yPosition; }
    public void setYPosition(int v) { yPosition = Math.max(0, Math.min(1000, v)); notifyChange(); }

    public SizeType getSizeType() { return sizeType; }
    public void setSizeType(SizeType v) { sizeType = v; notifyChange(); }

    public int getAbsoluteWidth() { return absoluteWidth; }
    public void setAbsoluteWidth(int v) { absoluteWidth = Math.max(20, v); notifyChange(); }

    public int getAbsoluteHeight() { return absoluteHeight; }
    public void setAbsoluteHeight(int v) { absoluteHeight = Math.max(20, v); notifyChange(); }

    public int getPercentageWidth() { return percentageWidth; }
    public void setPercentageWidth(int v) { percentageWidth = Math.max(10, Math.min(500, v)); notifyChange(); }

    public int getPercentageHeight() { return percentageHeight; }
    public void setPercentageHeight(int v) { percentageHeight = Math.max(10, Math.min(500, v)); notifyChange(); }

    public boolean isUseScreenWidth() { return useScreenWidth; }
    public void setUseScreenWidth(boolean v) { useScreenWidth = v; notifyChange(); }

    public int computeWidth(int screenW, int screenH) {
        if (sizeType == SizeType.ABSOLUTE) return absoluteWidth;
        int ref = useScreenWidth ? screenW : screenH;
        return ref * percentageWidth / 1000;
    }

    public int computeHeight(int screenW, int screenH) {
        if (sizeType == SizeType.ABSOLUTE) return absoluteHeight;
        int ref = useScreenWidth ? screenW : screenH;
        return ref * percentageHeight / 1000;
    }

    public int computeX(int screenW, int buttonW) {
        return (screenW - buttonW) * xPosition / 1000;
    }

    public int computeY(int screenH, int buttonH) {
        return (screenH - buttonH) * yPosition / 1000;
    }

    @Override
    public BaseInfoData clone() {
        BaseInfoData c = new BaseInfoData();
        c.visibilityType = visibilityType;
        c.xPosition = xPosition;
        c.yPosition = yPosition;
        c.sizeType = sizeType;
        c.absoluteWidth = absoluteWidth;
        c.absoluteHeight = absoluteHeight;
        c.percentageWidth = percentageWidth;
        c.percentageHeight = percentageHeight;
        c.useScreenWidth = useScreenWidth;
        return c;
    }
}
