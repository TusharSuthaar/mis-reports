package money.epsilon.brokerage.masterimport;

import money.epsilon.brokerage.platform.importing.UploadedFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import money.epsilon.brokerage.platform.job.JobService;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.platform.storage.FileStore;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Masters workbook: download the template, upload a filled one (runs as a job; poll /api/jobs/{id}). */
@RestController
@RequestMapping("/api/imports/masters")
class MasterImportController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final FileStore files;
    private final JobService jobs;
    private final ObjectMapper mapper;

    MasterImportController(FileStore files, JobService jobs, ObjectMapper mapper) {
        this.files = files;
        this.jobs = jobs;
        this.mapper = mapper;
    }

    @GetMapping("/template")
    @PreAuthorize(Roles.MASTER_READ)
    ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("masters_template.xlsx").build().toString())
                .body(MasterTemplate.build());
    }

    /** @param dryRun true = check the file and report what would change, without saving anything */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize(Roles.MASTER_WRITE)
    Map<String, Long> upload(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean dryRun)
            throws IOException {
        UploadedFile f = UploadedFile.of(file, "upload.xlsx", "the masters workbook");
        String path = files.save(MasterImportJob.AREA, f.content(), ".xlsx");
        var payload = new MasterImportJob.Payload(path, f.name(), f.sha256(), dryRun);
        return Map.of("jobId", jobs.submit(MasterImportJob.TYPE, mapper.valueToTree(payload)));
    }

}
