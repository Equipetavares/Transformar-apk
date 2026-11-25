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
        DelayQueue q=new DelayQueue();
        DelayQueue.Ticket a=q.offer("a","1","n1",1000);
        eq("delay not before 5 seconds",0,q.claim(a,5999)?1:0);
        eq("duplicate does not schedule",0,q.offer("a","1","n1",2000)==null?0:1);
        eq("exact five second delay",1,q.claim(a,6000)?1:0);
        eq("ticket cannot run twice",0,q.claim(a,7000)?1:0);
        DelayQueue.Ticket b=q.offer("a","2","n1",7000);
        DelayQueue.Ticket c=q.offer("a","3","n1",8000);
        eq("superseded ticket invalid",0,q.claim(b,12000)?1:0);
        eq("new message waits five seconds",0,q.claim(c,12999)?1:0);
        eq("new message dispatch",1,q.claim(c,13000)?1:0);
        DelayQueue.Ticket x=q.offer("a","4","n1",14000);
        DelayQueue.Ticket y=q.offer("b","1","n2",14000);
        q.cancelNotification("n1");
        eq("removed notification cancels",0,q.claim(x,19000)?1:0);
        eq("independent contact remains",1,q.claim(y,19000)?1:0);
        DelayQueue.Ticket z=q.offer("c","1","n3",20000);
        q.clear();
        eq("disconnect clears pending",0,q.claim(z,25000)?1:0);
        System.out.println(count+" tests passed");
    }
}
