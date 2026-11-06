package cn.local.oneshotalarm;
import android.app.*;
import android.content.*;

public final class DirectDeliveryTests {
    static int count;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);count++;System.out.println("PASS "+message);}
    public static void main(String[] args)throws Exception{
        AlarmTests.C c=new AlarmTests.C();Alarm a=DeliveryTests.saved(c);
        PendingIntent registered=c.am.jobs.values().iterator().next();
        check(registered.foregroundService&&registered.intent.target==PlaybackService.class,"system alarm directly targets a foreground service");
        check(AlarmDelivery.ACTION.equals(registered.intent.getAction()),"service delivery has an explicit alarm action");
        Alarm due=AlarmDelivery.consume(c,registered.intent,a.schedule.nextAt);
        check(due!=null&&due.execution.isPending(),"cold service can consume a scheduled occurrence without a broadcast");
        check(AlarmDelivery.consume(c,registered.intent,a.schedule.nextAt)==null,"duplicate direct callback cannot play again");
        Alarm started=AlarmController.started(c,due.id,due.execution.session(),System.currentTimeMillis());
        check(started!=null,"direct delivery enters existing playback state machine");
        AlarmController.completed(c,due.id,due.execution.session(),0);
        check(AlarmRepository.get(c,a.id).plan.enabled,"direct playback keeps the next daily occurrence");
        c=new AlarmTests.C();a=DeliveryTests.saved(c);a.plan.repeats=1;AlarmController.save(c,a);a=AlarmRepository.get(c,a.id);
        due=AlarmDelivery.consume(c,c.am.jobs.values().iterator().next().intent,a.schedule.nextAt);
        started=AlarmController.started(c,a.id,due.execution.session(),System.currentTimeMillis());
        PendingIntent retry=AlarmScheduler.operation(c,a.id,true,started.execution.retryToken());
        check(retry.foregroundService,"snooze uses the same direct system entry");
        Alarm retryDue=AlarmDelivery.consume(c,retry.intent,started.execution.retryAt());
        check(retryDue!=null&&retryDue.execution.round()==1,"direct snooze increments the round once");
        AlarmController.close(c,a.id,retryDue.execution.session(),"closed");
        check(AlarmDelivery.consume(c,retry.intent,started.execution.retryAt())==null,"X invalidates pending direct snooze");
        c=new AlarmTests.C();a=DeliveryTests.saved(c);
        Intent legacy=new Intent(c,AlarmReceiver.class).setData(android.net.Uri.parse("recordingalarm://alarm/"+a.id+"/regular"));
        PendingIntent old=PendingIntent.getBroadcast(c,0,legacy,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        c.am.jobs.put(old.key,old);c.am.times.put(old.key,a.schedule.nextAt);
        AlarmScheduler.register(c,a,false);
        check(c.am.jobs.size()==1&&!c.am.jobs.containsKey(old.key),"registration removes old broadcast alarms during upgrade");
        AlarmController.delete(c,a.id);
        check(c.am.jobs.isEmpty(),"deletion cancels direct and legacy registrations");
        System.out.println("TOTAL "+count+" direct delivery checks passed");
    }
}
