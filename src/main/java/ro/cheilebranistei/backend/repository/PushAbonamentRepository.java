package ro.cheilebranistei.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.cheilebranistei.backend.model.PushAbonament;
import java.util.Optional;

public interface PushAbonamentRepository extends JpaRepository<PushAbonament, Long> {
    Optional<PushAbonament> findByEndpoint(String endpoint);
}
