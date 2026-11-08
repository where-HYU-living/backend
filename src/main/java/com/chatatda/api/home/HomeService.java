package com.chatatda.api.home;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import com.chatatda.api.pricing.Grade;
import com.chatatda.api.pricing.PriceCheckService;
import com.chatatda.api.pricing.PriceInput;
import com.chatatda.api.pricing.PriceResult;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 집 기록: 집 1채·월세 납부·공과금·만료 알림 설정·월간 리포트 (계약 5-9) */
@Service
public class HomeService {

    static final String MONTH = "^\\d{4}-(0[1-9]|1[0-2])$";

    public record HomeDto(@NotBlank @Size(max = 300) String address, @NotBlank @Size(max = 50) String dong,
                          @NotNull @DecimalMin("33") @DecimalMax("39") Double lat, @NotNull @DecimalMin("124") @DecimalMax("132") Double lng, @NotNull @PositiveOrZero Long deposit,
                          @NotNull @PositiveOrZero Long rent, @PositiveOrZero Long mgmtFee,
                          @NotNull @Positive Double areaM2, @NotNull LocalDate contractStart,
                          @NotNull LocalDate contractEnd) {
    }

    public record Payment(@NotNull @Pattern(regexp = MONTH) String month, @NotNull @PositiveOrZero Long amount,
                          @NotNull LocalDate paidAt) {
    }

    public record Utility(@NotNull @Pattern(regexp = MONTH) String month, @PositiveOrZero Long electricity,
                          @PositiveOrZero Long gas, @PositiveOrZero Long water, @PositiveOrZero Long internet) {
    }

    public record Alert(long id, int daysBefore, boolean enabled) {
    }

    public record Breakdown(long rent, Long mgmtFee, Long electricity, Long gas, Long water, Long internet) {
    }

    public record Increase(String item, long diff) {
    }

    public record Excess(long monthly, long cumulative, Grade grade) {
    }

    public record Report(String month, long total, Breakdown breakdown, Long prevTotal, Long diff, Increase topIncrease,
                         Excess excess) {
    }

    private final JdbcTemplate jdbc;
    private final PriceCheckService priceCheck;
    private final Clock clock;

    public HomeService(JdbcTemplate jdbc, PriceCheckService priceCheck, Clock clock) {
        this.jdbc = jdbc;
        this.priceCheck = priceCheck;
        this.clock = clock;
    }

