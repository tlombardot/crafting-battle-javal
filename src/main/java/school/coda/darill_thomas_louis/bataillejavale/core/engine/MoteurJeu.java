package school.coda.darill_thomas_louis.bataillejavale.core.engine;

import school.coda.darill_thomas_louis.bataillejavale.core.event.ResultatTir;
import school.coda.darill_thomas_louis.bataillejavale.core.model.EtatJeu;
import school.coda.darill_thomas_louis.bataillejavale.core.model.JoueurPlay;
import school.coda.darill_thomas_louis.bataillejavale.core.model.Session;
import school.coda.darill_thomas_louis.bataillejavale.core.model.Vaisseau;
import school.coda.darill_thomas_louis.bataillejavale.infrastructure.database.JoueurRepository;
import school.coda.darill_thomas_louis.bataillejavale.infrastructure.database.PartieRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Le cœur logique du jeu. Indépendant de l'interface graphique.
 */
public class MoteurJeu {

    private final Random random;
    private final PartieRepository partieRepo;
    private final JoueurRepository joueurRepo;

    public MoteurJeu() {
        this(new Random(), new PartieRepository(), new JoueurRepository());
    }

    public MoteurJeu(Random random, PartieRepository partieRepo, JoueurRepository joueurRepo) {
        this.random = random;
        this.partieRepo = partieRepo;
        this.joueurRepo = joueurRepo;
    }

    // --- INITIALISATION ---

    public List<Vaisseau> genererFlotteStandard() {
        return new ArrayList<>(Arrays.asList(
                new Vaisseau("Porte-avions", 5), new Vaisseau("Cuirassé", 4),
                new Vaisseau("Destroyer", 3), new Vaisseau("Sous-marin", 3), new Vaisseau("Patrouilleur", 2)
        ));
    }

    public int initialiserPartieSolo(EtatJeu etat) {
        placerBateauxCPU(etat.getJoueur2());
        return partieRepo.creerNouvellePartie(etat);
    }

    public int hebergerPartieMulti(EtatJeu etat) {
        return partieRepo.hebergerPartieMulti(etat);
    }

    public void rejoindrePartieMulti(int idPartie, EtatJeu etat) {
        partieRepo.demarrerPartieMulti(idPartie, preparerEtatPourDB(etat, true));
    }

    // --- STRUCTURE DE RETOUR POUR L'UI ---

    public static class RapportTir {
        public int x, y;
        public ResultatTir resultat;
        public Vaisseau cible;
        public boolean partieTerminee;
        public boolean victoire;
    }

    // --- ACTIONS DE COMBAT ---

    public RapportTir executerTirJoueur(EtatJeu etat, int idPartie, int x, int y, boolean estInvite) {
        RapportTir rapport = resoudreTir(etat.getJoueur1(), etat.getJoueur2(), x, y);
        finaliserTour(etat, idPartie, estInvite, rapport);
        return rapport;
    }

    public RapportTir executerTourCPU(EtatJeu etat, int idPartie) {
        int[] coords = calculerCibleCPU(etat.getJoueur2());
        RapportTir rapport = resoudreTir(etat.getJoueur2(), etat.getJoueur1(), coords[0], coords[1]);
        finaliserTour(etat, idPartie, false, rapport);
        return rapport;
    }

    private RapportTir resoudreTir(JoueurPlay attaquant, JoueurPlay defenseur, int x, int y) {
        RapportTir rapport = new RapportTir();
        rapport.x = x;
        rapport.y = y;
        rapport.cible = defenseur.getGrilleOcean().getVaisseauAt(x, y);
        rapport.resultat = defenseur.getGrilleOcean().recevoirTir(x, y);
        attaquant.getGrilleRadar().enregistrerTir(x, y, rapport.resultat);
        return rapport;
    }

    private void finaliserTour(EtatJeu etat, int idPartie, boolean estInvite, RapportTir rapport) {
        etat.setTourCourant(etat.getTourCourant() + 1);
        partieRepo.mettreAJourPartie(idPartie, preparerEtatPourDB(etat, estInvite));
        verifierEtCloturerPartie(etat, idPartie, rapport);
    }

    private void verifierEtCloturerPartie(EtatJeu etat, int idPartie, RapportTir rapport) {
        if (etat.getJoueur2().aPerdu()) {
            rapport.partieTerminee = true; rapport.victoire = true;
            partieRepo.terminerPartie(idPartie, "TERMINEE_VICTOIRE", Session.idJoueur);
            joueurRepo.updateStats(true);
        } else if (etat.getJoueur1().aPerdu()) {
            rapport.partieTerminee = true; rapport.victoire = false;
            partieRepo.terminerPartie(idPartie, "TERMINEE_DEFAITE", -1);
            joueurRepo.updateStats(false);
        } else {
            rapport.partieTerminee = false;
        }
    }

