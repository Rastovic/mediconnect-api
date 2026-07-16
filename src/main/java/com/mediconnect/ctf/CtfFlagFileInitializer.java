package com.mediconnect.ctf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Materialises file-based CTF flags onto disk at startup. Flag plaintext lives in
 * classpath resources under ctf-flags/; this copies each to
 * {user.dir}/ctf-flags/ so file-read exploits (A05 path traversal, and later the
 * RCE flag files) have a real target on the filesystem.
 *
 * Compartmentalised per plan §12.1: one file per challenge, distinct names, so a
 * single RCE + grep cannot trivially assume one location holds every flag.
 */
@Component
public class CtfFlagFileInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CtfFlagFileInitializer.class);
    private static final String CLASSPATH_GLOB = "classpath*:ctf-flags/*";

    @Override
    public void run(String... args) throws Exception {
        Path targetDir = Path.of(System.getProperty("user.dir"), "ctf-flags");
        Files.createDirectories(targetDir);
        // .ftl flag templates also go into the Freemarker template dir so the
        // A03 #205 challenge (renderByName has no allow-list) can load the
        // "unintended" seeded template by name.
        Path templateDir = Path.of(System.getProperty("user.dir"), "notes", "templates");
        Files.createDirectories(templateDir);

        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources(CLASSPATH_GLOB);

        int written = 0;
        for (Resource r : resources) {
            String name = r.getFilename();
            if (name == null) continue;
            try (InputStream in = r.getInputStream()) {
                Files.copy(in, targetDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                written++;
            }
            if (name.endsWith(".ftl")) {
                try (InputStream in = r.getInputStream()) {
                    Files.copy(in, templateDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        log.info("[CTF] Materialised {} flag file(s) into {}", written, targetDir);
    }
}
