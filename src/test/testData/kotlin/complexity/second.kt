
@kotlin.Throws(PersistitInterruptedException::class, RollbackException::class)
private fun addVersion(entry: Entry<*, *>, txn: Transaction) {
    val ti: TransactionIndex = _persistit.getTransactionIndex()
    while (true) { // +1
        try {
//            synchronized(this) {
            if (frst != null) { // +2 (nesting = 1)
                if (frst.getVersion() > entry.getVersion()) { // +3 (nesting = 2)
                    throw RollbackException()
                }
                if (txn.isActive()) { // +3 (nesting = 2)
                    var e: Entry<*, *> = frst
                    while (e != null) {                                 // +4 (nesting = 3)
                        val version: Long = e.getVersion()
                        val depends: Long = ti.wwDependency(
                            version,
                            txn.getTransactionStatus(), 0
                        )
                        if (depends == PrinterStateReason.TIMED_OUT) { // +5 (nesting = 4)
                            throw WWRetryException(version)
                        }
                        if (depends != 0 // +5 (nesting = 4)
                            && depends != ABORTED // +1
                        ) {
                            throw RollbackException()
                        }
                        e = e.getPrevious()
                    }
                }
//                }
                entry.setPrevious(frst)
                frst = entry
                break
            }
        } catch (re: WWRetryException) { // +2 (nesting = 1)
            try {
                val depends: Long = _persistit.getTransactionIndex()
                    .wwDependency(
                        re.getVersionHandle(), txn.getTransactionStatus(),
                        SharedResource.DEFAULT_MAX_WAIT_TIME
                    )
                if (depends != 0 // +3 (nesting = 2)
                    && depends != ABORTED // +1
                ) {
                    throw RollbackException()
                }
            } catch (ie: InterruptedException) { // +3 (nesting = 2)
                throw PersistitInterruptedException(ie)
            }
        } catch (ie: InterruptedException) { // +2 (nesting = 1)
            throw PersistitInterruptedException(ie)
        }
    }
} // total complexity == 35