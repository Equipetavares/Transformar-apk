package com.alineoff.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReplyListener extends NotificationListenerService {
    private static final String WHATSAPP="com.whatsapp";
    private static final Object LOCK=new Object();
    private static volatile boolean connected=false;
    private static final Map<String,PendingIntent> OPEN_ACTIONS=new LinkedHashMap<String,PendingIntent>(32,0.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,PendingIntent> e){return size()>100;}
    };
    static boolean isConnected(){return connected;}
    static boolean openConversation(String key){
        PendingIntent action;
        synchronized(OPEN_ACTIONS){action=OPEN_ACTIONS.get(key);}
        if(action==null || !WHATSAPP.equals(action.getCreatorPackage()))return false;
        try{action.send();return true;}catch(PendingIntent.CanceledException | SecurityException e){return false;}
    }
    @Override public void onListenerConnected(){
        super.onListenerConnected(); connected=true;
        Store.cleanup(this,System.currentTimeMillis());
        Store.status(this,"Leitor conectado. Aguardando uma nova notificação do WhatsApp.");
    }
    @Override public void onListenerDisconnected(){
        connected=false;super.onListenerDisconnected();
        Store.status(this,"Leitor desconectado. Verifique o acesso especial e reconecte.");
        if(Store.prefs(this).getBoolean("consent_v2",false))requestRebind(new ComponentName(this,ReplyListener.class));
    }
    @Override public void onDestroy(){connected=false;super.onDestroy();}
    @Override public void onNotificationPosted(StatusBarNotification sbn){
        if(sbn==null || !connected)return;
        SharedPreferences p=Store.prefs(this);
        if(!p.getBoolean("consent_v2",false))return;
        if(getPackageName().equals(sbn.getPackageName()) && sbn.getId()==1401 && "aline-local-test".equals(sbn.getTag())){
            if(p.getLong("health_test_requested",0)>0){
                p.edit().putLong("health_test_seen",System.currentTimeMillis()).apply();
                Store.status(this,"Teste local recebido: o leitor está funcionando.");
            }
            return;
        }
        if(!WHATSAPP.equals(sbn.getPackageName()))return;
        p.edit().putLong("health_whatsapp_seen",System.currentTimeMillis()).apply();
        synchronized(LOCK){
            try{process(sbn);}catch(RuntimeException e){Store.status(this,"Falha ao processar notificação: "+e.getClass().getSimpleName());}
        }
    }
    private static String str(CharSequence x){return x==null?"":x.toString().trim();}
    private void skip(String reason){Store.prefs(this).edit().putString("health_last_skip",reason).apply();}
    private void process(StatusBarNotification sbn){
        SharedPreferences p=Store.prefs(this);
        if(!connected || !p.getBoolean("consent_v2",false))return;
        if(!p.getBoolean("enabled",false)){Store.status(this,"WhatsApp recebido, mas automação desligada.");return;}
        long now=System.currentTimeMillis();
        Notification n=sbn.getNotification();
        if(n==null || n.extras==null){skip("Notificação sem conteúdo disponível.");return;}
        if((n.flags&Notification.FLAG_GROUP_SUMMARY)!=0){skip("Resumo de notificações; aguardando a conversa individual.");return;}
        if((n.flags&Notification.FLAG_ONGOING_EVENT)!=0 || Notification.CATEGORY_CALL.equals(n.category)){skip("Chamada ou notificação contínua.");return;}
        if(n.visibility==Notification.VISIBILITY_SECRET){skip("Conteúdo protegido pelo Android.");return;}
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
                if(from==null){skip("Última mensagem é própria ou não tem remetente identificável.");return;}
                sender=str(from.getName());
                String key=str(from.getKey()),uri=str(from.getUri());
                if(me!=null && NotificationPolicy.samePerson(key,uri,sender,str(me.getKey()),str(me.getUri()),str(me.getName()))){skip("Mensagem própria, sem resposta.");return;}
                if(!selfName.isEmpty() && NotificationPolicy.same(sender,selfName)){skip("Mensagem própria.");return;}
                senderId=!key.isEmpty()?key:uri;
                String message=str(m.getText());
                if(!message.isEmpty())body=message;
                else if(m.getDataMimeType()!=null)body="[Anexo recebido]";
                messageAt=m.getTimestamp();
                if(title.isEmpty())title=sender;
            }
        }
        if(NotificationPolicy.isGroup(explicit,conversation,sender,title,shortcut,str(sbn.getTag()))){skip("Grupo ou transmissão excluída.");return;}
        if(title.isEmpty() || title.equalsIgnoreCase("WhatsApp") || NotificationPolicy.same(title,selfName) || body.isEmpty()){skip("Sem remetente ou texto visível.");return;}
        String first=p.getString("first",Store.DEFAULT_FIRST),second=p.getString("second",Store.DEFAULT_SECOND);
        if(body.equals(first.trim()) || body.equals(second.trim())){skip("Eco de uma mensagem automática.");return;}
        long timestamp=NotificationPolicy.messageTime(messageAt,sbn.getPostTime(),now);
        if(now-timestamp>120000L || timestamp>now+60000L || !Store.day(timestamp).equals(Store.day(now))){skip("Mensagem antiga ou data inválida.");return;}
        String identity=!shortcut.isEmpty()?"shortcut:"+shortcut:!senderId.isEmpty()?"sender:"+senderId:"title:"+title;
        String key=Store.hash(sbn.getUser().toString()+"|"+identity);
        String event=Store.hash(identity+"|"+body+"|"+timestamp);
        // Preserve the prior daily state keys; also reject the legacy text-only signature.
        RuleEngine.State previous=Store.read(this,key);
        if(previous!=null && previous.event.equals(Store.hash(identity+"|"+body))){skip("Duplicidade de registro anterior.");return;}
        int stage=RuleEngine.decide(previous,Store.day(now),event,timestamp,now,true,p.getBoolean("second_enabled",true),!first.trim().isEmpty(),!second.trim().isEmpty());
        if(stage==RuleEngine.NONE){skip("Limite diário, duplicidade, segunda resposta desativada ou mensagem anterior.");return;}
        List<NotificationCompat.Action> actions=replyActions(n);
        if(actions.isEmpty()){Store.status(this,"WhatsApp recebido, mas não há ação de resposta autorizada. Confira se a notificação oferece Responder.");return;}
        if(!connected || !p.getBoolean("consent_v2",false) || !p.getBoolean("enabled",false))return;
        String reply=stage==RuleEngine.FIRST?first:second;
        // Reserve the daily stage before dispatch, restoring it only if no action was sent.
        long sentAt=System.currentTimeMillis();
        if(!Store.write(this,key,new RuleEngine.State(Store.day(now),stage,event,sentAt))){Store.status(this,"Falha ao persistir o controle diário.");return;}
        boolean sent=false;
        for(NotificationCompat.Action action:actions){
            try{
                ArrayList<RemoteInput> inputs=new ArrayList<>();
                Bundle data=new Bundle();
                for(RemoteInput input:action.getRemoteInputs())if(input.getAllowFreeFormInput()){
                    inputs.add(input);data.putCharSequence(input.getResultKey(),reply);
                }
                if(inputs.isEmpty())continue;
                Intent fill=new Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                RemoteInput.addResultsToIntent(inputs.toArray(new RemoteInput[0]),fill,data);
                action.actionIntent.send(this,0,fill);
                sent=true;break;
            }catch(PendingIntent.CanceledException e){/* Try another valid reply action from this notification. */}
            catch(SecurityException e){Store.status(this,"O Android recusou a ação de resposta.");break;}
            catch(RuntimeException e){Store.status(this,"Falha de resposta: "+e.getClass().getSimpleName());break;}
        }
        if(!sent){
            Store.write(this,key,previous);
            Store.status(this,"Não foi possível encaminhar a resposta: ação indisponível ou recusada.");return;
        }
        if(n.contentIntent!=null && WHATSAPP.equals(n.contentIntent.getCreatorPackage()))synchronized(OPEN_ACTIONS){OPEN_ACTIONS.put(key,n.contentIntent);}
        p.edit().putLong("health_last_dispatch",sentAt).putString("health_last_skip","").apply();
        if(!Attendance.record(this,key,title,body,reply,stage,sentAt)){Store.status(this,"Resposta encaminhada, mas registro de atendimento falhou.");return;}
        Store.status(this,stage==RuleEngine.FIRST?"Primeira resposta encaminhada ao WhatsApp.":"Segunda resposta encaminhada ao WhatsApp.");
    }
    private List<NotificationCompat.Action> replyActions(Notification n){
        ArrayList<NotificationCompat.Action> all=new ArrayList<>();
        int count=NotificationCompat.getActionCount(n);
        for(int i=0;i<count;i++){
            try{NotificationCompat.Action a=NotificationCompat.getAction(n,i);if(usable(a))all.add(a);}catch(RuntimeException ignored){}
        }
        try{for(NotificationCompat.Action a:new NotificationCompat.WearableExtender(n).getActions())if(usable(a))all.add(a);}catch(RuntimeException ignored){}
        all.sort((a,b)->Integer.compare(score(b),score(a)));
        return all;
    }
    private int score(NotificationCompat.Action a){
        String label=str(a.getTitle()).toLowerCase(Locale.ROOT);
        return a.getSemanticAction()==NotificationCompat.Action.SEMANTIC_ACTION_REPLY?2:label.contains("reply")||label.contains("responder")||label.contains("répondre")?1:0;
    }
    private boolean usable(NotificationCompat.Action a){
        if(a==null || a.actionIntent==null || !WHATSAPP.equals(a.actionIntent.getCreatorPackage()) || a.getRemoteInputs()==null)return false;
        if(a.isAuthenticationRequired())return false;
        if(Build.VERSION.SDK_INT>=31 && a.actionIntent.isImmutable())return false;
        for(RemoteInput r:a.getRemoteInputs())if(r.getAllowFreeFormInput())return true;
        return false;
    }
}
