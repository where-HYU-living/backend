package com.chatatda.api.floorplan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatatda.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class FloorPlanApiTest extends IntegrationTest {

    @Test
    void detail_returnsStoredJson_withoutLogin() throws Exception {
        mvc.perform(get("/api/v1/floorplans/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=3600")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("청아진 18타입"))
                .andExpect(jsonPath("$.walls.length()").value(6))
                .andExpect(jsonPath("$.openings[0].type").value("door"))
                .andExpect(jsonPath("$.rooms[2].name").value("욕실"))
                .andExpect(jsonPath("$.entrance[0]").value(0.85))
                .andExpect(jsonPath("$.glbUrl").doesNotExist());
    }

    @Test
    void unknownId_is404() throws Exception {
        mvc.perform(get("/api/v1/floorplans/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void nonNumericId_is400() throws Exception {
        mvc.perform(get("/api/v1/floorplans/abc")).andExpect(status().is4xxClientError());
    }
}
