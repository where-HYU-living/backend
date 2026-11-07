package com.chatatda.api.world;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("world")
public record WorldProperties(
        @DefaultValue("30") int maxPlayers,
        @DefaultValue("100") long tickMs,
        @DefaultValue("60s") Duration idleTimeout,
        @DefaultValue("24h") Duration inviteTtl,
        /** 같은 IP(학교 와이파이는 공유 IP) 에서 동시에 들어올 수 있는 수. 시연은 한 와이파이 30명이라 정원과 같게 둔다(사실상 끔). 줄이려면 world.max-per-client */
        @DefaultValue("30") int maxPerClient) {
}
