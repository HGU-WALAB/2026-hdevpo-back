package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * CV list item (lightweight for list view).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvListItem {

    private Long id;
    private String title;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
}
