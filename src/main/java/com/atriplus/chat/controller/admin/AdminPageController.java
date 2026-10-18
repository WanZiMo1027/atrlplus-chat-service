package com.atriplus.chat.controller.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Vue 的深链接统一返回同一份入口页面。 */
@Controller
@ConditionalOnProperty(name = "admin.enabled", havingValue = "true")
public class AdminPageController {
    @GetMapping({"/admin", "/admin/", "/admin/login",
            "/admin/tencentdb/l1/memories", "/admin/tencentdb/l1/prompts",
            "/admin/chat-history", "/admin/group-reply-config", "/admin/group-reply-config/receive",
            "/admin/group-reply-config/reply", "/admin/media/images", "/admin/media/memes"})
    public String page() { return "forward:/admin/index.html"; }
}
