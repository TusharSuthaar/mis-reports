package money.epsilon.brokerage.reporting.end;

import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.reporting.end.EndFileService.EndFile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** The END workbook of a month: final when LOCKED, a DRAFT when PROCESSED. */
@RestController
class EndFileController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final EndFileService service;

    EndFileController(EndFileService service) {
        this.service = service;
    }

    @GetMapping("/api/months/{id}/end-file")
    @PreAuthorize(Roles.MASTER_READ)
    ResponseEntity<byte[]> download(@PathVariable long id) {
        EndFile f = service.build(id);
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.fileName()).build().toString())
                .body(f.content());
    }
}
