package money.epsilon.brokerage.sharing.load;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import money.epsilon.brokerage.platform.job.JobService;
import money.epsilon.brokerage.platform.security.Roles;
import money.epsilon.brokerage.sharing.load.ShareLoadService.Preview;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Partner shares from the client master: preview first (saves nothing), then load (a job). */
@RestController
@RequestMapping("/api/share-load")
class ShareLoadController {

    private final ShareLoadService service;
    private final JobService jobs;
    private final ObjectMapper mapper;

    ShareLoadController(ShareLoadService service, JobService jobs, ObjectMapper mapper) {
        this.service = service;
        this.jobs = jobs;
        this.mapper = mapper;
    }

    @PostMapping("/preview")
    @PreAuthorize(Roles.MASTER_WRITE)
    Preview preview(@RequestParam("file") MultipartFile file, @RequestParam("from") LocalDate from) throws IOException {
        String name = file.getOriginalFilename() == null ? "master.xlsx" : file.getOriginalFilename();
        if (file.isEmpty()) throw new IllegalArgumentException("The file is empty");
        if (!name.toLowerCase().endsWith(".xlsx")) throw new IllegalArgumentException("Upload the master as an .xlsx file");
        return service.preview(file.getBytes(), name, from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize(Roles.MASTER_WRITE)
    Map<String, Long> load(@Valid @RequestBody LoadRequest req) {
        ShareLoadService.requireOwnToken(req.token());
        return Map.of("jobId", jobs.submit(ShareLoadJob.TYPE, mapper.valueToTree(new ShareLoadJob.Payload(req.token(), req.fileName(), req.from()))));
    }

    /** Master MIS names an RM differently (e.g. OLD RM NAME for the HEAD OFFICE book): remember which RM it means. */
    @PostMapping("/rm-spelling")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.MASTER_WRITE)
    void rmSpelling(@Valid @RequestBody RmSpellingRequest req) {
        service.rememberRmSpelling(req.spelling(), req.rmId());
    }

    record RmSpellingRequest(@NotBlank String spelling, @NotNull Long rmId) {
    }

    record LoadRequest(@NotBlank String token, @NotBlank String fileName, @NotNull LocalDate from) {
    }
}
