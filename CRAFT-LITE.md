# CRAFT LITE : Bataille Javale

## 1. Lancement

Thomas Lombardot

```bash
mvn test
```

*(Note : le projet tourne sous Java 25.)*

## 2. Diagnostic (tag `lite-start`)

Analyse faite avec SonarQube sur l'état de départ :
- Nombre d'issues : 137
- Classe la plus complexe : MoteurJeu.java

| # | Smell | Localisation | Pourquoi c'est un problème |
|---|-------|--------------|----------------------------|
| 1 | Code dupliqué + dépendances créées avec `new` | `MoteurJeu.java` (`executerTirJoueur`, `executerTourCPU`, lignes 21-23) | Les deux méthodes font presque exactement les mêmes étapes pour gérer un tir. Si je change une règle d'un côté, je risque d'oublier l'autre. En plus, comme les repositories et `Random` sont instanciés en dur dans la classe, je ne peux pas la tester sans la base de données et ce n'est pas isolé comme ça doit l'être pour des tests. |
| 2 | Méthode trop longue et trop imbriquée | `MoteurJeu.java` (`reparerJoueur`) | Il y a deux boucles imbriquées avec plusieurs `if` dedans et la méthode fait deux choses différentes, c'est galère à relire même moi j'ai un peu de mal à la comprendre. |
| 3 | Calcul de coordonnées dupliqué + taille `10` en dur | `GrilleOcean.java` (`estPlacementValide`, `placerVaisseau`) | Le calcul des cases occupées par un vaisseau est copié dans les deux méthodes, et la taille `10` revient en dur dans le code. |

**Smell choisi :** le 1er dans `MoteurJeu.java` (et j'ai aussi eu le temps de faire le 2e en deuxième refactoring).  
J'ai pris celui-là parce que c'est du pur métier et que la classe dépend directement de la base de données Supabase, qui ne répond même plus puisque qu'il a été abandonnée donc l'isoler avec des tests doubles avait vraiment du sens.

## 3. Tests écrits

Classe testée : `MoteurJeu` (dans `src/test/java/school/coda/darill_thomas_louis/bataillejavale/core/engine/MoteurJeuTest.java`).

Tests unitaires :
- `executerTirJoueur_surCaseVide_retourneRateEtMetAJourLeRadar` : vérifie un tir dans l'eau (`RATE`, radar mis à jour, tour incrémenté, sauvegarde appelée).
- `executerTirJoueur_surVaisseauNonCoule_retourneToucheSansTerminerLaPartie` : vérifie un tir qui touche un vaisseau sans le couler (`TOUCHE`, partie toujours en cours, pas d'appel à `joueurRepo`).
- `executerTirJoueur_dernierVaisseauCoule_termineLaPartieEnVictoire` : vérifie la fin de partie en victoire quand le dernier vaisseau ennemi coule (`COULE`, clôture de partie et mise à jour des stats).
- `executerTirJoueur_enModeInvite_inverseLesJoueursPourLaSauvegarde` : vérifie qu'en mode invité (`estInvite = true`), `joueur1` et `joueur2` sont bien inversés dans l'état envoyé en base.
- `executerTourCPU_couleDernierVaisseauJoueur_termineLaPartieEnDefaite` : vérifie le tour du CPU avec un `Random` contrôlé jusqu'à la défaite du joueur.
- `reparerLiensMemoireSauvegarde_reattacheLesReferencesDesVaisseauxSurLePlateau` : vérifie que les copies désérialisées sur le plateau sont bien remplacées par les vraies instances de la flotte.
- `reparerLiensMemoireSauvegarde_recalculeLesDegatsDepuisHistoriqueRadar` : vérifie la remise à zéro et le recalcul des dégâts selon les tirs `TOUCHE` / `COULE` de l'historique radar.

Test paramétré (4 données) :
- `verifierAdversaireRejoint_selonStatutPartie_retourneResultatAttendu` : `@ParameterizedTest` avec `@CsvSource` sur `EN_COURS` (`true`), `EN_ATTENTE` (`false`), `TERMINEE_VICTOIRE` (`false`) et `NULL` (`false`).

Test doubles utilisés :
- Mocks Mockito sur `PartieRepository` et `JoueurRepository` (injectés par constructeur dans `MoteurJeu`) pour ne pas dépendre de la base Supabase qui n'existe plus.
- Un Stub (`StubRandom extends Random`) pour forcer les coordonnées tirées par le CPU dans `executerTourCPU` et avoir un test déterministe.

## 4. Refactorings réalisés

### Refactoring 1 : suppression de la duplication entre `executerTirJoueur` et `executerTourCPU`

Technique : Extract Method (avec injection des dépendances par constructeur en amont).  
Sécurisé par : les 5 premiers tests unitaires sur `executerTirJoueur` et `executerTourCPU`.

Avant :
```java
public RapportTir executerTirJoueur(EtatJeu etat, int idPartie, int x, int y, boolean estInvite) {
    JoueurPlay attaquant = etat.getJoueur1();
    JoueurPlay defenseur = etat.getJoueur2();

    RapportTir rapport = new RapportTir();
    rapport.x = x; rapport.y = y;
    rapport.cible = defenseur.getGrilleOcean().getVaisseauAt(x, y);
    rapport.resultat = defenseur.getGrilleOcean().recevoirTir(x, y);
    attaquant.getGrilleRadar().enregistrerTir(x, y, rapport.resultat);

    etat.setTourCourant(etat.getTourCourant() + 1);
    partieRepo.mettreAJourPartie(idPartie, preparerEtatPourDB(etat, estInvite));

    verifierEtCloturerPartie(etat, idPartie, rapport);
    return rapport;
}
```

Après :
```java
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
```

### Refactoring 2 : découpage de `reparerJoueur`

Technique : Extract Method (séparation des deux responsabilités + suppression des `10` en dur).  
Sécurisé par : les 2 tests sur `reparerLiensMemoireSauvegarde`.

Avant :
```java
private void reparerJoueur(JoueurPlay defenseur, JoueurPlay attaquant) {
    // 5 niveaux d'imbrication pour remmetre les vaisseaux + 2e double boucle pour recalculer les dégâts
    // subis
}
```

Après :
```java
private void reparerJoueur(JoueurPlay defenseur, JoueurPlay attaquant) {
    reattacherVaisseauxSurPlateau(defenseur);
    recalculerDegatsDepuisHistorique(defenseur, attaquant);
}
```

Commits concernés :
- `5d980b0` `feat: change MoteurJeu for test & fix dependency`
- `966ed1b` à `9c21969` : les commits `test(moteur): ...` écrits avant le 1er refactoring
- `5482959` `refactor(moteur): enhance readability & delete duplicate code`
- `22559df` `test(moteur): verify save repair on case and ship`
- `a2d3dd5` `refactor(moteur): improve repairPlayer function`

## 5. Ce qui reste à faire

- Smell non traité : le 3e dans `GrilleOcean.java` (duplication du calcul des cases et taille `10` en dur).
- Bugs potentiels repérés en relisant le code :
  - `GrilleOcean.estPlacementValide` ne vérifie pas si `startX < 0` ou `startY < 0`, ce qui provoque une `ArrayIndexOutOfBoundsException` en cas de coordonnées négatives.
  - `Vaisseau.recevoirDegat(int x, int y)` ignore `x` et `y` et incrémente `casesTouchees` à chaque appel (si on tire deux fois sur la même case, le vaisseau prend deux dégâts).
- Avec une heure de plus : j'écrirais des tests sur `GrilleOcean` et `Vaisseau`, je refactorerais `GrilleOcean`.
