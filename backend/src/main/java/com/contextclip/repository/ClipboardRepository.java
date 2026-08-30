package com.contextclip.repository;

import com.contextclip.dto.AnalyticsActivityResponse;
import com.contextclip.dto.AnalyticsCountResponse;
import com.contextclip.model.ClipboardEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClipboardRepository extends JpaRepository<ClipboardEntry, Long> {

    @Query("""
        SELECT c FROM ClipboardEntry c
        WHERE (:q IS NULL OR :q = '' OR LOWER(c.content) LIKE LOWER(CONCAT('%', :q, '%')))
          AND (:type IS NULL OR :type = '' OR UPPER(c.type) = UPPER(:type))
          AND (:technology IS NULL OR :technology = '' OR UPPER(c.technology) = UPPER(:technology))
          AND (:category IS NULL OR :category = '' OR UPPER(c.category) = UPPER(:category))
        ORDER BY c.capturedAt DESC
    """)
    List<ClipboardEntry> search(
            @Param("q") String q,
            @Param("type") String type,
            @Param("technology") String technology,
            @Param("category") String category
    );

    @Query("""
        SELECT new com.contextclip.dto.AnalyticsCountResponse(c.type, COUNT(c))
        FROM ClipboardEntry c
        GROUP BY c.type
        ORDER BY COUNT(c) DESC
    """)
    List<AnalyticsCountResponse> countGroupedByType();

    @Query("""
        SELECT new com.contextclip.dto.AnalyticsCountResponse(c.technology, COUNT(c))
        FROM ClipboardEntry c
        GROUP BY c.technology
        ORDER BY COUNT(c) DESC
    """)
    List<AnalyticsCountResponse> countGroupedByTechnology();

    @Query("""
        SELECT new com.contextclip.dto.AnalyticsCountResponse(c.category, COUNT(c))
        FROM ClipboardEntry c
        GROUP BY c.category
        ORDER BY COUNT(c) DESC
    """)
    List<AnalyticsCountResponse> countGroupedByCategory();

    @Query("""
        SELECT new com.contextclip.dto.AnalyticsActivityResponse(CAST(c.capturedAt as LocalDate), COUNT(c))
        FROM ClipboardEntry c
        GROUP BY CAST(c.capturedAt as LocalDate)
        ORDER BY CAST(c.capturedAt as LocalDate) ASC
    """)
    List<AnalyticsActivityResponse> countDailyActivity();
}
