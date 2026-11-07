package com.chatatda.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.mail", name = "mode", havingValue = "log", matchIfMissing = true)
public class LoggingMailAdapter implements MailPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailAdapter.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[메일 로그 모드] to={} subject={} body={}", to, subject, body);
    }
}
