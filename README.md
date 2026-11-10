# NOVA BOOST LAB 0.2.0

Application Android indépendante de diagnostic réseau. Cette version mesure des connexions de test ; elle ne prétend pas accélérer le réseau et ne modifie pas le routage du téléphone.

## Ce qui change en 0.2.0

- Cinq essais TCP au lieu d'une mesure isolée.
- Cinq essais HTTPS/TLS au lieu d'une mesure isolée.
- Médiane, minimum, maximum et nombre de réponses reçues pour chaque série.
- Détection du transport réseau actif (Wi-Fi, données mobiles, Ethernet, VPN externe ou autre).
- Affichage de l'état de validation Internet communiqué par Android.
- Délais de connexion et de lecture limités, fermeture systématique des connexions HTTP.
- Suppression du faux contrôle VPN : le service précédent s'arrêtait immédiatement et ne créait aucun tunnel.
- Icône de lancement dédiée et version d'application mise à jour.
- Tests unitaires des calculs de médiane.

## Comprendre les mesures

- **TCP Connect** : temps nécessaire pour ouvrir une connexion TCP vers connectivitycheck.gstatic.com:443. La résolution du nom peut influencer ce temps.
- **HTTPS/TLS** : durée jusqu'à l'obtention de la réponse HTTP de www.google.com/generate_204. Ce temps inclut plusieurs étapes (DNS, TCP, TLS et réponse du serveur).
- **Médiane** : valeur centrale des réponses réussies. Les essais échoués sont comptés séparément et ne sont pas transformés en latence artificielle.

## Limites assumées

- Ces tests ne mesurent pas le ping UDP réel d'un match Call of Duty Mobile.
- Le test HTTPS n'est pas une mesure pure de latence réseau.
- Le résultat dépend des serveurs de test, de la couverture, du fournisseur d'accès, du DNS et de la charge momentanée.
- Android peut signaler un accès Internet validé sans garantir que chaque service distant fonctionne.
- Aucun tunnel VPN, relais NOVA, proxy, chiffrement de trafic ou changement de route n'est inclus.
- Aucun résultat ne peut garantir une baisse du ping en jeu.

## Compilation

Configuration : JDK 17, Android SDK 35, Gradle 8.9.

Depuis Android Studio : ouvrir le dossier du projet, synchroniser Gradle, puis exécuter la configuration app.

Commande disponible dans un environnement avec Gradle installé :

    gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug

APK debug :

    app/build/outputs/apk/debug/app-debug.apk

Le workflow GitHub Actions exécute les tests unitaires avant de générer l'APK.

## Suite envisagée

1. Ajouter un historique local des diagnostics.
2. Permettre des tests vers plusieurs destinations configurables et clairement identifiées.
3. Étudier un protocole de mesure UDP contrôlé, sans le confondre avec le trafic réel du jeu.
4. Concevoir l'architecture d'un relais uniquement après définition d'un serveur, d'un protocole, de la sécurité et de mesures comparatives crédibles.
