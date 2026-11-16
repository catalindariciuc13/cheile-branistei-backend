package ro.cheilebranistei.backend.service;

import ro.cheilebranistei.backend.model.Rezervare;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regulile de rezervare pe sezoane. O „noapte” e identificată prin data în care începe
 * (noaptea de 24 decembrie = de pe 24 pe 25).
 *
 * - Iarna (1 noiembrie – 31 martie): se închiriază doar toată casa, minim 2 nopți.
 *   O rezervare de iarnă ocupă toate camerele, iar o zi e liberă doar dacă e liberă toată casa.
 * - Crăciun (nopțile 23 – 27 decembrie): minim 3 nopți (pachetul de Crăciun).
 * - Nopți blocate (ex. Revelion deja rezervat): nu se pot rezerva de pe site.
 *
 * Contul de admin poate trece peste reguli (pentru rezervări introduse manual).
 */
public final class ReguliSezon {

    public static final int CAMERE = 7;
    public static final int PRET_CAMERA = 250;
    public static final int PRET_CASA = 3000;
    public static final int PACHET_CRACIUN = 8000;
    public static final int MIN_NOPTI_IARNA = 2;
    public static final int MIN_NOPTI_CRACIUN = 3;

    /** Revelion 2026/2027 — rezervat integral: check-in 30 dec, check-out 3 ian (nopțile 30, 31 dec, 1, 2 ian). */
    private static final List<LocalDate[]> BLOCATE = List.<LocalDate[]>of(
        new LocalDate[] { LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 3) }
    );

    private ReguliSezon() {}

    private static int lunaZi(LocalDate d) {
        return d.getMonthValue() * 100 + d.getDayOfMonth();
    }

    /** Iarna: 1 noiembrie – 31 martie. */
    public static boolean esteIarna(LocalDate noapte) {
        int md = lunaZi(noapte);
        return md >= 1101 || md <= 331;
    }

    /** Crăciun: nopțile 23 – 27 decembrie. */
    public static boolean esteCraciun(LocalDate noapte) {
        int md = lunaZi(noapte);
        return md >= 1223 && md <= 1227;
    }

    public static boolean esteBlocata(LocalDate noapte) {
        return BLOCATE.stream().anyMatch(p -> !noapte.isBefore(p[0]) && noapte.isBefore(p[1]));
    }

    /** Câte camere ocupă o rezervare într-o noapte: iarna toată casa, altfel câte o cameră la 2 persoane. */
    public static int camere(Rezervare r, LocalDate noapte) {
        if (esteIarna(noapte)) return CAMERE;
        int persoane = r.getNrPersoane() == null ? 1 : r.getNrPersoane();
        return (int) Math.ceil(persoane / 2.0);
    }

    /** Camere ocupate de rezervările active într-o noapte (blocările de pe site nu intră aici). */
    public static int ocupate(List<Rezervare> active, LocalDate noapte) {
        return active.stream()
            .filter(r -> r.getStatus() != Rezervare.Status.ANULATA)
            .filter(r -> !r.getDataCheckin().isAfter(noapte) && r.getDataCheckout().isAfter(noapte))
            .mapToInt(r -> camere(r, noapte))
            .sum();
    }

    /** Camere libere pentru o rezervare nouă din site, în noaptea dată (0 dacă e blocată sau, iarna, dacă nu e liberă toată casa). */
    public static int libere(List<Rezervare> active, LocalDate noapte) {
        if (esteBlocata(noapte)) return 0;
        int ocup = Math.min(CAMERE, ocupate(active, noapte));
        if (esteIarna(noapte)) return ocup == 0 ? CAMERE : 0;
        return CAMERE - ocup;
    }

    /** Verifică regulile de sezon pentru o rezervare din site. Întoarce mesajul de eroare sau null. */
    public static String verifica(LocalDate checkin, LocalDate checkout) {
        long nopti = ChronoUnit.DAYS.between(checkin, checkout);
        boolean iarna = false, craciun = false;
        for (LocalDate z = checkin; z.isBefore(checkout); z = z.plusDays(1)) {
            if (esteBlocata(z)) {
                return "Revelionul (30 decembrie – 3 ianuarie) este deja rezervat integral. Te rugăm să alegi alte date.";
            }
            iarna |= esteIarna(z);
            craciun |= esteCraciun(z);
        }
        if (craciun && nopti < MIN_NOPTI_CRACIUN) {
            return "În perioada Crăciunului (23 – 27 decembrie) se poate rezerva minim " + MIN_NOPTI_CRACIUN + " nopți.";
        }
        if (iarna && nopti < MIN_NOPTI_IARNA) {
            return "Iarna se închiriază toată casa, pentru minim " + MIN_NOPTI_IARNA + " nopți.";
        }
        return null;
    }

    /** Regulile trimise site-ului, ca să afișeze prețurile și minimul de nopți în calendar. */
    public static Map<String, Object> pentruSite() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("iarna", Map.of("start", "11-01", "sfarsit", "03-31"));
        m.put("craciun", Map.of("start", "12-23", "sfarsit", "12-27"));
        m.put("pretCamera", PRET_CAMERA);
        m.put("pretCasa", PRET_CASA);
        m.put("pachetCraciun", PACHET_CRACIUN);
        m.put("minNoptiIarna", MIN_NOPTI_IARNA);
        m.put("minNoptiCraciun", MIN_NOPTI_CRACIUN);
        return m;
    }
}
