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

    /**
     * NpsEntrys of a month range narrowed in the database (audit M-04): active only unless cancelled ones are asked for,
     * one RM, one partner. Same order as findByMonthBetweenOrderByMonthDescIdDesc.
     */
    @org.springframework.data.jpa.repository.Query("SELECT x FROM NpsEntry x WHERE x.month BETWEEN :from AND :to"
            + " AND (:withCancelled = TRUE OR x.status = :active) AND (:rmId IS NULL OR x.rmId = :rmId)"
            + " AND (:partnerId IS NULL OR x.partnerId = :partnerId) ORDER BY x.month DESC, x.id DESC")
    List<NpsEntry> search(@org.springframework.data.repository.query.Param("from") LocalDate from,
                     @org.springframework.data.repository.query.Param("to") LocalDate to,
                     @org.springframework.data.repository.query.Param("withCancelled") boolean withCancelled,
                     @org.springframework.data.repository.query.Param("active") NpsEntry.Status active,
                     @org.springframework.data.repository.query.Param("rmId") Long rmId,
                     @org.springframework.data.repository.query.Param("partnerId") Long partnerId);
}
