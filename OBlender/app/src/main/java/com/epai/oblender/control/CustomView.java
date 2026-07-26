package com.epai.oblender.control;

public interface CustomView {
    CustomControl.ViewType getType();
    String getViewId();
    void switchParentVisibility();
    void removeListener();
}
