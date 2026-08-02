# Dead links

Links found while sweeping this repo's docs (READMEs, CHANGELOG, MIGRATION, examples,
code-comment URLs) that returned a non-200 / dead response when HTTP-checked. Placeholder
URLs used purely as illustrative examples in KDoc/README snippets (e.g. `https://...`,
`https://<your-link-domain>`, `https://example.com/...` test fixtures) are excluded — they
were never meant to resolve.

| repo-relative-path | dead URL | referencing doc |
|---|---|---|
| CHANGELOG.md | https://github.com/thepostedapp/posted-sdk-android/releases/tag/v3.0.0 | CHANGELOG.md |
| posted-sdk/build.gradle.kts | https://github.com/thepostedapp/posted-sdk-android | posted-sdk/build.gradle.kts |

**Not removed — flagged instead:** both rows above 404 today, but per CHANGELOG.md's own
`[3.0.0]` entry (lines 13-15, 38-39) the `posted-sdk-android` repo name is the *staged*
target of the still-pending 3.0.0 rename/publish ("not yet published... gated on a separate
board approval"), not a stale/erroneous reference. Mechanically deleting them would
contradict that documented, in-flight decision and would need re-adding once the rename/
publish lands. Left in place pending CTO/board confirmation on POS-327's Maven-publish
handoff; see issue comment on POS-525.
