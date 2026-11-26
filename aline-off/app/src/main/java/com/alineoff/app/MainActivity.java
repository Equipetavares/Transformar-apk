package com.alineoff.app;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.app.AlertDialog;
import org.json.JSONObject;
import java.util.List;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import android.text.InputType;
import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends Activity {
    private final int GREEN=Color.rgb(8,127,105), INK=Color.rgb(26,48,43), MUTED=Color.rgb(85,108,101), BG=Color.rgb(241,246,244);
    private SharedPreferences p;
    private TextView status, diagnostics, attendanceCount, permissionDetails;
    private final Handler uiHandler=new Handler(Looper.getMainLooper());
    private LinearLayout attendanceRows;
    private boolean showDone=false;
    private final SharedPreferences.OnSharedPreferenceChangeListener attendanceListener=(prefs,key)->runOnUiThread(this::refreshAttendance);
    private Switch enabled, secondEnabled;
    private EditText first, second;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener=(prefs,key)->{if(key!=null && (key.startsWith("last_status") || key.startsWith("health_"))) runOnUiThread(this::refresh);};
    int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle b){
        super.onCreate(b); p=Store.prefs(this);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20),dp(20),dp(20),dp(24));
        scroll.addView(root); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            root.setPadding(dp(20)+insets.getSystemWindowInsetLeft(),dp(20)+insets.getSystemWindowInsetTop(),dp(20)+insets.getSystemWindowInsetRight(),dp(24)+insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        }); scroll.requestApplyInsets();
        TextView brand=text("ALINE OFF  /  1.5",12,GREEN,true); root.addView(brand);
        ImageView mascot=new ImageView(this); mascot.setImageResource(R.drawable.logo); mascot.setContentDescription("Mascote pitbull do Aline off"); root.addView(mascot,new LinearLayout.LayoutParams(dp(76),dp(76)));
        root.addView(text("Seu WhatsApp,\nsempre atendido.",29,INK,true));
        root.addView(text("Respostas automáticas • WhatsApp Messenger",14,MUTED,false));
        LinearLayout setup=panel(root); setup.addView(text("Configuração e permissões",20,INK,true));
        permissionDetails=text("",14,INK,false);setup.addView(permissionDetails);
        setup.addView(button("1. Autorizar leitura de notificações",this::requestNotificationConsent,true));
        setup.addView(button("2. Permitir avisos do Aline off (opcional)",this::requestOwnNotifications,false));
        setup.addView(button("3. Testar conexão com Android",this::testConnection,false));
        setup.addView(button("Verificar / reconectar serviço",this::reconnect,false));
        setup.addView(button("Salvar e ativar automação",this::activateNow,true));
        setup.addView(text("A leitura de notificações é essencial. Os avisos do Aline off são opcionais e permitem o teste local. Nenhuma permissão de contatos, SMS ou acessibilidade é necessária.",13,MUTED,false));
        LinearLayout attendance=panel(root); attendance.addView(text("Central de Atendimentos",20,INK,true));
        attendanceCount=text("",15,GREEN,true); attendance.addView(attendanceCount);
        attendance.addView(text("Conversas com resposta automática encaminhada. O retorno humano deve ser confirmado por você.",13,MUTED,false));
        Button pendingFilter=button("Ver atendidos / pendentes",()->{showDone=!showDone;refreshAttendance();},false);
        attendance.addView(pendingFilter);
        attendanceRows=new LinearLayout(this); attendanceRows.setOrientation(LinearLayout.VERTICAL);attendance.addView(attendanceRows);
        LinearLayout access=panel(root); access.addView(text("01   Conexão com Android",18,INK,true));
        status=text("",14,MUTED,false); access.addView(status);
        access.addView(button("Conceder acesso às notificações",this::requestNotificationConsent,false));
        access.addView(text("O Android concede acesso amplo às notificações; o Aline off usa apenas as do WhatsApp Messenger para responder no seu aparelho. Nenhuma mensagem é enviada a servidores do Aline off.",13,MUTED,false));
        LinearLayout rules=panel(root); rules.addView(text("02   Suas mensagens",18,INK,true));
        enabled=new Switch(this); enabled.setText("Ativar respostas automáticas"); enabled.setTextColor(INK); enabled.setChecked(p.getBoolean("enabled",false) && p.getBoolean("consent_v2",false) && hasAccess()); rules.addView(enabled);
        enabled.setOnCheckedChangeListener((v,on)->{
            if(on && (!hasAccess() || !p.getBoolean("consent_v2",false))){v.setChecked(false); requestNotificationConsent();return;}
            if(on && p.getString("first",Store.DEFAULT_FIRST).trim().isEmpty()){v.setChecked(false);Toast.makeText(this,"Salve uma saudação primeiro.",Toast.LENGTH_SHORT).show();return;}
            p.edit().putBoolean("enabled",on).apply(); refresh();
        });
        rules.addView(text("Mensagem 1 · boas-vindas",15,INK,true));
        rules.addView(text("Uma vez por conversa a cada dia, sem atraso programado.",13,MUTED,false));
        first=editor(p.getString("first",Store.DEFAULT_FIRST)); rules.addView(first);
        secondEnabled=new Switch(this); secondEnabled.setText("Usar segunda mensagem"); secondEnabled.setTextColor(INK); secondEnabled.setChecked(p.getBoolean("second_enabled",true)); rules.addView(secondEnabled);
        rules.addView(text("Mensagem 2 · após nova mensagem",15,INK,true));
        second=editor(p.getString("second",Store.DEFAULT_SECOND)); second.setEnabled(secondEnabled.isChecked()); rules.addView(second);
        secondEnabled.setOnCheckedChangeListener((v,on)->second.setEnabled(on));
        rules.addView(button("Salvar mensagens",this::save,true));
        rules.addView(text("A resposta é encaminhada imediatamente quando uma notificação elegível chega. Depois da segunda resposta, não envia mais nada para a conversa até o dia seguinte. Grupos não são atendidos quando identificados.",13,MUTED,false));
        LinearLayout diag=panel(root); diag.addView(text("03   Diagnóstico",18,INK,true));
        diagnostics=text("",14,MUTED,false); diag.addView(diagnostics);
        diag.addView(button("Atualizar status",this::refresh,false));
        diag.addView(button("Configurações de bateria",()->{try{startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}},false));
        diag.addView(text("Instale pelo canal autorizado do Google Play. Se o Android não conceder acesso, a automação permanece desligada. Teste com outro telefone, saia da conversa no WhatsApp e confirme que uma notificação com botão Responder aparece. O teste local não envia mensagens reais.",13,MUTED,false));
        LinearLayout privacy=panel(root); privacy.addView(text("04   Privacidade e controle",18,INK,true));
        privacy.addView(button("Política de privacidade",()->new AlertDialog.Builder(this).setTitle("Privacidade").setMessage(PRIVACY).setPositiveButton("Fechar",null).show(),false));
        privacy.addView(button("Revogar consentimento e apagar dados",this::revoke,false));
        root.addView(text("Sem conta • sem anúncios • sem servidor",12,MUTED,false));
        refresh();
    }
    @Override public void onResume(){super.onResume();p.registerOnSharedPreferenceChangeListener(listener);Attendance.prefs(this).registerOnSharedPreferenceChangeListener(attendanceListener);if(!hasAccess() || !p.getBoolean("consent_v2",false)){p.edit().putBoolean("enabled",false).apply();enabled.setChecked(false);}refresh();}
    @Override public void onPause(){p.unregisterOnSharedPreferenceChangeListener(listener);Attendance.prefs(this).unregisterOnSharedPreferenceChangeListener(attendanceListener);uiHandler.removeCallbacksAndMessages(null);super.onPause();}
    private static final String PRIVACY="O Aline off processa localmente as notificações do WhatsApp Messenger, incluindo o texto e a identificação da conversa, para decidir e enviar as respostas configuradas. O Android pode disponibilizar notificações de outros aplicativos, mas elas são descartadas antes da leitura do conteúdo. Não há acesso à Internet pelo aplicativo, anúncios, SDKs de rastreamento ou transferência de conteúdo a servidores do desenvolvedor. As mensagens configuradas e identificadores derivados, data e etapa das respostas ficam no armazenamento privado do aparelho. O histórico técnico é removido após sete dias nas rotinas de limpeza. A central guarda localmente o nome exibido, trechos recebidos e respostas automáticas encaminhadas, com data e etapa, até 20 registros por conversa. Pendências permanecem até atendimento ou exclusão; atendidos são removidos após sete dias nas rotinas de limpeza. A resposta escolhida é encaminhada ao WhatsApp. Você pode desligar a automação ou revogar o consentimento e apagar os dados nesta tela. Também pode revogar a permissão nas configurações do Android. O Aline off não é afiliado à Meta.";
    private void requestNotificationConsent(){
        new AlertDialog.Builder(this).setTitle("Permitir respostas automáticas?")
            .setMessage("O Android pode disponibilizar todas as suas notificações, inclusive dados pessoais. O Aline off ignora outros apps e lê somente notificações do WhatsApp Messenger para enviar as duas respostas que você configurar, mesmo com o aplicativo fechado. Para a central de atendimentos, o app também guarda no aparelho o nome exibido, trechos das mensagens recebidas e respostas encaminhadas até você marcar como atendido ou apagar. Atendimentos concluídos são removidos após sete dias. Você pode revogar o acesso a qualquer momento. Deseja continuar?")
            .setNegativeButton("Agora não",null)
            .setPositiveButton("Concordo e continuar",(d,w)->{
                if(p.edit().putBoolean("consent_v2",true).commit()) openListenerSettings();
            }).show();
    }
    private void revoke(){
        new AlertDialog.Builder(this).setTitle("Apagar dados e desligar?")
            .setMessage("Isso apaga suas mensagens configuradas, o controle diário, os diagnósticos e a central de atendimentos. Revogue também o acesso do Aline off na próxima tela do Android.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Apagar e revogar",(d,w)->{
                p.edit().clear().commit(); Store.state(this).edit().clear().commit(); Attendance.clear(this);
                enabled.setChecked(false); first.setText(Store.DEFAULT_FIRST); second.setText(Store.DEFAULT_SECOND); secondEnabled.setChecked(true);
                refresh(); openListenerSettings();
            }).show();
    }
    private void openListenerSettings(){
        if(Build.VERSION.SDK_INT>=30){
            try{
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,new ComponentName(this,ReplyListener.class).flattenToString()));return;
            }catch(RuntimeException ignored){}
        }
        try{startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}
        catch(RuntimeException ex){new AlertDialog.Builder(this).setTitle("Acesso especial").setMessage("Abra Configurações > Aplicativos > Acesso especial > Acesso às notificações e selecione Aline off. Se o Android bloquear essa autorização, use um canal de instalação autorizado.").setPositiveButton("OK",null).show();}
    }
    private boolean ownNotifications(){
        NotificationManager nm=getSystemService(NotificationManager.class);
        return nm!=null && nm.areNotificationsEnabled() && (Build.VERSION.SDK_INT<33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED);
    }
    private void requestOwnNotifications(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            if(!p.getBoolean("asked_own_notifications",false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)){
                p.edit().putBoolean("asked_own_notifications",true).apply();
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},141);return;
            }
        }
        if(Build.VERSION.SDK_INT>=26){
            try{startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));return;}catch(RuntimeException ignored){}
        }
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);refresh();
    }
    private void activateNow(){
        if(!hasAccess() || !p.getBoolean("consent_v2",false)){requestNotificationConsent();return;}
        String a=first.getText().toString().trim(), b=second.getText().toString().trim();
        if(a.isEmpty() || (secondEnabled.isChecked() && b.isEmpty())){Toast.makeText(this,"Preencha as mensagens ativas.",Toast.LENGTH_LONG).show();return;}
        if(!p.edit().putString("first",a).putString("second",b).putBoolean("second_enabled",secondEnabled.isChecked()).putBoolean("enabled",true).commit()){Toast.makeText(this,"Falha ao salvar.",Toast.LENGTH_SHORT).show();return;}
        enabled.setChecked(true);reconnect();refresh();
    }
    private void reconnect(){
        if(!hasAccess() || !p.getBoolean("consent_v2",false)){requestNotificationConsent();return;}
        if(!ReplyListener.isConnected()){
            NotificationListenerService.requestRebind(new ComponentName(this,ReplyListener.class));
            Store.status(this,"Reconexão solicitada ao Android.");
            uiHandler.postDelayed(this::refresh,1500);
        }else{refresh();Toast.makeText(this,"Serviço conectado.",Toast.LENGTH_SHORT).show();}
    }
    private void testConnection(){
        if(!p.getBoolean("consent_v2",false) || !hasAccess()){requestNotificationConsent();return;}
        if(!ReplyListener.isConnected()){reconnect();Toast.makeText(this,"Serviço ainda não conectado. Verifique o status e teste novamente.",Toast.LENGTH_LONG).show();return;}
        if(!ownNotifications()){requestOwnNotifications();return;}
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel("aline_diagnostic","Teste de conexão",NotificationManager.IMPORTANCE_DEFAULT));
        long now=System.currentTimeMillis();
        p.edit().putLong("health_test_requested",now).putLong("health_test_seen",0).commit();
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"aline_diagnostic"):new Notification.Builder(this);
        try{nm.notify("aline-local-test",1401,b.setSmallIcon(android.R.drawable.stat_notify_chat).setContentTitle("Aline off: teste de conexão").setContentText("Notificação local de diagnóstico, sem enviar mensagem ao WhatsApp.").setAutoCancel(true).build());}
        catch(SecurityException ex){Store.status(this,"O Android não autorizou a notificação de teste.");}
        refresh();uiHandler.postDelayed(this::refresh,6500);
    }
    private boolean hasAccess(){
        ComponentName component=new ComponentName(this,ReplyListener.class);
        if(Build.VERSION.SDK_INT>=27){
            try{NotificationManager nm=getSystemService(NotificationManager.class);if(nm!=null)return nm.isNotificationListenerAccessGranted(component);}catch(RuntimeException ignored){}
        }
        String list=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");
        if(list==null)return false;
        ComponentName expected=new ComponentName(this,ReplyListener.class);
        for(String entry:list.split(":")) if(expected.equals(ComponentName.unflattenFromString(entry)))return true;
        return false;
    }
    private void refresh(){
        if(status==null)return;
        refreshAttendance();
        if(permissionDetails!=null){
            boolean consent=p.getBoolean("consent_v2",false), granted=hasAccess(), connected=ReplyListener.isConnected();
            long requested=p.getLong("health_test_requested",0), seen=p.getLong("health_test_seen",0), wa=p.getLong("health_whatsapp_seen",0);
            String test=requested==0?"Não realizado":seen>=requested?"Recebido com sucesso":System.currentTimeMillis()-requested<6500?"Aguardando notificação":"Não recebido; confira o canal de teste e a conexão";
            permissionDetails.setText("Consentimento: "+(consent?"concedido":"pendente")+"\nLeitura de notificações: "+(granted?"autorizada":"NÃO AUTORIZADA")+"\nServiço do Android: "+(granted&&connected?"conectado":"desconectado")+"\nAvisos próprios (opcional): "+(ownNotifications()?"permitidos":"bloqueados")+"\nWhatsApp Messenger: "+(getPackageManager().getLaunchIntentForPackage("com.whatsapp")!=null?"encontrado":"não encontrado")+"\nTeste local: "+test+"\nÚltimo evento WhatsApp: "+(wa>0?DateFormat.getDateTimeInstance().format(new Date(wa)):"nenhum desde a instalação"));
        }
        if(permissionDetails!=null){long sent=p.getLong("health_last_dispatch",0);permissionDetails.append("\nÚltima ação encaminhada: "+(sent>0?DateFormat.getDateTimeInstance().format(new Date(sent)):"nenhuma")+"\nÚltimo filtro: "+p.getString("health_last_skip","nenhum"));}
        status.setText(hasAccess()?"✓ Permissão concedida":"○ Permissão pendente"); status.setTextColor(hasAccess()?GREEN:MUTED);
        long at=p.getLong("last_status_at",0);
        diagnostics.setText((p.getBoolean("enabled",false)?"Automação ligada":"Automação desligada")+"\n"+p.getString("last_status","Nenhum evento registrado.")+(at>0?"\n"+DateFormat.getDateTimeInstance().format(new Date(at)):""));
    }
    private void refreshAttendance(){
        if(attendanceRows==null)return;
        List<Attendance.Entry> all=Attendance.list(this);
        int pending=0; for(Attendance.Entry e:all)if(!e.done)pending++;
        attendanceCount.setText(pending+" aguardando retorno  •  "+(all.size()-pending)+" atendidos");
        attendanceRows.removeAllViews();
        attendanceRows.addView(text(showDone?"Atendimentos concluídos":"Aguardando retorno humano",14,MUTED,true));
        boolean any=false;
        for(Attendance.Entry e:all){
            if(e.done!=showDone)continue; any=true;
            LinearLayout row=panel(attendanceRows);
            row.addView(text(e.name,16,INK,true));
            row.addView(text(DateFormat.getDateTimeInstance().format(new Date(e.at)),12,MUTED,false));
            JSONObject last=e.messages.optJSONObject(e.messages.length()-1);
            if(last!=null){
                row.addView(text("Recebida: "+last.optString("incoming"),14,INK,false));
                row.addView(text("Resposta automática "+last.optInt("stage")+": "+last.optString("outgoing"),13,GREEN,false));
            }
            row.addView(button("Ver histórico ("+e.messages.length()+")",()->showHistory(e),false));
            row.addView(button("Abrir WhatsApp",()->openWhatsApp(e.key),false));
            row.addView(button(e.done?"Reabrir pendência":"Marcar como atendido",()->{
                Attendance.mark(this,e.key,!e.done);refreshAttendance();
            },!e.done));
        }
        if(!any)attendanceRows.addView(text(showDone?"Nenhum atendimento concluído.":"Nenhum atendimento pendente.",14,MUTED,false));
    }
    private void showHistory(Attendance.Entry e){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<e.messages.length();i++){
            JSONObject m=e.messages.optJSONObject(i);if(m==null)continue;
            b.append(DateFormat.getDateTimeInstance().format(new Date(m.optLong("at")))).append("\nRecebida: ").append(m.optString("incoming")).append("\nAutomática ").append(m.optInt("stage")).append(": ").append(m.optString("outgoing")).append("\n\n");
        }
        new AlertDialog.Builder(this).setTitle(e.name).setMessage(b.toString()).setPositiveButton("Fechar",null).show();
    }
    private void openWhatsApp(String key){
        if(ReplyListener.openConversation(key))return;
        Intent launch=getPackageManager().getLaunchIntentForPackage("com.whatsapp");
        if(launch!=null){startActivity(launch);Toast.makeText(this,"Localize o contato no WhatsApp e confirme o retorno aqui.",Toast.LENGTH_LONG).show();}
        else Toast.makeText(this,"WhatsApp Messenger não encontrado.",Toast.LENGTH_LONG).show();
    }
    private void save(){
        String a=first.getText().toString().trim(), b=second.getText().toString().trim();
        if(a.isEmpty() || (secondEnabled.isChecked() && b.isEmpty())){Toast.makeText(this,"Preencha as mensagens ativas.",Toast.LENGTH_LONG).show();return;}
        if(!p.edit().putString("first",a).putString("second",b).putBoolean("second_enabled",secondEnabled.isChecked()).commit()){Toast.makeText(this,"Falha ao salvar.",Toast.LENGTH_SHORT).show();return;}
        Toast.makeText(this,"Mensagens salvas.",Toast.LENGTH_SHORT).show();refresh();
    }
    private TextView text(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setPadding(0,dp(5),0,dp(5));if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private GradientDrawable shape(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private LinearLayout panel(LinearLayout root){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(dp(16),dp(16),dp(16),dp(16));v.setBackground(shape(Color.WHITE,18,Color.rgb(221,233,228)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(16),0,0);root.addView(v,lp);return v;}
    private EditText editor(String value){EditText e=new EditText(this);e.setText(value);e.setTextSize(16);e.setTextColor(INK);e.setGravity(android.view.Gravity.TOP);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);e.setMinLines(3);e.setMaxLines(8);e.setPadding(dp(12),dp(12),dp(12),dp(12));e.setBackground(shape(BG,12,Color.rgb(221,233,228)));return e;}
    private Button button(String label,Runnable action,boolean primary){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:GREEN);b.setBackground(shape(primary?GREEN:BG,12,0));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));lp.setMargins(0,dp(10),0,dp(4));b.setLayoutParams(lp);return b;}
}
