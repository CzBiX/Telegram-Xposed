package com.czbix.telegram.xposed;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.Keep;
import android.text.TextPaint;

import org.telegram.messenger.Emoji;

@Keep
public class ExEmojiDrawable extends Emoji.EmojiDrawable {
    public static TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private CharSequence cs;

    public ExEmojiDrawable(CharSequence cs) {
        super(null);

        this.cs = cs;
    }

    @Override
    public void draw(Canvas canvas) {
        final Rect bounds = getDrawRect();

        textPaint.setTextSize(bounds.width() * 0.82f);
        canvas.drawText(cs, 0, cs.length(), bounds.left, bounds.bottom * 0.82f, textPaint);
    }
}
