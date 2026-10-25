# AetherMind Changelog

This file records the project fixes in chronological order, from the earliest stabilization work to the current Groq-only AetherMind release.

## v61.1.2 — Build and Static-Analysis Fixes

Fixed the build errors reported in v61.1.1:

1. Added the missing `NavigationArbiter` imports in `CompanionEntity`, `CompanionMeleeGoal`, and `CompanionRangedGoal`.
2. Removed the ambiguous `Comparator` usage in `SkillLibrary` by using explicit lambdas for `score()` and `lastUsed()`.
3. Fixed `StructureSearchTool` to convert coordinates through `BlockPos.containing(x, y, z)` and use the correct `Vec3` center values.

Static checks performed:

- Confirmed that `NavigationArbiter.java` exists.
- Confirmed that all three `NavigationArbiter` references use valid imports.
- Confirmed that no `BlockPos.containing(box.getCenter())` calls remain.
- Confirmed that no `Comparator.comparingDouble(Skill::score)` calls remain.
- Verified ZIP integrity after packaging.

## v61.1.3 — Cost-Safe Brain and Parser Audit

The project was audited to prevent accidental remote usage and improve tool-contract parsing:

- `aiProvider=auto` no longer selects Groq merely because a `groqApiKey` exists.
- `auto` is local-only; Groq brain inference requires explicit `aiProvider=groq` configuration.
- Added in-memory Groq brain limits for requests per minute, requests per day, and tokens per day.
- Any zero budget disables Groq brain traffic for that limit.
- Groq voice, speech-to-text, and text-to-speech remain separate paths.
- Corrected the README so it documents explicit Groq opt-in and budgets without contradictory wording.
- Updated `ToolContract.kindOf()` to recognize array and object structures before applying string heuristics.
- Removed unused regular-expression parser machinery while keeping the quote- and bracket-aware top-level parser.
- Removed the dead `ReflexEngine.java` file and confirmed that no references remained.
- Preserved the v61.1.2 fixes for `NavigationArbiter`, skill comparison, structure-search coordinates, Steve identity, and command handling.

## v61.1.4 — Groq-Only AetherMind Release

The architecture was simplified into a cost-safe Groq-only design:

- Removed local model providers, local AI configuration, and local fallback brain paths.
- Removed the separate advisory-provider path; the reasoning engine connects directly to the Groq OpenAI-compatible API.
- Fixed Groq request formatting and JSON tool-calling behavior.
- Added validation so `tool_choice=required` is only sent when usable tools are available.
- Kept the Groq API key in the local Forge configuration only.
- Added a diagnostic command that tests connectivity without printing the key.
- Preserved deterministic routing for basic commands and autonomy when Groq is unavailable.
- Added deterministic autonomy routing for miner, farmer, engineer, and explorer modes.
- Added high-confidence intent routing for Modern Standard Arabic and English commands.
- Kept tool-result verification so the agent does not report actions that did not actually happen.
- Preserved hand-swing and action-animation fixes for mining and block placement.
- Preserved persistent task, memory, and end-game quest progression systems.
- Renamed the public project and display name to **AetherMind**.
- Kept `modId="aicore"` to preserve compatibility with existing worlds and configuration paths.
- Changed the release archive name to `aethermind-0.1.4-61.1.4-groq-only.jar`.

## Validation

The final release was compiled successfully with Java 17 and ForgeGradle. The JAR was checked for:

- `displayName="AetherMind"`
- `modId="aicore"`
- `AgentEngine.class`
- `AutonomyActionRouter.class`
- `ToolSelectionRouter.class`

Both the final JAR and source ZIP passed archive-integrity checks.
