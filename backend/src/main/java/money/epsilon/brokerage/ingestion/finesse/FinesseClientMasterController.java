package money.epsilon.brokerage.ingestion.finesse;

import java.io.IOException;
import money.epsilon.brokerage.ingestion.finesse.FinesseClientMasterService.LoadResult;
import money.epsilon.brokerage.ingestion.finesse.FinesseClientMasterService.Status;
import money.epsilon.brokerage.platform.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** The Finesse client master export: which PAN / guardian PAN each Finesse client code belongs to. */
@RestController
class FinesseClientMasterController {

    private final FinesseClientMasterService service;

    FinesseClientMasterController(FinesseClientMasterService service) {
        this.service = service;
    }

    @GetMapping("/api/finesse/client-master")
    @PreAuthorize(Roles.MASTER_READ)
    Status status() {
        return service.status();
    }

    @PostMapping("/api/finesse/client-master")
    @PreAuthorize(Roles.MASTER_WRITE)
    LoadResult upload(@RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "ClientMasterReport.xlsx" : file.getOriginalFilename();
        if (file.isEmpty()) throw new IllegalArgumentException("The file is empty");
        if (!name.toLowerCase().endsWith(".xlsx")) throw new IllegalArgumentException("Upload the Finesse client master .xlsx file");
        return service.load(file.getBytes(), name);
    }
}
