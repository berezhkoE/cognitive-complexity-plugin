fun sumOfPrimes(max: Int): Int {
    var total = 0
    OUT@ for (i in 1..max) {           // +1
        for (j in 2 until i) {         // +2
            if (i % j == 0) {          // +3
                continue@OUT           // +1
            }
        }
        total += i
    }
    return total
} // Cognitive Complexity 7
