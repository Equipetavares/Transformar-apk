package com.alineoff.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

final class Attendance {
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("aline_attendance",Context.MODE_PRIVATE);}
    static final class Entry {
        final String key,name,pkg; final long at; final boolean done; final JSONArray messages;
        Entry(String key,JSONObject o){this.key=key;pkg=o.optString("package",WhatsAppApps.MESSENGER);name=o.optString("name","Contato");at=o.optLong("at");done=o.optBoolean("done");JSONArray m=o.optJSONArray("messages");messages=m==null?new JSONArray():m;}
    }
    static String limited(String s){return s.length()>1500?s.substring(0,1500)+"…":s;}
    private static JSONObject read(Context c,String key){
        try{return new JSONObject(prefs(c).getString(key,"{}"));}catch(Exception ex){return new JSONObject();}
    }
    static synchronized boolean record(Context c,String key,String pkg,String name,String incoming,String outgoing,int stage,long now){
        if(!Store.prefs(c).getBoolean("consent_v2",false) || !WhatsAppApps.supported(pkg))return false;
        try{
            JSONObject o=read(c,key);
            JSONArray history=o.optJSONArray("messages");
            if(history==null || o.optBoolean("done"))history=new JSONArray();
            JSONArray next=new JSONArray();
            for(int i=Math.max(0,history.length()-19);i<history.length();i++)next.put(history.getJSONObject(i));
            next.put(new JSONObject().put("incoming",limited(incoming)).put("outgoing",limited(outgoing)).put("stage",stage).put("at",now));
            o.put("package",pkg).put("name",limited(name)).put("at",now).put("done",false).put("messages",next);
            return prefs(c).edit().putString(key,o.toString()).commit();
        }catch(Exception e){return false;}
    }
    static synchronized void mark(Context c,String key,boolean done){
        try{
            JSONObject o=read(c,key);
            if(!o.has("name"))return;
            o.put("done",done).put("closedAt",done?System.currentTimeMillis():0L);
            prefs(c).edit().putString(key,o.toString()).commit();
        }catch(Exception ignored){}
    }
    static synchronized void clear(Context c){prefs(c).edit().clear().commit();}
    static synchronized void cleanup(Context c){
        long threshold=System.currentTimeMillis()-7L*86400000L;
        SharedPreferences.Editor edit=prefs(c).edit();
        for(String key:prefs(c).getAll().keySet()){
            JSONObject o=read(c,key);
            if(o.optBoolean("done") && o.optLong("closedAt")<threshold)edit.remove(key);
        }
        edit.apply();
    }
    static synchronized List<Entry> list(Context c){
        cleanup(c);
        List<Entry> all=new ArrayList<>();
        for(String key:prefs(c).getAll().keySet()){
            JSONObject o=read(c,key);if(o.has("name"))all.add(new Entry(key,o));
        }
        Collections.sort(all,(a,b)->Long.compare(b.at,a.at));
        return all;
    }
}
