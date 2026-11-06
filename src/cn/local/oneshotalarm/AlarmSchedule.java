package cn.local.oneshotalarm;

/** 下一次日历触发及其去重标识，与本轮重播独立。 */
final class AlarmSchedule {
    long nextAt;
    String token = "";

    AlarmSchedule copy() {
        AlarmSchedule copy = new AlarmSchedule();
        copy.nextAt = nextAt;
        copy.token = token;
        return copy;
    }
}
