package com.alineoff.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Person;
import android.app.RemoteInput;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.Locale;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class ReplyListener extends NotificationListenerService {
    private static final String WHATSAPP="com.whatsapp";
    private static final Object LOCK=new Object();
    private static final Map<String,PendingIntent> OPEN_ACTIONS=new LinkedHashMap<String,PendingIntent>(32,0.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,PendingIntent> e){return size()>100;}
    };
    static boolean openConversation(String key){
        PendingIntent action;
        synchronized(OPEN_ACTIONS){action=OPEN_ACTIONS.get(key);}
        if(action==null || !WHATSAPP.equals(action.getCreatorPackage()))return false;
        try{action.send();return true;}catch(PendingIntent.CanceledException | SecurityException e){return false;}
    }
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final DelayQueue queue=new DelayQueue();
    private long connectedAt=0;
    private void cancelPending(){queue.clear();handler.removeCallbacksAndMessages(null);}
    @Override public void onDestroy(){synchronized(LOCK){connectedAt=0;cancelPending();}super.onDestroy();}
    @Override public void onNotificationRemoved(StatusBarNotification sbn){
        if(sbn!=null && WHATSAPP.equals(sbn.getPackageName()))synchronized(LOCK){queue.cancelNotification(sbn.getKey());}
    }
    private void dispatchDelayed(DelayQueue.Ticket ticket){
        synchronized(LOCK){
            if(!queue.claim(ticket,SystemClock.uptimeMillis()))return;
            try{
                if(connectedAt==0)return;
                StatusBarNotification[] active=getActiveNotifications(new String[]{ticket.notification});
                if(active!=null)for(StatusBarNotification current:active){
                    if(current!=null && WHATSAPP.equals(current.getPackageName()) && ticket.notification.equals(current.getKey())){
                        process(current,ticket);return;
                    }
                }
                Store.status(this,"Resposta cancelada: a notificação não está mais ativa.");
            }catch(RuntimeException ex){Store.status(this,"Falha ao validar resposta: "+ex.getClass().getSimpleName());}
        }
    }
    @Override public void onListenerConnected() {
        super.onListenerConnected(); synchronized(LOCK){cancelPending();connectedAt=System.currentTimeMillis();}
        Store.cleanup(this,connectedAt); Store.status(this,"Acesso conectado. Aguardando novas mensagens.");
    }
    @Override public void onListenerDisconnected() {
        super.onListenerDisconnected(); synchronized(LOCK){connectedAt=0;cancelPending();} Store.status(this,"Acesso desconectado. Confira as permissões.");
        requestRebind(new ComponentName(this,ReplyListener.class));
    }
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if(sbn==null || !WHATSAPP.equals(sbn.getPackageName())) return;
        synchronized(LOCK) {
            try { process(sbn,null); } catch(RuntimeException e) { Store.status(this,"Notificação ignorada por incompatibilidade: "+e.getClass().getSimpleName()); }
        }
    }
    private static String str(CharSequence s) { return s==null?"":s.toString().trim(); }
    private void process(StatusBarNotification sbn,DelayQueue.Ticket dispatch) {
        SharedPreferences p=Store.prefs(this);
        if(!p.getBoolean("consent_v2",false) || !p.getBoolean("enabled",false) || connectedAt==0) return;
        long now=System.currentTimeMillis();
        if(sbn.getPostTime()<connectedAt || now-sbn.getPostTime()>120000L) return;
        Notification n=sbn.getNotification();
        if(n==null || n.extras==null || (n.flags&Notification.FLAG_GROUP_SUMMARY)!=0 || (n.flags&Notification.FLAG_ONGOING_EVENT)!=0) return;
        Bundle x=n.extras;
        if(n.visibility==Notification.VISIBILITY_SECRET || Notification.CATEGORY_CALL.equals(n.category)) return;
        Store.cleanup(this,now);
        if(x.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION,false) || (!x.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION) && !str(x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)).isEmpty())) return;
        String first=p.getString("first",Store.DEFAULT_FIRST), second=p.getString("second",Store.DEFAULT_SECOND);
        String title=str(x.getCharSequence(Notification.EXTRA_TITLE));
        String body=str(x.getCharSequence(Notification.EXTRA_TEXT));
        String senderId="";
        String selfName=str(x.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME));
        long timestamp=0;
        boolean strongMessage=false;
        Parcelable[] raw=x.getParcelableArray(Notification.EXTRA_MESSAGES);
        if(raw!=null && raw.length>0) {
            Parcelable last=raw[raw.length-1];
            if(!(last instanceof Bundle)) return;
            Bundle m=(Bundle)last;
            body=str(m.getCharSequence("text")); timestamp=m.getLong("time",0);
            String sender=str(m.getCharSequence("sender"));
            if(Build.VERSION.SDK_INT>=28) {
                Person person=m.getParcelable("sender_person");
                Person me=x.getParcelable(Notification.EXTRA_MESSAGING_PERSON);
                if(person!=null) {
                    sender=str(person.getName());
                    String key=str(person.getKey()), uri=str(person.getUri());
                    if(me!=null && ((!key.isEmpty() && key.equals(str(me.getKey()))) || (!uri.isEmpty() && uri.equals(str(me.getUri()))) || person.equals(me))) return;
                    senderId=!key.isEmpty()?key:uri;
                }
            }
            if(sender.isEmpty() || (!selfName.isEmpty() && sender.equalsIgnoreCase(selfName))) return;
            if(title.isEmpty()) title=sender;
            strongMessage=timestamp>0;
        }
        if(title.isEmpty() || title.equalsIgnoreCase("WhatsApp") || (!selfName.isEmpty() && title.equalsIgnoreCase(selfName)) || body.isEmpty() || body.equals(first.trim()) || body.equals(second.trim())) return;
        if(timestamp<=0){timestamp=n.when>0?n.when:sbn.getPostTime();strongMessage=n.when>0;}
        if(!Store.day(timestamp).equals(Store.day(now))) return;
        String shortcut=Build.VERSION.SDK_INT>=26?str(n.getShortcutId()):"";
        String identity=!shortcut.isEmpty()?"shortcut:"+shortcut:!senderId.isEmpty()?"sender:"+senderId:"title:"+title;
        String key=Store.hash(sbn.getUser().toString()+"|"+identity);
        String event=Store.hash(identity+"|"+body+(strongMessage?"|"+timestamp:""));
        if(dispatch!=null && (!dispatch.contact.equals(key) || !dispatch.event.equals(event))){
            Store.status(this,"Resposta cancelada: o conteúdo da notificação mudou.");return;
        }
        RuleEngine.State previous=Store.read(this,key);
        int stage=RuleEngine.decide(previous,Store.day(now),event,timestamp,now,true,p.getBoolean("second_enabled",true),!first.trim().isEmpty(),!second.trim().isEmpty());
        if(stage==RuleEngine.NONE) return;
        Notification.Action action=findReply(n);
        if(action==null) { Store.status(this,"WhatsApp não disponibilizou a ação de resposta rápida."); return; }
        if(dispatch==null){
            DelayQueue.Ticket ticket=queue.offer(key,event,sbn.getKey(),SystemClock.uptimeMillis());
            if(ticket!=null){
                handler.postAtTime(()->dispatchDelayed(ticket),ticket.due);
                Store.status(this,"Resposta automática programada para daqui a 5 segundos.");
            }
            return;
        }
        RemoteInput input=null; for(RemoteInput r:action.getRemoteInputs()) if(r.getAllowFreeFormInput()){input=r;break;}
        if(input==null) return;
        String reply=stage==RuleEngine.FIRST?first:second;
        Intent fill=new Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        Bundle data=new Bundle(); data.putCharSequence(input.getResultKey(),reply);
        RemoteInput.addResultsToIntent(new RemoteInput[]{input},fill,data);
        if(!p.getBoolean("consent_v2",false) || !p.getBoolean("enabled",false)) return;
        if(!Store.write(this,key,new RuleEngine.State(Store.day(now),stage,event,now))) { Store.status(this,"Não foi possível gravar o controle de respostas."); return; }
        try {
            action.actionIntent.send(this,0,fill);
        } catch(PendingIntent.CanceledException | SecurityException e) {
            Store.write(this,key,previous); Store.status(this,"Resposta recusada pelo Android: "+e.getClass().getSimpleName()); return;
        }
        if(n.contentIntent!=null && WHATSAPP.equals(n.contentIntent.getCreatorPackage()))synchronized(OPEN_ACTIONS){OPEN_ACTIONS.put(key,n.contentIntent);}
        if(!Attendance.record(this,key,title,body,reply,stage,now)){
            Store.status(this,"Resposta encaminhada, mas não foi possível gravar a central.");return;
        }
        Store.status(this,stage==RuleEngine.FIRST?"Saudação encaminhada ao WhatsApp.":"Segunda resposta encaminhada ao WhatsApp.");
    }
    private Notification.Action findReply(Notification n) {
        java.util.ArrayList<Notification.Action> candidates=new java.util.ArrayList<>();
        if(n.actions!=null)java.util.Collections.addAll(candidates,n.actions);
        candidates.addAll(new Notification.WearableExtender(n).getActions());
        Notification.Action best=null;int bestScore=-1;
        for(Notification.Action a:candidates){
            if(!usable(a))continue;
            int score=0;
            String label=str(a.title).toLowerCase(Locale.ROOT);
            if(label.contains("reply") || label.contains("responder") || label.contains("répondre") || label.contains("antworten"))score=1;
            if(Build.VERSION.SDK_INT>=28 && a.getSemanticAction()==Notification.Action.SEMANTIC_ACTION_REPLY)score=2;
            if(score>bestScore){best=a;bestScore=score;}
        }
        return best;
    }
    private boolean usable(Notification.Action a) {
        if(a==null || a.actionIntent==null || !WHATSAPP.equals(a.actionIntent.getCreatorPackage()) || a.getRemoteInputs()==null) return false;
        if(Build.VERSION.SDK_INT>=31 && a.isAuthenticationRequired())return false;
        if(Build.VERSION.SDK_INT>=31 && a.actionIntent.isImmutable())return false;
        for(RemoteInput r:a.getRemoteInputs()) if(r.getAllowFreeFormInput()) return true;
        return false;
    }
}
