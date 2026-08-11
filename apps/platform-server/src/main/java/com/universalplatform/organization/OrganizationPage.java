package com.universalplatform.organization;

import java.util.List;

record OrganizationPage<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    OrganizationPage { items = List.copyOf(items); }
}
