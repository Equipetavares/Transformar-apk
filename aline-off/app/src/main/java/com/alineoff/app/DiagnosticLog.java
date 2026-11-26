package com.alineoff.app;
import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class DiagnosticLog {
    static synchronized void add(Context c,String event){
        SharedPreferences p=Store.prefs(c);
        if(!p.getBoolean("consent_v2",false))return;
        String previous=p.getString("health_trace","");
        String entry=new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date())+"  "+event;
        String[] lines=(entry+"\n"+previous).split("\n");
        StringBuilder result=new StringBuilder();
        for(int i=0;i<Math.min(lines.length,30);i++)if(!lines[i].isEmpty())result.append(lines[i]).append('\n');
        p.edit().putString("health_trace",result.toString()).apply();
    }
}
