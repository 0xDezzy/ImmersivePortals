package qouteall.imm_ptl.core.compat.voxy;

import java.lang.reflect.Method;

/** Reflection is isolated here so pseudo mixins never shadow an Object-typed Viewport. */
public final class VoxyReflection {
    private VoxyReflection() {}

    private static final ClassValue<Method> DELETE = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                return type.getMethod("delete");
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unsupported Voxy viewport " + type.getName(), e);
            }
        }
    };

    public static Object defaultViewport(Object selector) {
        try {
            var field = selector.getClass().getDeclaredField("defaultViewport");
            field.setAccessible(true);
            return field.get(selector);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported Voxy ViewportSelector", e);
        }
    }

    public static void delete(Object viewport) {
        try {
            DELETE.get(viewport.getClass()).invoke(viewport);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot release Voxy viewport", e);
        }
    }
}
