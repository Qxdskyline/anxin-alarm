package cn.local.oneshotalarm;

import android.content.*;

public final class RestoreReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String action = i.getAction();
        boolean reset =
                Intent.ACTION_TIME_CHANGED.equals(action)
                        || Intent.ACTION_TIMEZONE_CHANGED.equals(action);
        AlarmController.restore(c, reset);
    }
}