    public HomeDto home(long userId) {
        return findHome(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "등록한 내 집이 없어요."));
    }

    Optional<HomeDto> findHome(long userId) {
        return jdbc.query("select * from my_homes where user_id = ?", (rs, i) -> new HomeDto(rs.getString("address"),
                rs.getString("dong"), rs.getDouble("lat"), rs.getDouble("lng"), rs.getLong("deposit"), rs.getLong("rent"),
                (Long) rs.getObject("mgmt_fee", Long.class), rs.getDouble("area_m2"),
                rs.getDate("contract_start").toLocalDate(), rs.getDate("contract_end").toLocalDate()), userId)
                .stream().findFirst();
    }

    @Transactional
    public HomeDto saveHome(long userId, HomeDto b) {
        if (b.contractEnd().isBefore(b.contractStart())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않아요.",
                    Map.of("contractEnd", "계약 시작일 이후여야 해요."));
        }
        jdbc.update("""
                insert into my_homes (user_id, address, dong, lat, lng, deposit, rent, mgmt_fee, area_m2, contract_start,
                                      contract_end, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (user_id) do update set address = excluded.address, dong = excluded.dong, lat = excluded.lat,
                    lng = excluded.lng, deposit = excluded.deposit, rent = excluded.rent, mgmt_fee = excluded.mgmt_fee,
                    area_m2 = excluded.area_m2, contract_start = excluded.contract_start,
                    contract_end = excluded.contract_end, updated_at = excluded.updated_at""",
                userId, b.address().strip(), b.dong().strip(), b.lat(), b.lng(), b.deposit(), b.rent(), b.mgmtFee(),
                b.areaM2(), Date.valueOf(b.contractStart()), Date.valueOf(b.contractEnd()), Timestamp.from(clock.instant()));
        return home(userId);
    }

    public List<Payment> payments(long userId) {
        return jdbc.query("select month, amount, paid_at from home_payments where user_id = ? order by month desc",
                (rs, i) -> new Payment(rs.getString("month"), rs.getLong("amount"), rs.getDate("paid_at").toLocalDate()),
                userId);
    }

    public Payment savePayment(long userId, Payment p) {
        jdbc.update("""
                insert into home_payments (user_id, month, amount, paid_at) values (?, ?, ?, ?)
                on conflict (user_id, month) do update set amount = excluded.amount, paid_at = excluded.paid_at""",
                userId, p.month(), p.amount(), Date.valueOf(p.paidAt()));
        return p;
    }

    public List<Utility> utilities(long userId) {
        return jdbc.query("select * from home_utilities where user_id = ? order by month desc", (rs, i) -> utility(rs),
                userId);
    }

    private static Utility utility(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Utility(rs.getString("month"), rs.getObject("electricity", Long.class), rs.getObject("gas", Long.class),
                rs.getObject("water", Long.class), rs.getObject("internet", Long.class));
    }

    public Utility saveUtility(long userId, Utility u) {
        jdbc.update("""
                insert into home_utilities (user_id, month, electricity, gas, water, internet) values (?, ?, ?, ?, ?, ?)
                on conflict (user_id, month) do update set electricity = excluded.electricity, gas = excluded.gas,
                    water = excluded.water, internet = excluded.internet""",
                userId, u.month(), u.electricity(), u.gas(), u.water(), u.internet());
        return u;
    }

    public List<Alert> alerts(long userId) {
        return jdbc.query("select id, days_before, enabled from home_alerts where user_id = ? order by days_before desc, id",
                (rs, i) -> new Alert(rs.getLong("id"), rs.getInt("days_before"), rs.getBoolean("enabled")), userId);
    }

    public Alert addAlert(long userId, int daysBefore, boolean enabled) {
        long id = jdbc.queryForObject(
                "insert into home_alerts (user_id, days_before, enabled) values (?, ?, ?) "
                        + "on conflict (user_id, days_before) do update set enabled = excluded.enabled returning id", Long.class,
                userId, daysBefore, enabled);
        return new Alert(id, daysBefore, enabled);
    }

    public Alert setAlert(long userId, long id, boolean enabled) {
        if (jdbc.update("update home_alerts set enabled = ? where id = ? and user_id = ?", enabled, id, userId) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "알림 설정을 찾을 수 없어요.");
        }
        return jdbc.queryForObject("select id, days_before, enabled from home_alerts where id = ?",
                (rs, i) -> new Alert(rs.getLong("id"), rs.getInt("days_before"), rs.getBoolean("enabled")), id);
    }

    public void deleteAlert(long userId, long id) {
        jdbc.update("delete from home_alerts where id = ? and user_id = ?", id, userId);
    }

    /** 월간 리포트: 총 주거비(월세는 납부 기록, 없으면 계약 월세) · 지난달 대비 · 적정가 대비 초과액 */
    public Report report(long userId, String month) {
        YearMonth ym = parseMonth(month);
        HomeDto h = home(userId);
        Breakdown cur = breakdown(userId, h, ym);
        long total = sum(cur);
        Long prevTotal = null, diff = null;
        Increase top = null;
        YearMonth prevYm = ym.minusMonths(1);
        if (hasRecord(userId, prevYm)) {
            Breakdown prev = breakdown(userId, h, prevYm);
            prevTotal = sum(prev);
            diff = total - prevTotal;
            top = topIncrease(prev, cur);
        }
        int months = h.contractStart() == null ? 24
                : (int) Math.max(1, ChronoUnit.MONTHS.between(YearMonth.from(h.contractStart()), YearMonth.from(h.contractEnd())));
        PriceResult r = priceCheck.evaluate(new PriceInput(h.deposit(), h.rent(), h.mgmtFee(), h.areaM2(), h.dong(), months));
        Excess excess = null;
        if (r.grade() != Grade.PENDING && r.monthlyExcess() != null) {
            // 지난 달 수는 계약 기간 안으로 자른다 (계약 끝난 뒤 달을 골라도 계약 기간 전체 초과액보다 커지지 않게, 입주 전이면 0)
            long elapsed = Math.min(months, Math.max(0, ChronoUnit.MONTHS.between(YearMonth.from(h.contractStart()), ym) + 1));
            excess = new Excess(r.monthlyExcess(), r.monthlyExcess() * elapsed, r.grade());
        }
        return new Report(ym.toString(), total, cur, prevTotal, diff, top, excess);
    }

    private static YearMonth parseMonth(String month) {
        try {
            if (month != null && month.matches(MONTH)) {
                return YearMonth.parse(month);
            }
        } catch (DateTimeParseException ignored) {
            // 아래에서 같은 오류로 처리
        }
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않아요.", Map.of("month", "YYYY-MM 형식이어야 해요."));
    }

    private boolean hasRecord(long userId, YearMonth ym) {
        Integer n = jdbc.queryForObject("""
                select (select count(*) from home_payments where user_id = ? and month = ?)
                     + (select count(*) from home_utilities where user_id = ? and month = ?)""", Integer.class,
                userId, ym.toString(), userId, ym.toString());
        return n != null && n > 0;
    }

    private Breakdown breakdown(long userId, HomeDto h, YearMonth ym) {
        Long paid = jdbc.query("select amount from home_payments where user_id = ? and month = ?",
                (rs, i) -> rs.getLong(1), userId, ym.toString()).stream().findFirst().orElse(null);
        Utility u = jdbc.query("select * from home_utilities where user_id = ? and month = ?", (rs, i) -> utility(rs),
                userId, ym.toString()).stream().findFirst().orElse(new Utility(ym.toString(), null, null, null, null));
        return new Breakdown(paid != null ? paid : h.rent(), h.mgmtFee(), u.electricity(), u.gas(), u.water(), u.internet());
    }

    private static long sum(Breakdown b) {
        return b.rent() + nz(b.mgmtFee()) + nz(b.electricity()) + nz(b.gas()) + nz(b.water()) + nz(b.internet());
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }

    private static Increase topIncrease(Breakdown prev, Breakdown cur) {
        Map<String, Long> d = new LinkedHashMap<>();
        d.put("rent", cur.rent() - prev.rent());
        d.put("mgmtFee", nz(cur.mgmtFee()) - nz(prev.mgmtFee()));
        d.put("electricity", nz(cur.electricity()) - nz(prev.electricity()));
        d.put("gas", nz(cur.gas()) - nz(prev.gas()));
        d.put("water", nz(cur.water()) - nz(prev.water()));
        d.put("internet", nz(cur.internet()) - nz(prev.internet()));
        return d.entrySet().stream().filter(e -> e.getValue() > 0).max(Map.Entry.comparingByValue())
                .map(e -> new Increase(e.getKey(), e.getValue())).orElse(null);
    }
}
