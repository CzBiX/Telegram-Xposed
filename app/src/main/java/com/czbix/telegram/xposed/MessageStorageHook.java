package com.czbix.telegram.xposed;

import android.support.annotation.Keep;
import android.text.TextUtils;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;


public class MessageStorageHook {
    private static final long YEARS_38 = TimeUnit.DAYS.toSeconds(365 * 38);

    @Keep
    static void call(Object db, ArrayList<Integer> messages, int channelId) throws Throwable {
        final SQLiteDatabase database = (SQLiteDatabase) db;
        String ids;
        if (channelId != 0) {
            StringBuilder builder = new StringBuilder(messages.size());
            for (int a = 0; a < messages.size(); a++) {
                long messageId = messages.get(a);
                messageId |= ((long) channelId) << 32;
                if (builder.length() > 0) {
                    builder.append(',');
                }
                builder.append(messageId);
            }
            ids = builder.toString();
        } else {
            ids = TextUtils.join(",", messages);
        }

//        database.beginTransaction();
        final SQLitePreparedStatement state = database.executeFast("SELECT data FROM messages WHERE mid = ?");
        final SQLitePreparedStatement updateState = database.executeFast("UPDATE messages SET data = ? WHERE mid = ?");

        for (Integer mid : messages) {
            state.requery();
            final SQLiteCursor cursor = state.query(new Object[]{mid});
            if (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null) {
                    final TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();

                    if (message != null) {
                        message.media.caption = "♻️" + message.media.caption + "♻️";
                        message.message = "♻️" + message.message + "♻️";
                        message.flags |= TLRPC.MESSAGE_FLAG_EDITED;
                        message.edit_date = (int) (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) - YEARS_38);

                        data = new NativeByteBuffer(message.getObjectSize());
                        message.serializeToStream(data);

                        updateState.requery();
                        updateState.bindByteBuffer(1, data);
                        updateState.bindInteger(2, mid);
                        updateState.stepThis();
                    }
                }
            }
        }

        state.dispose();
        updateState.dispose();

//        database.commitTransaction();
    }
}
