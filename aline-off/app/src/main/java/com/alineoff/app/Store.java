package com.alineoff.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Store {
    static final String DEFAULT_FIRST = "Olá! Obrigado por entrar em contato. Como posso ajudar?";
    static final String DEFAULT_SECOND = "Recebi sua mensagem. Assim que possível, retornarei o atendimento.";
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("aline_settings", Context.MODE_PRIVATE); }
    static SharedPreferences state(Context c) { return c.getSharedPreferences("aline_state", Context.MODE_PRIVATE); }
    static String day(long t) { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(t)); }
    static String hash(String text) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder(); for(byte b:bytes) out.append(String.format(Locale.US,"%02x",b&255)); return out.toString();
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
    static RuleEngine.State read(Context c,String key) {
        SharedPreferences p=state(c); String day=p.getString(key+".day",null);
        return day==null?null:new RuleEngine.State(day,p.getInt(key+".stage",0),p.getString(key+".event",""),p.getLong(key+".time",0));
    }
    static boolean write(Context c,String key,RuleEngine.State s) {
        SharedPreferences.Editor e=state(c).edit();
        if(s==null) e.remove(key+".day").remove(key+".stage").remove(key+".event").remove(key+".time");
        else e.putString(key+".day",s.day).putInt(key+".stage",s.stage).putString(key+".event",s.event).putLong(key+".time",s.sentAt);
        return e.commit();
    }
    static void status(Context c,String message) { prefs(c).edit().putString("last_status",message).putLong("last_status_at",System.currentTimeMillis()).apply(); }
    static void cleanup(Context c,long now) {
        SharedPreferences p=state(c); SharedPreferences.Editor e=p.edit();
        for(String k:p.getAll().keySet()) if(k.endsWith(".time") && p.getLong(k,0)<now-7L*86400000L) {
            String base=k.substring(0,k.length()-5); e.remove(k).remove(base+".day").remove(base+".stage").remove(base+".event");
        } e.apply();
    }
}
