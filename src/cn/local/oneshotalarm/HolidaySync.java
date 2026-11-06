package cn.local.oneshotalarm;

import android.app.Activity;

import java.util.concurrent.atomic.AtomicBoolean;

/** 手动更新用例：下载成功才保存并重新安排日历闹钟。 */
final class HolidaySync {
    private static final AtomicBoolean running = new AtomicBoolean();

    static void update(Activity activity, int year, Runnable done) {
        if (!running.compareAndSet(false, true)) {
            Ui.toast(activity, "正在更新，请稍候");
            return;
        }
        Ui.toast(activity, "正在获取 " + year + " 年调休安排…");
        BackgroundWork.run(
                activity,
                "holiday-download",
                () -> {
                    try {
                        return HolidayFiles.download(year);
                    } finally {
                        running.set(false);
                    }
                },
                file -> {
                    try {
                        AlarmController.importHolidays(activity, file);
                        Ui.toast(activity, year + " 年日历已更新");
                        if (done != null) done.run();
                    } catch (Exception e) {
                        Ui.error(activity, e);
                    }
                },
                error -> Ui.toast(activity, "更新未完成：" + error.getMessage() + "。原有日历仍可使用。"),
                ignored -> {});
    }
}
