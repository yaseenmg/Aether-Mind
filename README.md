# AetherMind — Forge 1.20.1

AetherMind is an autonomous Minecraft companion mod for Forge 1.20.1. It uses a Groq OpenAI-compatible API for optional remote reasoning, deterministic routing for reliable basic actions, persistent task state, tool execution, and autonomous modes.

> **Compatibility note:** The internal mod ID remains `aicore` to preserve existing world saves and configuration paths. The public project name is **AetherMind**.

## Development and Fix History

The project was stabilized in the following order:

1. **Build and source fixes** — corrected missing imports, ambiguous comparators, and block-position conversion errors.
2. **Cost and safety controls** — made remote brain usage explicit and added request/token budgets.
3. **Tool and autonomy reliability** — added deterministic routing for high-confidence actions and verified tool results before continuing.
4. **Groq-only architecture** — removed local-provider and fallback brain paths; the remote brain now connects directly to Groq.
5. **AetherMind rebrand** — changed the public display name and archive name while retaining `aicore` as the mod ID.

See [`CHANGELOG.md`](CHANGELOG.md) for the detailed chronological record.

## Requirements

- Minecraft **1.20.1**
- Minecraft Forge **47.4.10 or newer in the 47.x line**
- Java **17** for building and running the development environment
- A Groq API key for remote reasoning features

## Groq Configuration

1. Build the mod and launch Forge once.
2. Open:

   `config/aicore-common.toml`

3. Set your key:

   ```toml
   groqApiKey = "gsk_..."
   ```

4. Select the model in `groqModel`. The default API base URL is:

   `https://api.groq.com/openai/v1`

5. Never commit or upload `aicore-common.toml`; it contains your private API key.
6. Use `/aic diagnose` to test the Groq connection without exposing the key.

The mod does not embed the API key in source code and does not send it to any endpoint other than the configured Groq-compatible base URL.

## Autonomy and Tool Selection

Supported autonomy modes include:

```text
/aic autonomy true miner
/aic autonomy true farmer
/aic autonomy true engineer
/aic autonomy true explorer
```

The deterministic router handles high-confidence actions such as gathering wood, collecting resources, farming, cooking, following, and guarding before requesting remote reasoning.

When no deterministic route matches, the agent sends only the relevant tool definitions to Groq, requests a tool call, executes the selected tool, and verifies the real world result before continuing. If Groq is unavailable, deterministic commands and autonomy remain usable; the mod does not claim a task succeeded when it did not.

## Cost and Security Controls

- The API key is stored only in the local Forge configuration.
- Groq brain budgets include:
  - `groqMaxRequestsPerMinute`
  - `groqMaxRequestsPerDay`
  - `groqMaxTokensPerDay`
- A value of `0` disables Groq brain traffic for that limit.
- API keys are not written to logs or displayed in chat messages.
- Remote brain inference requires explicit Groq configuration; the mod does not silently select a remote provider.

## End-Game Quest

The command router recognizes Modern Standard Arabic and English intent for end-game requests such as “finish the game” and “kill the dragon”. The explicit quest commands are:

```text
/aic quest start
/aic quest stop
/aic quest status
```

The quest system can coordinate long-running resource gathering, crafting, navigation, Nether progression, and End progression through persistent task state.

## Building

Use Java 17:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew clean build
```

The final mod is generated at:

```text
build/libs/aethermind-0.1.4-61.1.4-groq-only.jar
```

The source package can be created with:

```bash
zip -qr AetherMind-v61.1.4-Groq-Only-source.zip . \
  -x '.git/*' '.gradle/*' 'build/*' 'run/*' '*.class'
```

## Project Structure

- `agent/` — Groq request loop, planning, tool calls, and tool selection.
- `autonomy/` — deterministic autonomous action routing and state.
- `tool/` — tool contracts, registry, execution, crafting, gathering, combat, and world actions.
- `entity/` — companion entity behavior and interaction.
- `quest/` — persistent end-game quest progression.
- `state/` and `memory/` — persistent world, task, and companion state.
- `client/` — client-side rendering and animation hooks.

## License

This project is distributed under the MIT license included in the repository.
