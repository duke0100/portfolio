package com.example.project2.entity;

/**
 * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#enumerated-and-transient
 * Kept as an enum rather than a String so a typo in a status is a compile error.
 */
public enum ReviewStatus {

    /** A new review, not visible on the site yet. */
    PENDING,

    /** Approved and visible. */
    PUBLISHED,

    /** Rejected by a moderator. */
    REJECTED
}
