# NOVA BOOST LAB 0.6.0 — Tunnel & Alternative Routing

Application Android indépendante qui compare les chemins réseau déjà disponibles et peut lier le processus NOVA BOOST LAB au chemin mesuré comme le plus intéressant.

## Feuille de route officielle

| Version | Objectif |
| --- | --- |
| 0.1 | Moteur indépendant initial |
| 0.2 | Fiabilisation et compilation |
| **0.3** | **Smart Network Optimizer** |
| **0.4** | **Gaming Traffic Analyzer** |
| 0.5 | Infrastructure Relay |
| 0.6 | Tunnel et routage alternatif |
| 0.7 | Moteur BOOST central |
| 1.0 | Intégration complète dans l'application cible |

## Fonctionnalités réellement implémentées en 0.3

- Détection des réseaux Wi-Fi, cellulaires et Ethernet déjà disponibles et validés par Android.
- Mesure HTTPS indépendante sur chaque chemin, via l'objet Android `Network`.
- Sept sondes HTTPS par chemin candidat, avec au moins cinq réponses réussies exigées.
- Après changement de chemin, trois sondes supplémentaires vérifient l'application ; au moins deux doivent réussir.
- Comparaison des médianes et contrôle du nombre de réponses réussies.
- Hystérésis : changement seulement si l'alternative est au moins 15 % plus rapide que le chemin courant mesuré.
- Application réelle du chemin sélectionné au processus NOVA BOOST LAB avec `ConnectivityManager.bindProcessToNetwork`.
- Bouton de restauration du chemin réseau Android par défaut.
- Tests unitaires de la politique de sélection.

## Limite Android essentielle

La liaison `bindProcessToNetwork` ne concerne que le processus de cette application. Elle ne redirige pas le trafic d'autres applications comme Call of Duty Mobile. La 0.3 constitue un moteur actif de sélection de chemin pour NOVA BOOST LAB et une base réutilisable pour l'architecture ultérieure ; elle ne prétend pas optimiser le trafic d'un jeu externe.

Le moteur ne force pas l'activation du Wi-Fi ou des données mobiles. Il ne compare que les chemins que le système expose déjà comme disponibles et validés. Si Android ne présente qu'un seul réseau, le moteur ne peut pas en inventer un second.

Les mesures HTTPS comprennent la résolution DNS, l'établissement TCP, TLS et la réponse serveur. Elles ne sont ni un ping UDP ni une garantie de performance en jeu.

## Prototype expérimental — découverte Wi-Fi à proximité

La branche `feature/wifi-neighbor-discovery` ajoute un scan manuel des points d'accès Wi-Fi visibles :

