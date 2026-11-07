package com.chatatda.api.notification;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.CurrentUser;
import com.chatatda.api.common.ErrorCode;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Q1 알림함 (계약 5-10). 알림은 {@link NotificationJob} 이 매일 09:00 KST 에 만든다. */
@RestController
@RequestMapping("/api/v1/me/notifications")
public class NotificationController {

    public record NotificationItem(long id, String type, String title, String body, String targetType, Long targetId,
                                   Instant createdAt, boolean read) {
    }

    private final JdbcTemplate jdbc;

    public NotificationController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<NotificationItem> list(@CurrentUser Long userId) {
        return jdbc.query("""
                select * from notifications where user_id = ? order by created_at desc, id desc limit 50""",
                (rs, i) -> new NotificationItem(rs.getLong("id"), rs.getString("type"), rs.getString("title"),
                        rs.getString("body"), rs.getString("target_type"), rs.getObject("target_id", Long.class),
                        rs.getTimestamp("created_at").toInstant(), rs.getBoolean("read")), userId);
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@CurrentUser Long userId, @PathVariable long id) {
        if (jdbc.update("update notifications set read = true where id = ? and user_id = ?", id, userId) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "알림을 찾을 수 없어요.");
        }
    }
}
