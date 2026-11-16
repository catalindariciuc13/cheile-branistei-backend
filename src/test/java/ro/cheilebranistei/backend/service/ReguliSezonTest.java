package ro.cheilebranistei.backend.service;

import org.junit.jupiter.api.Test;
import ro.cheilebranistei.backend.model.Rezervare;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReguliSezonTest {

    private static LocalDate d(int an, int luna, int zi) { return LocalDate.of(an, luna, zi); }

    private static Rezervare rez(LocalDate ci, LocalDate co, int persoane) {
        Rezervare r = new Rezervare();
        r.setDataCheckin(ci);
        r.setDataCheckout(co);
        r.setNrPersoane(persoane);
        r.setStatus(Rezervare.Status.CONFIRMATA);
        return r;
    }

    @Test
    void sezoane() {
        assertTrue(ReguliSezon.esteIarna(d(2026, 11, 1)));
        assertFalse(ReguliSezon.esteIarna(d(2026, 10, 31)));
        assertTrue(ReguliSezon.esteIarna(d(2027, 3, 31)));
        assertFalse(ReguliSezon.esteIarna(d(2027, 4, 1)));
        assertTrue(ReguliSezon.esteCraciun(d(2026, 12, 23)));
        assertTrue(ReguliSezon.esteCraciun(d(2026, 12, 27)));
        assertFalse(ReguliSezon.esteCraciun(d(2026, 12, 28)));
    }

    @Test
    void revelionBlocat() {
        assertFalse(ReguliSezon.esteBlocata(d(2026, 12, 29)));
        assertTrue(ReguliSezon.esteBlocata(d(2026, 12, 30)));
        assertTrue(ReguliSezon.esteBlocata(d(2027, 1, 2)));
        assertFalse(ReguliSezon.esteBlocata(d(2027, 1, 3)));   // ziua de check-out e liberă
        assertNotNull(ReguliSezon.verifica(d(2026, 12, 28), d(2026, 12, 31)));
        assertNull(ReguliSezon.verifica(d(2027, 1, 3), d(2027, 1, 5)));
        assertNull(ReguliSezon.verifica(d(2026, 12, 28), d(2026, 12, 30)));   // check-out pe 30 e ok
    }

    @Test
    void minimNopti() {
        assertNotNull(ReguliSezon.verifica(d(2026, 12, 5), d(2026, 12, 6)));     // iarna: minim 2
        assertNull(ReguliSezon.verifica(d(2026, 12, 5), d(2026, 12, 7)));
        assertNotNull(ReguliSezon.verifica(d(2026, 12, 24), d(2026, 12, 26)));   // Crăciun: minim 3
        assertNull(ReguliSezon.verifica(d(2026, 12, 24), d(2026, 12, 27)));
        assertNull(ReguliSezon.verifica(d(2026, 12, 21), d(2026, 12, 24)));      // atinge Crăciunul, dar are 3 nopți
        assertNotNull(ReguliSezon.verifica(d(2026, 12, 22), d(2026, 12, 24)));   // atinge Crăciunul cu doar 2 nopți
        assertNull(ReguliSezon.verifica(d(2027, 4, 10), d(2027, 4, 11)));        // în afara iernii: 1 noapte ok
    }

    @Test
    void iarnaDoarCasaIntreaga() {
        List<Rezervare> active = List.of(rez(d(2026, 12, 10), d(2026, 12, 12), 2));
        assertEquals(0, ReguliSezon.libere(active, d(2026, 12, 10)));            // o singură rezervare ocupă toată casa
        assertEquals(ReguliSezon.CAMERE, ReguliSezon.libere(active, d(2026, 12, 12)));
        List<Rezervare> vara = List.of(rez(d(2027, 7, 10), d(2027, 7, 12), 4));
        assertEquals(ReguliSezon.CAMERE - 2, ReguliSezon.libere(vara, d(2027, 7, 10)));  // vara: pe camere
    }
}
