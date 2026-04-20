package com.maaend.android.preview.hidden;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.display.VirtualDisplay;
import android.os.Handler;
import android.view.Display;
import android.view.Surface;

import com.maaend.android.preview.DisplayInfo;
import com.maaend.android.preview.FakeContext;
import com.maaend.android.preview.Size;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

@SuppressLint("PrivateApi")
public final class DisplayManager {

    public interface Listener {
        void onDisplayChanged(int displayId);
    }

    public static final class ListenerHandle {
        private final Object proxy;

        private ListenerHandle(Object proxy) {
            this.proxy = proxy;
        }
    }

    private final Object manager;
    private Method getDisplayInfoMethod;

    static DisplayManager create() {
        try {
            Class<?> clazz = Class.forName("android.hardware.display.DisplayManagerGlobal");
            Method getInstanceMethod = clazz.getDeclaredMethod("getInstance");
            Object dmg = getInstanceMethod.invoke(null);
            return new DisplayManager(dmg);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private DisplayManager(Object manager) {
        this.manager = manager;
    }

    private synchronized Method getGetDisplayInfoMethod() throws NoSuchMethodException {
        if (getDisplayInfoMethod == null) {
            getDisplayInfoMethod = manager.getClass().getMethod("getDisplayInfo", int.class);
        }
        return getDisplayInfoMethod;
    }

    public DisplayInfo getDisplayInfo(int displayId) {
        try {
            Method method = getGetDisplayInfoMethod();
            Object displayInfo = method.invoke(manager, displayId);
            if (displayInfo == null) {
                return null;
            }
            Class<?> cls = displayInfo.getClass();
            int width = cls.getDeclaredField("logicalWidth").getInt(displayInfo);
            int height = cls.getDeclaredField("logicalHeight").getInt(displayInfo);
            int rotation = cls.getDeclaredField("rotation").getInt(displayInfo);
            int layerStack = cls.getDeclaredField("layerStack").getInt(displayInfo);
            int flags = cls.getDeclaredField("flags").getInt(displayInfo);
            int dpi = cls.getDeclaredField("logicalDensityDpi").getInt(displayInfo);
            String uniqueId = (String) cls.getDeclaredField("uniqueId").get(displayInfo);
            return new DisplayInfo(displayId, new Size(width, height), rotation, layerStack, flags, dpi, uniqueId);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public VirtualDisplay createNewVirtualDisplay(Context context, String name, int width, int height, int dpi, Surface surface, int flags) throws Exception {
        Constructor<android.hardware.display.DisplayManager> ctor =
                android.hardware.display.DisplayManager.class.getDeclaredConstructor(Context.class);
        ctor.setAccessible(true);
        android.hardware.display.DisplayManager dm = ctor.newInstance(new FakeContext(context));
        return dm.createVirtualDisplay(name, width, height, dpi, surface, flags);
    }

    public ListenerHandle registerDisplayListener(Listener listener, Handler handler) {
        try {
            Class<?> displayListenerClass = Class.forName("android.hardware.display.DisplayManager$DisplayListener");
            Object proxy = Proxy.newProxyInstance(
                    ClassLoader.getSystemClassLoader(),
                    new Class[]{displayListenerClass},
                    (p, method, args) -> {
                        if ("onDisplayChanged".equals(method.getName())) {
                            listener.onDisplayChanged((int) args[0]);
                        }
                        return null;
                    });
            try {
                manager.getClass()
                        .getMethod("registerDisplayListener", displayListenerClass, Handler.class, long.class)
                        .invoke(manager, proxy, handler, 1L << 2);
            } catch (NoSuchMethodException e) {
                manager.getClass()
                        .getMethod("registerDisplayListener", displayListenerClass, Handler.class)
                        .invoke(manager, proxy, handler);
            }
            return new ListenerHandle(proxy);
        } catch (Exception ignored) {
            return null;
        }
    }

    public void unregisterDisplayListener(ListenerHandle handle) {
        if (handle == null) {
            return;
        }
        try {
            Class<?> displayListenerClass = Class.forName("android.hardware.display.DisplayManager$DisplayListener");
            manager.getClass().getMethod("unregisterDisplayListener", displayListenerClass).invoke(manager, handle.proxy);
        } catch (Exception ignored) {
        }
    }
}
