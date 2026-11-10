# NOVA BOOST LAB 0.3 — Tests de projection du moteur actif

## Objectif

Vérifier la sélection d'un chemin réseau parmi les chemins disponibles et validés par Android, son application au processus NOVA BOOST LAB et la vérification de sa connectivité après application. Les tests déterministes ne remplacent pas les essais sur téléphone.

## Règles de sélection et de sécurité

- Le chemin courant du processus est lu avec `ConnectivityManager.getBoundNetworkForProcess()`; le réseau actif Android sert de repli si le processus n'est pas lié.
- Si le réseau par défaut ou le réseau lié au processus est un VPN, l'optimisation est suspendue pour ne pas contourner sa politique.
- Un candidat doit avoir une médiane strictement positive, au moins 2 réponses réussies et un compteur cohérent.
- Si le chemin courant est mesurable, un changement n'est autorisé que si l'alternative est au moins 15 % plus rapide.
- Si le chemin retenu est déjà le chemin courant, l'application indique qu'il est conservé, et non qu'un changement a eu lieu.
- Après une nouvelle liaison, le moteur vérifie de nouveau la validation Android et exécute trois sondes HTTPS. Si moins de deux réussissent, il retire la liaison et rend la main au réseau par défaut.
- Aucun résultat de simulation n'est présenté comme une mesure réelle.

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
2. Avec un seul réseau disponible, vérifier que le moteur indique qu'il conserve le chemin courant et qu'aucune comparaison multi-réseau n'a été possible.
3. Si Wi-Fi et données mobiles sont simultanément disponibles et validés, vérifier que les deux chemins sont listés séparément.
4. Vérifier qu'une alternative n'est choisie que si son gain mesuré atteint 15 %.
5. Vérifier qu'une seconde optimisation reconnaît correctement le réseau déjà lié au processus.
6. Avec un VPN actif, vérifier que l'optimisation est suspendue et que le VPN n'est pas contourné.
7. Vérifier les sondes post-application et la restauration automatique si elles échouent.
8. Appuyer sur « Restaurer le chemin par défaut » et vérifier que l'application retrouve l'accès réseau normal.
9. Couper un réseau pendant un essai et vérifier qu'aucune liaison devenue invalide n'est conservée.

## Critères d'acceptation

- Tous les tests unitaires de sélection passent.
- La compilation debug réussit.
- Le réseau courant du processus est identifié correctement.
- Un VPN actif bloque l'optimisation.
- Une liaison nouvelle est vérifiée après application.
- L'interface distingue chemin conservé, chemin modifié et échec.
- La restauration du chemin par défaut reste accessible.
- L'interface précise que la liaison ne concerne que NOVA BOOST LAB.

## Limites et hors périmètre

La découverte repose encore sur la liste des réseaux exposés par Android au moment du lancement de l'optimisation ; le suivi événementiel continu par `registerNetworkCallback()` reste une amélioration à réaliser. La version 0.3 ne route pas le trafic d'autres applications. L'analyse du trafic de jeu (0.4), l'infrastructure relais (0.5) et le tunnel/routage alternatif (0.6) restent des étapes distinctes.