- Affichage des SSID diffusés, de la puissance reçue (dBm), de la bande de fréquence et du type de sécurité annoncé.
- Classement par puissance du signal, avec une seule entrée par SSID (point d'accès au signal le plus fort).
- Résultats explicitement signalés comme actualisés ou potentiellement issus du cache Android ; refus de scan et délai dépassé sont également signalés.
- Classification de sécurité distinguant notamment WPA3, WPA3 Enhanced Open (OWE), réseaux d'entreprise, WPA/WPA2, WEP et réseaux ouverts.
- Autorisation de localisation précise et services de localisation requis selon les règles Android ; aucune coordonnée GPS n'est lue.
- Aucun BSSID n'est affiché ou conservé ; aucun réseau n'est rejoint automatiquement.

**Limite de mesure :** le scan radio ne permet pas de connaître le ping, le débit, la congestion réelle ni la qualité Internet d'un réseau auquel le téléphone n'est pas connecté. Il sert uniquement à repérer et comparer la puissance des signaux reçus. La connexion à un autre réseau reste une action de l'utilisateur.

Cette évolution est isolée sur une branche expérimentale et ne modifie pas la version publiée 0.3.0 ni la feuille de route officielle.


## Fonctionnalités ajoutées en 0.4.0 — Gaming Traffic Analyzer

- Session d'analyse manuelle au premier plan : jusqu'à 20 sondes HTTPS, avec une pause d'environ 1,5 seconde après chaque réponse ; la durée réelle varie selon les réponses et les changements de réseau.
- Sondes exécutées sur le réseau Android par défaut, sans changer le routage du processus.
- Vérification de l'état du réseau par défaut avant chaque sonde et après chaque réponse. Si Android bascule du Wi-Fi aux données mobiles, la prochaine sonde utilise le nouveau chemin ; une réponse obtenue sur l'ancien chemin après le basculement est écartée.
- En l'absence de réseau Internet validé, attente d'une fenêtre de récupération de 2,5 secondes. Si aucun chemin validé ne revient, la session s'arrête et conserve un rapport partiel au lieu de continuer jusqu'à 20 échecs artificiels.
- Actualisation en direct de l'état Wi-Fi, cellulaire et Ethernet dans l'écran via `ConnectivityManager.NetworkCallback`.
- Mesure du temps de réponse HTTPS médian et du taux de réussite des sondes.
- Variation médiane des temps de réponse entre sondes adjacentes réussies, explicitement présentée comme un indicateur indirect de stabilité et non comme le jitter UDP du jeu.
- Comptage des événements de perte du réseau par défaut signalés par `ConnectivityManager.NetworkCallback`, lorsque l'enregistrement du callback est autorisé.
- Résultats sans données inventées : si aucune sonde ne réussit, les latences restent indisponibles ; les sessions interrompues sont explicitement identifiées.
- Annulation coroutine respectée, délais réseau bornés et callbacks désenregistrés en fin de session et à la fermeture de l'écran.
- Aucun service en arrière-plan, aucune collecte distante, aucun serveur payant et aucune permission Android supplémentaire.

### Limites de mesure en 0.4.0

Les sondes utilisent HTTPS vers `https://www.google.com/generate_204`. La pause de 1,5 seconde commence après la fin de chaque requête : ce n'est pas un intervalle fixe entre les débuts de sondes. Elles mesurent le temps de réponse complet de la requête (réseau, DNS éventuel, TCP, TLS et serveur), pas un ping ICMP ou UDP. Les échecs comptabilisés sont des échecs de sondes HTTPS, pas des pertes de paquets du jeu. Les événements de perte rapportés par Android décrivent les événements du réseau par défaut, pas chaque microcoupure d'un flux applicatif. L'application ne lit pas, ne déchiffre pas et ne redirige pas le trafic de Call of Duty Mobile ou d'une autre application.

## Fonctionnalités ajoutées en 0.5.0 — Infrastructure Relay

- Validation stricte des points de contrôle HTTPS fournis par l'opérateur : nom borné, hôte obligatoire, HTTPS obligatoire, absence d'identifiants intégrés, de paramètres et de fragments.
- Sondes HTTPS bornées à 3 secondes de connexion et de lecture, sans redirection automatique.
- Affichage du code HTTP, de l'accessibilité et du temps de réponse mesuré.
- Sélection déterministe du point de contrôle joignable le plus rapide parmi les résultats mesurés.
- Tests unitaires de validation des URL et de sélection.
- Aucun secret ni jeton n'est demandé ou conservé.

**Limite fonctionnelle :** 0.5.0 vérifie la disponibilité d'un endpoint de santé. Cela ne prouve pas qu'un serveur relaie des paquets et ne crée pas de tunnel. Aucun serveur relais n'est fourni dans le dépôt ; aucun trafic CODM ou d'une autre application n'est redirigé.

## Fonctionnalités ajoutées en 0.6.0 — Tunnel et routage alternatif

- Évaluation explicite des prérequis avant tout futur démarrage VPN : réseau par défaut validé, contrôle de santé du relais, compatibilité du protocole, consentement Android et absence d'un autre VPN actif.
- Politique de routage fermée par défaut : un tunnel ne peut être retenu que s'il est établi, si le relais amont est sain et si le réseau reste validé ; sinon la politique demande le retour au chemin Android par défaut.
- État de préparation présenté dans l'interface, avec indication explicite du protocole relais manquant.
- Tests unitaires des états bloquants, de l'état prêt et des décisions de repli.

**Limite fonctionnelle importante :** cette version ne démarre pas encore de `VpnService` et ne redirige aucun paquet. Le point de contrôle HTTPS de 0.5.0 n'est pas un protocole de relais. Il manque un serveur amont compatible et son protocole de transport ; démarrer un VPN sans transfert amont fonctionnel risquerait de couper Internet. L'autorisation VPN Android n'est donc pas demandée prématurément.

## Tests et compilation

Configuration : JDK 17, Android SDK 35, Gradle 8.9.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

APK debug : `app/build/outputs/apk/debug/app-debug.apk`.

Les tests unitaires valident la politique de sélection, le classement des points d'accès, la classification de sécurité, les états de résultat du scan et les calculs de synthèse de l'analyse gaming. Un test sur téléphone reste nécessaire pour vérifier la visibilité des chemins réseau, la permission de liaison, le maintien de la connexion, la restauration du chemin par défaut et le comportement réel d'une session d'analyse.
