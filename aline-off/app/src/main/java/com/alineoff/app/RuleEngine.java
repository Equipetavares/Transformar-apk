package com.alineoff.app;

/** Pure Java state machine. Day is local calendar date; second stage is optional. */
final class RuleEngine {
    static final int NONE = 0, FIRST = 1, SECOND = 2;
    static final class State {
        final String day, event; final int stage; final long sentAt;
        State(String day, int stage, String event, long sentAt) {
            this.day=day; this.stage=stage; this.event=event; this.sentAt=sentAt;
        }
    }
    static int decide(State s, String day, String event, long incomingAt,
                      long now, boolean enabled, boolean secondEnabled, boolean firstReady, boolean secondReady) {
        if (!enabled || !firstReady || event == null || event.isEmpty() || incomingAt <= 0 || incomingAt > now + 60000L || now-incomingAt > 120000L) return NONE;
        if (s != null && event.equals(s.event)) return NONE;
        if (s == null || !day.equals(s.day)) {
            if (s != null && incomingAt <= s.sentAt) return NONE;
            return FIRST;
        }
        if (s.stage != FIRST || !secondEnabled || !secondReady || incomingAt <= s.sentAt) return NONE;
        return SECOND;
    }
}
