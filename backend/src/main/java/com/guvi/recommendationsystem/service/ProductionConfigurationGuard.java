package com.guvi.recommendationsystem.service;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import java.net.URI;

@Component
@Profile("production")
public class ProductionConfigurationGuard implements InitializingBean {
    private final Environment environment;
    public ProductionConfigurationGuard(Environment environment){this.environment=environment;}
    @Override public void afterPropertiesSet(){
        URI origin;
        try{origin=URI.create(environment.getRequiredProperty("app.public-url"));}
        catch(RuntimeException ex){throw new IllegalStateException("Production requires an HTTPS public origin.");}
        if(!"https".equals(origin.getScheme()) || origin.getHost()==null || origin.getUserInfo()!=null
                || origin.getQuery()!=null || origin.getFragment()!=null || !(origin.getPath().isEmpty()||origin.getPath().equals("/")))
            throw new IllegalStateException("Production requires an HTTPS public origin.");
        for(String property:new String[]{"spring.datasource.password","spring.mail.username","spring.mail.password"})
            if(environment.getProperty(property,"").isBlank())throw new IllegalStateException("Production database and authenticated SMTP credentials must be configured.");
        if(!environment.getProperty("server.servlet.session.cookie.secure",Boolean.class,false))
            throw new IllegalStateException("Production requires secure session cookies.");
    }
}
