# NOVA BOOST LAB 0.3.0 — Active Network Path Optimizer

Application Android indépendante qui compare les chemins réseau déjà disponibles et peut lier le processus NOVA BOOST LAB au chemin mesuré comme le plus intéressant.

## Feuille de route officielle

| Version | Objectif |
| --- | --- |
| 0.1 | Moteur indépendant initial |
| 0.2 | Fiabilisation et compilation |
| **0.3** | **Smart Network Optimizer** |
| 0.4 | Gaming Traffic Analyzer |
| 0.5 | Infrastructure Relay |
| 0.6 | Tunnel et routage alternatif |
| 0.7 | Moteur BOOST central |
| 1.0 | Intégration complète dans l'application cible |

## Fonctionnalités réellement implémentées en 0.3

- Détection des réseaux Wi-Fi, cellulaires et Ethernet déjà disponibles et validés par Android.
- Mesure HTTPS indépendante sur chaque chemin, via l'objet Android `Network`.
- Trois essais par chemin candidat.
- Comparaison des médianes et contrôle du nombre de réponses réussies.
- Hystérésis : changement seulement si l'alternative est au moins 15 % plus rapide que le chemin courant mesuré.
- Application réelle du chemin sélectionné au processus NOVA BOOST LAB avec `ConnectivityManager.bindProcessToNetwork`.
- Bouton de restauration du chemin réseau Android par défaut.
- Tests unitaires de la politique de sélection.

## Limite Android essentielle

La liaison `bindProcessToNetwork` ne concerne que le processus de cette application. Elle ne redirige pas le trafic d'autres applications comme Call of Duty Mobile. La 0.3 constitue un moteur actif de sélection de chemin pour NOVA BOOST LAB et une base réutilisable pour l'architecture ultérieure ; elle ne prétend pas optimiser le trafic d'un jeu externe.

Le moteur ne force pas l'activation du Wi-Fi ou des données mobiles. Il ne compare que les chemins que le système expose déjà comme disponibles et validés. Si Android ne présente qu'un seul réseau, le moteur ne peut pas en inventer un second.

Les mesures HTTPS comprennent la résolution DNS, l'établissement TCP, TLS et la réponse serveur. Elles ne sont ni un ping UDP ni une garantie de performance en jeu.

## Tests et compilation

Configuration : JDK 17, Android SDK 35, Gradle 8.9.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

APK debug : `app/build/outputs/apk/debug/app-debug.apk`.

Les tests unitaires valident la politique de sélection. Un test sur téléphone reste nécessaire pour vérifier la visibilité des chemins réseau, la permission de liaison, le maintien de la connexion et la restauration du chemin par défaut.
