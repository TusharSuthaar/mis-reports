package money.epsilon.brokerage.nps.repository;

import java.time.LocalDate;
import java.util.List;
import money.epsilon.brokerage.nps.domain.NpsEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NpsEntryRepository extends JpaRepository<NpsEntry, Long> {

    List<NpsEntry> findByMonthBetweenOrderByMonthDescIdDesc(LocalDate from, LocalDate to);

    /** Newest first: for suggestions (POPs). */
    List<NpsEntry> findTop500ByStatusOrderByIdDesc(NpsEntry.Status status);

    List<NpsEntry> findByMonthOrderByIdAsc(LocalDate month);
}
