package com.swu.atch.reward.admin;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.charset.StandardCharsets;

@Hidden
@Controller
public class AdminPageController {
    @GetMapping("/admin/login")
    public ResponseEntity<Resource> login() {
        return page("login.html");
    }

    @GetMapping("/admin/items")
    public ResponseEntity<Resource> items() {
        return page("items.html");
    }

    @GetMapping("/admin/csrf")
    @ResponseBody
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    private ResponseEntity<Resource> page(String file) {
        return ResponseEntity.ok().contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(new ClassPathResource("admin/" + file));
    }

    public record CsrfResponse(String headerName, String parameterName, String token) { }
}
