# Code Review Checklist (applied to this project)

Checklist used while building and reviewing `ResourceFinderModel` and its
tests, the kind of review pass this project is meant to demonstrate.

## Query Builder specific
- [x] `path` predicate always set — never an unscoped repository-wide query.
- [x] `p.limit` always set and clamped server-side, never trusts raw input.
- [x] `orderby` set explicitly — never relies on default/JCR node order.
- [x] Query failure doesn't throw out of the component (`try/catch` in
      `init()`), degrades to an empty/error state instead of a 500.
- [x] Per-hit failures don't fail the whole result set.

## Sling Model / component conventions
- [x] `defaultInjectionStrategy = OPTIONAL` set explicitly — dialog fields
      are optional in content, model shouldn't fail adaption just because a
      field is unset; `hasError` handles the "required but missing" case
      deliberately instead.
- [x] No business logic in the HTL template — template only reads
      `hasError` / `empty` / `items`, all computation happens in the model.
- [x] Dialog field names match `@ValueMapValue` field names exactly
      (`searchPath`, `categoryTag`, `resultLimit`) — a common source of
      "the dialog saves fine but the component shows nothing" bugs.

## Testing
- [x] Tests cover the success path, the "required field missing" path, the
      "query throws" path, and a regression test for the specific limit-
      clamping bug (see `BUGS_FIXED.md` #1) — not just the happy path.
- [x] Query Builder itself is mocked (`QueryBuilder`/`Query`/`SearchResult`/
      `Hit`), not executed for real — AEM Mocks doesn't simulate real Query
      Builder predicate matching, so testing "does Query Builder work" isn't
      this project's job. Testing "does the model behave correctly given
      what Query Builder returns" is.
- [x] Real content seeded into the mock JCR (`context.create().page(...)`)
      so `adaptTo(Page.class)` on the mocked hit's resource resolves to
      something real, not null.

## Logging
- [x] Every caught exception is logged with enough context to actually debug
      from the log alone (path, tag, the exception itself) — not a bare
      `catch (Exception e) {}`.
- [x] `WARN` used for recoverable/expected issues (missing config, one bad
      hit), `ERROR` reserved for the query/repository failure case.

## What's intentionally left out (and why)
- No caching layer — for a demo this size, premature. A real high-traffic
  deployment of this component would likely want a short-TTL cache in front
  of the Query Builder call, called out here rather than silently added,
  since caching has its own correctness trade-offs (staleness) worth a
  separate conversation with whoever owns the content freshness requirements.
- No pagination UI — `p.offset` support would be the natural next feature if
  this needs to show more than one page of results.
