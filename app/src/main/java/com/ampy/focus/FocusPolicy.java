package com.ampy.focus;

import android.content.Context;
import java.util.Set;

/** Edition-specific privileged policy implementation. Never infer privileges from build flavor. */
public interface FocusPolicy {
    boolean isAvailable(Context context);
    String availabilityMessage(Context context);
    Result apply(Context context, boolean active, Set<String> blockedPackages);

    final class Result {
        public final boolean success;
        public final String message;

        public Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
