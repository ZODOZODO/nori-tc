package com.nori.tc.apps.commgateway.comm;

import com.nori.tc.comm.core.message.OutboundRawFrame;

/**
 * Outbound command wrapper for retry control.
 */
public record OutboundCommand(
        OutboundRawFrame frame,
        int attempt,
        long createdAtEpochMs
) {
    public OutboundCommand {
        if (frame == null) {
            throw new IllegalArgumentException("frame is null");
        }
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must be >= 0");
        }
    }

    public OutboundCommand nextAttempt() {
        return new OutboundCommand(frame, attempt + 1, createdAtEpochMs);
    }
}
