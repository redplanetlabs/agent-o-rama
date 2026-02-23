# Invokes Pagination: Why Per-Task Trimming Exists

This note explains the pagination behavior in `declare-get-invokes-page-topology` in plain language, with a worked example.

## The short version

`invokes-page` is a distributed query:

- each task scans and filters its own shard of invoke rows
- origin merges task-local pages into one sorted result page
- the response carries `pagination-params` as a map: `task-id -> cursor`

Per-task trimming exists so one hot task cannot dump a huge local page into the merge step and destabilize pagination behavior.

## Why task IDs are required

The query must call `ops/current-task-id` because cursor state is task-local.

Each task needs its own ID so it can:

1. read its cursor (`get pagination-params task-id`)
2. continue scanning from the correct boundary for that task
3. write back its next cursor under that same task ID
4. aggregate task-local results at origin keyed by task ID

Without task IDs, cursors from different shards would overwrite each other and pagination would be incorrect.

## What trimming means

After a task scans and applies filters, it has a local sorted map of matches (`task-page`).

Trimming means:

- keep only the most recent `adjust-page-size(page-size)` items from that local `task-page`
- if we dropped older local matches due to trimming, keep a resume cursor so next request can continue into the dropped region

In code terms:

- `trim-invokes-task-page` bounds each local page
- when trimmed, resume cursor is set to first key in the trimmed page

## Worked example

Assume:

- `page-size = 2`
- `adjust-page-size(page-size) = 3` (so each task local page is capped at 3)
- two tasks: `0` and `1`
- scan amount per iteration is fixed at `100`
- sorting is newest first

Filtered matches per task (newest -> oldest):

- task `0`: `[A9, A8, A7, A6, A5]`
- task `1`: `[B9, B8, B7, B6, B5]`

Interleaved by timestamp globally:

- `[A9, B9, A8, B8, A7, B7, A6, B6, A5, B5]`

### Case A: no trimming

If each task returns all local matches in one call:

- task `0` page has 5 items
- task `1` page has 5 items

Origin merge can return first global results, but now per-task carry-over can be large and uneven. In practice this causes unstable behavior (too much local spillover in a single request, unpredictable page counts, and brittle cursor handoff when mixing scan-level and merge-level leftovers).

### Case B: with trimming (current behavior)

Each task local page is capped to 3:

- task `0` returns `[A9, A8, A7]`, keeps resume toward `A6/A5`
- task `1` returns `[B9, B8, B7]`, keeps resume toward `B6/B5`

Origin merges those bounded local pages and returns page 1 (`page-size = 2`):

- page 1: `[A9, B9]`

The response cursor map keeps enough info to continue:

- scan cursor if there is remaining raw scan work
- merge cursor if there is remaining unreturned merge work
- combined per-task cursor prefers merge leftovers first, then scan fallback

Next requests continue predictably:

- page 2: `[A8, B8]`
- page 3: `[A7, B7]`
- page 4: `[A6, B6]`
- page 5: `[A5, B5]`

Result: all filtered matches are returned across pages, with no early termination and no giant per-task local payloads.

## Why trimming is justified

Trimming gives three concrete benefits:

1. **Bounded local contribution**  
   A single task cannot dominate origin merge with an oversized local page.

2. **Stable pagination shape**  
   Requests progress in smaller, consistent chunks rather than "one huge local spill + cleanup".

3. **Correct cursor handoff with filters**  
   Combined cursor logic can safely continue both:
   - unconsumed merge leftovers from this request
   - deeper scan progress when needed

This is especially important when filters are sparse or skewed across tasks.

## Mental model

Think of each task as maintaining a local queue of filtered candidates.

Trimming keeps each queue bounded.
Origin then does a k-way merge over bounded queues.
The returned cursor map tells each queue exactly where to resume next time.
