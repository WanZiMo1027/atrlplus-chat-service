package com.atriplus.chat.controller;

import com.atriplus.chat.config.AdminSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminSessionController.class)
@Import(AdminSecurityConfig.class)
@TestPropertySource(properties = "admin.enabled=false")
class AdminSecurityDisabledTest {
    @Autowired private MockMvc mvc;

    @Test
    void adminIsClosedButExistingMachineRoutesAreNotCsrfBlocked() throws Exception {
        mvc.perform(get("/api/admin/csrf")).andExpect(status().isForbidden());
        mvc.perform(post("/onebot/v11/events")).andExpect(status().isNotFound());
    }
}
