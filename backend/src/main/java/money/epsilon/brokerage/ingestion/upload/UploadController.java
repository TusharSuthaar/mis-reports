package money.epsilon.brokerage.ingestion.upload;

import money.epsilon.brokerage.platform.importing.UploadedFile;
import java.io.IOException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import money.epsilon.brokerage.ingestion.upload.UploadService.CommitResult;
import money.epsilon.brokerage.ingestion.upload.UploadService.MonthView;
import money.epsilon.brokerage.ingestion.upload.UploadService.Review;
import money.epsilon.brokerage.platform.security.Roles;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Monthly MF brokerage upload (Finesse): upload (a job) → review → save the month. */
@RestController
class UploadController {

    private final UploadService service;

    UploadController(UploadService service) {
        this.service = service;
    }

    /** @param month e.g. 2026-08 */
    @PostMapping("/api/uploads")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize(Roles.MASTER_WRITE)
    Map<String, Long> upload(@RequestParam("file") MultipartFile file, @RequestParam String month) throws IOException {
        UploadedFile f = UploadedFile.of(file, "brokerage.xlsx", "the Finesse MF brokerage file");
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Month must look like 2026-08");
        }
        return Map.of("jobId", service.start(f, ym));
    }

    @GetMapping("/api/uploads/{id}/review")
    @PreAuthorize(Roles.MASTER_READ)
    Review review(@PathVariable long id) {
        return service.review(id);
    }

    /** The clients of this upload not in Master MIS, as an Excel list to fix them from. */
    @GetMapping("/api/uploads/{id}/not-in-master.xlsx")
    @PreAuthorize(Roles.MASTER_READ)
    ResponseEntity<byte[]> notInMaster(@PathVariable long id) {
        byte[] xlsx = NotInMasterWorkbook.build(service.notInMaster(id));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("Not_in_Master_MIS.xlsx").build().toString())
                .body(xlsx);
    }

    @PostMapping("/api/uploads/{id}/commit")
    @PreAuthorize(Roles.MASTER_WRITE)
    CommitResult commit(@PathVariable long id) {
        return service.commit(id);
    }

    @PostMapping("/api/uploads/{id}/discard")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void discard(@PathVariable long id) {
        service.discard(id);
    }

    @GetMapping("/api/months")
    @PreAuthorize(Roles.MASTER_READ)
    List<MonthView> months() {
        return service.months();
    }

}
