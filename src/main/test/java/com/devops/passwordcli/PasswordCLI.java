package com.devops.passwordcli;

import java.util.*;

public class PasswordCLI {

    public static void main(String[] args) {
        // Valeurs par défaut (mot de passe fort selon NIST)
        int length = 12, count = 1;
        boolean useUpper = true, useLower = true, useDigits = true, useSymbols = false;
        boolean skipDocker = false;

        // --- Mode arguments ---
        if (args.length > 0) {
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
                    default -> { printUsage(); return; }
                }
            }
        } else {
            // --- Mode interactif ---
            try (Scanner sc = new Scanner(System.in)) {
                System.out.print("Longueur [12] : ");
                String in = sc.nextLine().trim();
                if (!in.isEmpty()) length = Integer.parseInt(in);

                System.out.print("Majuscules ? [O/n] : ");
                useUpper = !sc.nextLine().trim().equalsIgnoreCase("n");

                System.out.print("Minuscules ? [O/n] : ");
                useLower = !sc.nextLine().trim().equalsIgnoreCase("n");

                System.out.print("Chiffres ? [O/n] : ");
                useDigits = !sc.nextLine().trim().equalsIgnoreCase("n");

                System.out.print("Symboles ? [o/N] : ");
                useSymbols = sc.nextLine().trim().equalsIgnoreCase("o");

                System.out.print("Nombre [1] : ");
                in = sc.nextLine().trim();
                if (!in.isEmpty()) count = Integer.parseInt(in);

                System.out.print("Valider via Docker ? [O/n] : ");
                skipDocker = sc.nextLine().trim().equalsIgnoreCase("n");
            }
        }

        // Vérification : au moins un type de caractère
        if (!useUpper && !useLower && !useDigits && !useSymbols) {
            System.err.println("Erreur : aucun type de caractère choisi.");
            return;
        }

        // Générateur et validateur
        PasswordGenerator generator = new PasswordGenerator(length, useUpper, useLower, useDigits, useSymbols);
        DockerValidator validator = skipDocker ? null : new DockerValidator();

        // Génération et affichage
        for (int i = 0; i < count; i++) {
            String pwd = generator.generate();
            System.out.printf("%n[%d] %s%n", i + 1, pwd);
            System.out.println("Score interne : " + generator.scorePassword(pwd));
            if (validator != null) System.out.println("Score CrackLib : " + validator.validate(pwd));
        }
    }

    // Affichage rapide de l’aide
    private static void printUsage() {
        System.out.println("""
            Usage: java -jar password-cli.jar [OPTIONS]
              --length N     Longueur (défaut 12)
              --count N      Nombre (défaut 1)
              --upper/--no-upper   Majuscules
              --lower/--no-lower   Minuscules
              --digits/--no-digits Chiffres
              --symbols            Symboles
              --no-docker          Désactiver validation Docker
            """);
    }
}
