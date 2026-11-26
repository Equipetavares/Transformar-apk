package com.alineoff.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;


import java.util.LinkedHashMap;
import java.util.List;

import java.util.Map;

public class ReplyListener extends NotificationListenerService {
    private static final Object LOCK=new Object();
    private static volatile boolean connected=false;
    private static volatile ReplyListener instance;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ReceptionGate reception=new ReceptionGate();
    private boolean recoveryEnabled=false;
    private final Runnable recoveryTick=new Runnable(){
        @Override public void run(){
            if(!connected || instance!=ReplyListener.this)return;
            try{scanMessenger(true);}catch(RuntimeException e){DiagnosticLog.add(ReplyListener.this,"Consulta ativa falhou: "+e.getClass().getSimpleName());}
            if(connected && instance==ReplyListener.this)handler.postDelayed(this,4000);
        }
    };
    private boolean messengerEnabled(){
        SharedPreferences p=Store.prefs(this);
        return p.getBoolean("consent_v2",false)&&p.getBoolean("enabled",false)&&p.getBoolean("app_messenger",true);
    }
    private final SharedPreferences.OnSharedPreferenceChangeListener receptionSettings=(p,k)->{
        if("enabled".equals(k)||"app_messenger".equals(k)||"consent_v2".equals(k)){
            recoveryEnabled=messengerEnabled();reception.reset(System.currentTimeMillis());
        }
    };
    static boolean inspectMessenger(){
        ReplyListener r=instance;if(r==null||!connected)return false;
        r.handler.post(()->r.scanMessenger(false));return true;
    }
    private void scanMessenger(boolean recover){
        if(!connected || instance!=this)return;
        SharedPreferences p=Store.prefs(this);
        if(!p.getBoolean("consent_v2",false))return;
        if(recover && !messengerEnabled())return;
        long now=System.currentTimeMillis();
        if(messengerEnabled()!=recoveryEnabled){recoveryEnabled=messengerEnabled();reception.reset(now);}
        StatusBarNotification[] active=getActiveNotifications();
        if(active==null){
            if(!recover)p.edit().putString("health_scan","Android não retornou a lista de notificações.").apply();
            return;
        }
        int messenger=0,business=0,valid=0;
        for(StatusBarNotification sbn:active){
            if(sbn==null)continue;
            String pkg=sbn.getPackageName();
            if(WhatsAppApps.BUSINESS.equals(pkg)){if(!recover)business++;continue;}
            if(!WhatsAppApps.MESSENGER.equals(pkg))continue;
            messenger++;
            Notification n=sbn.getNotification();
            if(n==null || (n.flags&Notification.FLAG_GROUP_SUMMARY)!=0 || (n.flags&Notification.FLAG_ONGOING_EVENT)!=0 || n.visibility==Notification.VISIBILITY_SECRET)continue;
            if(!recover && !ReplyTransport.scan(n,pkg).candidates.isEmpty())valid++;
            if(recover && recoveryEnabled && reception.recover(sbn.getKey(),sbn.getPostTime(),now)){
                countEvent(sbn,true);
                synchronized(LOCK){try{process(sbn);}catch(RuntimeException e){DiagnosticLog.add(this,"Recuperação: "+e.getClass().getSimpleName());}}
            }
        }
        if(!recover){
            String report="Ativas Messenger: "+messenger+" (com resposta: "+valid+"), Business: "+business;
            p.edit().putString("health_scan",report).apply();DiagnosticLog.add(this,report);
        }
    }
    private void countEvent(StatusBarNotification sbn,boolean recovered){
        SharedPreferences p=Store.prefs(this);
        String pkg=sbn.getPackageName(),suffix=WhatsAppApps.BUSINESS.equals(pkg)?"business":"messenger";
        p.edit().putLong("health_whatsapp_seen",System.currentTimeMillis()).putInt("health_wa_count",p.getInt("health_wa_count",0)+1).putInt("health_count_"+suffix,p.getInt("health_count_"+suffix,0)+1).apply();
        if(recovered)p.edit().putInt("health_recovered",p.getInt("health_recovered",0)+1).apply();
        DiagnosticLog.add(this,WhatsAppApps.label(pkg)+(recovered?": evento recuperado da lista ativa.":": evento recebido."));
    }
    private static final Map<String,PendingIntent> OPEN_ACTIONS=new LinkedHashMap<String,PendingIntent>(32,0.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,PendingIntent> e){return size()>100;}
    };
    static boolean isConnected(){return connected;}
    static boolean openConversation(String key,String pkg){
        PendingIntent action;
        synchronized(OPEN_ACTIONS){action=OPEN_ACTIONS.get(key);}
        if(action==null || !WhatsAppApps.supported(pkg) || !pkg.equals(action.getCreatorPackage()))return false;
        try{action.send();return true;}catch(PendingIntent.CanceledException | SecurityException e){return false;}
    }
    @Override public void onListenerConnected(){
        super.onListenerConnected(); connected=true;instance=this;
        reception.reset(System.currentTimeMillis());recoveryEnabled=messengerEnabled();
        Store.prefs(this).registerOnSharedPreferenceChangeListener(receptionSettings);
        handler.removeCallbacks(recoveryTick);handler.postDelayed(recoveryTick,4000);
        DiagnosticLog.add(this,"Serviço conectado pelo Android.");
        Store.cleanup(this,System.currentTimeMillis());
        Store.status(this,"Leitor conectado. Aguardando uma nova notificação do WhatsApp.");
    }
    @Override public void onListenerDisconnected(){
        connected=false;if(instance==this)instance=null;handler.removeCallbacksAndMessages(null);Store.prefs(this).unregisterOnSharedPreferenceChangeListener(receptionSettings);super.onListenerDisconnected();
        DiagnosticLog.add(this,"Serviço desconectado.");
        Store.status(this,"Leitor desconectado. Verifique o acesso especial e reconecte.");
        if(Store.prefs(this).getBoolean("consent_v2",false))requestRebind(new ComponentName(this,ReplyListener.class));
    }
    @Override public void onDestroy(){if(instance==this){connected=false;instance=null;}handler.removeCallbacksAndMessages(null);Store.prefs(this).unregisterOnSharedPreferenceChangeListener(receptionSettings);super.onDestroy();}
    @Override public void onNotificationPosted(StatusBarNotification sbn){
        if(sbn==null || !connected)return;
        SharedPreferences p=Store.prefs(this);
        if(!p.getBoolean("consent_v2",false))return;
        if(getPackageName().equals(sbn.getPackageName()) && sbn.getId()==1401 && "aline-local-test".equals(sbn.getTag())){
            String token=p.getString("health_probe_token","");
            long requested=p.getLong("health_test_requested",0);
            if(!token.isEmpty() && requested>0 && System.currentTimeMillis()-requested<60000 && token.equals(sbn.getNotification().extras.getString("aline_probe_token"))){
                if(p.getLong("health_test_seen",0)>=requested)return;
                p.edit().putLong("health_test_seen",System.currentTimeMillis()).apply();
                ReplyTransport.Scan scan=ReplyTransport.scan(sbn.getNotification(),getPackageName());
                DiagnosticLog.add(this,"Teste local: "+scan.description());
                String result=ReplyTransport.send(this,scan,"ALINE_TEST_"+token);
                p.edit().putString("health_probe_transport",result).apply();
                if(!result.equals("NATIVA")&&!result.equals("COMPAT"))Store.status(this,"Teste local: "+result);
            }
            return;
        }
        String pkg=sbn.getPackageName();
        if(!WhatsAppApps.supported(pkg))return;
        String rawSuffix=WhatsAppApps.BUSINESS.equals(pkg)?"business":"messenger";
        p.edit().putInt("health_raw_"+rawSuffix,p.getInt("health_raw_"+rawSuffix,0)+1).apply();
        if(WhatsAppApps.MESSENGER.equals(pkg))reception.observe(sbn.getKey(),sbn.getPostTime());
        if(!WhatsAppApps.enabled(pkg,p.getBoolean("app_messenger",true),p.getBoolean("app_business",false)))return;
        countEvent(sbn,false);
        synchronized(LOCK){
            try{process(sbn);}catch(RuntimeException e){Store.status(this,"Falha ao processar notificação: "+e.getClass().getSimpleName());}
        }
    }
    private static String str(CharSequence x){return x==null?"":x.toString().trim();}
    private void skip(String reason){Store.prefs(this).edit().putString("health_last_skip",reason).apply();DiagnosticLog.add(this,reason);}
    private void process(StatusBarNotification sbn){
        SharedPreferences p=Store.prefs(this);
        String pkg=sbn.getPackageName();
        if(!connected || !p.getBoolean("consent_v2",false) || !WhatsAppApps.enabled(pkg,p.getBoolean("app_messenger",true),p.getBoolean("app_business",false)))return;
        if(!p.getBoolean("enabled",false)){Store.status(this,"WhatsApp recebido, mas automação desligada.");return;}
        long now=System.currentTimeMillis();
        Notification n=sbn.getNotification();
        if(n==null || n.extras==null){skip("Notificação sem conteúdo disponível.");return;}
        if((n.flags&Notification.FLAG_GROUP_SUMMARY)!=0){skip("Resumo de notificações; aguardando a conversa individual.");return;}
        if((n.flags&Notification.FLAG_ONGOING_EVENT)!=0 || Notification.CATEGORY_CALL.equals(n.category)){skip("Chamada ou notificação contínua.");return;}
        if(n.visibility==Notification.VISIBILITY_SECRET){skip("Conteúdo protegido pelo Android.");return;}
        ReplyTransport.Scan scan=ReplyTransport.scan(n,pkg);
        DiagnosticLog.add(this,scan.description());
        if(scan.candidates.isEmpty()){skip("Notificação sem ação de resposta válida; aguardando nova atualização.");return;}
        Bundle x=n.extras;
        String title=str(x.getCharSequence(Notification.EXTRA_TITLE)),body=str(x.getCharSequence(Notification.EXTRA_TEXT));
        String sender="",senderId="",selfName=str(x.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME));
        String conversation=str(x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
        String shortcut=Build.VERSION.SDK_INT>=26?str(n.getShortcutId()):"";
        long messageAt=0;
        Boolean explicit=x.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION)?x.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION):null;
        NotificationCompat.MessagingStyle style=null;
        try{style=NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n);}catch(RuntimeException ignored){}
        if(style!=null){
            if(conversation.isEmpty())conversation=str(style.getConversationTitle());
            List<NotificationCompat.MessagingStyle.Message> messages=style.getMessages();
            if(!messages.isEmpty()){
                NotificationCompat.MessagingStyle.Message m=messages.get(messages.size()-1);
                Person from=m.getPerson(),me=style.getUser();
                if(from==null){skip("Última mensagem sem remetente: possivelmente própria.");return;}
                sender=str(from.getName());
                String key=str(from.getKey()),uri=str(from.getUri());
                if(me!=null && NotificationPolicy.samePerson(key,uri,sender,str(me.getKey()),str(me.getUri()),str(me.getName()))){skip("Mensagem própria, sem resposta.");return;}
                if(key.isEmpty() && uri.isEmpty() && !selfName.isEmpty() && NotificationPolicy.same(sender,selfName)){skip("Mensagem própria.");return;}
                senderId=!key.isEmpty()?key:uri;
                String message=str(m.getText());
                if(!message.isEmpty())body=message;
                else if(m.getDataMimeType()!=null)body="[Anexo recebido]";
                messageAt=m.getTimestamp();
                if(title.isEmpty())title=sender;
            }
        }
        if(NotificationPolicy.isGroup(explicit,conversation,sender,title,shortcut,str(sbn.getTag()))){skip("Grupo ou transmissão excluída.");return;}
        if(title.isEmpty() || (title.equalsIgnoreCase("WhatsApp") || title.equalsIgnoreCase("WhatsApp Business")) || NotificationPolicy.same(title,selfName) || body.isEmpty()){skip("Sem remetente ou texto visível.");return;}
        String first=p.getString("first",Store.DEFAULT_FIRST),second=p.getString("second",Store.DEFAULT_SECOND);
        if(body.equals(first.trim()) || body.equals(second.trim())){skip("Eco de uma mensagem automática.");return;}
        long timestamp=NotificationPolicy.messageTime(messageAt,sbn.getPostTime(),now);
        if(now-timestamp>120000L || timestamp>now+60000L || !Store.day(timestamp).equals(Store.day(now))){skip("Mensagem antiga ou data inválida.");return;}
        String identity=!shortcut.isEmpty()?"shortcut:"+shortcut:!senderId.isEmpty()?"sender:"+senderId:"title:"+title;
        String key=Store.hash(WhatsAppApps.contactScope(pkg,sbn.getUser().toString(),identity));
        String event=Store.hash(identity+"|"+body+"|"+timestamp);
        // Preserve the prior daily state keys; also reject the legacy text-only signature.
        RuleEngine.State previous=Store.read(this,key);
        if(previous!=null && previous.event.equals(Store.hash(identity+"|"+body))){skip("Duplicidade de registro anterior.");return;}
        int stage=RuleEngine.decide(previous,Store.day(now),event,timestamp,now,true,p.getBoolean("second_enabled",true),!first.trim().isEmpty(),!second.trim().isEmpty());
        if(stage==RuleEngine.NONE){skip(previous!=null && Store.day(now).equals(previous.day) && previous.stage==2?"Limite de duas respostas atingido para este contato.":"Duplicidade, segunda resposta desativada ou mensagem anterior.");return;}
        if(!connected || !p.getBoolean("consent_v2",false) || !p.getBoolean("enabled",false) || !WhatsAppApps.enabled(pkg,p.getBoolean("app_messenger",true),p.getBoolean("app_business",false)))return;
        String reply=stage==RuleEngine.FIRST?first:second;
        // Reserve the daily stage before dispatch, restoring it only if no action was sent.
        long sentAt=System.currentTimeMillis();
        if(!Store.write(this,key,new RuleEngine.State(Store.day(now),stage,event,sentAt))){Store.status(this,"Falha ao persistir o controle diário.");return;}
        String result=ReplyTransport.send(this,scan,reply);
        DiagnosticLog.add(this,"Encaminhamento: "+result+"; etapa "+stage);
        boolean sent=result.equals("NATIVA") || result.equals("COMPAT");
        if(!sent){
            Store.write(this,key,previous);
            Store.status(this,"Não foi possível encaminhar: "+result);return;
        }
        if(n.contentIntent!=null && pkg.equals(n.contentIntent.getCreatorPackage()))synchronized(OPEN_ACTIONS){OPEN_ACTIONS.put(key,n.contentIntent);}
        p.edit().putLong("health_last_dispatch",sentAt).putString("health_last_skip","").apply();
        if(!Attendance.record(this,key,pkg,title,body,reply,stage,sentAt)){Store.status(this,"Resposta encaminhada, mas registro de atendimento falhou.");return;}
        Store.status(this,(stage==RuleEngine.FIRST?"Primeira":"Segunda")+" resposta encaminhada ao "+WhatsAppApps.label(pkg)+".");
    }
}
