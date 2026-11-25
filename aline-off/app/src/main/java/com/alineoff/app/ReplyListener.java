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
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.List;

public class ReplyListener extends NotificationListenerService {
    private static final String WHATSAPP="com.whatsapp";
    private static final Object LOCK=new Object();
    private long connectedAt=0;
    @Override public void onListenerConnected() {
        super.onListenerConnected(); connectedAt=System.currentTimeMillis();
        Store.cleanup(this,connectedAt); Store.status(this,"Acesso conectado. Aguardando novas mensagens.");
    }
    @Override public void onListenerDisconnected() {
        super.onListenerDisconnected(); connectedAt=0; Store.status(this,"Acesso desconectado. Confira as permissões.");
        requestRebind(new ComponentName(this,ReplyListener.class));
    }
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if(sbn==null || !WHATSAPP.equals(sbn.getPackageName())) return;
        synchronized(LOCK) {
            try { process(sbn); } catch(RuntimeException e) { Store.status(this,"Notificação ignorada por incompatibilidade: "+e.getClass().getSimpleName()); }
        }
    }
    private static String str(CharSequence s) { return s==null?"":s.toString().trim(); }
    private void process(StatusBarNotification sbn) {
        SharedPreferences p=Store.prefs(this);
        if(!p.getBoolean("consent_v1",false) || !p.getBoolean("enabled",false) || connectedAt==0) return;
        long now=System.currentTimeMillis();
        if(sbn.getPostTime()<connectedAt || now-sbn.getPostTime()>120000L) return;
        Notification n=sbn.getNotification();
        if(n==null || n.extras==null || (n.flags&Notification.FLAG_GROUP_SUMMARY)!=0 || (n.flags&Notification.FLAG_ONGOING_EVENT)!=0) return;
        Bundle x=n.extras;
        if(n.visibility==Notification.VISIBILITY_SECRET) return;
        Store.cleanup(this,now);
        if(x.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION,false) || (!x.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION) && !str(x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)).isEmpty())) return;
        String first=p.getString("first",Store.DEFAULT_FIRST), second=p.getString("second",Store.DEFAULT_SECOND);
        String title=str(x.getCharSequence(Notification.EXTRA_TITLE));
        String body=str(x.getCharSequence(Notification.EXTRA_TEXT));
        String senderId="";
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
            if(sender.isEmpty()) return;
            if(title.isEmpty()) title=sender;
            strongMessage=timestamp>0;
        }
        if(title.isEmpty() || body.isEmpty() || body.equals(first.trim()) || body.equals(second.trim())) return;
        if(timestamp<=0) timestamp=sbn.getPostTime();
        if(!Store.day(timestamp).equals(Store.day(now))) return;
        String shortcut=Build.VERSION.SDK_INT>=26?str(n.getShortcutId()):"";
        String identity=!shortcut.isEmpty()?"shortcut:"+shortcut:!senderId.isEmpty()?"sender:"+senderId:"title:"+title;
        String key=Store.hash(sbn.getUser().toString()+"|"+identity);
        String event=Store.hash(identity+"|"+body+(strongMessage?"|"+timestamp:""));
        RuleEngine.State previous=Store.read(this,key);
        int stage=RuleEngine.decide(previous,Store.day(now),event,timestamp,now,true,p.getBoolean("second_enabled",true),!first.trim().isEmpty(),!second.trim().isEmpty());
        if(stage==RuleEngine.NONE) return;
        Notification.Action action=findReply(n);
        if(action==null) { Store.status(this,"WhatsApp não disponibilizou a ação de resposta rápida."); return; }
        RemoteInput input=null; for(RemoteInput r:action.getRemoteInputs()) if(r.getAllowFreeFormInput()){input=r;break;}
        if(input==null) return;
        String reply=stage==RuleEngine.FIRST?first:second;
        Intent fill=new Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        Bundle data=new Bundle(); data.putCharSequence(input.getResultKey(),reply);
        RemoteInput.addResultsToIntent(new RemoteInput[]{input},fill,data);
        if(!p.getBoolean("consent_v1",false) || !p.getBoolean("enabled",false)) return;
        if(!Store.write(this,key,new RuleEngine.State(Store.day(now),stage,event,now))) { Store.status(this,"Não foi possível gravar o controle de respostas."); return; }
        try {
            action.actionIntent.send(this,0,fill);
        } catch(PendingIntent.CanceledException | SecurityException e) {
            Store.write(this,key,previous); Store.status(this,"Resposta recusada pelo Android: "+e.getClass().getSimpleName()); return;
        }
        Store.status(this,stage==RuleEngine.FIRST?"Saudação encaminhada ao WhatsApp.":"Segunda resposta encaminhada ao WhatsApp.");
    }
    private Notification.Action findReply(Notification n) {
        Notification.Action first=null;
        if(n.actions!=null) for(Notification.Action a:n.actions) {
            if(!usable(a)) continue;
            if(Build.VERSION.SDK_INT>=28 && a.getSemanticAction()==Notification.Action.SEMANTIC_ACTION_REPLY) return a;
            if(first==null) first=a;
        }
        if(first!=null) return first;
        List<Notification.Action> wearable=new Notification.WearableExtender(n).getActions();
        for(Notification.Action a:wearable) if(usable(a)) return a;
        return null;
    }
    private boolean usable(Notification.Action a) {
        if(a==null || a.actionIntent==null || !WHATSAPP.equals(a.actionIntent.getCreatorPackage()) || a.getRemoteInputs()==null) return false;
        for(RemoteInput r:a.getRemoteInputs()) if(r.getAllowFreeFormInput()) return true;
        return false;
    }
}
