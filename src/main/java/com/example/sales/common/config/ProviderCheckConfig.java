package com.example.sales.common.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class ProviderCheckConfig {

    /// Binds [SalesProperties] before any other bean is created. With an unknown
    /// provider, startup then fails with a binding error that lists the valid values,
    /// instead of a "no bean of type EventPublisher" error from the first service
    /// that needs the missing adapter.
    @Bean
    static BeanFactoryPostProcessor salesProviderCheck(Environment environment) {
        return _ -> Binder.get(environment).bind("sales", SalesProperties.class);
    }
}
