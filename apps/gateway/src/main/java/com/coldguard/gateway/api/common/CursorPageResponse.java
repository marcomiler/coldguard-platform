package com.coldguard.gateway.api.common;

import java.util.List;

/** One page of a keyset-paginated listing; {@code nextCursor} is empty on the last page. */
public record CursorPageResponse<T>(List<T> items, String nextCursor, boolean hasMore) {}
