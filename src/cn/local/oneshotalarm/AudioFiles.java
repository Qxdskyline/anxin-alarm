package cn.local.oneshotalarm;

import android.content.Context;

import java.io.File;

/** 录音文件归属及引用清理；文件名由导入器生成，不使用原文件名作为路径。 */
final class AudioFiles {
    static File file(Context context, Alarm alarm) {
        return new File(context.getFilesDir(), alarm.plan.audio);
    }

    static boolean available(Context context, AlarmPlan plan) {
        for (AlarmPlan.Track track : plan.tracks()) {
            if (new File(context.getFilesDir(), track.file).isFile()) return true;
        }
        return false;
    }

    static void removeIfUnused(Context context, String name) {
        synchronized (AlarmController.class) {
            if (name.isEmpty()) return;
            for (Alarm alarm : AlarmRepository.all(context)) {
                for (AlarmPlan.Track track : alarm.plan.tracks())
                    if (track.file.equals(name)) return;
            }
            new File(context.getFilesDir(), name).delete();
        }
    }
}
