package money.epsilon.brokerage.ingestion.finesse;

import money.epsilon.brokerage.platform.importing.UploadedFile;
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
    private final money.epsilon.brokerage.client.service.ClientService clients;

    FinesseClientMasterController(FinesseClientMasterService service, money.epsilon.brokerage.client.service.ClientService clients) {
        this.service = service;
        this.clients = clients;
    }

    /** Everything the Ops client master has for this client, per client code (R-08). */
    @GetMapping("/api/clients/{id}/ops-master")
    @PreAuthorize(Roles.MASTER_READ)
    java.util.List<FinesseClientMasterService.OpsRecord> opsMaster(@org.springframework.web.bind.annotation.PathVariable long id) {
        var who = clients.identity(id);
        return service.records(who.panHash(), who.formerPanHashes(), who.codes().getOrDefault("FINESSE", java.util.List.of()), Roles.canSeeFullPan());
    }

    @GetMapping("/api/finesse/client-master")
    @PreAuthorize(Roles.MASTER_READ)
    Status status() {
        return service.status();
    }

    @PostMapping("/api/finesse/client-master")
    @PreAuthorize(Roles.MASTER_WRITE)
    LoadResult upload(@RequestParam("file") MultipartFile file) throws IOException {
        UploadedFile f = UploadedFile.of(file, "ClientMasterReport.xlsx", "the Finesse client master");
        return service.load(f.content(), f.name());
    }
}
