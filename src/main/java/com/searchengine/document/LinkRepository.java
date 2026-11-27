package com.searchengine.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface LinkRepository extends JpaRepository<LinkEntity, Long> {
    @Transactional
    void deleteByFromDocId(int fromDocId);
}
