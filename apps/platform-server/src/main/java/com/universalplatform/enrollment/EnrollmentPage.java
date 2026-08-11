package com.universalplatform.enrollment;

import java.util.List;

public record EnrollmentPage<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
