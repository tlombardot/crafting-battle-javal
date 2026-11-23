package school.coda.darill_thomas_louis.bataillejavale.core.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import school.coda.darill_thomas_louis.bataillejavale.core.event.ResultatTir;
import school.coda.darill_thomas_louis.bataillejavale.core.model.EtatJeu;
import school.coda.darill_thomas_louis.bataillejavale.core.model.Session;
import school.coda.darill_thomas_louis.bataillejavale.core.model.Vaisseau;
import school.coda.darill_thomas_louis.bataillejavale.infrastructure.database.JoueurRepository;
import school.coda.darill_thomas_louis.bataillejavale.infrastructure.database.PartieRepository;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MoteurJeuTest {

    @Mock
    private PartieRepository partieRepo;

    @Mock
    private JoueurRepository joueurRepo;

    private MoteurJeu moteur;

    @BeforeEach
    void setUp() {
        Session.idJoueur = 42;
        moteur = new MoteurJeu(new Random(0), partieRepo, joueurRepo);
    }

    @Test
    @DisplayName("Exécuter un tir sur une case vide retourne RATE avec le radar mis à jour")
    void executerTirJoueur_surCaseVide_retourneRateEtMetAJourLeRadar() {
        
        EtatJeu etat = new EtatJeu();
        Vaisseau patrouilleur = new Vaisseau("Patrouilleur", 2);
        etat.getJoueur2().getGrilleOcean().placerVaisseau(patrouilleur, 0, 0, true);
        etat.getJoueur2().getFlotte().add(patrouilleur);

        
        MoteurJeu.RapportTir rapport = moteur.executerTirJoueur(etat, 1, 5, 5, false);

        
        assertThat(rapport.resultat).isEqualTo(ResultatTir.RATE);
        assertThat(rapport.cible).isNull();
        assertThat(rapport.partieTerminee).isFalse();
        assertThat(etat.getJoueur1().getGrilleRadar().getHistoriqueTirs()[5][5]).isEqualTo(ResultatTir.RATE);
        assertThat(etat.getTourCourant()).isEqualTo(1);
        verify(partieRepo).mettreAJourPartie(1, etat);
    }

    @Test
    @DisplayName("Exécuter un tir sur un vaisseau qui n'est pas coulé retourne TOUCHE sans terminer la partie")
    void executerTirJoueur_surVaisseauNonCoule_retourneToucheSansTerminerLaPartie() {

        EtatJeu etat = new EtatJeu();
        Vaisseau patrouilleur = new Vaisseau("Patrouilleur", 2);
        etat.getJoueur2().getGrilleOcean().placerVaisseau(patrouilleur, 0, 0, true);
        etat.getJoueur2().getFlotte().add(patrouilleur);

        MoteurJeu.RapportTir rapport = moteur.executerTirJoueur(etat, 1, 0, 0, false);

        assertThat(rapport.resultat).isEqualTo(ResultatTir.TOUCHE);
        assertThat(rapport.cible).isSameAs(patrouilleur);
        assertThat(rapport.partieTerminee).isFalse();
        assertThat(patrouilleur.getCasesTouchees()).isEqualTo(1);
        assertThat(etat.getJoueur1().getGrilleRadar().getHistoriqueTirs()[0][0]).isEqualTo(ResultatTir.TOUCHE);
        verifyNoInteractions(joueurRepo);
    }

    @Test
    @DisplayName("Exécuter un tir qui coule le dernier vaisseau termine la partie en victoire")
    void executerTirJoueur_dernierVaisseauCoule_termineLaPartieEnVictoire() {
        
        EtatJeu etat = new EtatJeu();
        Vaisseau patrouilleur = new Vaisseau("Patrouilleur", 2);
        patrouilleur.setCasesTouchees(1);
        etat.getJoueur2().getGrilleOcean().placerVaisseau(patrouilleur, 0, 0, true);
        etat.getJoueur2().getFlotte().add(patrouilleur);

        MoteurJeu.RapportTir rapport = moteur.executerTirJoueur(etat, 7, 1, 0, false);

        assertThat(rapport.resultat).isEqualTo(ResultatTir.COULE);
        assertThat(rapport.partieTerminee).isTrue();
        assertThat(rapport.victoire).isTrue();
        verify(partieRepo).terminerPartie(7, "TERMINEE_VICTOIRE", 42);
        verify(joueurRepo).updateStats(true);
    }

    @Test
    @DisplayName("En mode invité, les joueurs sont inversés pour la sauvegarde, inverse bien joueur 1 et joueur 2")
    void executerTirJoueur_enModeInvite_inverseLesJoueursPourLaSauvegarde() {

        EtatJeu etat = new EtatJeu();
        ArgumentCaptor<EtatJeu> etatCaptor = ArgumentCaptor.forClass(EtatJeu.class);

        moteur.executerTirJoueur(etat, 10, 3, 4, true);

        verify(partieRepo).mettreAJourPartie(eq(10), etatCaptor.capture());
        EtatJeu etatSauvegarde = etatCaptor.getValue();
        assertThat(etatSauvegarde.getJoueur1()).isSameAs(etat.getJoueur2());
        assertThat(etatSauvegarde.getJoueur2()).isSameAs(etat.getJoueur1());
        assertThat(etatSauvegarde.getTourCourant()).isEqualTo(1);
    }

    @Test
    @DisplayName("Quand l'ordinateur coule le dernier vaisseau du joueur, la partie se termine en défaite")
    void executerTourCPU_couleDernierVaisseauJoueur_termineLaPartieEnDefaite() {
        MoteurJeu moteurDeterministe = new MoteurJeu(new StubRandom(2, 3), partieRepo, joueurRepo);
        EtatJeu etat = new EtatJeu();
        Vaisseau patrouilleur = new Vaisseau("Patrouilleur", 2);
        patrouilleur.setCasesTouchees(1);
        etat.getJoueur1().getGrilleOcean().placerVaisseau(patrouilleur, 2, 3, true);
        etat.getJoueur1().getFlotte().add(patrouilleur);

        MoteurJeu.RapportTir rapport = moteurDeterministe.executerTourCPU(etat, 5);

        assertThat(rapport.x).isEqualTo(2);
        assertThat(rapport.y).isEqualTo(3);
        assertThat(rapport.resultat).isEqualTo(ResultatTir.COULE);
        assertThat(rapport.partieTerminee).isTrue();
        assertThat(rapport.victoire).isFalse();
        assertThat(etat.getJoueur2().getGrilleRadar().getHistoriqueTirs()[2][3]).isEqualTo(ResultatTir.COULE);
        assertThat(etat.getTourCourant()).isEqualTo(1);
        verify(partieRepo).mettreAJourPartie(5, etat);
        verify(partieRepo).terminerPartie(5, "TERMINEE_DEFAITE", -1);
        verify(joueurRepo).updateStats(false);
    }

    private static class StubRandom extends Random {
        private final int[] valeurs;
        private int index = 0;

        StubRandom(int... valeurs) {
            this.valeurs = valeurs;
        }

        @Override
        public int nextInt(int bound) {
            return valeurs[index++];
        }
    }
}
