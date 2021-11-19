@Nullable
private fun overriddenSymbolFrom(classType: ClassJavaType): MethodJavaSymbol? {
    if (classType.isUnknown()) { // +1
        return unknownMethodSymbol
    }
    var unknownFound = false
    val symbols: List<JavaSymbol> = classType.getSymbol().members().lookup(name)
    for (overrideSymbol in symbols) { // +1
        if (overrideSymbol.isKind(JavaSymbol.MTH) // +2 (nesting = 1)
            && !overrideSymbol.isStatic()  // +1
        ) {
            val methodJavaSymbol: MethodJavaSymbol = overrideSymbol as MethodJavaSymbol
            if (canOverride(methodJavaSymbol)) { // +3 (nesting = 2)
                val overriding: Boolean = checkOverridingParameters(
                    methodJavaSymbol,
                    classType
                )
                if (overriding == null) { // +4 (nesting = 3)
                    if (!unknownFound) { // +5 (nesting = 4)
                        unknownFound = true
                    }
                } else if (overriding) { // +1
                    return methodJavaSymbol
                }
            }
        }
    }
    return if (unknownFound) { // +1
        unknownMethodSymbol
    } else null                // +1!
} // total complexity != 19 == 20

