# Research notes (Android rewrite)

- **Why Oboe**: lowest-latency portable AAudio/OpenSL ES path on Android; suitable for continuous masking + capture.
- **Why Kotlin/Compose**: official Android UI stack; no RN bridge overhead.
- **Why no backend**: product decision 1A — local-only MVP; sync deferred indefinitely.
- **Procedural audio**: placeholder MP3 assets are tiny; engine synthesizes seamless loops so sessions work without asset decode.
- **Heuristic classifier**: port of prior TS band-ratio heuristic; replaceable inside `HeuristicAnalyzer` without UI changes.
