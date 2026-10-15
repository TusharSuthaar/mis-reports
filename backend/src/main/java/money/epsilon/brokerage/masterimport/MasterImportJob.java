package money.epsilon.brokerage.masterimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.masterimport.MasterImportProcessor.RowError;
import money.epsilon.brokerage.masterimport.MasterImportProcessor.SheetCount;
import money.epsilon.brokerage.masterimport.WorkbookReader.SheetData;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.importing.TransactionalJob;
import money.epsilon.brokerage.platform.storage.FileStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** Background job: applies an uploaded masters workbook all-or-nothing (or only checks it, for a dry run). */
@Component
class MasterImportJob extends TransactionalJob<MasterImportJob.Payload> {

    static final String TYPE = "master-import";
    static final String AREA = "master-imports";

    private final FileStore files;
    private final MasterImportProcessor processor;
    private final AuditService audit;

    MasterImportJob(FileStore files, MasterImportProcessor processor, TransactionTemplate tx, AuditService audit, ObjectMapper mapper) {
        super(TYPE, "Imported masters from a file", Payload.class, tx, mapper);
        this.files = files;
        this.processor = processor;
        this.audit = audit;
    }

    record Payload(String path, String fileName, String sha256, boolean dryRun) {
    }

    record Result(String fileName, boolean dryRun, boolean applied, List<SheetCount> sheets, List<RowError> errors) {
    }

    record ImportAudit(String sha256, Result result) {
    }

    @Override
    protected Object apply(Payload payload, TransactionStatus status) {
        List<RowError> errors = new ArrayList<>();
        Map<SheetSpec, SheetData> sheets;
        try {
            sheets = WorkbookReader.read(files.read(AREA, payload.path()));
        } catch (IllegalArgumentException e) {
            errors.add(new RowError("", 0, e.getMessage()));
            return new Result(payload.fileName(), payload.dryRun(), false, List.of(), errors);
        }
        if (sheets.isEmpty()) {
            errors.add(new RowError("", 0, "No known sheets found. Expected some of: "
                    + String.join(", ", SheetSpec.ALL.stream().map(SheetSpec::name).toList())));
            return new Result(payload.fileName(), payload.dryRun(), false, List.of(), errors);
        }
        List<SheetCount> counts = processor.apply(sheets, "Bulk import: " + payload.fileName(), errors);
        boolean apply = errors.isEmpty() && !payload.dryRun();
        Result r = new Result(payload.fileName(), payload.dryRun(), apply, counts, List.copyOf(errors));
        if (apply) {
            audit.record("MASTER_IMPORT", null, "IMPORT", null, new ImportAudit(payload.sha256(), r));
        } else {
            status.setRollbackOnly();
        }
        return r;
    }
}
