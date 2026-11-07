package com.chatatda.api.floorplan;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FloorPlanService {

    private final JdbcTemplate jdbc;

    public FloorPlanService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 저장된 FloorPlan JSON 원문. 스키마가 늘어나도 서버 코드는 고치지 않는다 */
    public String json(long id) {
        List<String> rows = jdbc.queryForList("select data::text from floorplans where id = ?", String.class, id);
        if (rows.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "평면도를 찾을 수 없어요.");
        }
        return rows.get(0);
    }

    public boolean exists(long id) {
        Integer n = jdbc.queryForObject("select count(*) from floorplans where id = ?", Integer.class, id);
        return n != null && n > 0;
    }
}
