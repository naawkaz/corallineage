# CoralLineage

Version-controlled framework for semi-automated coral image annotation.
Three services plus Docker infra.

- corallineage-backend: Spring Boot 4.1, Java 21, port 8080
- corallineage-frontend: React 19 + Vite, port 3000
- corallineage-ml-service: Flask + PyTorch, port 5001
- docker-compose.yml: Postgres 15 on host port 5433, MLflow on port 5000

## Prereqs

- JDK 21. Must be a JDK, not a JRE. Set JAVA_HOME to it. Maven reads JAVA_HOME, not PATH.
- Node.js 20 or newer, with npm.
- Python 3.12 exactly. Not 3.13 or 3.14. The pinned torch and torchvision only work on 3.12.
- Docker Desktop, running.
- Git and DVC on PATH. The backend calls both as external commands.

## First-time setup

1. Extract the zip. Open a terminal in the extracted corallineage folder.

2. Make it a git repo. The backend runs git commit for every dataset snapshot and fails if this is not a repo.

```
git init
git add -A
git commit -m "initial"
```

3. Fix the DVC working directory. In corallineage-backend/src/main/resources/application.properties set app.dvc.working-directory to the absolute path of this extracted folder.

4. Fix the DVC remote. In .dvc/config change both remote url lines to a folder that exists on this machine. An empty folder is fine. dvc push writes there.

5. Start infra.

```
docker compose up -d
```

Check docker ps shows corallineage-postgres-1 and corallineage-mlflow-1 up.

6. ML service.

```
cd corallineage-ml-service
python -m venv venv
venv\Scripts\pip install -r requirements.txt --extra-index-url https://download.pytorch.org/whl/cpu
venv\Scripts\python app.py
```

The extra-index-url is required. Without it pip cannot find torch 2.13.0+cpu. First training run downloads model weights over the internet.

7. Backend, new terminal.

```
cd corallineage-backend
mvnw spring-boot:run
```

Use mvnw.cmd on Windows. Tables are created automatically on first boot against the empty database.

8. Frontend, new terminal.

```
cd corallineage-frontend
npm install
npm run dev
```

9. Open http://localhost:3000. There are no default users. Click Create one and register an account.

## Ports

- 3000 frontend
- 8080 backend
- 5001 ML service
- 5433 Postgres, mapped from container port 5432
- 5000 MLflow

## Config

Backend config is corallineage-backend/src/main/resources/application.properties. Values are literal, not env vars. DB url, ML service url, MLflow url, JWT secret and DVC paths all live there. The JWT secret in the file is a dev placeholder.

Frontend API base url is hardcoded in corallineage-frontend/src/shared/api/client.ts as http://localhost:8080/api/v1.

## What works and what needs the setup above

- Auth, projects, sources, browsing: work once the four services are up.
- Dataset version create, import, training: call DVC and git. They fail unless steps 2, 3 and 4 are done.
- MLflow diagnostics on the model and report pages: empty until you run a training job. They read from the MLflow container.

## Notes

- The root npm run dev script starts all four at once but is Windows only and assumes the ML venv already exists.
- No data included. Fresh database, empty MLflow. The dataset folder has sample images for import testing.
- No seeded users. No migrations. Schema is generated from JPA entities.
