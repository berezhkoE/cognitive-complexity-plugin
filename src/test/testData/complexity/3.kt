fun myMethod() {
    try {
        if (a) {                       // +1
            for (i in 1..10) {         // +2 (nesting=1)
                while (b) {            // +3 (nesting=2)

                }
            }
        }
    } catch (Exception e) {            // +1
        if (c) {
        }                     // +2 (nesting=1)
    }
} // Cognitive Complexity 9
