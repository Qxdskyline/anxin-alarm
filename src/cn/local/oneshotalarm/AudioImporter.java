package cn.local.oneshotalarm;

import android.content.Context;
import android.database.Cursor;
import android.media.*;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.*;
import java.util.UUID;

/** 同步导入操作，由 BackgroundWork 在后台调用；失败不会留下半份录音。 */
final class AudioImporter {
    static final class Result {
        final String fileName;
        final String displayName;
        final long durationMs;

        Result(String fileName, String displayName, long durationMs) {
            this.fileName = fileName;
            this.displayName = displayName;
            this.durationMs = durationMs;
        }
    }

    static Result importAudio(Context c, Uri uri) throws Exception {
        String name = "audio-" + UUID.randomUUID();
        File file = new File(c.getFilesDir(), name);
        String displayName = "录音";
        boolean success = false;
        try {
            try (Cursor cursor =
                    c.getContentResolver()
                            .query(
                                    uri,
                                    new String[] {OpenableColumns.DISPLAY_NAME},
                                    null,
                                    null,
                                    null)) {
                if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                    displayName = cursor.getString(0);
                }
            }
            try (InputStream input = c.getContentResolver().openInputStream(uri);
                    FileOutputStream output = new FileOutputStream(file)) {
                BoundedStreams.copy(input, output, 200L * 1024 * 1024, "录音不能超过 200 MB");
                output.getFD().sync();
            }
            MediaPlayer check = new MediaPlayer();
            long duration;
            try {
                check.setDataSource(file.getAbsolutePath());
                check.prepare();
                duration = check.getDuration();
                if (duration <= 0) throw new IOException("录音长度无效");
            } finally {
                check.release();
            }
            success = true;
            return new Result(name, displayName, duration);
        } finally {
            if (!success) file.delete();
        }
    }

    static long readDuration(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return value == null ? 0 : Long.parseLong(value);
        } catch (Exception ignored) {
            return 0;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }
}
