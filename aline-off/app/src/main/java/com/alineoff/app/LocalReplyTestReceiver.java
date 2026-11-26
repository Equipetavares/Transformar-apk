package com.alineoff.app;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

public class LocalReplyTestReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null || !"com.alineoff.app.LOCAL_REPLY_TEST".equals(intent.getAction()))return;
        SharedPreferences p=Store.prefs(context);
        String token=p.getString("health_probe_token","");
        if(token.isEmpty() || !p.getBoolean("consent_v2",false) || !token.equals(intent.getStringExtra("probe_token")))return;
        long request=p.getLong("health_test_requested",0);
        if(request<=0 || System.currentTimeMillis()-request>60000)return;
        Bundle b=android.app.RemoteInput.getResultsFromIntent(intent);
        CharSequence received=b==null?null:b.getCharSequence("aline_probe_reply");
        if(("ALINE_TEST_"+token).contentEquals(received==null?"":received)){
            p.edit().putLong("health_probe_verified",System.currentTimeMillis()).remove("health_probe_token").apply();
            Store.status(context,"Teste completo aprovado: texto enviado e recebido pelo receptor local.");
        }else{
            p.edit().putString("health_probe_error","O receptor recebeu uma ação sem o texto esperado.").apply();
        }
    }
}
