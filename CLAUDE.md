# sm64-RL — Claude Instructions

## Contexto del Proyecto

**TFG: Agente de Reinforcement Learning para Super Mario 64 (nivel Bob-omb Battlefield, 1-1)**

**Objetivo:** Entrenar un agente RL que aprenda a pasarse el nivel 1-1 de Super Mario 64 usando la librería Gymnasium de Farama Foundation y algoritmos de Google (e.g. PPO, DQN).

**Arquitectura general:**
- El juego corre como **ejecutable nativo para PC** (compilado con `make version=us`), no como ROM en emulador.
- El juego expone su estado interno (`gMarioState`: posición, velocidad, orientación, acción, monedas, estrellas) directamente vía un puente **C ↔ Java ↔ Python**.
- Una interfaz **JavaFX** (launcher) permite configurar y lanzar episodios.
- El entorno Python implementa la API de **Gymnasium** (`reset` / `step` / recompensa) para enchufar cualquier librería RL estándar.
- Se pueden lanzar **múltiples instancias en paralelo** (sin ventana/render) para acelerar el entrenamiento.

**Stack técnico:**
- C (código del juego SM64 PC port)
- Java / JavaFX (launcher e interfaz de configuración)
- Python + Gymnasium (entorno RL)
- Jira (`https://tfgsm64.atlassian.net/jira/software/projects/KAN/boards/1`) para gestión del proyecto

**Estrategia de entrenamiento:**
- Arrancar directamente en Bob-omb Battlefield (sin menús ni animaciones)
- Frame skip: mantener una acción varios frames seguidos
- Apagar render durante entrenamiento
- No modificar la física del juego original
- Empezar con el nivel simplificado, escalar a nivel completo

---

## Agent Routing

Delegate automatically to the appropriate agent based on the topic. Do not handle these yourself.

### Jira
Any mention of Jira tickets, comments, transitions, issues, sprints, or task tracking → delegate to the **`jira-commenter`** agent.

### Java / JavaFX Interface
Any mention of the Java launcher, the UI/interface, JavaFX views, FXML, CSS theming, or the Java side of the C↔Java bridge → delegate to the **`javafx-expert`** agent.

### C Code / Game
Any mention of C files, game behaviour, physics, levels, CLI args, RL hooks, the SM64 binary, `pc_main.c`, or the C side of the C↔Java bridge → delegate to the **`sm64-c-expert`** agent.

---

When a request spans multiple domains (e.g. adding a C param AND wiring it to Java), delegate to each relevant agent in sequence, passing the output of one as context to the next.
