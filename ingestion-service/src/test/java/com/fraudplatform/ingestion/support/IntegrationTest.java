package com.fraudplatform.ingestion.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full-context integration test against real containers.
 *
 * <p>Every IT must use this annotation <b>unchanged</b> (no extra properties or mocks). Identical
 * configuration means Spring reuses one cached context, so Kafka and Redis start once per JVM
 * instead of once per test class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {}
