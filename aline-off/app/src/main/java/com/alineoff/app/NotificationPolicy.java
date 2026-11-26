package com.alineoff.app;

import java.util.Locale;

final class NotificationPolicy {
    static boolean same(String a,String b){return a!=null && b!=null && !a.isEmpty() && a.equalsIgnoreCase(b);}
    static boolean isGroup(Boolean explicit,String conversation,String sender,String title,String shortcut,String tag){
        if(Boolean.TRUE.equals(explicit))return true;
        if(groupId(shortcut)||groupId(tag))return true;
        if(Boolean.FALSE.equals(explicit))return false;
        if(conversation==null || conversation.trim().isEmpty())return false;
        return !same(conversation,sender) && !same(conversation,title) || (!sender.isEmpty() && !same(sender,conversation));
    }
    static boolean groupId(String id){return id!=null && (id.toLowerCase(Locale.ROOT).contains("@g.us") || id.toLowerCase(Locale.ROOT).contains("@broadcast"));}
    static long messageTime(long message,long post,long now){
        // The notification's display 'when' may refer to an older conversation.
        // A real message timestamp takes priority; otherwise use the posting time.
        return message>0?message:post>0?post:now;
    }
    static boolean samePerson(String key,String uri,String name,String myKey,String myUri,String myName){
        if(!key.isEmpty() && !myKey.isEmpty())return key.equals(myKey);
        if(!uri.isEmpty() && !myUri.isEmpty())return uri.equals(myUri);
        return same(name,myName);
    }
}
