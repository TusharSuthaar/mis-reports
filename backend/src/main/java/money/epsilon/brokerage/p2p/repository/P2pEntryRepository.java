package money.epsilon.brokerage.p2p.repository;

import java.time.LocalDate;
import java.util.List;
import money.epsilon.brokerage.p2p.domain.P2pEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface P2pEntryRepository extends JpaRepository<P2pEntry, Long> {

    List<P2pEntry> findByMonthBetweenOrderByMonthDescIdDesc(LocalDate from, LocalDate to);

    /** Newest first: for suggestions (platforms, schemes). */
    List<P2pEntry> findTop500ByStatusOrderByIdDesc(P2pEntry.Status status);
}
