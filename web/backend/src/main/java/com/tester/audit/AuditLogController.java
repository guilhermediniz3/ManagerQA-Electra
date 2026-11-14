package com.tester.audit;

import org.springframework.web.bind.annotation.*;

/** Protected by the application's authenticated-by-default security chain. */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {
    private final AuditLogService service;
    public AuditLogController(AuditLogService service) { this.service = service; }

    @GetMapping
    public AuditLogService.Result list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType) {
        return service.list(page, size, action, entityType);
    }
}
