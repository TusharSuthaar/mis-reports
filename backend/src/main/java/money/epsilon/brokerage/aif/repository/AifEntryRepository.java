package money.epsilon.brokerage.aif.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import money.epsilon.brokerage.aif.domain.AifEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AifEntryRepository extends JpaRepository<AifEntry, Long> {

    List<AifEntry> findByMonthOrderByIdAsc(LocalDate month);

    List<AifEntry> findByMonthBetweenOrderByMonthDescIdDesc(LocalDate from, LocalDate to);

    long countByStatusAndPaymentStatusIn(AifEntry.Status status, Collection<AifEntry.PaymentStatus> paymentStatuses);

    /** Newest first: for suggestions (fund names, the mark-up last used). */
    List<AifEntry> findTop500ByStatusOrderByIdDesc(AifEntry.Status status);
}
