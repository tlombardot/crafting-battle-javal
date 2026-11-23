# CRAFT LITE : Bataille Javale

## 1. Lancement

Thomas Lombardot

```bash
mvn test
```

*(Note : le projet tourne sous Java 25.)*

## 2. Diagnostic (tag `lite-start`)

Analyse faite avec SonarQube for IDE sur l'état de départ :
- Nombre d'issues : 137
- Classe la plus complexe : 

| # | Smell | Localisation | Pourquoi c'est un problème |
|---|-------|--------------|----------------------------|
| 1 | Code dupliqué + dépendances créées avec `new` | `MoteurJeu.java` (`executerTirJoueur`, `executerTourCPU`, lignes 21-23) | Les deux méthodes font presque exactement les mêmes étapes pour gérer un tir. Si je change une règle d'un côté, je risque d'oublier l'autre. En plus, comme les repositories et `Random` sont instanciés en dur dans la classe, je ne peux pas la tester sans la base de données et donc sont pas isolé comme il doît l'être pour des tests. |
| 2 | Méthode trop longue et trop imbriquée | `MoteurJeu.java` (`reparerJoueur`) | Il y a deux boucles imbriquées avec plusieurs `if` dedans et la méthode fait deux choses différentes, c'est galère à relire même j'ai un peu de mal à le comprendre. |
| 3 | Calcul de coordonnées dupliqué + taille `10` en dur | `GrilleOcean.java` (`estPlacementValide`, `placerVaisseau`) | Le calcul des cases occupées par un vaisseau est copié dans les deux méthodes, et la taille `10` revient en dur dans le code. |

**Smell choisi :** le n°1 dans `MoteurJeu.java`.  
J'ai pris celui-là parce que c'est du pur métier et que la classe dépend directement de la basee de données Supabase, qui ne répond même pas donc l'isoler avec des tests doubles sera vraiment utile dans un sens.

## 3. Tests écrits

Classe testée : `` (dans *** ``).

Tests unitaires :
- 
- 
- 

Test paramétré :
- `` : [avec `` sur ...]

Test double :
- 

## 4. Refactoring réalisé

Technique : ***   
Sécurisé par :  ***

Avant :
```java
```

Après :
```java
```

Commits :
