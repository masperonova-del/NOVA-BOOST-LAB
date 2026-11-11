# NOVA BOOST LAB — Spécification de complétude et plan de remédiation

**Référence de travail :** branche `feature/0.6.0-tunnel-routing`  
**Nature du document :** spécification technique et critères d'acceptation.  
**Règle de protection :** aucun changement sur `main`, aucun merge et aucun déploiement dans le cadre de ce document.

## 1. Décision d'architecture

Les versions 0.5.0 et 0.6.0 constituent des fondations partielles, pas encore des fonctions opérationnelles de relais ou de tunnel.

- 0.5.0 fournit une validation et un contrôle de santé HTTPS d'un endpoint. Ce n'est pas un relais réseau.
- 0.6.0 évalue des prérequis et une politique de routage prudente. Ce n'est pas un VPN opérationnel.
- Le moteur central 0.7.0 ne doit pas être développé avant que les contrats d'interface et les tests d'intégration ci-dessous soient satisfaits.

L'application doit rester honnête sur ses capacités : aucune promesse d'amélioration du ping d'un jeu tant qu'une mesure réelle, reproductible et comparative ne l'établit pas.

## 2. Architecture cible

### 2.1 Couches

1. **Présentation**
   - Écrans Compose dédiés : état réseau, analyse gaming, relais, tunnel, paramètres et diagnostic.
   - Les composables affichent l'état et transmettent les intentions utilisateur.
   - Aucun appel HTTP ou calcul métier long directement dans un composable.

2. **Coordination applicative**
   - ViewModels avec états UI immuables et événements explicites.
   - Cas d'utilisation : mesurer, comparer, sélectionner, démarrer, arrêter et restaurer.
   - Gestion du cycle de vie, annulation et erreurs.

3. **Domaine**
   - Modèles indépendants d'Android : candidat réseau, échantillon, relais, résultat de santé, état tunnel, décision de routage.
   - Interfaces de dépôt et de moteurs.
   - Politiques pures et déterministes, couvertes par tests unitaires.

4. **Infrastructure Android**
   - Adaptateur ConnectivityManager pour le réseau par défaut et les réseaux disponibles.
   - Adaptateur Wi-Fi pour les informations autorisées par Android.
   - Client HTTPS avec délais, annulation et fermeture systématique des ressources.
   - Persistance locale pour les préférences et les relais.
   - Service VPN uniquement dans le jalon tunnel, avec consentement Android et notification requise.

### 2.2 Modules/classes cibles

| Élément | Responsabilité | Dépend de |
|---|---|---|
| `NetworkRepository` | Exposer les réseaux et changements de réseau | Adaptateurs Android |
| `NetworkMeasurementEngine` | Mesures HTTPS et qualité des échantillons | Client HTTP, repository |
| `GamingSessionUseCase` | Orchestrer une session d'observation | Mesure, callbacks réseau |
| `RelayRepository` | CRUD local des relais configurés | Persistance |
| `RelayHealthChecker` | Vérifier un endpoint selon un contrat documenté | Client HTTP |
| `RelaySelectionUseCase` | Comparer les relais réellement compatibles | Santé, politique de sélection |
| `TunnelController` | Démarrer/arrêter le tunnel et exposer son état | Service VPN |
| `TunnelSafetyPolicy` | Autoriser/refuser le routage et prévoir le repli | État réseau, tunnel, upstream |
| `BoostCoordinator` | Coordonner les cas d'utilisation sans refaire leurs mesures | Interfaces des moteurs |
| ViewModels | Exposer des états UI et recevoir les actions | Cas d'utilisation |

Les noms sont des contrats de conception : ils ne doivent pas être considérés comme des classes déjà présentes.

## 3. Remédiation ordonnée

### Lot A — Assainissement structurel

- Extraire les opérations réseau et les calculs de `MainActivity`.
- Introduire ViewModels et états UI immuables.
- Isoler les composants Compose par fonctionnalité.
- Définir les interfaces domaine/infrastructure.
- Préserver le comportement existant pendant le refactoring.
- Corriger la documentation qui présente encore le scanner Wi-Fi comme une branche séparée alors qu'il figure dans la branche 0.6.0.

