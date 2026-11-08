# Bugs Found & Fixed

This file documents real debugging work done on this component, written the
way a fix would actually be described in a PR or incident writeup: what broke,
why, how it was found, and the fix. The `ResourceFinderModel` in this repo
already has the fixes applied — this file is the "before" story.

---

## #1 — Unbounded `p.limit` from author input

**Symptom:** A content author set the component's "Result Limit" dialog field
to a very large number (thousands), either by mistake or by typing a wrong
value. The page using the component became slow to render, and in the Query
Performance tool the query showed a high `time` and a large result set size.

**Root cause:** The original code passed the author-entered `resultLimit`
value straight into `p.limit` with no upper bound:

```java
// BEFORE — trusts dialog input directly
predicateMap.put("p.limit", String.valueOf(resultLimit));
```

Query Builder will happily try to return however many results you ask for.
There's nothing in Query Builder itself that protects you from a bad
`p.limit` value — that's the calling code's job.

**How it was found:**
1. Page load time regression reported.
2. Checked `/libs/granite/ui/content/queryoverview.html` (Query Performance
   tool) for queries run by this page, found the Resource Finder query with
   an unusually large result count.
3. Traced the `p.limit` value back to the specific page's dialog
   configuration, confirmed it was a typo (an extra zero).

**Fix:** Clamp the limit server-side, independent of what the dialog allows:

```java
// AFTER — hard ceiling regardless of input
int limit = (resultLimit != null && resultLimit > 0)
        ? Math.min(resultLimit, MAX_RESULT_LIMIT)
        : DEFAULT_RESULT_LIMIT;
predicateMap.put("p.limit", String.valueOf(limit));
```

Also added a `max` constraint on the dialog's `numberfield` as a first line
of defense, but the code-level clamp is the real fix — dialog constraints can
be bypassed by anyone editing the JCR property directly (e.g. via CRXDE or a
content migration script), so the component itself has to be the one that's
actually safe.

**Regression test:** `clampsResultLimitToHardCeiling()` in
`ResourceFinderModelTest.java` asserts the limit sent to `QueryBuilder` is
`50` even when the dialog value is `5000`.

---

## #2 — A single bad hit failing the entire component

**Symptom:** Occasionally the whole component would render empty (or the
page would 500) even though most of the underlying content was fine.

**Root cause:** The loop converting Query Builder hits into page data called
`hit.getResource()` and `adaptTo(Page.class)` with no error handling per hit.
One stale/broken reference in the result set (e.g. content deleted after the
index was built, before the query ran) threw an exception that propagated out
of the whole `fetchResources()` call, which — before the outer try/catch was
added — propagated out of `@PostConstruct` entirely and broke the Sling Model
adaption for the whole component.

**Fix:** Two layers, matching what's in the current code:
1. An inner `try/catch` around each individual hit, so one bad result is
   skipped and logged, not fatal.
2. An outer `try/catch` around the whole query call in `init()`, so even a
   total Query Builder/repository failure sets `hasError` and lets the
   component render an empty/error state instead of breaking the page.

**Regression tests:** `degradesGracefullyWhenQueryBuilderThrows()` covers the
outer case directly. The inner per-hit handling is harder to unit test
cleanly with mocks (would need a hit that fails specifically on `adaptTo`),
noted here as a gap rather than silently skipped — a real next step would be
adding a dedicated test for that path with a `Resource` mock that throws.

---

## #3 — Build/toolchain issues hit while actually building this project

Worth including since this is itself the kind of environment debugging that
comes up constantly on real AEM projects, especially starting fresh on a
new machine.

1. **Wrong dependency for AEMaaCS**: started with `com.adobe.aem:uber-jar`,
   which is for on-prem AEM 6.x only (only goes up to 6.5.5 on Adobe's
   public repo). AEM as a Cloud Service projects use
   `com.adobe.aem:aem-sdk-api` instead, available on Maven Central directly.
2. **`org.apache.sling.models.annotations` doesn't exist as a separate
   artifact anymore** — in current Sling versions, the `@Model`,
   `@ValueMapValue` etc. annotations ship inside
   `org.apache.sling.models.api` itself.
3. **Java version mismatch**: the AEM SDK version used here targets Java 17
   (AEMaaCS's current baseline), compiling with Java 11 failed with a class
   file version error on the Sling Models annotation processor. Fixed by
   building with JDK 17.
4. **AEM Mocks doesn't auto-discover Sling Models at test time** — in a real
   deployed bundle, `Sling-Model-Packages` in the OSGi manifest tells Sling
   Models which packages to scan. That manifest doesn't exist during `mvn
   test` (it's generated later, during the bundle-packaging step), so
   `context.request().adaptTo(ResourceFinderModel.class)` silently returned
   `null` until the model package was registered explicitly in the test
   (`context.addModelsForPackage(...)`).
5. **Query Builder's `p.limit` isn't retrievable from the built
   `PredicateGroup` under the key `"p.limit"`** — Query Builder normalizes
   it internally to `Predicate.PARAM_LIMIT` (`"limit"`). A regression test
   asserting `predicateGroup.get("p.limit")` silently got `null` back
   instead of failing loudly, until the assertion was corrected to use the
   actual constant. A reminder that Query Builder's input key names and its
   internal/output key names aren't always the same.
6. **A redundant, incorrect adapt check** (`hitResource.getResourceResolver().adaptTo(ResourceResolver.class) != null ? hitResource.adaptTo(Page.class) : null`)
   silently swallowed every page result — root cause found by adding
   temporary diagnostic logging, isolating that `searchPath` and the overall
   control flow were correct, and narrowing down to the one line actually
   producing `null`. Fixed by simplifying to the direct, correct call:
   `hitResource.adaptTo(Page.class)`.

None of this shows up if you just read the final code — it's the part of
AEM work that's genuinely hard to learn from documentation alone, and
exactly the kind of thing day-to-day AEM support work runs into.

## What this demonstrates

Both of these are the kind of bugs that come from *not* defending against bad
input and partial failure, not from getting the main logic wrong. That's
deliberate here — it's closer to the kind of debugging work that actually
comes up on a real AEM support engagement (dialog misuse, partial content
corruption, performance regressions) than a contrived "off-by-one" bug would
be.
