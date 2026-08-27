# ContextClip Desktop Agent

A lightweight desktop agent that monitors the local operating system clipboard and captures text copy events.

## Features

- Event-driven monitoring using standard Java AWT `FlavorListener` (no busy polling).
- Safe clipboard reading with retry backoff for OS clipboard lock contention.
- In-memory deduplication preventing repeated outputs for identical consecutive copies.
- Graceful shutdown unregistering listeners cleanly on termination.

## Prerequisites

- Java 25
- Apache Maven 3.9+

## Build

From the `desktop-agent/` directory:

```bash
mvn clean package
```

## Run

Run directly using Java:

```bash
java -jar target/desktop-agent-0.0.1-SNAPSHOT.jar
```

Or via Maven:

```bash
mvn exec:java
```

## Testing

Run automated unit tests:

```bash
mvn test
```

### Manual Verification

1. Start the agent in a terminal.
2. Copy text from any application (Notepad, VS Code, Browser, or terminal).
3. Verify the formatted output in the agent terminal window.
4. Press `Ctrl+C` to stop the agent.
