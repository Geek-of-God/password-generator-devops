package com.devops.passwordcli;

import java.security.SecureRandom;

/**
 * Génération de mots de passe cryptographiquement sûrs.
 *
 * Pourquoi SecureRandom et pas Random ?
 * java.util.Random utilise un algorithme linéaire congruentiel : si un attaquant
 * observe quelques sorties, il peut prédire les suivantes en O(1). SecureRandom
 * s'appuie sur /dev/urandom (Linux) ou CryptGenRandom (Windows), des sources
 * d'entropie matérielles imprévisibles. Pour un mot de passe, c'est non négociable.
 */
public class PasswordGenerator {

    private static final String UPPER   = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER   = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS  = "0123456789";
    // Les symboles choisis sont ceux acceptés par la majorité des sites web.
    // On évite volontairement les caractères ambigus comme ` ' " qui posent
    // des problèmes dans les shells et les requêtes SQL.
    private static final String SYMBOLS = "!@#$%^&*()-_=+[]{}|;:,.<>?";

    private final int    length;
    private final boolean useUpper, useLower, useDigits, useSymbols;
    private final String alphabet;
    private final SecureRandom random;

    public PasswordGenerator(int length, boolean useUpper, boolean useLower,
                             boolean useDigits, boolean useSymbols) {
        if (length < 4) throw new IllegalArgumentException("Longueur minimale : 4 caractères");

        this.length     = length;
        this.useUpper   = useUpper;
        this.useLower   = useLower;
        this.useDigits  = useDigits;
        this.useSymbols = useSymbols;
        this.random     = new SecureRandom();

        // L'alphabet est construit une seule fois au lieu de le recréer à chaque génération.
        // Cela évite des allocations répétées et améliore les performances en mode rafale.
        StringBuilder sb = new StringBuilder();
        if (useUpper)   sb.append(UPPER);
        if (useLower)   sb.append(LOWER);
        if (useDigits)  sb.append(DIGITS);
        if (useSymbols) sb.append(SYMBOLS);
        this.alphabet = sb.toString();
    }

    /**
     * Génère un mot de passe en trois phases pour garantir à la fois
     * la conformité aux règles et la distribution uniforme des caractères.
     *
     * Phase 1 — Garantie de présence : on place un caractère de chaque type activé.
     *   Sans cette étape, un mot de passe de 8 caractères avec symboles requis
     *   aurait environ 1 chance sur 3 de ne contenir aucun symbole (par malchance).
     *
     * Phase 2 — Remplissage : le reste est tiré du pool global (tous types mélangés).
     *
     * Phase 3 — Mélange Fisher-Yates : si on omettait cette étape, les caractères
     *   "obligatoires" seraient toujours aux premières positions, réduisant l'espace
     *   des permutations et donc l'entropie effective du mot de passe.
     */
    public String generate() {
        char[] password = new char[length];
        int pos = 0;

        // Phase 1 : un représentant de chaque catégorie activée
        if (useUpper)   password[pos++] = randomChar(UPPER);
        if (useLower)   password[pos++] = randomChar(LOWER);
        if (useDigits)  password[pos++] = randomChar(DIGITS);
        if (useSymbols) password[pos++] = randomChar(SYMBOLS);

        // Phase 2 : compléter avec des caractères du pool global
        for (int i = pos; i < length; i++) {
            password[i] = randomChar(alphabet);
        }

        // Phase 3 : Fisher-Yates garantit que chaque permutation est équiprobable.
        // L'algorithme parcourt le tableau de droite à gauche et échange chaque élément
        // avec un élément choisi aléatoirement parmi ceux qui le précèdent (inclus).
        for (int i = length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = password[i];
            password[i] = password[j];
            password[j] = tmp;
        }

        return new String(password);
    }

    private char randomChar(String pool) {
        return pool.charAt(random.nextInt(pool.length()));
    }

    // =========================================================================
    // Scoring interne — heuristique basée sur l'entropie estimée
    // =========================================================================

