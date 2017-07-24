package com.czbix.telegram.xposed;

import android.content.res.XModuleResources;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.support.annotation.Keep;
import android.text.Spannable;
import android.text.TextPaint;

import dalvik.system.PathClassLoader;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

@Keep
public class Hook implements IXposedHookZygoteInit, IXposedHookLoadPackage {
    private static final String PKG_NAME = "org.telegram.messenger";
    private static final String[] APP_NAMES = {PKG_NAME, "org.telegram.plus"};

    private static String MODULE_PATH;
    private static Class<?> clsExEmojiDrawable;
    private int drawImgSize;

    private boolean isInited;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
       MODULE_PATH = startupParam.modulePath;
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        boolean isTarget = false;
        for (String appName : APP_NAMES) {
            if (appName.equals(lpparam.packageName)) {
                isTarget = true;
                break;
            }
        }
        if (!isTarget) {
            return;
        }

        final ClassLoader classLoader = lpparam.classLoader;

        initCls(classLoader);

        final Class<?> clsEmoji = XposedHelpers.findClass(PKG_NAME + ".Emoji", classLoader);

        hookGetEmoji(clsEmoji);
    }

    private void hookGetEmoji(final Class<?> clsEmoji) {
        XposedHelpers.findAndHookMethod(clsEmoji, "getEmojiDrawable", CharSequence.class, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                final CharSequence code = (CharSequence) param.args[0];
                final Drawable emojiDrawable = (Drawable) XposedHelpers.newInstance(clsExEmojiDrawable, code);

                if (!isInited) {
                    drawImgSize = XposedHelpers.getStaticIntField(clsEmoji, "drawImgSize");

                    final Typeface typeface = Typeface.createFromAsset(
                            XModuleResources.createInstance(MODULE_PATH, null).getAssets(),
                            "NotoColorEmoji.ttf");
                    ((TextPaint) XposedHelpers.getStaticObjectField(clsExEmojiDrawable, "textPaint")).setTypeface(typeface);

                    isInited = true;
                }

                emojiDrawable.setBounds(0, 0, drawImgSize, drawImgSize);

                return emojiDrawable;
            }
        });
    }

    private void initCls(ClassLoader classLoader) throws ClassNotFoundException {
        PathClassLoader loader = new PathClassLoader(MODULE_PATH, classLoader);
        clsExEmojiDrawable = loader.loadClass("com.czbix.telegram.xposed.ExEmojiDrawable");
    }
}