**Acceptation A :**
- `MainActivity` ne contient plus de logique réseau ni de requêtes HTTP.
- Les opérations longues sont annulables et ne bloquent pas le thread UI.
- Les changements d'orientation/recréation ne relancent pas involontairement une mesure.
- Les tests unitaires existants restent verts.
- APK Debug compilé et vérification manuelle des écrans existants.

### Lot B — Gestion réelle des relais

- Remplacer le contrôle d'un seul endpoint par une liste de relais configurables.
- Persister localement nom, URL, protocole déclaré, date du dernier contrôle et résultat.
- Valider strictement les URL : HTTPS uniquement par défaut, pas d'identifiants intégrés, pas de paramètres ou fragments non autorisés.
- Ajouter ajout, modification, suppression, activation/désactivation et test individuel.
- Connecter effectivement le sélecteur de relais à l'interface et au cas d'utilisation.
- Ne sélectionner que des relais dont le protocole est pris en charge par le client.

**Acceptation B :**
- Les relais persistent après fermeture et redémarrage.
- Les entrées invalides sont refusées avec un message explicite.
- Le sélecteur est appelé par le flux applicatif réel et testé en intégration.
- Les erreurs DNS, TLS, timeout, HTTP et annulation sont distinguées.
- Un endpoint HTTPS générique n'est jamais présenté comme un relais de transit.

### Lot C — Contrat de transport et tunnel opérationnel

Ce lot ne commence pas par coder un VPN. Il commence par définir le protocole et l'infrastructure amont.

- Documenter le protocole de transport : authentification, établissement, maintien, fermeture, erreurs, limites et compatibilité.
- Définir l'infrastructure serveur nécessaire et les conditions d'exploitation.
- Évaluer les exigences de confidentialité, sécurité, coûts et disponibilité.
- Implémenter ensuite un `VpnService` Android uniquement avec un protocole réellement compatible.
- Demander le consentement VPN via le mécanisme Android.
- Gérer MTU, DNS, IPv4/IPv6, routes, reconnexion, arrêt, notification et cycle de vie.
- Appliquer une politique fail-closed : ne jamais envoyer silencieusement le trafic dans une route non vérifiée ; restaurer le réseau Android si le tunnel échoue.

**Acceptation C :**
- Un test de bout en bout démontre un tunnel établi vers un serveur compatible.
- Le trafic de test autorisé traverse le tunnel et atteint sa destination.
- Le trafic est interrompu ou revient au chemin normal selon une politique explicitement testée en cas de panne.
- Les fuites DNS et les comportements IPv4/IPv6 sont vérifiés.
- Aucun tunnel n'est annoncé comme actif si le service et le transport ne le confirment pas.
- Les tests couvrent arrêt forcé, perte réseau, changement Wi-Fi/cellulaire et révocation du consentement.

### Lot D — Moteur central BOOST 0.7.0

- Créer un coordinateur qui consomme les résultats des moteurs existants.
- Éviter les mesures redondantes et les décisions contradictoires.
- Produire une décision explicable : chemin normal, relais candidat, tunnel autorisé, repli.
- Appliquer hystérésis, limites de fréquence, délais et garde-fous énergétiques.
- Séparer observation, recommandation et action effective.
- Ne jamais promettre une optimisation du trafic d'une autre application sans preuve du chemin de données réellement pris en charge par Android.

**Acceptation D :**
- Tests déterministes couvrant réseau stable, réseau instable, changement de transport, relais indisponible, tunnel défaillant et absence de réseau validé.
- Aucun cycle de bascule rapide entre deux chemins.
- Le moteur restaure le chemin normal après échec.
- L'interface explique quelle action a été prise et pourquoi.

### Lot E — Intégration produit 1.0

