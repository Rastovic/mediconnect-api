package com.mediconnect.config;

import com.mediconnect.interceptor.LoggingInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoggingInterceptor loggingInterceptor;

    // Register LoggingInterceptor for every incoming request.
    // No path exclusions — even /api/auth/login (which carries plaintext passwords
    // in the request body) is intercepted and its payload stored in audit_logs.
    // [A06][A09] There is no allowlist of "safe" paths to skip sensitive-data capture.
    //
    // Body capture is provided by ContentCachingFilter (@Component, @Order(1)),
    // which wraps every request/response without a size limit [A08 CWE-400].
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggingInterceptor)
                .addPathPatterns("/**");
    }
}
