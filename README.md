# Bibliotheque municipale — architecture microservices

Exercice final du module 10. Deux services metier (`book-service`, `loan-service`)
poses sur l'infrastructure Spring Cloud du cours (`eureka-server`, `config-server`,
`api-gateway`).

## Architecture

```
                         eureka-server :8761
                          (annuaire des services)
                                   ^
                                   |
       +---------------------------+---------------------------+
       |                           |                           |
  api-gateway :8085          book-service :8091          loan-service :8092
  (porte d'entree)           base H2 "bookdb"            base H2 "loandb"
       |                           ^                           |
       |                           +---- Feign (BookClient) ---+
       |                                lecture ET ecriture
       v
  config-server :8888  <--  config-repo/*.yml
```

Toutes les requetes passent par la gateway. Les ports 8091 et 8092 ne sont exposes
que pour le debug.

> **Port de la gateway : 8085 sur cette machine.** Le port 8080 y est deja occupe
> par un autre projet, donc `docker-compose.yml` publie la gateway sur `8085:8080` :
> elle ecoute toujours sur 8080 a l'interieur de son conteneur, seul le port cote
> machine change. Si 8080 est libre chez vous, remettez `"8080:8080"` dans
> `docker-compose.yml` et `@gateway = http://localhost:8080` dans `bibliotheque.http`.

## Prerequis

Docker uniquement. Ni JDK ni Maven ne sont necessaires : la compilation se fait
dans un conteneur Maven (build multi-stage).

## Demarrage

```bash
docker compose up --build
```

Le demarrage est sequence par des healthchecks :
`eureka-server` -> `config-server` -> `book-service` -> `loan-service` / `api-gateway`.
Comptez 2 a 4 minutes au premier lancement (compilation + telechargement des dependances).

Verifications :

| URL | Contenu attendu |
|---|---|
| http://localhost:8761 | Les 4 services enregistres dans Eureka |
| http://localhost:8888/book-service/default | La config servie a book-service |
| http://localhost:8091/swagger-ui.html | Documentation OpenAPI de book-service |
| http://localhost:8092/swagger-ui.html | Documentation OpenAPI de loan-service |

Arret : `docker compose down`

## Tests

```bash
docker run --rm -v "$PWD:/app" -w /app -v biblio-m2:/root/.m2 \
  maven:3.9-eclipse-temurin-21 mvn test
```

Sous PowerShell, remplacez `$PWD` par le chemin absolu du projet.

| Fichier | Type | Ce qu'il couvre |
|---|---|---|
| `BookServiceTest` | unitaire (Mockito) | Refus du decrement a 0, plafond de l'increment a `totalCopies` |
| `BookControllerIT` | integration (MockMvc + H2) | 409 sur stock epuise, 404, 400 de validation |
| `LoanServiceTest` | unitaire (Mockito) | Stock epuise sans appel a `decrement-stock`, cas de concurrence, snapshot du titre |
| `LoanControllerIT` | integration (MockMvc + `@MockitoBean BookClient`) | 201 / 409 / 400, double retour refuse |

## Scenario manuel

`bibliotheque.http` (VS Code + extension REST Client, ou IntelliJ) enchaine :
creation d'un livre, deux emprunts reussis, un emprunt refuse pour stock epuise,
un retour, une tentative de double retour.

## Endpoints

### book-service (via la gateway : `http://localhost:8085`)

| Methode | URL | Reponse | Description |
|---|---|---|---|
| GET | `/api/books` | 200 | Liste. Filtres optionnels `?author=` ou `?title=` |
| GET | `/api/books/{id}` | 200 / 404 | Detail d'un livre |
| POST | `/api/books` | 201 / 400 / 409 | Creation. `availableCopies` = `totalCopies` |
| PUT | `/api/books/{id}` | 200 / 404 / 409 | Mise a jour |
| DELETE | `/api/books/{id}` | 204 / 404 | Suppression |
| PATCH | `/api/books/{id}/decrement-stock` | 200 / 404 / **409** | **Interne** — appele par loan-service |
| PATCH | `/api/books/{id}/increment-stock` | 200 / 404 | **Interne** — plafonne a `totalCopies` |

