package com.roadresq.roadresq.utils;

import lombok.Builder;

@Builder
public record RefinedPagination(
        int page,
        int size
) {

}
