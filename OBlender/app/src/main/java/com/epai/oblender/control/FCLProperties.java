package com.epai.oblender.control;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/* Minimal JavaFX-style property stubs for FCL ControlButton port */

interface FCLObservable {
    void addListener(Runnable listener);
    void removeListener(Runnable listener);
}

class SimpleBooleanProperty {
    private boolean value;
    private final Object bean;
    private final String name;
    private final List<Runnable> listeners = new ArrayList<>();
    private FCLBinding binding;

    public SimpleBooleanProperty(Object bean, String name, boolean initial) {
        this.bean = bean; this.name = name; this.value = initial;
    }
    public SimpleBooleanProperty(Object bean, String name) { this(bean, name, false); }
    public SimpleBooleanProperty(boolean initial) { this(null, "", initial); }
    public SimpleBooleanProperty() { this(false); }
    public Object getBean() { return bean; }
    public String getName() { return name; }
    public boolean get() { return binding != null ? binding.get() : value; }
    public void set(boolean v) { value = v; fire(); }
    public void addListener(Runnable r) { listeners.add(r); }
    public void removeListener(Runnable r) { listeners.remove(r); }
    public void bind(FCLBinding b) { binding = b; if (b != null) b.addListener(() -> fire()); }
    public void unbind() { binding = null; }
    public boolean isBound() { return binding != null; }
    void fire() { for (Runnable r : listeners) r.run(); }
}

class SimpleObjectProperty<T> {
    private T value;
    private final Object bean;
    private final String name;
    private final List<Runnable> listeners = new ArrayList<>();

    public SimpleObjectProperty(Object bean, String name, T initial) {
        this.bean = bean; this.name = name; this.value = initial;
    }
    public SimpleObjectProperty(Object bean, String name) { this(bean, name, null); }
    public SimpleObjectProperty(T initial) { this(null, "", initial); }
    public SimpleObjectProperty() { this(null, "", null); }
    public Object getBean() { return bean; }
    public String getName() { return name; }
    public T get() { return value; }
    public void set(T v) { value = v; fire(); }
    public void addListener(Runnable r) { listeners.add(r); }
    public void removeListener(Runnable r) { listeners.remove(r); }
    void fire() { for (Runnable r : listeners) r.run(); }
}

interface FCLBinding {
    boolean get();
    void addListener(Runnable r);
    void removeListener(Runnable r);
}

class FCLBooleanBinding implements FCLBinding {
    private final Callable<Boolean> func;
    private final List<Runnable> listeners = new ArrayList<>();
    private boolean valid = false;
    private boolean cached;

    public FCLBooleanBinding(Callable<Boolean> func, FCLObservable... deps) {
        this.func = func;
        Runnable listener = () -> { valid = false; fire(); };
        for (FCLObservable dep : deps) dep.addListener(listener);
    }
    public boolean get() {
        if (!valid) { try { cached = func.call(); } catch (Exception e) { cached = false; } valid = true; }
        return cached;
    }
    public void addListener(Runnable r) { listeners.add(r); }
    public void removeListener(Runnable r) { listeners.remove(r); }
    void fire() { for (Runnable r : listeners) r.run(); }
}
