package com.devops.passwordcli;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Point d'entrée de l'application et gestionnaire de l'interface CLI.
 *
 * Deux modes coexistent intentionnellement :
 *
 *  - Mode arguments (args non vide) : adapté aux scripts, pipelines CI/CD et
 *    à l'automatisation. Exemple : générer 100 mots de passe dans un script shell.
 *
 *  - Mode interactif (args vide) : guidé par des questions, il abaisse la barrière
 *    pour un utilisateur non technique qui ne connaît pas les options.
 *
 * Ce choix évite de forcer l'utilisateur à consulter --help pour un usage simple,
 * tout en offrant la puissance des arguments pour les usages avancés.
 */
public class PasswordCLI {

    public static void main(String[] args) {
        // Valeurs par défaut choisies pour produire un mot de passe "Fort" dès le départ,
        // sans configuration : 12 caractères mixtes couvrent la recommandation NIST minimale.
        int     length     = 12;
        boolean useUpper   = true;
        boolean useLower   = true;
        boolean useDigits  = true;
        boolean useSymbols = false;  // désactivé par défaut car certains systèmes les refusent
        int     count      = 1;
        boolean skipDocker = false;

        if (args.length > 0) {
            // --- Mode arguments ---
            // On utilise un switch sur String (disponible depuis Java 7) plutôt qu'une
            // chaîne de if/else pour rendre l'ajout de nouvelles options trivial.
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--length"    -> length     = Integer.parseInt(args[++i]);
                    case "--count"     -> count      = Integer.parseInt(args[++i]);
                    case "--upper"     -> useUpper   = true;
                    case "--lower"     -> useLower   = true;
                    case "--digits"    -> useDigits  = true;
                    case "--symbols"   -> useSymbols = true;
                    case "--no-upper"  -> useUpper   = false;
                    case "--no-lower"  -> useLower   = false;
                    case "--no-digits" -> useDigits  = false;
                    case "--no-docker" -> skipDocker = true;
                    default -> {
                        System.err.println("Argument inconnu : " + args[i]);
                        printUsage();
                        System.exit(1);
                    }
                }
            }
        } else {
            // --- Mode interactif ---
            // On utilise try-with-resources pour fermer le Scanner proprement,
            // bien que System.in ne soit pas vraiment "fermable" — bonne pratique.
            try (Scanner sc = new Scanner(System.in)) {
                System.out.println("=== Générateur de mots de passe sécurisés ===\n");

                System.out.print("Longueur du mot de passe [12] : ");
                String input = sc.nextLine().trim();
                // Entrée vide = conserver la valeur par défaut, pas une erreur
                if (!input.isEmpty()) length = Integer.parseInt(input);

                System.out.print("Inclure majuscules ? [O/n] : ");
                useUpper = !sc.nextLine().trim().equalsIgnoreCase("n");

                System.out.print("Inclure minuscules ? [O/n] : ");
                useLower = !sc.nextLine().trim().equalsIgnoreCase("n");

                System.out.print("Inclure chiffres ? [O/n] : ");
                useDigits = !sc.nextLine().trim().equalsIgnoreCase("n");

                System.out.print("Inclure symboles ? [o/N] : ");
                useSymbols = sc.nextLine().trim().equalsIgnoreCase("o");

                System.out.print("Nombre de mots de passe [1] : ");
                String countInput = sc.nextLine().trim();
                if (!countInput.isEmpty()) count = Integer.parseInt(countInput);

                System.out.print("Valider via Docker/CrackLib ? [O/n] : ");
                skipDocker = sc.nextLine().trim().equalsIgnoreCase("n");
            }
        }

        // Garde-fou : sans aucun type de caractère, l'alphabet serait vide et
        // randomChar() lèverait une StringIndexOutOfBoundsException indébuggable.
        if (!useUpper && !useLower && !useDigits && !useSymbols) {
            System.err.println("Erreur : sélectionnez au moins un type de caractère.");
            System.exit(1);
        }

        PasswordGenerator generator = new PasswordGenerator(length, useUpper, useLower, useDigits, useSymbols);

        // Le validator est null si l'utilisateur a demandé --no-docker.
        // On préfère un null explicite à un booléen supplémentaire pour éviter
        // la logique conditionnelle répétée dans la boucle d'affichage.
        DockerValidator validator = skipDocker ? null : new DockerValidator();

        System.out.println("\n--- Résultats ---");

        // On génère tous les mots de passe d'abord pour isoler la phase de génération
        // de la phase d'affichage — utile si on voulait les trier ou filtrer plus tard.
        List<String> passwords = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            passwords.add(generator.generate());
        }

        for (int i = 0; i < passwords.size(); i++) {
            String pwd           = passwords.get(i);
            String internalScore = generator.scorePassword(pwd);

            System.out.printf("%n[%d] Mot de passe  : %s%n", i + 1, pwd);
            System.out.printf("    Score interne  : %s%n", internalScore);

            if (validator != null) {
                // Chaque appel est un aller-retour HTTP vers le conteneur Docker.
                // En mode rafale avec un grand --count, cela peut prendre du temps :
                // un batch endpoint serait une amélioration pertinente.
                String dockerScore = validator.validate(pwd);
                System.out.printf("    Score CrackLib : %s%n", dockerScore);
            }
        }

        System.out.println("\n=== Terminé ===");
    }

    private static void printUsage() {
        System.out.println("""
            Usage: java -jar password-cli.jar [OPTIONS]
              --length N     Longueur du mot de passe        (défaut : 12)
              --count N      Nombre de mots de passe         (défaut : 1)
              --upper        Inclure les majuscules           (défaut : actif)
              --no-upper     Exclure les majuscules
              --lower        Inclure les minuscules           (défaut : actif)
              --no-lower     Exclure les minuscules
              --digits       Inclure les chiffres             (défaut : actif)
              --no-digits    Exclure les chiffres
              --symbols      Inclure les symboles !@#$%...    (défaut : inactif)
              --no-docker    Désactiver la validation CrackLib
            """);
    }
}