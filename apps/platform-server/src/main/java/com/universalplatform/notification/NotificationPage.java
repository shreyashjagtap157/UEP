package com.universalplatform.notification;

import java.util.List;

record NotificationPage<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
