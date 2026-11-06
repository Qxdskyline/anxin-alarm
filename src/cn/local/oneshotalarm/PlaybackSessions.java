package cn.local.oneshotalarm;

import java.util.HashSet;
import java.util.Set;

/** 仅保存进程内正在准备/播放的标识，不持有 Service 或 Activity。 */
final class PlaybackSessions {
    private static final Set<String> sessions = new HashSet<>();

    private static String key(int id, String session) {
        return id + ":" + session;
    }

    static synchronized void add(int id, String session) {
        sessions.add(key(id, session));
    }

    static synchronized void remove(int id, String session) {
        sessions.remove(key(id, session));
    }

    static synchronized boolean contains(int id, String session) {
        return sessions.contains(key(id, session));
    }
}
