// Based on FoldCraftLauncher (FCL-Team) control editor concept, GPL-3.0
// https://github.com/FCL-Team/FoldCraftLauncher
package com.epai.oblender.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ButtonEventData implements Cloneable {
    public static class Event implements Cloneable {
        private boolean autoKeep;
        private boolean autoClick;
        private List<Integer> keycodes = new ArrayList<>();
        private String outputText = "";
        private List<Runnable> changeListeners = new ArrayList<>();

        public void addChangeListener(Runnable r) { changeListeners.add(r); }
        private void notifyChange() { for (Runnable r : changeListeners) r.run(); }

        public boolean isAutoKeep() { return autoKeep; }
        public void setAutoKeep(boolean v) { autoKeep = v; notifyChange(); }

        public boolean isAutoClick() { return autoClick; }
        public void setAutoClick(boolean v) { autoClick = v; notifyChange(); }

        public List<Integer> getKeycodes() { return keycodes; }
        public void setKeycodes(List<Integer> v) { keycodes = new ArrayList<>(v); notifyChange(); }
        public void addKeycode(int k) { if (!keycodes.contains(k)) { keycodes.add(k); notifyChange(); } }
        public void removeKeycode(int k) { if (keycodes.remove((Integer) k)) notifyChange(); }
        public void clearKeycodes() { keycodes.clear(); notifyChange(); }

        public List<Integer> outputKeycodesList() { return keycodes; }
        public String getOutputText() { return outputText; }
        public void setOutputText(String v) { outputText = v != null ? v : ""; notifyChange(); }

        public boolean hasAnyKey() {
            return !keycodes.isEmpty();
        }

        @Override
        public Event clone() {
            Event e = new Event();
            e.autoKeep = autoKeep;
            e.autoClick = autoClick;
            e.keycodes = new ArrayList<>(keycodes);
            return e;
        }
    }

    private Event pressEvent = new Event();
    private Event longPressEvent = new Event();
    private Event clickEvent = new Event();
    private Event doubleClickEvent = new Event();
    private boolean pointerFollow;
    private boolean movable;

    private List<Runnable> changeListeners = new ArrayList<>();

    public void addChangeListener(Runnable r) {
        changeListeners.add(r);
        pressEvent.addChangeListener(r);
        longPressEvent.addChangeListener(r);
        clickEvent.addChangeListener(r);
        doubleClickEvent.addChangeListener(r);
    }
    private void notifyChange() { for (Runnable r : changeListeners) r.run(); }

    public Event getPressEvent() { return pressEvent; }
    public void setPressEvent(Event v) { pressEvent = v != null ? v : new Event(); notifyChange(); }

    public Event getLongPressEvent() { return longPressEvent; }
    public void setLongPressEvent(Event v) { longPressEvent = v != null ? v : new Event(); notifyChange(); }

    public Event getClickEvent() { return clickEvent; }
    public void setClickEvent(Event v) { clickEvent = v != null ? v : new Event(); notifyChange(); }

    public Event getDoubleClickEvent() { return doubleClickEvent; }
    public void setDoubleClickEvent(Event v) { doubleClickEvent = v != null ? v : new Event(); notifyChange(); }

    public boolean isPointerFollow() { return pointerFollow; }
    public void setPointerFollow(boolean v) { pointerFollow = v; notifyChange(); }

    public boolean isMovable() { return movable; }
    public void setMovable(boolean v) { movable = v; notifyChange(); }

    @Override
    public ButtonEventData clone() {
        ButtonEventData d = new ButtonEventData();
        d.pressEvent = pressEvent.clone();
        d.longPressEvent = longPressEvent.clone();
        d.clickEvent = clickEvent.clone();
        d.doubleClickEvent = doubleClickEvent.clone();
        d.pointerFollow = pointerFollow;
        d.movable = movable;
        return d;
    }
}
