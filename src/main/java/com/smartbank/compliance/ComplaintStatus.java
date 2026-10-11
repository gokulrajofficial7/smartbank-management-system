package com.smartbank.compliance;

/**
 * Lifecycle status of an unauthorized transaction complaint under RBI framework.
 */
public enum ComplaintStatus {
    SUBMITTED("Submitted by Customer"),
    UNDER_INVESTIGATION("Under Internal Investigation"),
    SHADOW_CREDITED("Shadow Credit Applied (10-Day Policy)"),
    RESOLVED("Resolved"),
    REJECTED("Rejected with Grounds");

    private final String description;

    ComplaintStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
