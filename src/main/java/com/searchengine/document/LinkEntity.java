package com.searchengine.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** A hyperlink found on a crawled document; the target may or may not be crawled itself. */
@Entity
@Table(name = "links", indexes = @Index(name = "idx_links_from", columnList = "fromDocId"))
public class LinkEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int fromDocId;

    @Column(length = 2048)
    private String toUrl;

    protected LinkEntity() {
    }

    public LinkEntity(int fromDocId, String toUrl) {
        this.fromDocId = fromDocId;
        this.toUrl = toUrl;
    }

    public int getFromDocId() {
        return fromDocId;
    }

    public String getToUrl() {
        return toUrl;
    }
}
