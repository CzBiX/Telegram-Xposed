package com.czbix.telegram.xposed;

import android.content.res.XModuleResources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.support.annotation.Keep;

import java.util.ArrayList;

import dalvik.system.PathClassLoader;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookZygoteInit, IXposedHookLoadPackage {
    private static final String PKG_NAME = "org.telegram.messenger";

    private static String MODULE_PATH;
    private static Class<?> clsExEmojiDrawable;
    private static Class<?> clsTelegramWord;
    private int drawImgSize;
    private Typeface typeface;

    @Keep
    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
       MODULE_PATH = startupParam.modulePath;
    }

    @Keep
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(PKG_NAME)) {
            return;
        }

        typeface = Typeface.createFromAsset(
                XModuleResources.createInstance(MODULE_PATH, null).getAssets(),
                "NotoColorEmoji.ttf");

        initCls(lpparam.classLoader);

        final Class<?> clsEmoji = XposedHelpers.findClass("org.telegram.messenger.Emoji", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(clsEmoji, "getEmojiDrawable", CharSequence.class, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                final CharSequence code = (CharSequence) param.args[0];
                final Drawable emojiDrawable = (Drawable) XposedHelpers.newInstance(clsExEmojiDrawable, code, typeface);

                if (drawImgSize == 0) {
                    drawImgSize = XposedHelpers.getStaticIntField(clsEmoji, "drawImgSize");
                }

                emojiDrawable.setBounds(0, 0, drawImgSize, drawImgSize);

                return emojiDrawable;
            }
        });

        final Class<?> clsMsgController = XposedHelpers.findClass("org.telegram.messenger.MessagesController", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(clsMsgController, "processUpdateArray",
                ArrayList.class, ArrayList.class, ArrayList.class, boolean.class,
                new MessageControllerHook());

        XposedHelpers.findAndHookMethod("org.telegram.messenger.MessagesStorage", lpparam.classLoader, "markMessagesAsDeletedInternal",
                ArrayList.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        //noinspection unchecked
                        final ArrayList<Integer> messages = (ArrayList<Integer>) param.args[0];

                        if (XposedHelpers.getAdditionalInstanceField(messages, "ccc_keep") == null) {
                            return;
                        }

                        final int channelId = (int) param.args[1];
                        final Object database = XposedHelpers.getObjectField(param.thisObject, "database");
                        XposedHelpers.callStaticMethod(clsTelegramWord, "markMessagesAsDeletedInternal", database, messages, channelId);

                        param.setResult(null);
                    }
                });

        final Class<?> clsNotificationCenter = XposedHelpers.findClass("org.telegram.messenger.NotificationCenter", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(clsNotificationCenter, "postNotificationNameInternal",
                int.class, boolean.class, Object[].class, new XC_MethodHook() {
                    private final int messagesDeleted = XposedHelpers.getStaticIntField(clsNotificationCenter, "messagesDeleted");
                    private final int messagesReadContent = XposedHelpers.getStaticIntField(clsNotificationCenter, "messagesReadContent");

                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        final int id = (int) param.args[0];
                        final Object[] args = (Object[]) param.args[2];
                        if (id == messagesDeleted) {
                            //noinspection unchecked
                            ArrayList<Integer> msgIntIds = (ArrayList<Integer>) args[0];

                            if (XposedHelpers.getAdditionalInstanceField(msgIntIds, "ccc_keep") == null) {
                                return;
                            }

                            ArrayList<Long> msgLongIds = new ArrayList<>(msgIntIds.size());

                            int channelId = (int) args[1];
                            if (channelId != 0) {
                                for (Integer msgIntId : msgIntIds) {
                                    msgLongIds.add(msgIntId | ((long) channelId) << 32);
                                }
                            } else {
                                for (Integer msgIntId : msgIntIds) {
                                    msgLongIds.add(Long.valueOf(msgIntId));
                                }
                            }

                            param.args[0] = messagesReadContent;
                            param.args[2] = new Object[] {msgLongIds};
                        }
                    }
                });

        final Class<?> clsChatMessageCell = XposedHelpers.findClass("org.telegram.ui.Cells.ChatMessageCell", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(clsChatMessageCell, "measureTime", "org.telegram.messenger.MessageObject", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final Object messageObject = param.args[0];

                final boolean isDeleteMessage = (boolean) XposedHelpers.callStaticMethod(clsTelegramWord, "isDeleteMessage", messageObject);
                if (isDeleteMessage) {
                    String currentTimeString = (String) XposedHelpers.getObjectField(param.thisObject, "currentTimeString");
                    currentTimeString = currentTimeString.replace("edited", "delete");

                    XposedHelpers.setObjectField(param.thisObject, "currentTimeString", currentTimeString);
                }
            }
        });
    }

    private void initCls(ClassLoader classLoader) throws ClassNotFoundException {
        MessageControllerHook.clsUpdateDeleteMessages = XposedHelpers.findClass("org.telegram.tgnet.TLRPC.TL_updateDeleteMessages", classLoader);
        MessageControllerHook.clsUpdateDeleteChannelMessages = XposedHelpers.findClass("org.telegram.tgnet.TLRPC.TL_updateDeleteChannelMessages", classLoader);

        PathClassLoader loader = new PathClassLoader(MODULE_PATH, classLoader);
        clsExEmojiDrawable = loader.loadClass("com.czbix.telegram.xposed.ExEmojiDrawable");
        clsTelegramWord = loader.loadClass("com.czbix.telegram.xposed.TelegramWorld");
    }
}
