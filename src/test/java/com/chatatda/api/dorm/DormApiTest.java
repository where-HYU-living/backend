package com.chatatda.api.dorm;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatatda.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class DormApiTest extends IntegrationTest {

    @Test
    void dorms_monthlyCost() throws Exception {
        mvc.perform(get("/api/v1/dorms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(107))
                .andExpect(jsonPath("$[0].lat").value(37.5585))
                .andExpect(jsonPath("$[0].roomTypes", hasSize(3)))
                .andExpect(jsonPath("$[0].roomTypes[0].name").value("3인실 (여)"))
                .andExpect(jsonPath("$[0].roomTypes[0].monthlyCost").value(250_250))
                .andExpect(jsonPath("$[0].roomTypes[1].monthlyCost").value(284_250))
                .andExpect(jsonPath("$[0].roomTypes[2].mealIncluded").value(true))
                .andExpect(jsonPath("$[0].roomTypes[2].floorplanId").value(9));
    }

    @Test
    void dorm_detailWalkMin() throws Exception {
        mvc.perform(get("/api/v1/dorms/107").param("buildingId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walkMin").value(7))
                .andExpect(jsonPath("$.roomTypes", hasSize(3)));
        mvc.perform(get("/api/v1/dorms/107"))
                .andExpect(jsonPath("$.walkMin").value(nullValue()));
    }

    @Test
    void dorm_unknown404() throws Exception {
        mvc.perform(get("/api/v1/dorms/999")).andExpect(status().isNotFound());
    }
}
