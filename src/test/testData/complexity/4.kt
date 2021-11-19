fun myMethod2() {
    Runnable r =() -> {               // +0 (but nesting level is now 1)
        if (a) {
        }                      // +2 (nesting=1)
    };
} // Cognitive Complexity 2
