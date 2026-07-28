package ro.cheilebranistei.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.cheilebranistei.backend.model.CheckinOaspete;
import java.time.LocalDateTime;
import java.util.List;

public interface CheckinOaspeteRepository extends JpaRepository<CheckinOaspete, Long> {
    List<CheckinOaspete> findByRezervareId(Long rezervareId);
    List<CheckinOaspete> findByDataCreareBefore(LocalDateTime limita);
}
