package money.epsilon.brokerage.mapping.repository;

import java.util.Optional;
import money.epsilon.brokerage.mapping.domain.SourceAlias;
import money.epsilon.brokerage.mapping.domain.SourceAlias.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SourceAliasRepository extends JpaRepository<SourceAlias, Long> {

    Optional<SourceAlias> findBySourceAndEntityTypeAndRawValueNormalized(String source, EntityType type, String raw);

    @Modifying
    @Query("UPDATE SourceAlias a SET a.targetId = :to WHERE a.entityType = :type AND a.targetId = :from")
    int retarget(@Param("type") EntityType type, @Param("from") long from, @Param("to") long to);
}
