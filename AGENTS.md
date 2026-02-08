# Repository Guidelines

## Project Structure & Module Organization
- Source code lives in `src/clj_karaoke/`.
- Tests live in `test/clj_karaoke/` (currently centered in `core_test.clj`).
- CI workflows are under `.github/workflows/`.
- Documentation and references are in `doc/` and `README.md`.
- Utility scripts for local media processing are at repo root: `midi-to-mp3.fish` and `sanitize_lyrics_files.fish`.

Namespace naming follows Clojure conventions: `clj-karaoke.*` in code maps to `src/clj_karaoke/*.clj(c)` paths.

## Build, Test, and Development Commands
- `lein deps` installs project dependencies.
- `lein test` runs the test suite (`clojure.test`).
- `lein uberjar` builds the runnable JAR in `target/`.
- `java -jar target/<uberjar>.jar input.mid output.json --type json` runs single-file extraction.
- `java -jar target/<uberjar>.jar -i <input-dir> -o <output-dir> -t ass --offset -1000` runs batch extraction.

Use the same commands in local validation that CI uses (`lein test`, `lein uberjar`).

## MCP Tooling
- `clojure-mcp` is the primary toolset for this repository.
- Prefer `clojure-mcp` tools for project exploration and edits:
  - `clojure_inspect_project` for high-level orientation.
  - `glob_files` / `grep` for fast file and content discovery.
  - `read_file` for focused reads.
  - `clojure_edit` / `clojure_edit_replace_sexp` for structural Clojure edits.
  - `clojure_eval` for REPL verification of behavior.
- Use `ref` tools for external documentation lookup:
  - `ref_search_documentation` to find relevant docs or references.
  - `ref_read_url` to read the selected source URL content.
- Use `context7` for library/framework API docs and examples:
  - First call `resolve-library-id`.
  - Then call `query-docs` with the resolved library ID.
  - Keep queries specific to the task to minimize noise.

## Coding Style & Naming Conventions
- Use idiomatic Clojure formatting with two-space indentation and aligned bindings.
- Prefer `kebab-case` for vars/functions, lowercase keywords, and descriptive namespace aliases (`str`, `io`, `json`).
- Keep protocol and record names in `CamelCase` (`PMidiReader`, `MidiReader`).
- Preserve the existing separation of concerns: MIDI parsing (`midi.clj`), lyric model/event/frame logic (`lyrics_*.cljc`), and CLI entrypoint (`core.clj`).

## Testing Guidelines
- Write tests with `clojure.test` using `deftest` + `testing` blocks.
- Place tests in matching namespace files (example: `clj-karaoke.core-test` in `test/clj_karaoke/core_test.clj`).
- Add focused assertions around timing offsets, frame splitting/joining, and output format behavior when changing parsing logic.
- Run `lein test` before opening a PR.

## Commit & Pull Request Guidelines
- Keep commits short, imperative, and scoped (history examples: `readme update`, `opts`, `Update subtitles.yml`).
- Prefer one logical change per commit.
- PRs should include: purpose, key behavior changes, test evidence (`lein test` output), and sample CLI usage when output format behavior changes.
- If workflow behavior changes, reference the touched file in `.github/workflows/`.
