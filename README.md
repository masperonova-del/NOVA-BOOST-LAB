# NOVA BOOST LAB 0.3.0 — Smart Network Optimizer

Application Android indépendante de diagnostic et d'aide à la décision réseau.

La version 0.3 ajoute un moteur local qui interprète les mesures TCP et HTTPS de la 0.2 et produit une recommandation explicable. Il s'agit d'une optimisation **consultative** : le moteur n'applique aucune modification au réseau Android.

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

## Ce qui entre dans la 0.3

- Réutilisation des mesures TCP et HTTPS de la 0.2.
- Vérification de la disponibilité et de la validation Internet communiquées par Android.
- Contrôle du volume minimal de mesures valides.
- Analyse du taux de réussite de chaque série.
- Analyse de la dispersion des temps de réponse.
- Classification : absence d'accès, réseau non validé, données insuffisantes, connexion irrégulière, réponses lentes ou profil cohérent.
- Recommandation lisible et explicable affichée dans l'application.
- Tests unitaires déterministes du moteur de décision.

## Seuils heuristiques initiaux

Ces seuils servent uniquement à établir une première logique testable. Ils devront être calibrés avec des mesures répétées sur des appareils et des réseaux réels.

- Au moins 3 réponses valides dans chacune des séries TCP et HTTPS.
- Au moins 80 % de réussite dans chaque série.
- Dispersion (maximum - minimum) / médiane supérieure à 0,50 : profil irrégulier.
- Médiane TCP d'au moins 300 ms ou médiane HTTPS d'au moins 1 500 ms : réponses lentes sur les sondes.

La mesure HTTPS inclut plusieurs étapes (DNS, TCP, TLS et réponse du serveur). Elle ne constitue pas une mesure pure de latence. La médiane TCP et la médiane HTTPS restent donc deux indicateurs distincts.

## Ce qui est explicitement exclu de la 0.3

- Aucune activation ou création de VPN.
- Aucun tunnel, proxy ou relais distant.
- Aucun changement de route, d'APN ou de DNS.
- Aucun basculement automatique entre Wi-Fi et données mobiles.
- Aucune modification des paramètres système Android.
- Aucune interception ou analyse du trafic privé d'un jeu.
- Aucune mesure présentée comme le ping UDP réel de Call of Duty Mobile.
- Aucune promesse de réduction du ping ou d'augmentation du débit.

Le moteur peut conseiller une comparaison manuelle des accès ; il ne peut pas la réaliser à la place de l'utilisateur dans cette version.

## Tests de projection

Le plan détaillé est dans docs/PROJECTION_TESTS_0.3.md.

Exécution locale :

    gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug

Le workflow GitHub Actions exécute les tests unitaires avant de produire l'APK debug.

## Compilation

Configuration : JDK 17, Android SDK 35, Gradle 8.9.

APK debug :

    app/build/outputs/apk/debug/app-debug.apk

Une compilation réussie et des tests unitaires réussis ne remplacent pas un essai sur téléphone. Les performances réelles, la couverture radio, les serveurs de test et les conditions de jeu restent à vérifier sur appareil.
