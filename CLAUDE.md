# sm64-RL — Claude Instructions

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
