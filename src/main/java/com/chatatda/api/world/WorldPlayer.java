package com.chatatda.api.world;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WorldPlayer {

    public record Pose(double x, double y, double z, double r, String a) {
    }

    private final String id;
    private final String nickname;
    private final String clientKey;
    private final Outbox outbox;
    private volatile Pose pose;
    private volatile long lastSeenMs;
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean lookDirty = new AtomicBoolean();
    /** 검증된 외형 (jacket/hair/skin). 아직 안 보냈으면 null → 받는 쪽이 기본 외형으로 그린다 */
    private volatile Map<String, String> look;

    // 스팸 방지용 (접속 하나당 한 스레드가 읽고 쓰므로 volatile 이면 충분)
    volatile long moveWindowStartMs;
    volatile int moveInWindow;
    volatile long lastChatMs;
    volatile long lastEmoteMs;

    WorldPlayer(String id, String nickname, String clientKey, Outbox outbox, Pose pose, long nowMs) {
        this.id = id;
        this.clientKey = clientKey;
        this.nickname = nickname;
        this.outbox = outbox;
        this.pose = pose;
        this.lastSeenMs = nowMs;
    }

    public String id() {
        return id;
    }

    public String nickname() {
        return nickname;
    }

    String clientKey() {
        return clientKey;
    }

    Outbox outbox() {
        return outbox;
    }

    public Pose pose() {
        return pose;
    }

    void pose(Pose pose) {
        this.pose = pose;
        dirty.set(true);
    }

    long lastSeenMs() {
        return lastSeenMs;
    }

    void touch(long nowMs) {
        this.lastSeenMs = nowMs;
    }

    public Map<String, String> look() {
        return look;
    }

    void look(Map<String, String> look) {
        this.look = look;
        lookDirty.set(true);
    }

    boolean consumeLookDirty() {
        return lookDirty.getAndSet(false);
    }

    boolean consumeDirty() {
        return dirty.getAndSet(false);
    }
}
