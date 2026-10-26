package com.sb.peh.compat;

import java.util.stream.Collectors;

/** Tiny helper so mixin handlers do not need lambdas. */
public final class FellingDiagnostics {
    private FellingDiagnostics() {
    }

    /** Who called the mixin handler: skips this method and the handler, shows the next few frames. */
    public static String callChain() {
        return StackWalker.getInstance().walk(frames -> frames
                .skip(2)
                .limit(5)
                .map(f -> f.getClassName() + "." + f.getMethodName())
                .collect(Collectors.joining(" <- ")));
    }
}
