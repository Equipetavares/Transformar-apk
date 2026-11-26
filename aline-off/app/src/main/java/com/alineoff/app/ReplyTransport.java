package com.alineoff.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.NotificationCompat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

final class ReplyTransport {
    static final class Candidate {
        final PendingIntent target;
        final android.app.RemoteInput[] nativeInputs;
        final androidx.core.app.RemoteInput[] compatInputs;
        final int score;
        Candidate(PendingIntent p, android.app.RemoteInput[] n, androidx.core.app.RemoteInput[] c,int score){
            target=p;nativeInputs=n;compatInputs=c;this.score=score;
        }
    }
    static final class Scan {
        final List<Candidate> candidates=new ArrayList<>();
        int nativeCount,compatCount,blockedCount,invalidCount;
        String description(){return "Ações nativas: "+nativeCount+" | compatíveis: "+compatCount+" | válidas: "+candidates.size()+" | restritas: "+blockedCount+" | sem texto: "+invalidCount;}
    }
    static Scan scan(Notification n,String owner){
        Scan out=new Scan();
        if(n.actions!=null)for(Notification.Action a:n.actions){out.nativeCount++;addNative(out,a,owner);}
        try{for(Notification.Action a:new Notification.WearableExtender(n).getActions()){out.nativeCount++;addNative(out,a,owner);}}catch(RuntimeException ignored){}
        try{
            for(int i=0;i<NotificationCompat.getActionCount(n);i++){out.compatCount++;addCompat(out,NotificationCompat.getAction(n,i),owner);}
            for(NotificationCompat.Action a:new NotificationCompat.WearableExtender(n).getActions()){out.compatCount++;addCompat(out,a,owner);}
        }catch(RuntimeException ignored){}
        out.candidates.sort((a,b)->Integer.compare(b.score,a.score));
        return out;
    }
    private static boolean targetAllowed(PendingIntent p,String owner,boolean auth){
        return p!=null && owner.equals(p.getCreatorPackage()) && !auth && !(Build.VERSION.SDK_INT>=31 && p.isImmutable());
    }
    private static int score(CharSequence title,boolean semantic,boolean nativeApi){
        String t=title==null?"":title.toString().toLowerCase(Locale.ROOT);
        return (semantic?20:t.contains("reply")||t.contains("responder")||t.contains("répondre")?10:0)+(nativeApi?2:0);
    }
    private static void addNative(Scan out,Notification.Action a,String owner){
        if(a==null)return;
        android.app.RemoteInput[] raw=a.getRemoteInputs();
        if(raw==null || raw.length==0)return;
        if(!targetAllowed(a.actionIntent,owner,Build.VERSION.SDK_INT>=31 && a.isAuthenticationRequired())){out.blockedCount++;return;}
        ArrayList<android.app.RemoteInput> inputs=new ArrayList<>();
        for(android.app.RemoteInput r:raw)if(r!=null && r.getAllowFreeFormInput() && r.getResultKey()!=null)inputs.add(r);
        if(inputs.isEmpty()){out.invalidCount++;return;}
        out.candidates.add(new Candidate(a.actionIntent,inputs.toArray(new android.app.RemoteInput[0]),null,score(a.title,Build.VERSION.SDK_INT>=28 && a.getSemanticAction()==Notification.Action.SEMANTIC_ACTION_REPLY,true)));
    }
    private static void addCompat(Scan out,NotificationCompat.Action a,String owner){
        if(a==null || a.getRemoteInputs()==null || a.getRemoteInputs().length==0)return;
        if(!targetAllowed(a.actionIntent,owner,a.isAuthenticationRequired())){out.blockedCount++;return;}
        ArrayList<androidx.core.app.RemoteInput> inputs=new ArrayList<>();
        for(androidx.core.app.RemoteInput r:a.getRemoteInputs())if(r!=null && r.getAllowFreeFormInput() && r.getResultKey()!=null)inputs.add(r);
        if(inputs.isEmpty()){out.invalidCount++;return;}
        out.candidates.add(new Candidate(a.actionIntent,null,inputs.toArray(new androidx.core.app.RemoteInput[0]),score(a.getTitle(),a.getSemanticAction()==NotificationCompat.Action.SEMANTIC_ACTION_REPLY,false)));
    }
    // A returned PendingIntent means Android accepted dispatch, not that WhatsApp delivered it.
    static String send(Context context,Scan scan,String text){
        HashSet<PendingIntent> canceled=new HashSet<>();
        for(Candidate c:scan.candidates){
            if(canceled.contains(c.target))continue;
            Intent fill=new Intent();
            Bundle data=new Bundle();
            try{
                if(c.nativeInputs!=null){
                    for(android.app.RemoteInput r:c.nativeInputs)data.putCharSequence(r.getResultKey(),text);
                    android.app.RemoteInput.addResultsToIntent(c.nativeInputs,fill,data);
                    if(Build.VERSION.SDK_INT>=28)android.app.RemoteInput.setResultsSource(fill,android.app.RemoteInput.SOURCE_FREE_FORM_INPUT);
                }else{
                    for(androidx.core.app.RemoteInput r:c.compatInputs)data.putCharSequence(r.getResultKey(),text);
                    androidx.core.app.RemoteInput.addResultsToIntent(c.compatInputs,fill,data);
                    androidx.core.app.RemoteInput.setResultsSource(fill,androidx.core.app.RemoteInput.SOURCE_FREE_FORM_INPUT);
                }
            }catch(RuntimeException ex){continue;}
            try{
                c.target.send(context,0,fill);
                return c.nativeInputs!=null?"NATIVA":"COMPAT";
            }catch(PendingIntent.CanceledException ex){canceled.add(c.target);}
            catch(SecurityException ex){return "RECUSADA_PELO_ANDROID";}
            catch(RuntimeException ex){return "ERRO_"+ex.getClass().getSimpleName();}
        }
        return scan.candidates.isEmpty()?"SEM_ACAO_VALIDA":"ACAO_CANCELADA";
    }
}
