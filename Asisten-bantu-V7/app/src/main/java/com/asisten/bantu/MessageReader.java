package com.asisten.bantu;

import android.Manifest;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.Telephony;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Membaca SMS masuk terbaru untuk dibacakan lewat suara. */
public final class MessageReader {
    private MessageReader() {}

    public static String latestSms(Context c, int count) {
        if (!Perms.has(c, Manifest.permission.READ_SMS)) {
            return "Izin membaca SMS belum aktif. Buka Kobra, lalu tekan Izin.";
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        SimpleDateFormat fmt = new SimpleDateFormat("d MMMM, HH:mm", new Locale("id", "ID"));
        try (Cursor cur = c.getContentResolver().query(
                Telephony.Sms.Inbox.CONTENT_URI,
                new String[]{Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE},
                null, null, Telephony.Sms.DATE + " DESC")) {
            while (cur != null && cur.moveToNext() && n < count) {
                String from = nameFor(c, cur.getString(0));
                String body = cur.getString(1) == null ? "" : cur.getString(1).trim();
                if (body.length() > 300) body = body.substring(0, 300) + ". Masih ada lanjutannya.";
                n++;
                sb.append("SMS ").append(n).append(" dari ").append(from)
                        .append(", ").append(fmt.format(new Date(cur.getLong(2))))
                        .append(". ").append(body).append(" ");
            }
        } catch (Exception e) {
            return "SMS tidak bisa dibaca di HP ini.";
        }
        return n == 0 ? "Tidak ada SMS masuk." : sb.toString().trim();
    }

    private static String nameFor(Context c, String number) {
        if (number == null || number.isEmpty()) return "pengirim tidak dikenal";
        if (!Perms.has(c, Manifest.permission.READ_CONTACTS)) return number;
        try (Cursor cur = c.getContentResolver().query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception ignored) { }
        return number;
    }
}
