package com.universalplatform.content;
import java.util.List;
record ContentPage<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
