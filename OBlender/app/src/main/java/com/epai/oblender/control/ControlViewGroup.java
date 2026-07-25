package com.epai.oblender.control;

import java.util.ArrayList;
import java.util.List;

public class ControlViewGroup {
    public enum Visibility { VISIBLE, INVISIBLE }

    private String id;
    private Visibility visibility = Visibility.VISIBLE;
    private ViewData viewData = new ViewData();
    private String name = "default";

    public ControlViewGroup(String id) { this.id = id; }

    public String getId() { return id; }
    public void setId(String v) { id = v; }

    public Visibility getVisibility() { return visibility; }
    public void setVisibility(Visibility v) { visibility = v; }

    public ViewData getViewData() { return viewData; }
    public void setViewData(ViewData v) { viewData = v; }

    public String getName() { return name; }
    public void setName(String v) { name = v; }

    public static class ViewData {
        private List<ControlButtonData> buttonList = new ArrayList<>();
        private List<ControlDirectionData> directionList = new ArrayList<>();

        public List<ControlButtonData> buttonList() { return buttonList; }
        public List<ControlDirectionData> directionList() { return directionList; }

        public void addButton(ControlButtonData d) { buttonList.add(d); }
        public void removeButton(ControlButtonData d) { buttonList.remove(d); }
        public void addDirection(ControlDirectionData d) { directionList.add(d); }
        public void removeDirection(ControlDirectionData d) { directionList.remove(d); }
    }

    // Minimal ControlDirectionData needed for ViewManager to compile
    public static class ControlDirectionData implements CustomControl {
        private String id;
        public ControlDirectionData(String id) { this.id = id; }
        public String getId() { return id; }
        public String getViewId() { return id; }
        public CustomControl.ViewType getType() { return CustomControl.ViewType.CONTROL_DIRECTION; }
    }
}
