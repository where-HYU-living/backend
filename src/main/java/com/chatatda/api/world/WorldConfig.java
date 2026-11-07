package com.chatatda.api.world;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableScheduling
public class WorldConfig implements WebSocketConfigurer {

    private final WorldSocketHandler handler;
    private final WorldService world;
    private final String[] allowedOrigins;

    public WorldConfig(WorldSocketHandler handler, WorldService world,
                       @Value("${cors.allowed-origins}") String[] allowedOrigins) {
        this.handler = handler;
        this.world = world;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 브라우저의 Origin 이 REST 와 같은 허용 목록에 있을 때만 접속시킨다
        registry.addHandler(handler, "/ws/world").setAllowedOrigins(allowedOrigins);
    }

    @Scheduled(fixedRateString = "${world.tick-ms:100}")
    void tick() {
        world.tick();
    }

    @Scheduled(fixedRate = 20_000)
    void pingAll() {
        world.pingAll();
    }

    @Scheduled(fixedRate = 10_000)
    void reapIdle() {
        world.reapIdle();
    }
}
