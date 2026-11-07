package com.chatatda.api.auth;

public interface MailPort {

    void send(String to, String subject, String body);
}
