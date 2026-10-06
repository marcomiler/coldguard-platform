package com.coldguard.asset.application;

import java.util.List;

/** One page of a keyset-paginated listing; {@code nextCursor} is empty on the last page. */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {}
