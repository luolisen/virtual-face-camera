package io.github.alanlaw.vfc.api101;

import io.github.libxposed.api.XposedModule;

public final class Api101Runtime {
    private static volatile XposedModule module;

    private Api101Runtime() {
    }

    public static void setModule(XposedModule xposedModule) {
        module = xposedModule;
    }

    public static XposedModule getModule() {
        return module;
    }

    public static XposedModule requireModule() {
        XposedModule current = module;
        if (current == null) {
            throw new IllegalStateException("XposedModule is not ready");
        }
        return current;
    }
}
