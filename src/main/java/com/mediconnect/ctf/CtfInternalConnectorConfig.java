package com.mediconnect.ctf;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds a second Tomcat connector bound to loopback for the SSRF internal
 * endpoint (plan §5.3 reachability caveat, option a). In the intended
 * docker-compose deployment this port is NOT published, so /internal/** is only
 * reachable from inside the app - i.e. only via the server-side SSRF fetch, not
 * the host browser. (Running the jar directly on the host, the port is of course
 * locally reachable; that is a dev convenience, not the graded topology.)
 */
@Configuration
public class CtfInternalConnectorConfig {

    /** Unpublished internal port. Keep in sync with InternalMetadataController. */
    public static final int INTERNAL_PORT = 8099;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> ctfInternalConnector() {
        return factory -> {
            Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
            connector.setPort(INTERNAL_PORT);
            connector.setProperty("address", "127.0.0.1");
            factory.addAdditionalTomcatConnectors(connector);
        };
    }
}
