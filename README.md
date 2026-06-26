# 🔐 PasswordCLI – Générateur et Validateur de Mots de Passe

## 📖 Table des matières
1. Introduction
2. Objectifs du projet
3. Architecture et organisation du code
4. Installation et compilation
5. Utilisation du CLI
6. Validation via Docker
7. Tests unitaires
8. Bonnes pratiques Git
9. Perspectives d’amélioration
10. Conclusion

---

## 1. Introduction
Ce projet a été conçu pour fournir un **outil en ligne de commande (CLI)** permettant de générer des mots de passe sécurisés et de vérifier leur robustesse grâce à **CrackLib**, exécuté dans un conteneur Docker.  
Il s’inscrit dans une démarche pédagogique visant à illustrer l’intégration **Java + Docker** pour la sécurité applicative.

> 💡 *Commentaire : Cette section pose le contexte et la raison d’être du projet.*

---

## 2. Objectifs du projet
- Fournir un générateur de mots de passe configurable (longueur, complexité).
- Vérifier la robustesse des mots de passe via CrackLib.
- Garantir la reproductibilité grâce à Docker.
- Documenter le projet pour un usage académique et professionnel.

> 💡 *Commentaire : Les objectifs sont clairs et mesurables.*

---

## 3. Architecture et organisation du code
# 📂 Arborescence du projet PasswordCLI

PasswordCLI/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/devops/passwordcli/
│   │   │       ├── PasswordCLI.java          # Classe principale CLI (Picocli)
│   │   │       ├── PasswordGenerator.java    # Génération de mots de passe
│   │   │       ├── DockerValidator.java      # Client HTTP vers CrackLibServer
│   │   │       └── server/
│   │   │           └── CrackLibServer.java   # Serveur REST exposant CrackLib
│   │   └── test/
│   │       └── java/
│   │           └── com/devops/passwordcli/
│   │               └── PasswordCLI.java      # Classe de test (JUnit)
│
├── test/
│   └── java/                                # Répertoire pour tests supplémentaires
│
├── pom.xml                                  # Dépendances Maven (Picocli, JUnit, etc.)
├── Dockerfile                               # Image Docker pour CrackLibServer
├── docker-compose.yml                       # Orchestration du conteneur Docker
└── README.md                                # Documentation du projet

Chaque fichier est commenté pour faciliter la compréhension.

---

## 4. Installation et compilation
1. Cloner le dépôt :
   ```bash
   git clone https://github.com/<ton-compte>/PasswordCLI.git
   cd PasswordCLI

Compiler avec Maven :

bash
mvn clean package
Exécuter le CLI :

bash
java -jar target/PasswordCLI-1.0-SNAPSHOT-jar-with-dependencies.jar --length 12

## 5. Utilisation du CLI
Exemple de génération :

bash
java -jar target/PasswordCLI-1.0-SNAPSHOT-jar-with-dependencies.jar --length 16 --symbols true
Options disponibles :

--length : longueur du mot de passe.

--symbols : inclure des caractères spéciaux.

--digits : inclure des chiffres.


## 6 Validation via Docker
Construire l’image :

bash
docker build -t cracklib-api -f docker/cracklib-api/Dockerfile .
Lancer le conteneur :

bash
docker-compose up -d
Tester l’API :

bash
curl -X POST http://localhost:5000/check \
     -H "Content-Type: application/json" \
     -d '{"password":"Test123!"}'



## 7 Tests unitaires
Exécuter les tests :

bash
mvn test
Exemple de test :

java
@Test
void testPasswordLength() {
    String pwd = PasswordGenerator.generate(12);
    assertEquals(12, pwd.length());
}


## 8 Bonnes pratiques Git
Dépôt privé sur GitHub.

Commits fréquents et explicites (feat: add DockerValidator client).

Inviter l’évaluateur : snguessanble@univmetiers.ci en lecture seule.

Utiliser des branches (feature/generator, feature/docker).

## 9 Perspectives d’amélioration
Ajouter une interface graphique (JavaFX).

Intégrer une API REST externe pour la validation.

Mettre en place un pipeline CI/CD avec GitHub Actions.

## 10 Conclusion
Ce projet illustre l’intégration Java + Docker pour la sécurité des mots de passe, avec une documentation claire et une validation reproductible.
Il constitue une base solide pour des projets académiques ou professionnels en DevOps et sécurité.