    public void enregistrerDefaiteDistante() {
        joueurRepo.updateStats(false);
    }

    // --- SYNCHRONISATION RÉSEAU & RÉPARATION JSON ---

    public boolean verifierAdversaireRejoint(int idPartie) {
        return "EN_COURS".equals(partieRepo.getStatutPartie(idPartie));
    }

    public EtatJeu ecouterReseau(int idPartie, int tourLocal, boolean estInvite) {
        EtatJeu dbEtat = partieRepo.chargerPartieActiveOuTerminee(idPartie);
        if (dbEtat != null && dbEtat.getTourCourant() > tourLocal) {
            EtatJeu nouvelEtat = estInvite ? preparerEtatPourDB(dbEtat, true) : dbEtat;
            reparerLiensMemoireSauvegarde(nouvelEtat);
            return nouvelEtat;
        }
        return null;
    }

    public void reparerLiensMemoireSauvegarde(EtatJeu etat) {
        reparerJoueur(etat.getJoueur1(), etat.getJoueur2());
        reparerJoueur(etat.getJoueur2(), etat.getJoueur1());
    }

    // --- OUTILS INTERNES (IA et Mémoire) ---

    private void placerBateauxCPU(JoueurPlay cpu) {
        for (Vaisseau navire : genererFlotteStandard()) {
            boolean place = false;
            while (!place) {
                place = cpu.getGrilleOcean().placerVaisseau(navire, random.nextInt(10), random.nextInt(10), random.nextBoolean());
                if (place) cpu.getFlotte().add(navire);
            }
        }
    }

    private int[] calculerCibleCPU(JoueurPlay cpu) {
        int cx;
        int cy;
        do {
            cx = random.nextInt(10); cy = random.nextInt(10);
        } while (cpu.getGrilleRadar().getHistoriqueTirs()[cx][cy] != null);
        return new int[]{cx, cy};
    }

    private EtatJeu preparerEtatPourDB(EtatJeu etatLocal, boolean estInvite) {
        if (estInvite) {
            EtatJeu dbEtat = new EtatJeu();
            dbEtat.setJoueur1(etatLocal.getJoueur2());
            dbEtat.setJoueur2(etatLocal.getJoueur1());
            dbEtat.setTourCourant(etatLocal.getTourCourant());
            return dbEtat;
        }
        return etatLocal;
    }

    private void reparerJoueur(JoueurPlay defenseur, JoueurPlay attaquant) {
        reattacherVaisseauxSurPlateau(defenseur);
        recalculerDegatsDepuisHistorique(defenseur, attaquant);
    }

    private void reattacherVaisseauxSurPlateau(JoueurPlay defenseur) {
        Vaisseau[][] plateau = defenseur.getGrilleOcean().getPlateau();
        List<Vaisseau> flotte = defenseur.getFlotte();
        for (int x = 0; x < defenseur.getGrilleOcean().getTailleX(); x++) {
            for (int y = 0; y < defenseur.getGrilleOcean().getTailleY(); y++) {
                if (plateau[x][y] != null) {
                    plateau[x][y] = trouverVaisseauParNom(flotte, plateau[x][y]);
                }
            }
        }
    }

    private Vaisseau trouverVaisseauParNom(List<Vaisseau> flotte, Vaisseau cible) {
        for (Vaisseau vraiVaisseau : flotte) {
            if (vraiVaisseau.getNom().equals(cible.getNom())) {
                return vraiVaisseau;
            }
        }
        return cible;
    }

    private void recalculerDegatsDepuisHistorique(JoueurPlay defenseur, JoueurPlay attaquant) {
        for (Vaisseau vaisseau : defenseur.getFlotte()) {
            vaisseau.setCasesTouchees(0);
        }
        Vaisseau[][] plateau = defenseur.getGrilleOcean().getPlateau();
        ResultatTir[][] historique = attaquant.getGrilleRadar().getHistoriqueTirs();
        for (int x = 0; x < defenseur.getGrilleOcean().getTailleX(); x++) {
            for (int y = 0; y < defenseur.getGrilleOcean().getTailleY(); y++) {
                if (estTirReussi(historique[x][y]) && plateau[x][y] != null) {
                    plateau[x][y].recevoirDegat(x, y);
                }
            }
        }
    }

    private boolean estTirReussi(ResultatTir resultat) {
        return resultat == ResultatTir.TOUCHE || resultat == ResultatTir.COULE;
    }
}