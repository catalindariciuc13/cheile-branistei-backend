package ro.cheilebranistei.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ro.cheilebranistei.backend.model.Rezervare;
import java.time.LocalDate;
import java.util.List;

public interface RezervareRepository extends JpaRepository<Rezervare, Long> {

    List<Rezervare> findAllByOrderByDataCreareDesc();

    // Calculeaza total camere ocupate in perioada respectiva
    // Exclude doar ANULATA — numara PENDING, CONFIRMATA si BLOCAT
    @Query(value = "SELECT COALESCE(SUM(CEIL(nr_persoane / 2.0)), 0) FROM rezervari " +
                   "WHERE status != 'ANULATA' " +
                   "AND data_checkin < :checkout AND data_checkout > :checkin",
           nativeQuery = true)
    Integer getCamereOcupate(@Param("checkin") LocalDate checkin,
                              @Param("checkout") LocalDate checkout);

    // Rezervarile active (ne-anulate) care se suprapun cu perioada data
    List<Rezervare> findByStatusNotAndDataCheckinLessThanAndDataCheckoutGreaterThan(
            Rezervare.Status status, LocalDate checkout, LocalDate checkin);
}