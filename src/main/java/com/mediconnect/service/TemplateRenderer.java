package com.mediconnect.service;

import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

// [A03] Software Supply Chain Failures — caller-controlled template + caller-
//        controlled model. Two paths into Freemarker:
//
//        1. `renderByName(name, data)`: name is concatenated into a filesystem
//           path under {workdir}/notes/templates/. No normalisation — passing
//           "../../etc/passwd" reads outside the directory.
//        2. `renderInline(source, data)`: caller's raw string runs through the
//           template engine. SSTI payloads (`<#assign x = "freemarker.template.utility.Execute"?new()>${x("id")}`)
//           execute commands as the JVM user.
//
//        Both paths share a single Configuration with the new_built_ins_enabled
//        set true so the Execute built-in works without further config.
@Component
public class TemplateRenderer {

    private Configuration cfg;
    private Path baseDir;

    @PostConstruct
    void init() throws IOException {
        // Templates live under {workdir}/notes/templates/. The directory is
        // created if missing and seeded with one default template so the demo
        // works on a fresh checkout.
        baseDir = Path.of("notes", "templates");
        Files.createDirectories(baseDir);
        writeIfMissing("soap.ftl",
                "<h3>SOAP Note</h3>"
                        + "<p><b>S:</b> ${(data.subjective)!''}</p>"
                        + "<p><b>O:</b> ${(data.objective)!''}</p>"
                        + "<p><b>A:</b> ${(data.assessment)!''}</p>"
                        + "<p><b>P:</b> ${(data.plan)!''}</p>");
        writeIfMissing("progress.ftl",
                "<h3>Progress Note</h3>"
                        + "<p>${(data.summary)!''}</p>"
                        + "<p>Next: ${(data.next)!''}</p>");
        writeIfMissing("triage.ftl",
                "<h3>Triage Note</h3>"
                        + "<p>Complaint: ${(data.complaint)!''}</p>"
                        + "<p>Priority: ${(data.priority)!''}</p>");

        cfg = new Configuration(Configuration.VERSION_2_3_34);
        cfg.setDirectoryForTemplateLoading(baseDir.toFile());
        cfg.setDefaultEncoding("UTF-8");
        // [A03] DEBUG_HANDLER rethrows the original exception so SSTI failures
        //        bubble back to the HTTP response with full stack traces.
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.DEBUG_HANDLER);
        // [A03] new_built_ins_enabled defaults true — keep it that way so
        //        `freemarker.template.utility.Execute?new()` is reachable.
    }

    private void writeIfMissing(String name, String contents) throws IOException {
        File f = baseDir.resolve(name).toFile();
        if (!f.exists()) {
            Files.writeString(f.toPath(), contents);
        }
    }

    // [A03] Path is caller-controlled. Freemarker is given a name, then
    //        builds the full filesystem path against the configured directory.
    //        Freemarker normalises the path, but caller can still escape
    //        the templates directory by passing a relative path that
    //        resolves outside, and can pull in arbitrary `.ftl` files
    //        already on disk (e.g. via the multipart import endpoint).
    public String renderByName(String name, Map<String, Object> data) {
        try {
            Map<String, Object> model = wrap(data);
            freemarker.template.Template tpl = cfg.getTemplate(name + ".ftl");
            StringWriter out = new StringWriter();
            tpl.process(model, out);
            return out.toString();
        } catch (Exception e) {
            // [A10] Raw exception message returned so SSTI / path-traversal
            //        failures surface with full diagnostic detail to the caller.
            return "<pre class=\"render-error\">" + e.getClass().getSimpleName() + ": " + e.getMessage() + "</pre>";
        }
    }

    // [A03] Inline rendering — caller's literal Freemarker source executed.
    //        Trivial SSTI demo:
    //          {"templateBody":"<#assign x = \"freemarker.template.utility.Execute\"?new()>${x(\"id\")}"}
    public String renderInline(String source, Map<String, Object> data) {
        try {
            Map<String, Object> model = wrap(data);
            freemarker.template.Template tpl = new freemarker.template.Template(
                    "inline-" + Integer.toHexString(System.identityHashCode(source)),
                    new java.io.StringReader(source), cfg);
            StringWriter out = new StringWriter();
            tpl.process(model, out);
            return out.toString();
        } catch (Exception e) {
            return "<pre class=\"render-error\">" + e.getClass().getSimpleName() + ": " + e.getMessage() + "</pre>";
        }
    }

    private Map<String, Object> wrap(Map<String, Object> data) {
        Map<String, Object> m = new HashMap<>();
        m.put("data", data == null ? Map.of() : data);
        return m;
    }
}
