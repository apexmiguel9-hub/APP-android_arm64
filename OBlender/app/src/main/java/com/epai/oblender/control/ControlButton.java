// Based on FoldCraftLauncher (FCL-Team) ControlButton, GPL-3.0
// https://github.com/FCL-Team/FoldCraftLauncher
package com.epai.oblender.control;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import java.util.Objects;
import java.util.UUID;

@SuppressLint("ViewConstructor")
public class ControlButton extends Button implements CustomView {

    private Runnable notifyListener;
    private Runnable dataChangeListener;
    private Runnable boundaryListener;
    private Runnable visibilityListener;
    private Runnable alphaListener;

    private final GameMenuStub menu;
    private Path boundaryPath;
    private final Paint boundaryPaint;
    private final int screenWidth;
    private final int screenHeight;

    private SimpleBooleanProperty visibilityProperty;

    private final SimpleBooleanProperty parentVisibilityProperty = new SimpleBooleanProperty(this, "parentVisibility", true);

    public SimpleBooleanProperty parentVisibilityProperty() { return parentVisibilityProperty; }
    public void setParentVisibility(boolean v) { parentVisibilityProperty.set(v); }
    public boolean isParentVisibility() { return parentVisibilityProperty.get(); }

    private final SimpleObjectProperty<ControlButtonData> dataProperty =
            new SimpleObjectProperty<>(this, "data", new ControlButtonData(UUID.randomUUID().toString()));

    public SimpleObjectProperty<ControlButtonData> dataProperty() { return dataProperty; }
    public void setData(ControlButtonData data) { dataProperty.set(data); }
    public ControlButtonData getData() { return dataProperty.get(); }

    public ControlButton(Context context, GameMenuStub menu, ViewListener listener) {
        super(context);
        this.menu = menu;
        setElevation(113.0f);
        setStateListAnimator(null);

        boundaryPath = new Path();
        boundaryPaint = new Paint();
        boundaryPaint.setAntiAlias(true);
        boundaryPaint.setColor(Color.RED);
        boundaryPaint.setStyle(Paint.Style.STROKE);
        boundaryPaint.setStrokeWidth(3);
        screenWidth = menu.screenWidth;
        screenHeight = menu.screenHeight;

        notifyListener = () -> {
            notifyData();
            cancelAllEvent();
        };
        dataChangeListener = () -> {
            notifyData();
            cancelAllEvent();
            getData().addListener(notifyListener);
        };
        boundaryListener = () -> {
            boundaryPath = new Path();
            invalidate();
        };
        visibilityListener = () -> {
            if (!visibilityProperty.get()) cancelAllEvent();
        };
        alphaListener = () -> setAlpha(menu.isHideAllViews() ? 0 : 1);

        post(() -> {
            notifyData();
            menu.editModeProperty().addListener(notifyListener);
            dataProperty.addListener(dataChangeListener);
            getData().addListener(notifyListener);
            menu.showViewBoundariesProperty().addListener(boundaryListener);
            setAlpha(menu.isHideAllViews() ? 0 : 1);
            menu.hideAllViewsProperty().addListener(alphaListener);
            if (listener != null) listener.onReady(this);
        });
    }

    private void notifyData() {
        if (visibilityListener == null) return;
        ControlButtonData data = getData();
        setText(data.getText());
        refreshBaseInfo(data);
        post(() -> {
            refreshStyle(data);
            boundaryPath = new Path();
            invalidate();
        });
    }

    private void refreshBaseInfo(ControlButtonData data) {
        int width, height;
        if (data.getBaseInfo().getSizeType() == BaseInfoData.SizeType.ABSOLUTE) {
            width = dp(data.getBaseInfo().getAbsoluteWidth());
            height = dp(data.getBaseInfo().getAbsoluteHeight());
        } else {
            width = data.getBaseInfo().getPercentageWidth() > 0 ?
                    (int)(screenWidth * (data.getBaseInfo().getPercentageWidth() / 1000f)) : 60;
            height = data.getBaseInfo().getPercentageHeight() > 0 ?
                    (int)(screenHeight * (data.getBaseInfo().getPercentageHeight() / 1000f)) : 60;
        }
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp == null) lp = new ViewGroup.LayoutParams(width, height);
        lp.width = width;
        lp.height = height;
        setLayoutParams(lp);