### loan-service

| Methode | URL | Reponse | Description |
|---|---|---|---|
| GET | `/api/loans` | 200 | Liste des emprunts |
| GET | `/api/loans/{id}` | 200 / 404 | Detail d'un emprunt |
| POST | `/api/loans` | 201 / 400 / **409** | Creation. Corps : `{ "memberName": "Bob", "bookId": 3 }` |
| PATCH | `/api/loans/{id}/return` | 200 / 404 / **409** | Retour |

### Codes d'erreur

| Code | Situation |
|---|---|
| 400 | Payload invalide, ou `bookId` referencant un livre inexistant |
| 404 | Emprunt ou livre introuvable par son id |
| 409 | Stock epuise, emprunt deja rendu, ISBN deja utilise, plafond d'emprunts atteint |
| 503 | book-service injoignable depuis loan-service |

Toutes les erreurs partagent le meme corps `ApiError` :

```json
{
  "timestamp": "2026-09-07T10:12:33.412Z",
  "status": 409,
  "error": "Conflict",
  "message": "Aucun exemplaire disponible pour ce livre (3)",
  "path": "/api/loans"
}
```

## La regle metier centrale, et pourquoi elle est verifiee deux fois

Creation d'un emprunt :

1. `loan-service` lit le livre chez `book-service` (`GET /api/books/{id}`)
   — 400 si le livre n'existe pas.
2. Si `availableCopies == 0` : **409 immediat, sans appeler `decrement-stock`**.
3. Sinon `PATCH /api/books/{id}/decrement-stock`
   — si `book-service` repond 409, `loan-service` transmet un 409 au client.
4. Seulement alors, l'emprunt est enregistre (`ACTIVE`, `dueDate = loanDate + 14 jours`).

L'etape 2 est une optimisation ; l'etape 3 est celle qui fait autorite. Entre la
lecture (etape 1) et l'ecriture (etape 3), un autre emprunt a pu consommer le
dernier exemplaire : c'est un **TOCTOU** (Time-Of-Check to Time-Of-Use).
C'est pourquoi `BookService.decrementStock` reverifie `availableCopies > 0` a
l'interieur de sa propre transaction, au lieu de faire confiance a l'appelant.

Principe general : **un service ne fait jamais confiance aux verifications d'un
appelant, meme interne**. La verification cote appelant sert le confort et la
performance ; celle cote proprietaire de la donnee sert la correction.

## Limites connues (assumees pour l'exercice)

- **Pas de transaction distribuee.** Si l'enregistrement de l'emprunt echoue apres
  le decrement, le stock reste decremente a tort. En production on traiterait cela
  par une saga avec compensation (rappeler `increment-stock`) ou par un outbox
  transactionnel.
- **La reverification n'elimine pas totalement la concurrence** sous forte charge :
  deux transactions simultanees peuvent lire `availableCopies = 1` avant que l'une
  n'ecrive. Le durcissement classique est un verrou optimiste (`@Version` sur
  l'entite) ou un `UPDATE ... WHERE available_copies > 0` atomique.
- **Bases H2 en memoire** : les donnees disparaissent a chaque redemarrage.

## Bonus traites

- [x] Filtre de recherche `GET /api/books?author=...` / `?title=...`
- [x] Limite de 3 emprunts `ACTIVE` par membre (parametrable dans `config-repo/loan-service.yml`)
- [x] Documentation Swagger sur les deux services
- [x] Conteneurisation complete (`docker compose up --build`)
- [ ] Pagination sur `GET /api/books`

## Detail technique a connaitre

Le client HTTP par defaut de Feign (`HttpURLConnection`) **ne sait pas envoyer de
requete PATCH**. Comme les deux endpoints internes sont en PATCH, `loan-service`
declare explicitement `feign-hc5` (Apache HttpClient 5) et l'active via
`spring.cloud.openfeign.httpclient.hc5.enabled: true`. Sans cela : `ProtocolException`
a l'execution.
