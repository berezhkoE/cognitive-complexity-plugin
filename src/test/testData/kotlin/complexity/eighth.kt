private fun toRegexp(
    antPattern: String,
    directorySeparator: String
): String? {
    val escapedDirectorySeparator = '\\'.toString() + directorySeparator
    val sb = StringBuilder(antPattern.length)
    sb.append('^')
    var i = if (antPattern.startsWith("/") ||  // +1
        antPattern.startsWith("\\")            // +1
    ) 1 else 0 // +1
    while (i < antPattern.length) { // +1
        val ch = antPattern[i]
        if (SPECIAL_CHARS.indexOf(ch) !== -1) { // +2 (nesting = 1)
            sb.append('\\').append(ch)
        } else if (ch == '*') { // +1
            if (i + 1 < antPattern.length // +3 (nesting = 2)
                && antPattern[i + 1] == '*'       // +1
            ) {
                i += if (i + 2 < antPattern.length // +4 (nesting = 3)
                    && isSlash(antPattern[i + 2])  // +1
                ) {
                    sb.append("(?:.*")
                        .append(escapedDirectorySeparator).append("|)")
                    2
                } else { // +1
                    sb.append(".*")
                    1
                }
            } else { // +1
                sb.append("[^").append(escapedDirectorySeparator).append("]*?")
            }
        } else if (ch == '?') { // +1
            sb.append("[^").append(escapedDirectorySeparator).append("]")
        } else if (isSlash(ch)) { // +1
            sb.append(escapedDirectorySeparator)
        } else { // +1
            sb.append(ch)
        }
        i++
    }
    sb.append('$')
    return sb.toString()
} // total complexity = 21
