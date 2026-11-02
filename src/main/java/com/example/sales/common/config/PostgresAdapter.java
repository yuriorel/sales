package com.example.sales.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks a Postgres implementation of a persistence port. It is registered only
/// when `sales.persistence.provider=postgres`.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repository
@ConditionalOnProperty(name = "sales.persistence.provider", havingValue = "postgres")
public @interface PostgresAdapter {
}
