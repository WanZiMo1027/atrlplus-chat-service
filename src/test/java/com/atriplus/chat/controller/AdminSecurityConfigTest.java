package com.atriplus.chat.controller;

import com.atriplus.chat.config.AdminSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminSessionController.class)
@Import(AdminSecurityConfig.class)
@TestPropertySource(properties = {
        "admin.enabled=true", "admin.password=test-password", "admin.internal-token=test-token"
})
class AdminSecurityConfigTest {
    @Autowired private MockMvc mvc;

    @Test
    void loginIsRequiredAndValidPasswordCreatesAdminSession() throws Exception {
        mvc.perform(get("/api/admin/session")).andExpect(status().is3xxRedirection());

        var login = mvc.perform(post("/api/admin/login").with(csrf())
                        .param("username", "admin").param("password", "test-password"))
                .andExpect(status().is3xxRedirection()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/api/admin/session").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    void mutatingAdminRequestNeedsCsrfToken() throws Exception {
        mvc.perform(post("/api/admin/logout")).andExpect(status().isForbidden());
    }
}
