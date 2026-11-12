package dev.tvlimiter.service

/**
 * Turns monotonic clock readings into whole seconds to charge. A long gap (the service was
 * stalled or the device slept) counts at most two polling intervals, and leftover milliseconds
 * carry into the next tick so 2.003 s ticks don't lose time.
 */
class TickAccountant(private val intervalMillis: Long) {
    private var last: Long? = null
    private var carryMillis = 0L

    fun secondsToCharge(nowElapsed: Long, chargeable: Boolean): Long {
        val previous = last
        last = nowElapsed
        if (previous == null) return 0
        val delta = nowElapsed - previous
        if (delta <= 0 || !chargeable) {
            carryMillis = 0
            return 0
        }
        val total = minOf(delta, intervalMillis * 2) + carryMillis
        carryMillis = total % 1000
        return total / 1000
    }
}

/** Packages that never count and never get blocked: this app and the home launchers. */
class ExemptPackages(private val ownPackage: String, private val homePackages: Set<String>) {
    fun isExempt(packageName: String): Boolean = packageName == ownPackage || packageName in homePackages

    companion object {
        /**
         * Picks the real launchers from the home intent's resolvers, given as package and
         * priority. The system Settings app also answers the home intent with a negative-priority
         * fallback screen used during boot, and must not be exempted along with the launcher.
         */
        fun launchers(resolvers: List<Pair<String, Int>>): Set<String> =
            resolvers.filter { it.second >= 0 }.map { it.first }.toSet()

        /**
         * Only the home app actually in use is exempt, so another app that merely declares a home
         * screen can't dodge the limits. When no default is chosen the system answers with its
         * chooser (package "android"), and every real launcher stays exempt so Home still works.
         */
        fun homes(defaultHome: String?, resolvers: () -> List<Pair<String, Int>>): Set<String> =
            if (defaultHome != null && defaultHome != CHOOSER_PACKAGE) setOf(defaultHome) else launchers(resolvers())

        private const val CHOOSER_PACKAGE = "android"
    }
}
