# NOVA BOOST LAB 0.3 — Plan de tests de projection

## Objet

Vérifier par des scénarios contrôlés que le Smart Network Optimizer transforme les résultats de diagnostic 0.2 en recommandations cohérentes, prudentes et explicables.

Ces scénarios sont des tests logiciels avec données construites. Ils ne représentent pas des mesures radio ou Internet réelles.

## Règles de décision projetées

| Règle | Comportement attendu |
| --- | --- |
| Android indique aucune capacité Internet | État « Pas d'accès Internet », action de vérification |
| Internet annoncé mais non validé | État « Internet non validé », action de contrôle |
| Moins de 3 réponses valides dans TCP ou HTTPS | Données insuffisantes, demander un nouveau test |
| Réussite inférieure à 80 % dans une série | Profil irrégulier, demander un nouveau test |
| Dispersion (max - min) / médiane supérieure à 0,50 | Profil irrégulier, demander un nouveau test |
| Médiane TCP ≥ 300 ms ou HTTPS ≥ 1 500 ms | Réponses lentes sur les sondes, proposer une comparaison manuelle |
| Données suffisantes, taux de réussite ≥ 80 %, dispersion ≤ 0,50 et médianes sous les seuils | Profil cohérent, conserver le réseau actuel |

Les seuils sont des heuristiques de départ. Ils ne sont ni universels, ni équivalents au ping d'un jeu.

## Scénarios automatisés

| ID | Entrée simulée | Résultat attendu |
| --- | --- | --- |
| P01 | Séries complètes, rapides et peu dispersées | STABLE / KEEP_CURRENT |
| P02 | Android indique hasInternet = false | NO_INTERNET / CHECK_CONNECTION |
| P03 | hasInternet = true, validated = false | NOT_VALIDATED / CHECK_CONNECTION |
| P04 | Seulement 2 réponses TCP valides | INSUFFICIENT_DATA / RETEST |
| P05 | 3 réussites sur 5 dans une série | UNSTABLE / RETEST |
| P06 | Dispersion TCP supérieure à 50 % | UNSTABLE / RETEST |
| P07 | Médiane TCP supérieure ou égale à 300 ms | SLOW_RESPONSES / COMPARE_NETWORKS |
| P08 | Médiane HTTPS supérieure ou égale à 1 500 ms | SLOW_RESPONSES / COMPARE_NETWORKS |
| P09 | Valeur de mesure nulle ou négative dans les échantillons | INSUFFICIENT_DATA / RETEST |

Les scénarios P01 à P09 sont couverts par SmartNetworkOptimizerTest.kt.

## Vérifications supplémentaires avant validation sur téléphone

1. Vérifier que la carte du moteur apparaît uniquement après un diagnostic terminé.
2. Vérifier qu'un nouveau diagnostic efface l'ancienne recommandation pendant les mesures.
3. Vérifier que l'interface reste lisible pour chaque état et pour les textes longs.
4. Vérifier sur téléphone que l'application reste utilisable lorsque le réseau disparaît pendant le diagnostic.
5. Comparer Wi-Fi et données mobiles manuellement, en conservant le même appareil, le même emplacement et les mêmes sondes. Noter l'heure et les conditions.
6. Répéter chaque mesure plusieurs fois : une session unique ne suffit pas à calibrer les seuils.

## Critères d'acceptation 0.3

- Tous les tests unitaires de décision passent.
- La compilation debug réussit.
- Chaque état de décision est explicable dans l'interface.
- Le moteur ne modifie aucun réglage réseau.
- Aucun résultat simulé n'est présenté comme une performance mesurée.
- Les limites TCP/HTTPS et l'absence de ping UDP de jeu sont visibles dans la documentation.

## Hors périmètre

La 0.3 ne comprend pas l'analyse des flux de jeu (0.4), l'infrastructure de relais (0.5), les tunnels ou le routage alternatif (0.6), ni l'orchestration centrale BOOST (0.7). Ces blocs ne doivent pas être introduits en avance sous forme de fonctions fictives ou de boutons sans implémentation.
