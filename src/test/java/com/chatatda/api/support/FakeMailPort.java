package com.chatatda.api.support;

import com.chatatda.api.auth.MailPort;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FakeMailPort implements MailPort {

    private static final Pattern CODE = Pattern.compile("\\b\\d{6}\\b");

    private final Map<String, String> lastCodes = new ConcurrentHashMap<>();
    private volatile boolean failNext;
    private volatile int sentCount;

    @Override
    public void send(String to, String subject, String body) {
        if (failNext) {
            failNext = false;
            throw new IllegalStateException("메일 서버 장애");
        }
        Matcher m = CODE.matcher(body);
        if (m.find()) {
            lastCodes.put(to, m.group());
        }
        sentCount++;
    }

    public String lastCode(String email) {
        return lastCodes.get(email);
    }

    public int sentCount() {
        return sentCount;
    }

    public void failNext() {
        failNext = true;
    }

    public void clear() {
        lastCodes.clear();
        failNext = false;
        sentCount = 0;
    }
}
