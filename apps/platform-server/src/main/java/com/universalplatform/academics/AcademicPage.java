package com.universalplatform.academics;

import java.util.List;

public record AcademicPage<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
