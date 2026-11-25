package com.alineoff.app;

import java.util.HashMap;
import java.util.Map;

/** One delayed candidate per conversation. Uses monotonic time supplied by caller. */
final class DelayQueue {
    static final long DELAY_MS=5000L;
    static final class Ticket {
        final String contact,event,notification;
        final long due;
        Ticket(String c,String e,String n,long now){contact=c;event=e;notification=n;due=now+DELAY_MS;}
    }
    private final Map<String,Ticket> pending=new HashMap<>();
    Ticket offer(String contact,String event,String notification,long now){
        Ticket old=pending.get(contact);
        if(old!=null && old.event.equals(event) && old.notification.equals(notification))return null;
        if(old==null && pending.size()>=100)return null;
        Ticket ticket=new Ticket(contact,event,notification,now);
        pending.put(contact,ticket);return ticket;
    }
    boolean claim(Ticket ticket,long now){
        if(pending.get(ticket.contact)!=ticket || now<ticket.due)return false;
        pending.remove(ticket.contact);return true;
    }
    void cancelNotification(String notification){
        java.util.Iterator<Ticket> it=pending.values().iterator();
        while(it.hasNext())if(it.next().notification.equals(notification))it.remove();
    }
    void clear(){pending.clear();}
}
