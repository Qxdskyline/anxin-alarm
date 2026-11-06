package cn.local.oneshotalarm;

import java.util.UUID;

/** 本轮执行状态。只有这里可以改变阶段，避免 active/pending 的非法组合。 */
final class AlarmExecution {
    enum Phase {
        IDLE,
        QUEUED,
        PLAYING,
        WAITING
    }

    private Phase phase = Phase.IDLE;
    private String session = "";
    private int round;
    private long retryAt;
    private String retryToken = "";

    String session() {
        return session;
    }

    int round() {
        return round;
    }

    long retryAt() {
        return retryAt;
    }

    String retryToken() {
        return retryToken;
    }

    Phase phase() {
        return phase;
    }

    boolean isActive() {
        return phase != Phase.IDLE;
    }

    boolean isPending() {
        return phase == Phase.QUEUED;
    }

    boolean matches(String expectedSession) {
        return isActive() && session.equals(expectedSession);
    }

    void begin() {
        session = UUID.randomUUID().toString();
        round = 0;
        clearRetry();
        phase = Phase.QUEUED;
    }

    void enqueueRetry() {
        clearRetry();
        round++;
        phase = Phase.QUEUED;
    }

    void started(long now, int intervalSeconds, int repeats) {
        if (!isPending()) throw new IllegalStateException("当前执行未在等待播放");
        phase = Phase.PLAYING;
        retryAt = Schedule.repeatAt(now, intervalSeconds, round, repeats);
        retryToken = UUID.randomUUID().toString();
    }

    void completed(long now) {
        phase = retryAt > now ? Phase.WAITING : Phase.IDLE;
    }

    void finish() {
        phase = Phase.IDLE;
        clearRetry();
    }

    void clearRetry() {
        retryAt = 0;
        retryToken = "";
    }

    // 旧格式只有 active/pending，无法区分播放中和等待重播。
    // 恢复流程仍根据进程内播放记录和 retryAt 决定是否结束本轮。
    static AlarmExecution restore(
            String savedPhase,
            boolean active,
            boolean pending,
            String session,
            int round,
            long retryAt,
            String retryToken) {
        AlarmExecution result = new AlarmExecution();
        result.session = session;
        result.round = round;
        result.retryAt = retryAt;
        result.retryToken = retryToken;
        result.phase = !active ? Phase.IDLE : pending ? Phase.QUEUED : Phase.PLAYING;
        if (!savedPhase.isEmpty()) {
            Phase saved = Phase.valueOf(savedPhase);
            if ((saved != Phase.IDLE) != active || (saved == Phase.QUEUED) != pending) {
                throw new IllegalArgumentException("执行阶段与旧状态字段不一致");
            }
            result.phase = saved;
        }
        return result;
    }

    AlarmExecution copy() {
        AlarmExecution copy = new AlarmExecution();
        copy.phase = phase;
        copy.session = session;
        copy.round = round;
        copy.retryAt = retryAt;
        copy.retryToken = retryToken;
        return copy;
    }
}
