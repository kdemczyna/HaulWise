package com.haulwise.dto.plan;

/** How prominently the UI shows a plan warning. */
public enum WarningSeverity {
    /** Worth knowing; nothing to fix (e.g. estimated distances). */
    INFO,
    /** The dispatcher should act or accept a trade-off (e.g. a missed deadline). */
    WARNING
}
