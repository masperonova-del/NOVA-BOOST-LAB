# NOVA BOOST LAB 0.3 — Tests de projection du moteur actif

## Objectif

Vérifier la politique qui sélectionne un chemin réseau parmi les chemins déjà disponibles et validés par Android. Les tests de sélection utilisent des données déterministes ; ils ne remplacent pas les mesures sur téléphone.

## Règles de sélection

- Un candidat doit avoir une médiane strictement positive.
- Au moins 2 réponses réussies sont nécessaires.
- Le nombre de réussites ne peut pas dépasser le nombre d'essais.
- Le candidat ayant la médiane la plus faible est retenu si aucun chemin courant mesurable n'existe.
- Si le chemin courant est mesurable, un changement n'est autorisé que si l'alternative est au moins 15 % plus rapide.
- Si aucun candidat n'est admissible, aucune décision n'est prise.

## Scénarios unitaires

| ID | Situation | Résultat attendu |
| --- | --- | --- |
| S01 | Aucun chemin courant mesurable | Sélection du chemin le plus rapide |
| S02 | Alternative au moins 15 % plus rapide | Sélection de l'alternative |
| S03 | Gain inférieur à 15 % | Conservation du chemin courant |
| S04 | Candidat avec une seule réussite | Candidat ignoré |
| S05 | Aucun candidat avec deux réussites | Aucune décision |
| S06 | Latence nulle ou compteur incohérent | Candidat rejeté |

## Vérifications physiques obligatoires

1. Installer l'APK debug sur un téléphone Android.
2. Lancer l'optimisation avec un seul réseau disponible.
3. Vérifier le résultat et confirmer que le trafic de NOVA BOOST LAB fonctionne encore.
4. Si Wi-Fi et données mobiles sont simultanément disponibles, vérifier que les deux chemins sont listés séparément.
5. Vérifier qu'une alternative n'est choisie que si le gain mesuré dépasse le seuil.
6. Appuyer sur « Restaurer le chemin par défaut » et vérifier que l'application retrouve l'accès réseau normal.
7. Couper le réseau pendant un essai et vérifier qu'aucune liaison obsolète n'est appliquée.

## Critères d'acceptation

- Tous les tests unitaires de sélection passent.
- La compilation debug réussit.
- La liaison est appliquée uniquement après validation du réseau sélectionné.
- La restauration du chemin par défaut est accessible.
- L'interface indique explicitement que la liaison ne concerne que NOVA BOOST LAB.
- Aucun résultat de simulation n'est présenté comme une mesure réelle.

## Hors périmètre

La version 0.3 ne route pas le trafic d'autres applications. L'analyse du trafic de jeu (0.4), l'infrastructure relais (0.5) et le tunnel/routage alternatif (0.6) restent des étapes distinctes de la feuille de route.
