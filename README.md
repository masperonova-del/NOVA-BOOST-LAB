# NOVA BOOST LAB 0.4.0 — Gaming Traffic Analyzer + Active Network Path Optimizer

Application Android indépendante qui compare les chemins réseau déjà disponibles et peut lier le processus NOVA BOOST LAB au chemin mesuré comme le plus intéressant.

## Feuille de route officielle

| Version | Objectif |
| --- | --- |
| 0.1 | Moteur indépendant initial |
| 0.2 | Fiabilisation et compilation |
| **0.3** | **Smart Network Optimizer** |
| **0.4** | **Gaming Traffic Analyzer** |
| 0.4 | Gaming Traffic Analyzer |
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

- Session d'analyse manuelle au premier plan : 20 sondes HTTPS espacées d'environ 1,5 seconde.
- Sondes exécutées sur le réseau Android par défaut, sans changer le routage du processus.
- Mesure du temps de réponse HTTPS médian et du taux de réussite des sondes.
- Variation médiane des temps de réponse entre sondes adjacentes réussies, explicitement présentée comme un indicateur indirect de stabilité et non comme le jitter UDP du jeu.
- Comptage des événements de perte du réseau par défaut signalés par `ConnectivityManager.NetworkCallback`, lorsque l'enregistrement du callback est autorisé.
- Résultats sans données inventées : si aucune sonde ne réussit, les latences restent indisponibles.
- Annulation coroutine respectée, délais réseau bornés et callback toujours désenregistré en fin de session.
- Aucun service en arrière-plan, aucune collecte distante, aucun serveur payant et aucune permission Android supplémentaire.

### Limites de mesure en 0.4.0

Les sondes utilisent HTTPS vers `https://www.google.com/generate_204`. Elles mesurent le temps de réponse complet de la requête (réseau, DNS éventuel, TCP, TLS et serveur), pas un ping ICMP ou UDP. Les échecs comptabilisés sont des échecs de sondes HTTPS, pas des pertes de paquets du jeu. Les événements de perte rapportés par Android décrivent les événements du réseau par défaut, pas chaque microcoupure d'un flux applicatif. L'application ne lit pas, ne déchiffre pas et ne redirige pas le trafic de Call of Duty Mobile ou d'une autre application.

## Tests et compilation

Configuration : JDK 17, Android SDK 35, Gradle 8.9.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

APK debug : `app/build/outputs/apk/debug/app-debug.apk`.

Les tests unitaires valident la politique de sélection, le classement des points d'accès, la classification de sécurité et les états de résultat du scan. Un test sur téléphone reste nécessaire pour vérifier la visibilité des chemins réseau, la permission de liaison, le maintien de la connexion et la restauration du chemin par défaut.
