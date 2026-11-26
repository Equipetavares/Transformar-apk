package com.alineoff.app;

import java.util.LinkedHashMap;
import java.util.Map;

/** Restricts recovery to newly posted notification versions not delivered by a normal callback. */
final class ReceptionGate {
    private final Map<String,Long> seen=new LinkedHashMap<String,Long>(64,0.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Long> e){return size()>512;}
    };
    private long since;
    void reset(long now){seen.clear();since=now;}
    void observe(String key,long posted){Long old=seen.get(key);if(old==null || posted>old)seen.put(key,posted);}
    boolean recover(String key,long posted,long now){
        if(posted<since || posted>now+1000L || now-posted>30000L)return false;
        Long old=seen.get(key);if(old!=null && posted<=old)return false;
        observe(key,posted);return true;
    }
}
