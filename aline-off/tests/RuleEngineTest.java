package com.alineoff.app;
public class RuleEngineTest {
    static int count;
    static void eq(String name,int expected,int actual){count++;if(expected!=actual)throw new AssertionError(name+": "+actual+" != "+expected);System.out.println("PASS "+name);}
    static int d(RuleEngine.State s,String day,String event,long incoming,long now,boolean enabled,boolean second){return RuleEngine.decide(s,day,event,incoming,now,enabled,second,true,true);}
    public static void main(String[] args){
        String day="2026-11-25", next="2026-11-26";long t=1000000;
        RuleEngine.State first=new RuleEngine.State(day,1,"a",t);
        RuleEngine.State second=new RuleEngine.State(day,2,"b",t+5000);
        eq("first incoming",1,d(null,day,"a",t-10,t,true,true));
        eq("duplicate callback",0,d(first,day,"a",t+100,t+200,true,true));
        eq("old incoming cannot trigger second",0,d(first,day,"b",t-10,t+200,true,true));
        eq("second incoming",2,d(first,day,"b",t+100,t+200,true,true));
        eq("third incoming",0,d(second,day,"c",t+6000,t+7000,true,true));
        eq("different contact",1,d(null,day,"z",t+100,t+200,true,true));
        eq("disabled",0,d(null,day,"a",t,t,false,true));
        eq("second disabled",0,d(first,day,"b",t+100,t+200,true,false));
        eq("next day resets",1,d(second,next,"new",t+86400000,t+86400001,true,true));
        eq("old duplicate next day",0,d(second,next,"b",t+86400000,t+86400001,true,true));
        eq("stale notification",0,d(null,day,"a",t-120001,t,true,true));
        eq("invalid future timestamp",0,d(null,day,"a",t+60001,t,true,true));
        eq("empty greeting",0,RuleEngine.decide(null,day,"a",t,t,true,true,false,true));
        eq("empty second",0,RuleEngine.decide(first,day,"b",t+100,t+200,true,true,true,false));
        eq("explicit private conversation title",0,NotificationPolicy.isGroup(false,"João","João","João","","")?1:0);
        eq("legacy individual conversation title",0,NotificationPolicy.isGroup(null,"João","João","João","","")?1:0);
        eq("explicit group",1,NotificationPolicy.isGroup(true,"João","João","João","","")?1:0);
        eq("legacy group sender differs",1,NotificationPolicy.isGroup(null,"Equipe","João","Equipe","","")?1:0);
        eq("group jid",1,NotificationPolicy.isGroup(false,"","","","abc@g.us","")?1:0);
        eq("fallback direct notification",0,NotificationPolicy.isGroup(null,"","","João","","")?1:0);
        eq("same own key",1,NotificationPolicy.samePerson("me","","A","me","","B")?1:0);
        eq("different person keys",0,NotificationPolicy.samePerson("a","","João","b","","João")?1:0);
        eq("timestamp from message",10,(int)NotificationPolicy.messageTime(10,20,30));
        eq("timestamp from post",20,(int)NotificationPolicy.messageTime(0,20,30));
        eq("immediate first",1,d(null,day,"instant",t,t,true,true));
        eq("immediate second",2,d(first,day,"b",t+1,t+1,true,true));
        eq("supports messenger",1,WhatsAppApps.supported(WhatsAppApps.MESSENGER)?1:0);
        eq("supports business",1,WhatsAppApps.supported(WhatsAppApps.BUSINESS)?1:0);
        eq("reject unrelated package",0,WhatsAppApps.supported("com.example.other")?1:0);
        eq("messenger only",0,WhatsAppApps.enabled(WhatsAppApps.BUSINESS,true,false)?1:0);
        eq("business only",0,WhatsAppApps.enabled(WhatsAppApps.MESSENGER,false,true)?1:0);
        eq("business enabled",1,WhatsAppApps.enabled(WhatsAppApps.BUSINESS,true,true)?1:0);
        eq("messenger legacy key preserved",1,WhatsAppApps.contactScope(WhatsAppApps.MESSENGER,"u","id").equals("u|id")?1:0);
        eq("account scopes isolated",0,WhatsAppApps.contactScope(WhatsAppApps.MESSENGER,"u","id").equals(WhatsAppApps.contactScope(WhatsAppApps.BUSINESS,"u","id"))?1:0);
        ReceptionGate gate=new ReceptionGate();gate.reset(1000);
        eq("old notification excluded",0,gate.recover("a",999,1100)?1:0);
        eq("new missed callback recovered",1,gate.recover("a",1100,1200)?1:0);
        eq("recovery not repeated",0,gate.recover("a",1100,1300)?1:0);
        gate.observe("b",1200);
        eq("normal callback not replayed",0,gate.recover("b",1200,1300)?1:0);
        eq("new version eligible",1,gate.recover("b",1400,1500)?1:0);
        eq("stale active excluded",0,gate.recover("c",1000,32000)?1:0);
        gate.reset(2000);
        eq("after reenabling no replay",0,gate.recover("a",1100,2100)?1:0);
        System.out.println(count+" tests passed");
    }
}
