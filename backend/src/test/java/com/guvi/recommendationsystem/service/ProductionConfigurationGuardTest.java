package com.guvi.recommendationsystem.service;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
class ProductionConfigurationGuardTest {
    @Test void requiresHttpsOriginSecretsAndSecureCookies(){
        var environment=new MockEnvironment().withProperty("app.public-url","http://example.test");
        assertThatThrownBy(()->new ProductionConfigurationGuard(environment).afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
        environment.setProperty("app.public-url","https://example.test");
        assertThatThrownBy(()->new ProductionConfigurationGuard(environment).afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
        for(String key:new String[]{"spring.datasource.password","spring.mail.username","spring.mail.password"})environment.setProperty(key,"isolated-test-value");
        assertThatThrownBy(()->new ProductionConfigurationGuard(environment).afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
        environment.setProperty("server.servlet.session.cookie.secure","true");
        assertThatCode(()->new ProductionConfigurationGuard(environment).afterPropertiesSet()).doesNotThrowAnyException();
        environment.setProperty("app.public-url","https://example.test/path");
        assertThatThrownBy(()->new ProductionConfigurationGuard(environment).afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
    }
}
