# AEM Resource Finder (demo project)

A small AEM Cloud Service component — "Resource Finder" — built to directly
demonstrate the skills requested: **debugging, component creation, JUnit
testing, and code review practices**, on real AEM project structure (Maven
multi-module, Sling Models, HTL, Query Builder).

## What it does

A Query Builder-backed component that lists content (pages) under an
author-configured path, optionally filtered by a category tag, sorted
newest-first, with a server-enforced result limit. Think: a list of
community resources, services, articles, or any other "searchable directory"
content type — the structure is intentionally generic so it can become a
reusable base for different real-world use cases later (not a one-off single
purpose component).

## Project structure

```
aem-resource-finder/
├── pom.xml                      parent (multi-module)
├── core/                        Java: Sling Model + JUnit tests
│   └── src/main/java/com/jikmunn/aem/core/models/ResourceFinderModel.java
│   └── src/test/java/.../ResourceFinderModelTest.java
├── ui.apps/                     component HTL template + author dialog
│   └── .../components/resourcefinder/
└── docs/
    ├── BUGS_FIXED.md            real debugging writeup (2 bugs, found + fixed)
    └── CODE_REVIEW.md           review checklist applied to this code
```

This mirrors the real AEM archetype layout (`core` / `ui.apps` / `ui.content`
/ `ui.tests` modules), trimmed to just `core` + `ui.apps` since this is a
focused demo, not a full site.

## What each piece demonstrates

| Ask | Where |
|---|---|
| Component creation | `ui.apps/.../resourcefinder/` (dialog + HTL) + the Sling Model behind it |
| Query Builder usage | `ResourceFinderModel.fetchResources()` — predicate map, path scoping, limit clamping, sorting |
| Debugging | `docs/BUGS_FIXED.md` — two real bugs (unbounded `p.limit`, single bad hit breaking the whole component), root cause, fix, regression test |
| JUnit testing | `ResourceFinderModelTest.java` — AEM Mocks + Mockito, 4 tests covering success, missing config, query failure, and the limit-clamp regression |
| Code review practices | `docs/CODE_REVIEW.md` — checklist actually applied, plus what was deliberately left out and why |

## Building it

Requires Maven and JDK 17 (AEM as a Cloud Service's baseline Java version).
The `aem-sdk-api` dependency is pulled automatically from Maven Central.

```
mvn clean install
```

This compiles both modules, runs the full JUnit test suite (4/4 passing),
and builds the `ui.apps` content package (`.zip`), validated against the
real AEMaaCS package validator. **Verified working end to end** — this is
not just written code, it actually builds clean.

Deploying the `ui.apps` package to a live AEM instance additionally requires
an actual AEM Cloud SDK instance or dev environment — this repo includes the
project code, tests, and a validated deployable package, not a running AEM
instance to deploy it to.

## Why this project, specifically

Built after being upfront that AEM wasn't existing hands-on experience, to
show real working knowledge, not just talk: correct project structure, a
component that handles real failure modes (not just the happy path), tests
that actually test the hard part (Query Builder code, which most tutorials
skip because it's annoying to mock), and documented reasoning behind every
decision — the same way a real review or handoff would be written.
