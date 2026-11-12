package com.haulwise.config;

import jakarta.validation.MessageInterpolator;
import java.util.Locale;
import org.springframework.boot.validation.MessageInterpolatorFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Bean Validation messages are always English, whatever the JVM locale or the request's
 * {@code Accept-Language}: API clients and startup (seed) errors get one predictable language.
 * Replaces Spring Boot's auto-configured {@code defaultValidator}; Spring MVC uses it too.
 */
@Configuration(proxyBeanMethods = false)
public class ValidationConfig {

    static final Locale MESSAGE_LOCALE = Locale.ENGLISH;

    @Bean
    public static LocalValidatorFactoryBean defaultValidator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setMessageInterpolator(new FixedLocaleMessageInterpolator(
                new MessageInterpolatorFactory().getObject(), MESSAGE_LOCALE));
        return validator;
    }

    /** Delegates to the standard interpolator but ignores the locale it is given. */
    record FixedLocaleMessageInterpolator(MessageInterpolator delegate, Locale locale)
            implements MessageInterpolator {

        @Override
        public String interpolate(String messageTemplate, Context context) {
            return delegate.interpolate(messageTemplate, context, locale);
        }

        @Override
        public String interpolate(String messageTemplate, Context context, Locale ignored) {
            return delegate.interpolate(messageTemplate, context, locale);
        }
    }
}
