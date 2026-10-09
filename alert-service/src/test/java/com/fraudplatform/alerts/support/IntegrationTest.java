package com.fraudplatform.alerts.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Full-context IT on real containers. Use unchanged so every IT shares one cached context. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // real server: needed for SSE
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {}
