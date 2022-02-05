fun testBinaryExpr(b: Boolean) {
    if (a               // +1 for `if`
        && b && c       // +1
        || d || e       // +1
        && f            // +1
    ) {
    }

    if (a               // +1 for `if`
        &&              // +1
        !(b && c)       // +1
                        //
                        //
                        //
                        //
    ) {
    }
}