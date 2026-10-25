# v61.1.3 — Cost-Safe Brain Audit

This release focused on preventing accidental remote usage and improving parser reliability.

- `aiProvider=auto` no longer selects Groq merely because `groqApiKey` exists. `auto` is local-only; Groq brain inference requires explicit `aiProvider=groq`.
- Added in-memory Groq brain limits for requests per minute, requests per day, and tokens per day. Any zero limit disables Groq brain traffic for that limit.
- Groq voice, speech-to-text, and text-to-speech remain separate paths.
- Corrected the README to remove contradictory “Groq key, voice only” wording and document explicit Groq opt-in and budgets.
- Updated `ToolContract.kindOf()` to recognize array and object structures before applying string heuristics.
- Removed unused regular-expression parser machinery. The top-level parser remains quote- and bracket-aware.
- Removed the dead `ReflexEngine.java` file and confirmed that no references remain.
- Preserved the v61.1.2 fixes for `NavigationArbiter` imports, skill comparison, `StructureSearchTool` center conversion, Steve identity, and command handling.

The original audit was source/static validation only because ForgeGradle dependencies were unavailable in that validation environment. The final AetherMind release was later compiled successfully with Java 17.
