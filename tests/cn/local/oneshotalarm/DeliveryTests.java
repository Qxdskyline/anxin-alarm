package cn.local.oneshotalarm;
import java.time.*;
import java.nio.file.*;
import android.content.Intent;

public final class DeliveryTests {
    private static int count;
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);count++;System.out.println("PASS "+name);}
    static Alarm saved(AlarmTests.C c)throws Exception{
        Alarm a=new Alarm(AlarmRepository.allocate(c));a.plan.mode="DAILY";a.plan.audio="clip";
        a.plan.durationMs=1000;Files.write(AudioFiles.file(c,a).toPath(),new byte[]{1});
        AlarmController.save(c,a);return AlarmRepository.get(c,a.id);
    }
    public static void main(String[] args)throws Exception{
        AlarmTests.C c=new AlarmTests.C();
        AlarmWakeBridge.acquire(c,1,"first");
        check(c.power.locks.get(0).isHeld(),"broadcast acquires wake lock before service launch");
        check(c.power.locks.get(0).timeout==30000&&!c.power.locks.get(0).referenceCounted,"delivery lock is bounded and non-reference-counted");
        AlarmWakeBridge.acquire(c,2,"second");AlarmWakeBridge.release(1,"first");
        check(!c.power.locks.get(0).isHeld()&&c.power.locks.get(1).isHeld(),"handoff release does not release another alarm");
        AlarmWakeBridge.release(2,"second");AlarmWakeBridge.release(2,"second");
        check(!c.power.locks.get(1).isHeld(),"handoff can be released safely once or twice");
        Alarm a=saved(c);String token=a.schedule.token;long at=a.schedule.nextAt;
        c.am.jobs.clear();c.am.times.clear();AlarmController.initialize(c);Alarm reloaded=AlarmRepository.get(c,a.id);
        check(c.am.jobs.size()==1&&reloaded.schedule.token.equals(token)&&reloaded.schedule.nextAt==at,"reopen rearms future alarm without moving its time or token");
        AlarmController.initialize(c);check(c.am.jobs.size()==1,"rearm is idempotent");
        reloaded.schedule.nextAt=System.currentTimeMillis()-100;AlarmRepository.put(c,reloaded);c.am.jobs.clear();c.am.times.clear();AlarmController.initialize(c);
        check(c.am.jobs.isEmpty()&&AlarmRepository.get(c,a.id).schedule.nextAt==reloaded.schedule.nextAt,"reopen never cancels or shifts a due callback");
        Intent delivery=AlarmScheduler.operation(c,a.id,false,token).intent;
        c.failStart=true;new AlarmReceiver().onReceive(c,delivery);
        check(!c.power.locks.get(c.power.locks.size()-1).isHeld(),"service start failure releases bridge immediately");
        check(AlarmTrace.read(c).contains("启动播放服务失败")&&AlarmTrace.read(c).contains("收到闹钟触发"),"trace distinguishes callback delivery from service failure");
        for(int i=0;i<100;i++)AlarmTrace.record(c,"event "+i);
        check(AlarmTrace.read(c).split("\n").length<=80,"event trail is capped at 80 lines");
        System.out.println("TOTAL "+count+" alarm delivery checks passed");
    }
}