    /**
     * Calcule un score de robustesse [0-100] basé sur trois axes :
     *
     *  1. Longueur : facteur dominant car l'entropie croît exponentiellement
     *     avec la longueur (chaque caractère supplémentaire multiplie l'espace
     *     des possibilités par la taille de l'alphabet).
     *
     *  2. Diversité des catégories : plus l'alphabet effectif est grand, plus
     *     le nombre d'essais nécessaires à une attaque brute-force augmente.
     *     Un alphabet {a-z} = 26 symboles ; {a-z, A-Z, 0-9, symboles} = ~90 symboles.
     *
     *  3. Pénalités de prévisibilité : les suites (abc, 123) et répétitions (aaa)
     *     sont les premières testées par les outils de crack car elles figurent
     *     dans les masques d'attaque Hashcat et les wordlists RockYou.
     *
     * Ce score est complémentaire à CrackLib : CrackLib détecte les mots du
     * dictionnaire, ce scoring détecte les patterns structurels.
     */
    public String scorePassword(String pwd) {
        int score = 0;

        // La longueur est pondérée par paliers car le gain sécuritaire n'est pas
        // linéaire : passer de 8 à 12 caractères est plus impactant que de 20 à 24.
        int len = pwd.length();
        if (len >= 8)  score += 10;
        if (len >= 12) score += 15;
        if (len >= 16) score += 15;
        if (len >= 20) score += 10;

        boolean hasUpper  = pwd.chars().anyMatch(Character::isUpperCase);
        boolean hasLower  = pwd.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit  = pwd.chars().anyMatch(Character::isDigit);
        boolean hasSymbol = pwd.chars().anyMatch(c -> SYMBOLS.indexOf(c) >= 0);

        if (hasUpper)  score += 10;
        if (hasLower)  score += 10;
        if (hasDigit)  score += 10;
        // Les symboles sont surpondérés car ils élargissent davantage l'alphabet
        // effectif : +33 symboles contre +26 pour les majuscules.
        if (hasSymbol) score += 15;

        // Bonus de cohérence : un mot de passe qui exploite tous les types disponibles
        // résiste mieux aux attaques par règles (ex: leetspeak, capitalisation).
        if (hasUpper && hasLower && hasDigit && hasSymbol) score += 5;

        // Pénalités : chaque suite détectée retire 5 points, chaque répétition 3 points.
        // On soustrait après avoir additionné pour ne pas bloquer les mots de passe
        // longs qui contiennent accidentellement une courte suite (ex: "password123...XY").
        score -= countSequentialRuns(pwd) * 5;
        score -= countRepeats(pwd)        * 3;

        score = Math.max(0, Math.min(100, score));
        return scoreLabel(score);
    }

    /**
     * Détecte les suites consécutives de 3 caractères ou plus (ex: "abc", "321", "XYZ").
     *
     * On vérifie la différence ASCII entre deux caractères adjacents : une différence
     * constante de +1 ou -1 trahit une progression alphabétique ou numérique.
     * On ne compte une suite qu'une fois (au moment où elle atteint la longueur 3)
     * pour éviter qu'une suite de 5 caractères pénalise trois fois au lieu d'une.
     */
    private int countSequentialRuns(String pwd) {
        int runs = 0, runLen = 1;
        for (int i = 1; i < pwd.length(); i++) {
            int diff = pwd.charAt(i) - pwd.charAt(i - 1);
            if (diff == 1 || diff == -1) {
                runLen++;
                if (runLen == 3) runs++;
            } else {
                runLen = 1;
            }
        }
        return runs;
    }

    /**
     * Détecte les répétitions de caractères identiques consécutifs (longueur ≥ 3).
     * Même logique de comptage que countSequentialRuns : on ne pénalise qu'une fois
     * par groupe, au passage du seuil de 3.
     */
    private int countRepeats(String pwd) {
        int repeats = 0, runLen = 1;
        for (int i = 1; i < pwd.length(); i++) {
            if (pwd.charAt(i) == pwd.charAt(i - 1)) {
                runLen++;
                if (runLen == 3) repeats++;
            } else {
                runLen = 1;
            }
        }
        return repeats;
    }

    /**
     * Convertit le score numérique en niveau lisible.
     * Les seuils sont alignés sur les conventions NIST SP 800-63B
     * qui recommande un minimum de 8 caractères (≈ score 20) pour un usage basique.
     */
    private String scoreLabel(int score) {
        if (score < 20) return "⚠  Très faible";
        if (score < 40) return "✗  Faible";
        if (score < 60) return "~  Moyen";
        if (score < 80) return "✓  Fort";
        return             "★  Très fort";
    }
}