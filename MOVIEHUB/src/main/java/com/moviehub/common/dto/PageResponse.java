package com.moviehub.common.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Standard paginated response. {@code page} is 1-based. */
public record PageResponse<T>(List<T> content, int page, int totalPages, long totalResults) {

    public static <T> PageResponse<T> empty() {
        return new PageResponse<>(List.of(), 1, 0, 0);
    }

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.map(mapper).getContent(), page.getNumber() + 1, page.getTotalPages(),
                page.getTotalElements());
    }
}
