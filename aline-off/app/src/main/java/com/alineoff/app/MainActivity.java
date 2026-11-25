package com.alineoff.app;

import android.app.Activity;
import android.app.AlertDialog;
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
    private TextView status, diagnostics;
    private Switch enabled, secondEnabled;
    private EditText first, second;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener=(prefs,key)->{if(key!=null && key.startsWith("last_status")) runOnUiThread(this::refresh);};
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
        TextView brand=text("ALINE OFF  /  1.1",12,GREEN,true); root.addView(brand);
        ImageView mascot=new ImageView(this); mascot.setImageResource(R.drawable.logo); mascot.setContentDescription("Mascote pitbull do Aline off"); root.addView(mascot,new LinearLayout.LayoutParams(dp(76),dp(76)));
        root.addView(text("Seu WhatsApp,\nsempre atendido.",29,INK,true));
        root.addView(text("Respostas automáticas • WhatsApp Messenger",14,MUTED,false));
        LinearLayout access=panel(root); access.addView(text("01   Conexão com Android",18,INK,true));
        status=text("",14,MUTED,false); access.addView(status);
        access.addView(button("Conceder acesso às notificações",this::requestNotificationConsent,false));
        access.addView(text("O Android concede acesso amplo às notificações; o Aline off usa apenas as do WhatsApp Messenger para responder no seu aparelho. Nenhuma mensagem é enviada a servidores do Aline off.",13,MUTED,false));
        LinearLayout rules=panel(root); rules.addView(text("02   Suas mensagens",18,INK,true));
        enabled=new Switch(this); enabled.setText("Ativar respostas automáticas"); enabled.setTextColor(INK); enabled.setChecked(p.getBoolean("enabled",false) && p.getBoolean("consent_v1",false) && hasAccess()); rules.addView(enabled);
        enabled.setOnCheckedChangeListener((v,on)->{
            if(on && (!hasAccess() || !p.getBoolean("consent_v1",false))){v.setChecked(false); Toast.makeText(this,"Conceda acesso às notificações primeiro.",Toast.LENGTH_LONG).show();return;}
            if(on && p.getString("first",Store.DEFAULT_FIRST).trim().isEmpty()){v.setChecked(false);Toast.makeText(this,"Salve uma saudação primeiro.",Toast.LENGTH_SHORT).show();return;}
            p.edit().putBoolean("enabled",on).apply(); refresh();
        });
        rules.addView(text("Mensagem 1 · boas-vindas",15,INK,true));
        rules.addView(text("Uma vez por conversa a cada dia.",13,MUTED,false));
        first=editor(p.getString("first",Store.DEFAULT_FIRST)); rules.addView(first);
        secondEnabled=new Switch(this); secondEnabled.setText("Usar segunda mensagem"); secondEnabled.setTextColor(INK); secondEnabled.setChecked(p.getBoolean("second_enabled",true)); rules.addView(secondEnabled);
        rules.addView(text("Mensagem 2 · após uma nova mensagem",15,INK,true));
        second=editor(p.getString("second",Store.DEFAULT_SECOND)); second.setEnabled(secondEnabled.isChecked()); rules.addView(second);
        secondEnabled.setOnCheckedChangeListener((v,on)->second.setEnabled(on));
        rules.addView(button("Salvar mensagens",this::save,true));
        rules.addView(text("Depois da segunda resposta, não envia mais nada para a conversa até o dia seguinte. Grupos não são atendidos quando identificados.",13,MUTED,false));
        LinearLayout diag=panel(root); diag.addView(text("03   Diagnóstico",18,INK,true));
        diagnostics=text("",14,MUTED,false); diag.addView(diagnostics);
        diag.addView(button("Atualizar status",this::refresh,false));
        diag.addView(button("Configurações de bateria",()->{try{startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}},false));
        diag.addView(text("Instale pelo canal autorizado do Google Play. Se o Android não conceder acesso, a automação permanece desligada. Teste com outro telefone e deixe as notificações do WhatsApp ativadas.",13,MUTED,false));
        LinearLayout privacy=panel(root); privacy.addView(text("04   Privacidade e controle",18,INK,true));
        privacy.addView(button("Política de privacidade",()->new AlertDialog.Builder(this).setTitle("Privacidade").setMessage(PRIVACY).setPositiveButton("Fechar",null).show(),false));
        privacy.addView(button("Revogar consentimento e apagar dados",this::revoke,false));
        root.addView(text("Sem conta • sem anúncios • sem servidor",12,MUTED,false));
        refresh();
    }
    @Override public void onResume(){super.onResume();p.registerOnSharedPreferenceChangeListener(listener);if(!hasAccess() || !p.getBoolean("consent_v1",false)){p.edit().putBoolean("enabled",false).apply();enabled.setChecked(false);}refresh();}
    @Override public void onPause(){p.unregisterOnSharedPreferenceChangeListener(listener);super.onPause();}
    private static final String PRIVACY="O Aline off processa localmente as notificações do WhatsApp Messenger, incluindo o texto e a identificação da conversa, para decidir e enviar as respostas configuradas. O Android pode disponibilizar notificações de outros aplicativos, mas elas são descartadas antes da leitura do conteúdo. Não há acesso à Internet pelo aplicativo, anúncios, SDKs de rastreamento ou transferência de conteúdo a servidores do desenvolvedor. As mensagens configuradas e identificadores derivados, data e etapa das respostas ficam no armazenamento privado do aparelho. O histórico técnico é removido após sete dias nas rotinas de limpeza. A resposta escolhida é encaminhada ao WhatsApp. Você pode desligar a automação ou revogar o consentimento e apagar os dados nesta tela. Também pode revogar a permissão nas configurações do Android. O Aline off não é afiliado à Meta.";
    private void requestNotificationConsent(){
        new AlertDialog.Builder(this).setTitle("Permitir respostas automáticas?")
            .setMessage("O Android pode disponibilizar todas as suas notificações, inclusive dados pessoais. O Aline off ignora outros apps e lê somente notificações do WhatsApp Messenger para enviar as duas respostas que você configurar, mesmo com o aplicativo fechado. Os dados ficam no aparelho. Você pode revogar o acesso a qualquer momento. Deseja continuar?")
            .setNegativeButton("Agora não",null)
            .setPositiveButton("Concordo e continuar",(d,w)->{
                if(p.edit().putBoolean("consent_v1",true).commit()) startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }).show();
    }
    private void revoke(){
        new AlertDialog.Builder(this).setTitle("Apagar dados e desligar?")
            .setMessage("Isso apaga suas mensagens configuradas, o controle diário e os diagnósticos. Revogue também o acesso do Aline off na próxima tela do Android.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Apagar e revogar",(d,w)->{
                p.edit().clear().commit(); Store.state(this).edit().clear().commit();
                enabled.setChecked(false); first.setText(Store.DEFAULT_FIRST); second.setText(Store.DEFAULT_SECOND); secondEnabled.setChecked(true);
                refresh(); startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }).show();
    }
    private boolean hasAccess(){
        String list=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");
        if(list==null)return false;
        ComponentName expected=new ComponentName(this,ReplyListener.class);
        for(String entry:list.split(":")) if(expected.equals(ComponentName.unflattenFromString(entry)))return true;
        return false;
    }
    private void refresh(){
        if(status==null)return;
        status.setText(hasAccess()?"✓ Permissão concedida":"○ Permissão pendente"); status.setTextColor(hasAccess()?GREEN:MUTED);
        long at=p.getLong("last_status_at",0);
        diagnostics.setText((p.getBoolean("enabled",false)?"Automação ligada":"Automação desligada")+"\n"+p.getString("last_status","Nenhum evento registrado.")+(at>0?"\n"+DateFormat.getDateTimeInstance().format(new Date(at)):""));
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
