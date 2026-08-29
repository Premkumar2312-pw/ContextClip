package com.contextclip.repository;

import com.contextclip.model.ClipboardEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClipboardRepository extends JpaRepository<ClipboardEntry, Long> {
}
