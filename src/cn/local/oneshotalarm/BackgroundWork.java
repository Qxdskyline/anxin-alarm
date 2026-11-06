package cn.local.oneshotalarm;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** 短任务运行器：I/O 在工作线程执行，界面回调只交给仍存活的页面。 */
final class BackgroundWork {
    static <T> void run(
            Activity owner,
            String name,
            Callable<T> work,
            Consumer<T> success,
            Consumer<Exception> failure,
            Consumer<T> discarded) {
        new Thread(
                        () -> {
                            T result = null;
                            Exception error = null;
                            try {
                                result = work.call();
                            } catch (Exception e) {
                                error = e;
                            }
                            final T value = result;
                            final Exception problem = error;
                            new Handler(Looper.getMainLooper())
                                    .post(
                                            () -> {
                                                if (owner.isDestroyed() || owner.isFinishing()) {
                                                    if (problem == null) discarded.accept(value);
                                                    return;
                                                }
                                                if (problem == null) success.accept(value);
                                                else failure.accept(problem);
                                            });
                        },
                        name)
                .start();
    }
}
