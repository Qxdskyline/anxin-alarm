package cn.local.oneshotalarm;

import java.time.*;

public final class RecoveryTests {
    private static int count;
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);count++;System.out.println("PASS "+label);}
    static Alarm pastDaily(AlarmTests.C context)throws Exception {
        Alarm alarm=DeliveryTests.saved(context);
        alarm.plan.hour=23;alarm.plan.minute=30;
        alarm.schedule.nextAt=LocalDate.now().minusDays(1).atTime(23,30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        AlarmRepository.put(context,alarm);context.am.jobs.clear();context.am.times.clear();return alarm;
    }
    public static void main(String[] args)throws Exception {
        AlarmTests.C context=new AlarmTests.C();Alarm old=pastDaily(context);
        AlarmController.initialize(context);Alarm fixed=AlarmRepository.get(context,old.id);
        long expected=fixed.plan.nextOccurrence(System.currentTimeMillis(),ZoneId.systemDefault(),AlarmRepository.holidays(context));
        check(fixed.schedule.nextAt==expected&&fixed.schedule.nextAt>System.currentTimeMillis(),"missed daily alarm advances to next valid day after reopen");
        check(context.am.jobs.size()==1,"recovered daily occurrence is actually registered");
        check(fixed.plan.enabled,"daily alarm remains enabled after missing a day");
        check(!fixed.execution.isActive(),"recovery never starts yesterday's recording");
        String token=fixed.schedule.token;long at=fixed.schedule.nextAt;
        AlarmController.initialize(context);fixed=AlarmRepository.get(context,old.id);
        check(fixed.schedule.token.equals(token)&&fixed.schedule.nextAt==at,"reopen twice keeps valid future occurrence stable");
        context=new AlarmTests.C();old=pastDaily(context);old.plan.mode="ONCE";old.plan.date=LocalDate.now().minusDays(1).toString();AlarmRepository.put(context,old);
        AlarmController.initialize(context);fixed=AlarmRepository.get(context,old.id);
        check(!fixed.plan.enabled&&fixed.schedule.nextAt==0&&context.am.jobs.isEmpty(),"expired one-time alarm is ended rather than moved to tomorrow");
        context=new AlarmTests.C();old=pastDaily(context);old.plan.mode="DATES";old.plan.dates.clear();old.plan.dates.add(LocalDate.now().minusDays(1).toString());old.plan.dates.add(LocalDate.now().plusDays(2).toString());AlarmRepository.put(context,old);
        AlarmController.initialize(context);fixed=AlarmRepository.get(context,old.id);
        check(fixed.schedule.nextAt==LocalDate.now().plusDays(2).atTime(23,30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),"selected dates recover to next selected future date only");
        context=new AlarmTests.C();old=pastDaily(context);old.plan.enabled=false;AlarmRepository.put(context,old);AlarmController.initialize(context);
        check(!AlarmRepository.get(context,old.id).plan.enabled&&context.am.jobs.isEmpty(),"disabled alarm is not resurrected by recovery");
        context=new AlarmTests.C();old=pastDaily(context);old.schedule.nextAt=System.currentTimeMillis()-200;String dueToken=old.schedule.token;AlarmRepository.put(context,old);AlarmController.initialize(context);
        check(AlarmRepository.get(context,old.id).schedule.token.equals(dueToken),"a just-due system callback keeps its token during grace period");
        long now=LocalDate.of(2026,10,27).atTime(8,10).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long yesterday=LocalDate.of(2026,10,26).atTime(23,30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        check(!AlarmText.upcoming(yesterday,now,1).contains("1 分钟"),"screenshot case never labels yesterday as one minute away");
        long tonight=LocalDate.of(2026,10,27).atTime(23,30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        check(AlarmText.upcoming(tonight,now,1).contains("今天 23:30"),"recovered same-day alarm is labelled today");
        check(AlarmText.upcoming(now+30000,now,1).contains("1 分钟"),"future sub-minute countdown rounds up normally");
        context=new AlarmTests.C();old=pastDaily(context);old.schedule.nextAt=yesterday;AlarmRepository.put(context,old);
        AlarmController.refresh(context,now,false);fixed=AlarmRepository.get(context,old.id);
        check(fixed.schedule.nextAt==tonight,"explicit 26-to-27 missed-day scenario advances without toggling");
        java.util.List<Alarm> alarms=new java.util.ArrayList<>();
        check(!AlarmProtectionPolicy.needed(true,alarms),"no alarms means no resident protection");
        alarms.add(fixed);
        check(AlarmProtectionPolicy.needed(true,alarms),"enabled daily alarm requires reliable protection");
        check(!AlarmProtectionPolicy.needed(false,alarms),"user can disable reliable mode");
        fixed.plan.enabled=false;
        check(!AlarmProtectionPolicy.needed(true,alarms),"disabling last alarm stops protection");
        fixed.execution.begin();
        check(AlarmProtectionPolicy.needed(true,alarms),"active playback series remains protected after one-shot disables");
        fixed.execution.finish();
        check(!AlarmProtectionPolicy.needed(true,alarms),"finished one-shot frees protection");
        context=new AlarmTests.C();old=pastDaily(context);AlarmController.initialize(context);
        for(int day=0;day<3;day++) {
            Alarm current=AlarmRepository.get(context,old.id);long due=current.schedule.nextAt;
            Alarm accepted=AlarmController.consume(context,current.id,false,current.schedule.token,due);
            check(accepted!=null,"daily occurrence is accepted on day "+(day+1));
            long following=Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).plusDays(1).toInstant().toEpochMilli();
            check(accepted.schedule.nextAt==following&&accepted.plan.enabled,"next day is independently registered before playback on day "+(day+1));
            AlarmController.started(context,accepted.id,accepted.execution.session(),due);
            AlarmController.completed(context,accepted.id,accepted.execution.session(),0);
        }
        System.out.println("TOTAL "+count+" recovery and reliability policy checks passed");
    }
}
