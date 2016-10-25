package com.czbix.telegram.xposed;

import android.support.annotation.Keep;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;


public class TelegramWorld {
    private static final long YEARS_38 = TimeUnit.DAYS.toSeconds(365 * 38);
    private static final long YEAR_2000;

    static {
        final Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(2000, 0, 1);
        YEAR_2000 = TimeUnit.MILLISECONDS.toSeconds(calendar.getTimeInMillis());
    }

    @Keep
    static void markMessagesAsDeletedInternal(Object db, ArrayList<Integer> messages, int channelId) throws Throwable {
        final SQLiteDatabase database = (SQLiteDatabase) db;

        final SQLitePreparedStatement state = database.executeFast("SELECT data FROM messages WHERE mid = ?");
        final SQLitePreparedStatement updateState = database.executeFast("UPDATE messages SET data = ? WHERE mid = ?");

        for (Integer mid : messages) {
            state.requery();

            final long lmid;
            if (channelId == 0) {
                lmid = mid;
            } else {
                lmid = mid | ((long) channelId) << 32;
            }
            state.bindLong(1, lmid);
            final SQLiteCursor cursor = state.query(new Object[]{});
            if (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null) {
                    final TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();

                    if (message != null) {
                        if (message.media instanceof TLRPC.TL_messageMediaEmpty) {
                            message.message += "♻️";
                        } else {
                            message.media.caption += "♻️";
                        }
                        message.flags |= TLRPC.MESSAGE_FLAG_EDITED;
                        message.edit_date = (int) (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) - YEARS_38);

                        data = new NativeByteBuffer(message.getObjectSize());
                        message.serializeToStream(data);

                        updateState.requery();
                        updateState.bindByteBuffer(1, data);
                        updateState.bindLong(2, lmid);
                        updateState.stepThis();
                    }
                }
            }
        }

        state.dispose();
        updateState.dispose();
    }

    @Keep
    static boolean isDeleteMessage(Object object) {
        final MessageObject messageObject = (MessageObject) object;

        return (messageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_EDITED) != 0
                && (messageObject.messageOwner.edit_date > 0 && messageObject.messageOwner.edit_date < YEAR_2000);
    }
}
