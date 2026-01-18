# Human Feedback Queue Pagination Conundrum

## Problem Summary

The human feedback queue has two pages that need to share item data:
1. **Queue Detail Page** (`detail`) - Lists all queue items in a table
2. **Item Review Page** (`item-detail`) - Shows a single item for review with prev/next navigation

### Current Behavior (Broken)

- **Queue Detail** uses cache key `[:human-feedback-queue-items module-id queue-id]`
- **Item Review** uses cache key `[:human-feedback-queue-items-review module-id queue-id]`
- These are **separate caches**, so data isn't shared

**Result:** When you click an item from the list, the item page fetches starting from that item's UUID. You lose access to earlier items and can't navigate backwards, even though you just saw them on the list.

## Backend Pagination

The backend uses `search-loop` which:
- Orders items by **UUID** (sorted map keys)
- Uses **forward-only cursor pagination** via `sorted-map-range-from` with `inclusive?=false`
- Returns `{:items [...] :pagination-params <next-cursor-uuid>}`
- No support for reverse pagination

## User Flows to Support

### Flow 1: Normal (List → Item)
1. User visits queue list → sees items 0-19
2. User clicks item 5 → reviews it
3. User navigates prev/next through items
4. **Expected:** Can navigate to any item in the loaded list (0-19)

### Flow 2: Deep Link (Direct URL to Item)
1. User has a direct URL to item 25 (from notification, bookmark, etc.)
2. User loads the URL directly
3. **Expected:** Item displays, user can navigate (at minimum forward)

### Flow 3: Mixed
1. User deep-links to item 25
2. User navigates around, then goes to queue list
3. **Expected:** List shows items from the beginning, not from item 25

## Options Considered

### Option A: Simple Shared Cache (No Initial Pagination)

**Change:**
- Both pages use same cache key
- Remove `:initial-pagination` from item page
- Always fetch from beginning when cache is empty

**Pros:**
- Simple implementation
- No merge logic needed
- List and item pages stay in sync

**Cons:**
- Deep link to item 25 fails if first page only has 20 items
- User sees "Item not found" for items beyond page 1

### Option B: Shared Cache + Initial Pagination + List Reset

**Change:**
- Both pages use same cache key
- Item page uses `:initial-pagination item-id` when cache is empty
- List page always refetches from nil on mount (resets cache)

**Pros:**
- Deep links work for any item
- Can navigate forward from deep-linked item
- List page always shows from beginning

**Cons:**
- After deep link, previous navigation is disabled (started at that item)
- List page refetch might feel slow if cache was valid

### Option C: Fetch + Backfill + Merge

**Change:**
- Item page fetches from item-id cursor
- Then also fetches from nil to get earlier items
- Merge by UUID order into single sorted list

**Pros:**
- Full navigation always available
- Best UX

**Cons:**
- Complex merge logic
- Multiple requests on initial load
- Need to track "lowest fetched cursor" and "highest fetched cursor"

### Option D: Item Page Without Pagination

**Change:**
- Item page doesn't use `use-paginated-query`
- Looks up current item from list cache if available
- If not in cache, fetches single item (new endpoint) or shows "go to queue"
- Navigation only within list cache

**Pros:**
- Clear separation of concerns
- No complex cache merging

**Cons:**
- Deep links have degraded navigation (no prev/next)
- Might need new "get single item" endpoint

## Recommendation

**Option B (Shared Cache + Initial Pagination + List Reset)** balances simplicity with decent UX:

1. Share the cache key between both pages
2. Keep `:initial-pagination item-id` on item page for empty cache case
3. List page clears cache and refetches from nil on mount

**Trade-offs accepted:**
- Deep link users can only navigate forward from their entry point
- Previous button is disabled when at the start of loaded data (index 0)
- List page always refetches (could optimize with "starting cursor" tracking later)

## Implementation Notes

### Frontend Changes Needed

1. `item-detail`: Change query-key from `[:human-feedback-queue-items-review ...]` to `[:human-feedback-queue-items ...]`

2. `detail`: Add effect to clear/refetch cache on mount if data exists but doesn't start from beginning

3. Navigation logic: "Previous" disabled when `current-idx === 0` (already the case)

### Backend (No Changes Needed)

The backend pagination via `search-loop` works correctly. It returns items in UUID order starting from the cursor.

### Future Enhancements

- Track "starting cursor" in cache state to avoid unnecessary refetches
- Implement merge logic (Option C) for seamless bidirectional navigation
- Add "get single item" endpoint for true deep link support without loading pages
