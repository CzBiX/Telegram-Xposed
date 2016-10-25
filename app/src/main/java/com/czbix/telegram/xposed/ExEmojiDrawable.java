package com.czbix.telegram.xposed;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.support.annotation.Keep;
import android.text.TextPaint;

import org.telegram.messenger.Emoji;

@Keep
public class ExEmojiDrawable extends Emoji.EmojiDrawable {
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private String code;

    public ExEmojiDrawable(CharSequence code, Typeface typeface) {
        super(null);

        this.code = code.toString();
        this.textPaint.setTypeface(typeface);
    }

    @Override
    public void draw(Canvas canvas) {
        final Rect drawRect = getDrawRect();

        textPaint.setTextSize(drawRect.width() * 0.8f);
        canvas.drawText(code, drawRect.left, drawRect.bottom * 0.8f, textPaint);
    }
}
