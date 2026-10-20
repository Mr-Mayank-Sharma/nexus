package com.nexus.oms.generic;

import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.jobs.JobHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Job types that run generic connections on the job manager's schedule, with logs and per-row failure files. */
final class GenericJobs {

    private GenericJobs() {}

    static final JobHandler.Param CONNECTION = new JobHandler.Param("connectionId", "Generic connection", "GENERIC_CONNECTION", true,
            "The generic connection this job runs.");

    static UUID connection(JobHandler.Context ctx) {
        UUID id = ctx.uuid("connectionId");
        if (id == null) throw new BadRequestException("This job needs a generic connection (connectionId).");
        return id;
    }

    @Component
    static class GenericImport implements JobHandler {
        private final GenericConnectionService service;

        GenericImport(GenericConnectionService service) { this.service = service; }

        public String type() { return "GENERIC_IMPORT"; }
        public String label() { return "Generic import (any JSON API)"; }
        public String description() { return "Reads orders or stock counts from any system with a JSON API, using a generic connection's mapping. Dry run unless you switch writing on. Orders already imported are skipped."; }
        public List<Param> params() {
            return List.of(CONNECTION, new Param("apply", "Write the changes", "BOOLEAN", false,
                    "Off = a dry run that reports what would be imported. On = create orders or set stock."));
        }

        public Outcome run(Context ctx) {
            return service.runImport(ctx.tenantId(), connection(ctx), ctx.flag("apply"), ctx);
        }
    }

    @Component
    static class GenericExport implements JobHandler {
        private final GenericConnectionService service;

        GenericExport(GenericConnectionService service) { this.service = service; }

        public String type() { return "GENERIC_EXPORT"; }
        public String label() { return "Generic export (any JSON API or a file)"; }
        public String description() { return "Sends orders, shipments or stock to another system using a generic connection's mapping, or writes them to a CSV file. Orders and shipments go out once each, oldest first."; }
        public List<Param> params() { return List.of(CONNECTION); }

        public Outcome run(Context ctx) {
            return service.runExport(ctx.tenantId(), connection(ctx), ctx);
        }
    }
}
