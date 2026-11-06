package cn.local.oneshotalarm;

import android.content.*;

public final class ControlReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        AlarmController.close(c, i.getIntExtra("id", 0), i.getStringExtra("session"), "已结束本轮提醒");
    }
}
