package com.chatatda.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatatda.api.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

class ProfileApiTest extends IntegrationTest {

    @Autowired
    UserRepository users;

    @Autowired
    ProfileService profileService;

    private long userId;

    @BeforeEach
    void createUser() {
        userId = users.save(new User("me@hanyang.ac.kr", "한양0001", clock.instant())).getId();
    }

    @Test
    void profile_initialState() throws Exception {
        mvc.perform(get("/api/v1/me/profile").header("Authorization", bearer(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.email").value("me@hanyang.ac.kr"))
                .andExpect(jsonPath("$.nickname").value("한양0001"))
                .andExpect(jsonPath("$.departmentId").isEmpty())
                .andExpect(jsonPath("$.buildingIds", hasSize(0)))
                .andExpect(jsonPath("$.budget").isEmpty());
    }

    @Test
    void profile_updatePartial() throws Exception {
        update("{\"departmentId\": 13}").andExpect(status().isOk());

        update("{\"budget\": {\"rentCap\": 600000, \"depositCap\": 10000000}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(13))
                .andExpect(jsonPath("$.buildingIds", contains(103)))
                .andExpect(jsonPath("$.budget.rentCap").value(600000))
                .andExpect(jsonPath("$.budget.depositCap").value(10000000));
    }

    @Test
    void profile_settingDepartment_fillsDefaultBuildings() throws Exception {
        update("{\"departmentId\": 11}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(11))
                .andExpect(jsonPath("$.buildingIds", contains(101, 102)));
    }

    @Test
    void profile_unknownIds_400() throws Exception {
        update("{\"departmentId\": 999}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.departmentId").exists());
        update("{\"buildingIds\": [101, 999]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.buildingIds").exists());
    }

    @Test
    void profile_clearBudget() throws Exception {
        update("{\"budget\": {\"rentCap\": 600000, \"depositCap\": 10000000}}").andExpect(status().isOk());

        update("{\"budget\": {\"rentCap\": null, \"depositCap\": null}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budget").isEmpty());
    }

    @Test
    void profile_tokenForMissingUser_401() throws Exception {
        mvc.perform(get("/api/v1/me/profile").header("Authorization", bearer(userId + 1000)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void service_exposesFirstBuildingAndBudget() throws Exception {
        assertThat(profileService.firstBuildingId(userId)).isEmpty();
        assertThat(profileService.budget(userId)).isEmpty();

        update("{\"departmentId\": 15, \"budget\": {\"rentCap\": 500000, \"depositCap\": null}}")
                .andExpect(status().isOk());

        assertThat(profileService.firstBuildingId(userId)).contains(104L);
        assertThat(profileService.budget(userId)).contains(new Budget(500000L, null));
    }

    private ResultActions update(String body) throws Exception {
        return mvc.perform(put("/api/v1/me/profile")
                .header("Authorization", bearer(userId))
                .contentType(APPLICATION_JSON)
                .content(body));
    }
}
