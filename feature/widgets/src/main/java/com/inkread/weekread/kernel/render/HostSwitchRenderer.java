package com.inkread.weekread.kernel.render;

import android.graphics.Canvas;
import com.inkread.weekread.kernel.CardView;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Calls the existing switch-symbol renderer without maintaining an alternate icon. */
public final class HostSwitchRenderer {
    private static final Method SWITCH = method("drawSwitchIcon", Canvas.class,
            float.class, float.class, float.class);
    private final CardRenderer renderer;

    public HostSwitchRenderer(CardView host) {
        renderer = new CardRenderer(host);
    }

    public void drawSwitch(Canvas canvas, float left, float top, float size) {
        invoke(SWITCH, canvas, left, top, size);
    }

    private static Method method(String name, Class<?>... parameters) {
        try {
            Method result = CardRenderer.class.getDeclaredMethod(name, parameters);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Pinned k6-host1 renderer API changed", failure);
        }
    }

    private void invoke(Method method, Object... arguments) {
        try {
            method.invoke(renderer, arguments);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Pinned k6-host1 rendering failed", failure);
        }
    }
}
