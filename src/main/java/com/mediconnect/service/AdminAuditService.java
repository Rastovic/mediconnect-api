package com.mediconnect.service;

import com.mediconnect.dto.AuditLogDto;
import com.mediconnect.entity.AuditLog;
import com.mediconnect.repository.AuditLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AuditLogRepository auditLogRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // [A03] SQL Injection — search filter concatenated into a native query.
    //        Caller controls the `q` parameter freely; classic UNION-based
    //        exfiltration possible against the `audit_logs` table.
    //
    //  Demo payload:
    //    GET /api/admin/logs?q=' UNION SELECT id,username,email,password_hash,'','','','',NOW() FROM users--
    //  Pulls every password hash out via the audit log surface — bypasses the
    //  fact that AdminUserController.list at least returns its hashes through
    //  the API (defenders may not log that), this path returns them via SQLi.
    public List<AuditLogDto> search(Long userId, String action, String from, String to, String q) {
        StringBuilder sql = new StringBuilder("SELECT id, user_id, action, entity_type, entity_id, ip_address, user_agent, details, created_at FROM audit_logs WHERE 1=1");

        if (userId != null) sql.append(" AND user_id = ").append(userId);
        if (action != null && !action.isBlank()) {
            // [A03] No parameterisation
            sql.append(" AND action = '").append(action).append("'");
        }
        if (from != null && !from.isBlank()) sql.append(" AND created_at >= '").append(from).append("'");
        if (to   != null && !to.isBlank())   sql.append(" AND created_at <= '").append(to).append("'");
        if (q    != null && !q.isBlank()) {
            // [A03] free-text LIKE built by concatenation — the SQLi vector
            sql.append(" AND (details LIKE '%").append(q).append("%' OR user_agent LIKE '%").append(q).append("%')");
        }
        sql.append(" ORDER BY created_at DESC LIMIT 500");

        Query nativeQuery = entityManager.createNativeQuery(sql.toString());
        @SuppressWarnings("unchecked")
        List<Object[]> rows = nativeQuery.getResultList();

        return rows.stream().map(row -> AuditLogDto.builder()
                .id(toLong(row[0]))
                .userId(toLong(row[1]))
                .action((String) row[2])
                .entityType((String) row[3])
                .entityId(toLong(row[4]))
                .ipAddress((String) row[5])
                .userAgent((String) row[6])
                .details((String) row[7])
                .createdAt(row[8] != null ? ((java.sql.Timestamp) row[8]).toLocalDateTime() : null)
                .build()
        ).collect(Collectors.toList());
    }

    // [A01][A10] No role check. Returns the full record including the raw
    //              stackTrace written by LoggingInterceptor — internal class
    //              names, framework versions, package layout all leak.
    public AuditLogDto findById(Long id) {
        AuditLog log = auditLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Audit log not found: " + id));
        return toDto(log);
    }

    // [A09] Single-row delete — selective tampering with the audit trail.
    public Map<String, Object> deleteOne(Long id) {
        auditLogRepository.deleteById(id);
        return Map.of("deleted", true, "id", id);
    }

    // [A09] Existing behaviour — wipe-everything. Kept here so AdminController
    //        can be slimmed down further; will be removed once /clear is moved.
    @Transactional
    public int clearAll() {
        long count = auditLogRepository.count();
        auditLogRepository.deleteAll();
        return (int) count;
    }

    // Export — CSV, JSON or XML. The XML branch demonstrates XXE.
    public String exportLogs(String format, Long userId, String action, String from, String to, String q) {
        List<AuditLogDto> logs = search(userId, action, from, to, q);

        if ("csv".equalsIgnoreCase(format)) {
            StringBuilder sb = new StringBuilder("id,userId,action,entityType,entityId,ipAddress,createdAt,details\n");
            for (AuditLogDto l : logs) {
                sb.append(l.getId()).append(',')
                  .append(l.getUserId() == null ? "" : l.getUserId()).append(',')
                  .append(csv(l.getAction())).append(',')
                  .append(csv(l.getEntityType())).append(',')
                  .append(l.getEntityId() == null ? "" : l.getEntityId()).append(',')
                  .append(csv(l.getIpAddress())).append(',')
                  .append(l.getCreatedAt() == null ? "" : l.getCreatedAt()).append(',')
                  // [A03] No escaping — details may contain commas/newlines/quotes
                  //        that break the CSV; opening the export in Excel may run
                  //        formulas if a cell starts with '=' (CSV injection).
                  .append(csv(l.getDetails())).append('\n');
            }
            return sb.toString();
        }

        if ("xml".equalsIgnoreCase(format)) {
            return logs.stream()
                    .map(this::toXmlElement)
                    .collect(Collectors.joining("\n", "<auditLogs>\n", "\n</auditLogs>"));
        }

        // Default JSON — delegated to Jackson via the controller signature.
        // Caller asking for JSON: handled directly by the JSON @GetMapping
        // (we never reach this branch for JSON requests).
        return "";
    }

    // [A03] XXE — DocumentBuilderFactory created with all the unsafe defaults:
    //        no FEATURE_SECURE_PROCESSING, external entities NOT disabled.
    //        Used by POST /api/admin/logs/import (a hypothetical future endpoint)
    //        and also called from the export path when the caller supplies
    //        ?template=... to drive the XML rendering.
    public String renderXmlWithTemplate(String xmlPayload) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // [A03] all four are the dangerous defaults — left unchanged on purpose
            // factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            // factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            // factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            // factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlPayload.getBytes(StandardCharsets.UTF_8)));
            Element root = doc.getDocumentElement();
            return root != null ? root.getTextContent() : "";
        } catch (Exception e) {
            // [A10] swallowed exception text — leaks parser internals
            return "XML parse failed: " + e.getMessage();
        }
    }

    private String toXmlElement(AuditLogDto l) {
        // [A03] No escaping. If `details` or `userAgent` contains '<', '>', '&',
        //        the generated XML is malformed AND an attacker who controls the
        //        User-Agent can inject XML structure (e.g. close one tag and
        //        open a CDATA section) that ends up in the exported document.
        return "<log id=\"" + l.getId() + "\">"
                + "<userId>" + (l.getUserId() == null ? "" : l.getUserId()) + "</userId>"
                + "<action>" + nullToEmpty(l.getAction()) + "</action>"
                + "<entityType>" + nullToEmpty(l.getEntityType()) + "</entityType>"
                + "<ipAddress>" + nullToEmpty(l.getIpAddress()) + "</ipAddress>"
                + "<createdAt>" + (l.getCreatedAt() == null ? "" : l.getCreatedAt()) + "</createdAt>"
                + "<details>" + nullToEmpty(l.getDetails()) + "</details>"
                + "<userAgent>" + nullToEmpty(l.getUserAgent()) + "</userAgent>"
                + "</log>";
    }

    private String csv(String s) {
        if (s == null) return "";
        // [A03] Just wrap in quotes — no escaping of embedded quotes
        return "\"" + s.replace("\n", "\\n") + "\"";
    }

    private String nullToEmpty(String s) { return s == null ? "" : s; }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Long) return (Long) o;
        if (o instanceof Number) return ((Number) o).longValue();
        return Long.valueOf(o.toString());
    }

    private AuditLogDto toDto(AuditLog l) {
        return AuditLogDto.builder()
                .id(l.getId())
                .userId(l.getUser() != null ? l.getUser().getId() : null)
                .action(l.getAction())
                .entityType(l.getEntityType())
                .entityId(l.getEntityId())
                .ipAddress(l.getIpAddress())
                .userAgent(l.getUserAgent())
                .details(l.getDetails())
                .createdAt(l.getCreatedAt())
                .build();
    }

    @SuppressWarnings("unused")
    private LocalDateTime safeParse(String s) {
        try { return s == null ? null : LocalDateTime.parse(s); }
        catch (Exception e) { return null; }
    }
}
