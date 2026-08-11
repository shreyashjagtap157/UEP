package com.universalplatform.curriculum;

import java.util.List;

public record CurriculumPage<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
