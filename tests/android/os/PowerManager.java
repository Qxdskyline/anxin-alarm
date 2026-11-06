package android.os;
import java.util.*;
public final class PowerManager {
    public static final int PARTIAL_WAKE_LOCK=1;
    public final List<WakeLock> locks=new ArrayList<>();
    public WakeLock newWakeLock(int level,String tag){WakeLock lock=new WakeLock();locks.add(lock);return lock;}
    public static final class WakeLock {
        public boolean held;public long timeout;public boolean referenceCounted=true;
        public void setReferenceCounted(boolean value){referenceCounted=value;}
        public void acquire(long timeout){this.timeout=timeout;held=true;}
        public boolean isHeld(){return held;}
        public void release(){held=false;}
    }
}
