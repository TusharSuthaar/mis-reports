package money.epsilon.brokerage.masterimport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.masterimport.MasterImportProcessor.RowError;
import money.epsilon.brokerage.masterimport.MasterImportProcessor.SheetCount;
import money.epsilon.brokerage.masterimport.WorkbookReader.SheetData;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.job.JobHandler;
import money.epsilon.brokerage.platform.storage.FileStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Background job: applies an uploaded masters workbook all-or-nothing (or only checks it, for a dry run). */
@Component
class MasterImportJob implements JobHandler {

    static final String TYPE = "master-import";

    private final FileStore files;
    private final MasterImportProcessor processor;
    private final TransactionTemplate tx;
    private final AuditService audit;
    private final ObjectMapper mapper;

    MasterImportJob(FileStore files, MasterImportProcessor processor, TransactionTemplate tx, AuditService audit, ObjectMapper mapper) {
        this.files = files;
        this.processor = processor;
        this.tx = tx;
        this.audit = audit;
        this.mapper = mapper;
    }

    record Payload(String path, String fileName, String sha256, boolean dryRun) {
    }

    record Result(String fileName, boolean dryRun, boolean applied, List<SheetCount> sheets, List<RowError> errors) {
    }

    record ImportAudit(String sha256, Result result) {
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public JsonNode run(JsonNode payloadJson) throws Exception {
        Payload payload = mapper.treeToValue(payloadJson, Payload.class);
        List<RowError> errors = new ArrayList<>();
        Map<SheetSpec, SheetData> sheets;
        try {
            sheets = WorkbookReader.read(files.read(payload.path()));
        } catch (IllegalArgumentException e) {
            errors.add(new RowError("", 0, e.getMessage()));
            return mapper.valueToTree(new Result(payload.fileName(), payload.dryRun(), false, List.of(), errors));
        }
        if (sheets.isEmpty()) {
            errors.add(new RowError("", 0, "No known sheets found. Expected some of: "
                    + String.join(", ", SheetSpec.ALL.stream().map(SheetSpec::name).toList())));
            return mapper.valueToTree(new Result(payload.fileName(), payload.dryRun(), false, List.of(), errors));
        }

        Result result = tx.execute(status -> {
            List<SheetCount> counts = processor.apply(sheets, "Bulk import: " + payload.fileName(), errors);
            boolean apply = errors.isEmpty() && !payload.dryRun();
            Result r = new Result(payload.fileName(), payload.dryRun(), apply, counts, List.copyOf(errors));
            if (apply) {
                audit.record("MASTER_IMPORT", null, "IMPORT", null, new ImportAudit(payload.sha256(), r));
            } else {
                status.setRollbackOnly();
            }
            return r;
        });
        return mapper.valueToTree(result);
    }
}
