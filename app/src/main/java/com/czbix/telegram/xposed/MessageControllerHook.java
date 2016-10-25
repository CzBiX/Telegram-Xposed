package com.czbix.telegram.xposed;

import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;


class MessageControllerHook extends XC_MethodHook {
    static Class<?> clsUpdateDeleteMessages;
    static Class<?> clsUpdateDeleteChannelMessages;

    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        final ArrayList<?> updates = (ArrayList<?>) param.args[0];
        if (updates.isEmpty()) {
            return;
        }
        for (Object update : updates) {
            if (clsUpdateDeleteMessages.isInstance(update) || clsUpdateDeleteChannelMessages.isInstance(update)) {
                //noinspection unchecked
                final ArrayList<Integer> messages = (ArrayList<Integer>) XposedHelpers.getObjectField(update, "messages");
                XposedHelpers.setAdditionalInstanceField(messages, "ccc_keep", false);
            }
        }
    }
}
