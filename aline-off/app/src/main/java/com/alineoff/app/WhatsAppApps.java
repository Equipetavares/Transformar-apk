package com.alineoff.app;

final class WhatsAppApps {
    static final String MESSENGER="com.whatsapp", BUSINESS="com.whatsapp.w4b";
    static boolean supported(String pkg){return MESSENGER.equals(pkg)||BUSINESS.equals(pkg);}
    static boolean enabled(String pkg,boolean messenger,boolean business){
        return MESSENGER.equals(pkg)?messenger:BUSINESS.equals(pkg)&&business;
    }
    static String label(String pkg){return BUSINESS.equals(pkg)?"WhatsApp Business":"WhatsApp Messenger";}
    static String contactScope(String pkg,String user,String identity){
        // Preserve Messenger keys from earlier versions, but isolate Business.
        return (BUSINESS.equals(pkg)?BUSINESS+"|":"")+user+"|"+identity;
    }
}
