package com.mediconnect.service;

import freemarker.core.TemplateClassResolver;
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
import java.util.Set;

// Server-side note rendering. Templates are engine-driven, so the two ways a
// caller reaches Freemarker are both locked down:
//
//   1. renderByName(name, data): `name` must be one of the known, server-owned
//      templates (ALLOWED_TEMPLATES) — no caller-chosen paths, so template
//      path-traversal is not possible.
//   2. renderInline(source, data): caller input is NEVER executed as a
//      template; it is returned as escaped text, so SSTI is not possible.
//
// The Configuration additionally forbids the ?new built-in from resolving any
// class (ALLOWS_NOTHING_RESOLVER) and disables the ?api built-in, so even a
// server-owned template cannot be coerced into instantiating arbitrary types.
@Component
public class TemplateRenderer {

    private static final Set<String> ALLOWED_TEMPLATES = Set.of("soap", "progress", "triage");

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
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        // Refuse to resolve any class via ?new, and disable the ?api built-in,
        // so a template cannot instantiate arbitrary types (e.g. Execute).
        cfg.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
        cfg.setAPIBuiltinEnabled(false);
    }

    private void writeIfMissing(String name, String contents) throws IOException {
        File f = baseDir.resolve(name).toFile();
        if (!f.exists()) {
            Files.writeString(f.toPath(), contents);
        }
    }

    // Only the known, server-owned templates may be rendered. A caller-supplied
    // name that is not in the allow-list is rejected, so no filesystem path is
    // ever built from caller input (no template path-traversal).
    public String renderByName(String name, Map<String, Object> data) {
        if (name == null || !ALLOWED_TEMPLATES.contains(name)) {
            return "<pre class=\"render-error\">Unknown template</pre>";
        }
        try {
            Map<String, Object> model = wrap(data);
            freemarker.template.Template tpl = cfg.getTemplate(name + ".ftl");
            StringWriter out = new StringWriter();
            tpl.process(model, out);
            return out.toString();
        } catch (Exception e) {
            // Generic message only — no engine/stack detail leaked to the caller.
            return "<pre class=\"render-error\">Could not render note</pre>";
        }
    }

    // Caller input is treated as literal text, not a template: the string is
    // HTML-escaped and returned. No Freemarker evaluation, so no SSTI.
    public String renderInline(String source, Map<String, Object> data) {
        if (source == null) return "";
        return "<p>" + escapeHtml(source) + "</p>";
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private Map<String, Object> wrap(Map<String, Object> data) {
        Map<String, Object> m = new HashMap<>();
        m.put("data", data == null ? Map.of() : data);
        return m;
    }
}