        post(() -> {
            int x = (int)((screenWidth - width) * (data.getBaseInfo().getXPosition() / 1000f));
            int y = (int)((screenHeight - height) * (data.getBaseInfo().getYPosition() / 1000f));
            setX(x);
            setY(y);
        });

        visibilityProperty().unbind();
        if (menu.isEditMode()) {
            visibilityProperty().set(true);
        } else {
            boolean inGame = getData().getBaseInfo().getVisibilityType() == BaseInfoData.VisibilityType.ALWAYS;
            visibilityProperty().set(inGame && isParentVisibility());
        }
        visibilityProperty().addListener(visibilityListener);
    }

    private GradientDrawable drawableNormal;
    private GradientDrawable drawablePressed;

    private void refreshStyle(ControlButtonData data) {
        drawableNormal = new GradientDrawable();
        drawableNormal.setCornerRadius(dp(data.getStyle().getCornerRadius() / 10f));
        drawableNormal.setStroke(dp(data.getStyle().getStrokeWidth() / 10f), data.getStyle().getStrokeColor());
        drawableNormal.setColor(data.getStyle().getFillColor());
        drawablePressed = new GradientDrawable();
        drawablePressed.setCornerRadius(dp(data.getStyle().getCornerRadiusPressed() / 10f));
        drawablePressed.setStroke(dp(data.getStyle().getStrokeWidthPressed() / 10f), data.getStyle().getStrokeColorPressed());
        drawablePressed.setColor(data.getStyle().getFillColorPressed());
        setGravity(Gravity.CENTER);
        setPadding(0, 0, 0, 0);
        setAllCaps(false);
        setTextSize(data.getStyle().getTextSize());
        setTextColor(data.getStyle().getTextColor());
        setBackground(drawableNormal);
    }

    private void setNormalStyle() {
        setTextSize(getData().getStyle().getTextSize());
        setTextColor(getData().getStyle().getTextColor());
        setBackground(drawableNormal);
    }

    private void setPressedStyle() {
        setTextSize(getData().getStyle().getTextSizePressed());
        setTextColor(getData().getStyle().getTextColorPressed());
        setBackground(drawablePressed);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (menu.isShowViewBoundaries()) {
            boundaryPath.reset();
            boundaryPath.moveTo(0, 0);
            boundaryPath.lineTo(getWidth(), 0);
            boundaryPath.lineTo(getWidth(), getHeight());
            boundaryPath.lineTo(0, getHeight());
            boundaryPath.lineTo(0, 0);
            canvas.drawPath(boundaryPath, boundaryPaint);
        }
    }

    private float downX, downY;
    private int initialX, initialY;
    private float positionX, positionY;
    private long downTime;
    private boolean pressEvent = false;
    private boolean longPress = false;
    private boolean longPressEvent = false;
    private boolean clickEvent = false;
    private int clickCount = 0;
    private long firstClickTime;
    private boolean doubleClickEvent = false;

    private final Handler handler = new Handler();
    private final Runnable runnable = () -> handleLongPressEvent(!longPressEvent);

    private void deleteView() {
        if (menu != null) menu.getViewManager().removeView(getData());
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (menu.isEditMode()) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    setPressedStyle();
                    downX = event.getX(); downY = event.getY();
                    positionX = getX(); positionY = getY();
                    downTime = System.currentTimeMillis();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    float tx = Math.max(0, Math.min(screenWidth - getWidth(), getX() + dx));
                    float ty = Math.max(0, Math.min(screenHeight - getHeight(), getY() + dy));
                    setX(tx); setY(ty);
                    autoFitPosition();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    removeLine(0); removeLine(1);
                    setNormalStyle();
                    if (System.currentTimeMillis() - downTime <= 100
                            && Math.abs(event.getX() - downX) <= 10
                            && Math.abs(event.getY() - downY) <= 10) {
                        setX(positionX); setY(positionY);
                        EditViewDialog.show(getContext(), getData().clone(), false, new EditViewDialog.Callback() {
                            public void onSave(ControlButtonData d) {
                                getData().setText(d.getText());
                                getData().setBaseInfo(d.getBaseInfo());
                                getData().setStyle(d.getStyle());
                                getData().setEvent(d.getEvent());
                                menu.getViewManager().saveController();
                            }
                            public void onClone(CustomControl view) {
                                menu.getViewManager().addView(view);
                            }
                            public void onDelete() {
                                menu.getViewManager().removeView(getData());
                            }
                        });
                    } else {
                        getData().getBaseInfo().setXPosition(
                                (int)((1000 * getX()) / (screenWidth - getMeasuredWidth())));
                        getData().getBaseInfo().setYPosition(
                                (int)((1000 * getY()) / (screenHeight - getMeasuredHeight())));
                        menu.getViewManager().saveController();
                    }
                    break;
            }
        } else {
            if (menu.getTouchController() != null && getData().getEvent().isPointerFollow()) {
                menu.getTouchController().moveView(event);
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    setPressedStyle();
                    downX = event.getX(); downY = event.getY();
                    initialX = menu.getPointerX();
                    initialY = menu.getPointerY();
                    positionX = getX(); positionY = getY();
                    downTime = System.currentTimeMillis();
                    handlePressEvent(!pressEvent);
                    handler.postDelayed(runnable, 400);
                    break;
                case MotionEvent.ACTION_MOVE:
                    handleMoveEvent(event);
                    if ((Math.abs(event.getX() - downX) > 2 || Math.abs(event.getY() - downY) > 2)
                            && System.currentTimeMillis() - downTime < 400) {
                        handler.removeCallbacks(runnable);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!getData().getEvent().getPressEvent().isAutoKeep()
                            && !(getData().getEvent().getLongPressEvent().isAutoKeep() && longPressEvent)) {
                        setNormalStyle();
                    }
                    if (Objects.equals(menu.getInput().getPointerId(), getData().getId())) {
                        menu.getInput().setPointerId(null);
                    }
                    handler.removeCallbacks(runnable);
                    handleUpAfterPressEvent();
                    if (longPress) handleUpAfterLongPressEvent();
                    if (System.currentTimeMillis() - downTime <= 100
                            && Math.abs(event.getX() - downX) <= 10
                            && Math.abs(event.getY() - downY) <= 10) {
                        handleClickEvent(!clickEvent);
                        clickCount++;
                        if (clickCount == 1) firstClickTime = System.currentTimeMillis();
                        if (clickCount == 2) {
                            if (System.currentTimeMillis() - firstClickTime < 400) {
                                handleDoubleEvent(!doubleClickEvent);
                                clickCount = 0;
                            } else {
                                clickCount = 1;
                                firstClickTime = System.currentTimeMillis();
                            }
                        }
                    }
                    break;
            }
        }
        return true;
    }

    private void showLine(int orientation, int pref, int self) {
        if (menu == null) return;
        menu.getTouchPad().drawLine(orientation, pref, self);
    }

    private void removeLine(int orientation) {
        if (menu == null) return;
        menu.getTouchPad().removeLine(orientation);
    }

    private void autoFitPosition() {
        if (menu == null || !menu.getMenuSetting().isAutoFit()) return;
        ViewGroup vg = (ViewGroup) getParent();
        int dist = dp(menu.getMenuSetting().getAutoFitDist());
        final int autoFitDist = Math.max(dist, dp(2));
        boolean[] xyPref = {false, false};
        int[] prefXY = {0, 0};
        int[] selfXY = {0, 0};
        int[] xyDist = {autoFitDist, autoFitDist};
        int left = (int) getX();
        int right = (int) (getX() + getWidth());
        int up = (int) getY();
        int down = (int) (getY() + getHeight());
        int[] posArr = {left, right, up, down};

        for (int i = 0; i < vg.getChildCount(); i++) {
            if (vg.getChildAt(i).getVisibility() == VISIBLE) {
                View btn = vg.getChildAt(i);
                if (btn == this || !(btn instanceof ControlButton)) continue;
                int[] btnPos = {
                        (int) btn.getX(),
                        (int) (btn.getX() + btn.getWidth()),
                        (int) btn.getY(),
                        (int) (btn.getY() + btn.getHeight())
                };
                int flag = -1;
                for (int j = 0; j < posArr.length; j++) {
                    flag *= -1;
                    int xyIndex = j / 2 % 2;
                    if (Math.abs(posArr[j] - btnPos[j]) < xyDist[xyIndex]) {
                        xyPref[xyIndex] = true;
                        prefXY[xyIndex] = btnPos[j];
                        xyDist[xyIndex] = posArr[j] - btnPos[j];
                        selfXY[xyIndex] = posArr[j] - xyDist[xyIndex];
                    }
                    int btnDist = posArr[j] - btnPos[j + flag];
                    if (flag * btnDist >= 0 && flag * btnDist < xyDist[xyIndex]) {
                        xyPref[xyIndex] = true;
                        prefXY[xyIndex] = btnPos[j + flag];
                        xyDist[xyIndex] = btnDist - flag * dist;
                        selfXY[xyIndex] = posArr[j] - xyDist[xyIndex];
                    }
                }
            }
        }
        if (xyPref[0]) { setX(left - xyDist[0]); showLine(0, prefXY[0], selfXY[0]); }
        else removeLine(0);
        if (xyPref[1]) { setY(up - xyDist[1]); showLine(1, prefXY[1], selfXY[1]); }
        else removeLine(1);
    }

    private void cancelAllEvent() {
        handleUpAfterPressEvent();
        handleUpAfterLongPressEvent();
        cancelTickEvent(getData().getEvent().getPressEvent());
        cancelTickEvent(getData().getEvent().getLongPressEvent());
        cancelTickEvent(getData().getEvent().getClickEvent());
        cancelTickEvent(getData().getEvent().getDoubleClickEvent());
        setNormalStyle();
        pressEvent = false; longPress = false; longPressEvent = false;
        clickEvent = false; clickCount = 0; doubleClickEvent = false;
    }

    private void handleMoveEvent(MotionEvent event) {
        if (getData().getEvent().isPointerFollow()) {
            int dx = (int)((event.getX() - downX) * menu.getMenuSetting().getMouseSensitivity());
            int dy = (int)((event.getY() - downY) * menu.getMenuSetting().getMouseSensitivity());
            menu.getInput().setPointerId(getData().getId());
            menu.getInput().setPointer(
                    Math.max(0, Math.min(screenWidth, initialX + dx)),
                    Math.max(0, Math.min(screenHeight, initialY + dy)),
                    getData().getId());
            menu.setPointerX(initialX + dx);
            menu.setPointerY(initialY + dy);
        }
        if (getData().getEvent().isMovable()) {
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            setX(Math.max(0, Math.min(screenWidth - getWidth(), getX() + dx)));
            setY(Math.max(0, Math.min(screenHeight - getHeight(), getY() + dy)));
        }
    }

    private void handlePressEvent(boolean enable) {
        pressEvent = enable;
        handleTickEvent(enable, getData().getEvent().getPressEvent(), 0);
    }
    private void handleUpAfterPressEvent() { handleUpEvent(getData().getEvent().getPressEvent()); }
    private void handleLongPressEvent(boolean enable) {
        longPress = true; longPressEvent = enable;
        handleTickEvent(enable, getData().getEvent().getLongPressEvent(), 1);
    }
    private void handleUpAfterLongPressEvent() {
        longPress = false;
        handleUpEvent(getData().getEvent().getLongPressEvent());
    }
    private void handleClickEvent(boolean enable) {
        clickEvent = enable;
        handleTickEvent(enable, getData().getEvent().getClickEvent(), 2);
    }
    private void handleDoubleEvent(boolean enable) {
        doubleClickEvent = enable;
        handleTickEvent(enable, getData().getEvent().getDoubleClickEvent(), 3);
    }

    private void handleUpEvent(ButtonEventData.Event event) {
        if (!event.isAutoKeep()) {
            if (event.isAutoClick()) handleAutoClick(event, false);
            else handleKeyEvent(event, false);
        }
    }
    private boolean keycodeOutputting = false;
    private void handleKeyEvent(ButtonEventData.Event event, boolean press) {
        if (!press && !keycodeOutputting) return;
        if (event.outputKeycodesList().isEmpty()) return;
        for (int keycode : event.outputKeycodesList()) {
            keycodeOutputting = press;
            menu.getInput().sendKeyEvent(keycode, press);
        }
    }

    private boolean autoClick = false;
    private ButtonEventData.Event autoClickEvent;
    private final Handler autoClickHandler = new Handler();
    private Runnable autoClickRunnable;
    private void handleAutoClick(ButtonEventData.Event event, boolean enable) {
        autoClick = enable;
        if (enable) {
            autoClickEvent = event;
            if (autoClickRunnable == null) {
                autoClickRunnable = new Runnable() {
                    public void run() {
                        ButtonEventData.Event ev = autoClickEvent;
                        handleKeyEvent(ev, true);
                        handleKeyEvent(ev, false);
                        if (autoClick) autoClickHandler.postDelayed(autoClickRunnable, 20);
                    }
                };
            }
            autoClickHandler.post(autoClickRunnable);
        }
    }

    private void cancelTickEvent(ButtonEventData.Event event) {
        if (event.isAutoKeep()) {
            if (event.isAutoClick()) handleAutoClick(event, false);
            else handleKeyEvent(event, false);
        }
    }

    private void handleTickEvent(boolean enable, ButtonEventData.Event event, int eventType) {
        if (event.isAutoKeep()) {
            if (event.isAutoClick()) handleAutoClick(event, enable);
            else handleKeyEvent(event, enable);
            if (enable) setPressedStyle(); else setNormalStyle();
        } else {
            switch (eventType) {
                case 0: case 1:
                    if (event.isAutoClick()) handleAutoClick(event, true);
                    else handleKeyEvent(event, true);
                    break;
                case 2: case 3:
                    handleKeyEvent(event, true);
                    handleKeyEvent(event, false);
                    break;
            }
        }
        if (event.getOutputText() != null && !event.getOutputText().isEmpty()) {
            for (int i = 0; i < event.getOutputText().length(); i++) {
                menu.getInput().sendChar(event.getOutputText().charAt(i));
            }
        }
    }

    public SimpleBooleanProperty visibilityProperty() {
        if (visibilityProperty == null) {
            visibilityProperty = new SimpleBooleanProperty(this, "visibility", true) {
                @Override
                public void set(boolean v) {
                    super.set(v);
                    post(() -> setVisibility(v ? VISIBLE : GONE));
                }
            };
        }
        return visibilityProperty;
    }

    @Override
    public CustomControl.ViewType getType() { return CustomControl.ViewType.CONTROL_BUTTON; }
    @Override
    public String getViewId() { return getData().getId(); }
    @Override
    public void switchParentVisibility() { setParentVisibility(!isParentVisibility()); }
    @Override
    public void removeListener() {
        menu.editModeProperty().removeListener(notifyListener);
        dataProperty.removeListener(dataChangeListener);
        getData().removeListener(notifyListener);
        menu.showViewBoundariesProperty().removeListener(boundaryListener);
        visibilityProperty().removeListener(visibilityListener);
        menu.hideAllViewsProperty().removeListener(alphaListener);
        notifyListener = null; dataChangeListener = null;
        boundaryListener = null; visibilityListener = null; alphaListener = null;
    }

    private int dp(float val) {
        return (int)(val * getResources().getDisplayMetrics().density);
    }
}
