fun getWords(int number): String {
    when (number) {         // +1
        1 -> return "one";
        2 -> return "a couple";
        3 -> return "a few";
        else -> return "lots";
    }
} // Cognitive Complexity 1
