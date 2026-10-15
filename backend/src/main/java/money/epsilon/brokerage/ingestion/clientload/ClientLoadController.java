package money.epsilon.brokerage.ingestion.clientload;

import money.epsilon.brokerage.platform.importing.UploadedFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import money.epsilon.brokerage.ingestion.clientload.ClientLoadService.Decisions;
import money.epsilon.brokerage.ingestion.clientload.ClientLoadService.Preview;
import money.epsilon.brokerage.platform.job.JobService;
import money.epsilon.brokerage.platform.security.Roles;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Load clients from an Investwell income file: preview first (saves nothing), then load (a job). */
@RestController
@RequestMapping("/api/client-load")
class ClientLoadController {

    private final ClientLoadService service;
    private final JobService jobs;
    private final ObjectMapper mapper;

    ClientLoadController(ClientLoadService service, JobService jobs, ObjectMapper mapper) {
        this.service = service;
        this.jobs = jobs;
        this.mapper = mapper;
    }

    @PostMapping("/preview")
    @PreAuthorize(Roles.MASTER_WRITE)
    Preview preview(@RequestParam("file") MultipartFile file) throws IOException {
        UploadedFile f = UploadedFile.of(file, "income.xlsx", "the Investwell MF file");
        return service.preview(f.content(), f.name());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize(Roles.MASTER_WRITE)
    Map<String, Long> load(@Valid @RequestBody LoadRequest req) {
        service.checkToken(req.token());
        var payload = new ClientLoadJob.Payload(req.token(), req.fileName(), new Decisions(req.from(), req.rmChoices(), req.partnerNames()));
        return Map.of("jobId", jobs.submit(ClientLoadJob.TYPE, mapper.valueToTree(payload)));
    }

    /**
     * @param rmChoices    file spelling → RM id, for spellings the user resolved (others resolve by alias / name)
     * @param partnerNames file sub-broker spelling → partner to use (an existing name merges into it)
     */
    record LoadRequest(@NotBlank String token, @NotBlank String fileName, @NotNull LocalDate from,
                       Map<String, Long> rmChoices, Map<String, String> partnerNames) {
    }
}
