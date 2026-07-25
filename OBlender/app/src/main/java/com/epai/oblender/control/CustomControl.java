package com.epai.oblender.control;

public interface CustomControl {
    enum ViewType { CONTROL_BUTTON, CONTROL_DIRECTION }
    ViewType getType();
    String getViewId();
}
