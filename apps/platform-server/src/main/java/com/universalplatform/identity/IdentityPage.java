package com.universalplatform.identity;

import java.util.List;

public record IdentityPage<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    public IdentityPage {
        items = List.copyOf(items);
    }
}
