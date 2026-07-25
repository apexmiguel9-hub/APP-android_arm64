// Based on FoldCraftLauncher (FCL-Team) control editor concept, GPL-3.0
// https://github.com/FCL-Team/FoldCraftLauncher
package com.epai.oblender.control;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ControlButtonData implements Cloneable {
    private String id;
    private String text = "";
    private ControlButtonStyle style = ControlButtonStyle.DEFAULT;
    private BaseInfoData baseInfo = new BaseInfoData();
    private ButtonEventData event = new ButtonEventData();

    private List<Runnable> changeListeners = new ArrayList<>();

    public ControlButtonData() {
        this.id = UUID.randomUUID().toString();
        setupChildListeners();
    }

    public ControlButtonData(String id) {
        this.id = id;
        setupChildListeners();
    }

    private void setupChildListeners() {
        style.addChangeListener(this::notifyChange);
        baseInfo.addChangeListener(this::notifyChange);
        event.addChangeListener(this::notifyChange);
    }

    public void addChangeListener(Runnable r) { changeListeners.add(r); }
    private void notifyChange() { for (Runnable r : changeListeners) r.run(); }

    public String getId() { return id; }

    public String getText() { return text; }
    public void setText(String v) { text = v != null ? v : ""; notifyChange(); }

    public ControlButtonStyle getStyle() { return style; }
    public void setStyle(ControlButtonStyle v) { style = v != null ? v : ControlButtonStyle.DEFAULT; notifyChange(); }

    public BaseInfoData getBaseInfo() { return baseInfo; }
    public void setBaseInfo(BaseInfoData v) { baseInfo = v != null ? v : new BaseInfoData(); notifyChange(); }

    public ButtonEventData getEvent() { return event; }
    public void setEvent(ButtonEventData v) { event = v != null ? v : new ButtonEventData(); notifyChange(); }

    @Override
    public ControlButtonData clone() {
        ControlButtonData c = new ControlButtonData(UUID.randomUUID().toString());
        c.text = text;
        c.style = style.clone();
        c.baseInfo = baseInfo.clone();
        c.event = event.clone();
        return c;
    }

    public ControlButtonData cloneView() {
        ControlButtonData c = clone();
        c.baseInfo.setXPosition(0);
        c.baseInfo.setYPosition(0);
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof ControlButtonData) return id.equals(((ControlButtonData) o).id);
        return false;
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}
