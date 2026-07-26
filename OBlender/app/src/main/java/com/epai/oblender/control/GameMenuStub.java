package com.epai.oblender.control;

import android.app.Activity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import com.epai.oblender.OblSettingFragment;

/**
 * Minimal stub that provides the GameMenu interface ControlButton needs.
 * Replaces FCL's GameMenu with Blender-specific key dispatch.
 */
public class GameMenuStub {

    public static class TouchController {
        public void moveView(android.view.MotionEvent event) {}
    }

    public static class TouchPad {
        public void drawLine(int orientation, int pref, int self) {}
        public void removeLine(int orientation) {}
    }

    public class Input {
        private OblSettingFragment.OBLSettingFragmentListener listener;
        private String pointerId;

        public Input(OblSettingFragment.OBLSettingFragmentListener l) { this.listener = l; }
        public void sendKeyEvent(int keycode, boolean press) {
            if (listener == null) return;
            if (press) listener.enterKeyOn(new int[]{keycode});
            else listener.enterKeyOff(new int[]{keycode});
        }
        public void sendChar(char c) {
            if (listener == null) return;
            listener.enterKey(new int[]{(int)c});
        }
        public void sendBoundKeyEvent(Object opt, int binding, int keycode, boolean press) {
            sendKeyEvent(keycode, press);
        }
        public String getPointerId() { return pointerId; }
        public void setPointerId(String id) { pointerId = id; }
        public void setPointer(int x, int y, String id) { pointerX = x; pointerY = y; }
    }

    public static class MenuSetting {
        private boolean autoFit = false;
        private int autoFitDist = 10;
        private float mouseSensitivity = 1.0f;
        private boolean enableGyroscope = false;
        private boolean hideMenuView = false;

        public boolean isAutoFit() { return autoFit; }
        public int getAutoFitDist() { return autoFitDist; }
        public float getMouseSensitivity() { return mouseSensitivity; }
        public boolean isEnableGyroscope() { return enableGyroscope; }
        public boolean isHideMenuView() { return hideMenuView; }
    }

    private Activity activity;
    private FrameLayout baseLayout;
    private SimpleBooleanProperty editModeProperty;
    private SimpleBooleanProperty showViewBoundariesProperty;
    private SimpleBooleanProperty hideAllViewsProperty;
    private SimpleObjectProperty<ControlViewGroup> viewGroupProperty;
    private int cursorMode = 1; // CursorDisabled for FCL
    private SimpleBooleanProperty cursorModeProperty;
    private Input input;
    private TouchController touchController;
    private TouchPad touchPad;
    private MenuSetting menuSetting = new MenuSetting();
    private int pointerX, pointerY;
    private boolean editMode = false;
    private boolean showBoundaries = false;
    private boolean hideAllViews = false;
    private ViewManager viewManager;
    private ControlViewGroup currentViewGroup;

    public int screenWidth, screenHeight;

    public GameMenuStub(Activity activity, FrameLayout baseLayout,
                        OblSettingFragment.OBLSettingFragmentListener keyListener) {
        this.activity = activity;
        this.baseLayout = baseLayout;
        this.editModeProperty = new SimpleBooleanProperty(this, "editMode", false);
        this.showViewBoundariesProperty = new SimpleBooleanProperty(this, "showViewBoundaries", false);
        this.hideAllViewsProperty = new SimpleBooleanProperty(this, "hideAllViews", false);
        this.cursorModeProperty = new SimpleBooleanProperty(this, "cursorMode", false);
        this.viewGroupProperty = new SimpleObjectProperty<>(this, "viewGroup", null);
        this.input = new Input(keyListener);
        this.touchController = new TouchController();
        this.touchPad = new TouchPad();
        this.viewManager = new ViewManager(this);
    }

    public Activity getActivity() { return activity; }
    public FrameLayout getBaseLayout() { return baseLayout; }
    public Input getInput() { return input; }
    public TouchController getTouchController() { return touchController; }
    public TouchPad getTouchPad() { return touchPad; }
    public MenuSetting getMenuSetting() { return menuSetting; }
    public ViewManager getViewManager() { return viewManager; }
    public int getPointerX() { return pointerX; }
    public int getPointerY() { return pointerY; }
    public void setPointerX(int v) { pointerX = v; }
    public void setPointerY(int v) { pointerY = v; }

    /* Properties */
    public SimpleBooleanProperty editModeProperty() { return editModeProperty; }
    public SimpleBooleanProperty showViewBoundariesProperty() { return showViewBoundariesProperty; }
    public SimpleBooleanProperty hideAllViewsProperty() { return hideAllViewsProperty; }
    public SimpleBooleanProperty cursorModeProperty() { return cursorModeProperty; }
    public SimpleObjectProperty<ControlViewGroup> viewGroupProperty() { return viewGroupProperty; }

    public boolean isEditMode() { return editModeProperty.get(); }
    public void setEditMode(boolean v) { editModeProperty.set(v); }
    public boolean isShowViewBoundaries() { return showViewBoundariesProperty.get(); }
    public void setShowViewBoundaries(boolean v) { showViewBoundariesProperty.set(v); }
    public boolean isHideAllViews() { return hideAllViewsProperty.get(); }
    public void setHideAllViews(boolean v) { hideAllViewsProperty.set(v); }
    public int getCursorMode() { return cursorMode; }
    public void setCursorMode(int v) { cursorMode = v; cursorModeProperty.set(v == 1); }
    public ControlViewGroup getViewGroup() { return viewGroupProperty.get(); }
    public void setViewGroup(ControlViewGroup g) { viewGroupProperty.set(g); }
}
