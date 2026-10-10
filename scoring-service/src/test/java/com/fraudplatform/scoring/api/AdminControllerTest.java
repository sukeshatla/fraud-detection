package com.fraudplatform.scoring.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.mockito.BDDMockito.given;

import com.fraudplatform.scoring.application.DeadLetterReplay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.fraudplatform.scoring.infrastructure.security.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@Import(SecurityConfig.class)
@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private DeadLetterReplay replay;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("AC-010-03: POST /admin/dlt/replay replays up to max and reports how many")
    void replays() {
        given(replay.replay(25)).willReturn(7);

        assertThat(mvc.post().uri("/admin/dlt/replay?max=25").with(asOps).exchange())
                .hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.replayed").isEqualTo(7);
    }

    @Test
    @DisplayName("max is clamped to 1..1000")
    void clampsMax() {
        given(replay.replay(1000)).willReturn(0);

        assertThat(mvc.post().uri("/admin/dlt/replay?max=999999").with(asOps).exchange()).hasStatus(HttpStatus.OK);
    }

    private static final org.springframework.test.web.servlet.request.RequestPostProcessor asOps =
            jwt().authorities(new SimpleGrantedAuthority("ROLE_OPS"));
}