- Parcours utilisateur cohérent et états accessibles.
- Paramètres et consentements explicites.
- Historique local compréhensible et contrôlable.
- Messages qui distinguent mesure, estimation, disponibilité et action réellement appliquée.
- Documentation utilisateur et technique alignée sur le comportement réel.

## 4. Stratégie de tests obligatoire

| Niveau | Vérification | Critère |
|---|---|---|
| Unitaires | Sélection, validation, politiques, calculs | Tous les tests passent |
| Intégration | ViewModel + dépôts + adaptateurs simulés | Flux complets et erreurs vérifiés |
| UI | Navigation, états chargement/erreur/vide, annulation | Parcours critiques automatisés |
| Instrumentés | Permissions, callbacks réseau, cycle de vie | Résultats reproductibles sur appareil |
| Compilation | Debug et Release | Les deux variantes compilent |
| Qualité | Lint et analyse statique | Aucune erreur bloquante non justifiée |
| Sécurité | Validation URL, secrets, TLS, permissions | Aucun secret embarqué ; règles documentées |
| Terrain | Plusieurs réseaux et transitions | Mesures comparatives consignées |
| Stabilité | Sessions prolongées et interruptions | Pas de fuite de ressources ni de blocage |

Les tests simulés ne remplacent jamais les tests sur appareil. Les tests sur appareil ne prouvent pas à eux seuls une amélioration du ping de CODM.

## 5. Protocole de mesure des performances

Pour toute affirmation d'amélioration :
- comparer le chemin normal et le chemin candidat dans des conditions aussi proches que possible ;
- conserver plusieurs séries, pas une mesure isolée ;
- relever médiane, P95, taux d'échec et variation ;
- consigner transport, opérateur, heure, destination et état du réseau ;
- distinguer latence HTTPS vers une sonde et latence réelle du serveur de jeu ;
- rejeter toute conclusion lorsque les échantillons sont insuffisants ou non comparables.

## 6. Contraintes Android et limites produit

- `bindProcessToNetwork` ne redirige que le trafic du processus de NOVA BOOST LAB ; il ne force pas le trafic de CODM à passer par l'application.
- Le trafic d'autres applications exige une architecture VPN Android appropriée, avec consentement et contraintes de plateforme.
- Une application ne peut pas forcer l'activation des données mobiles ni créer une connectivité inexistante.
- Un relais distant doit réellement transférer le trafic selon un protocole compatible ; un endpoint HTTPS de santé ne suffit pas.
- Un tunnel peut augmenter la latence si le relais ou son itinéraire est mauvais.
- L'application ne doit jamais afficher « ping CODM optimisé » sur la seule base de mesures HTTPS génériques.

## 7. Ordre de livraison et portes de validation

1. **A — Architecture** : refactoring sans changement fonctionnel.
2. **B — Relais** : gestion persistante et sélection intégrée, sans prétendre transporter du trafic.
3. **C — Tunnel** : seulement après définition et disponibilité d'un protocole et d'un serveur compatibles.
4. **D — BOOST 0.7.0** : coordination des moteurs opérationnels.
5. **E — 1.0** : intégration, documentation et certification.

Chaque lot doit être développé sur une branche dédiée, comparé à sa base, testé et documenté. Aucun passage au lot suivant si les critères du lot courant ne sont pas remplis. Aucun merge dans `main` sans autorisation explicite.

## 8. État initial à conserver comme référence

- 0.5.0 : validée uniquement comme vérificateur de santé HTTPS, sous réserve.
- 0.6.0 : préparation de sécurité du tunnel, pas de tunnel opérationnel.
- Sélecteur de relais : présent mais non intégré au flux UI observé.
- Persistance des relais : absente.
- `VpnService` et transfert de paquets : absents.
- Moteur central BOOST : absent.
- Tests de Release, Lint, UI/instrumentés et validation terrain 0.5/0.6 : non démontrés dans l'audit précédent.

**Conclusion :** ce document définit les travaux nécessaires et leurs critères de sortie. Il ne certifie aucune fonction qui n'a pas encore été implémentée et testée.
