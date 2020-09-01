// TARGET_BACKEND: JVM
// NO_CHECK_LAMBDA_INLINING
// WITH_RUNTIME
// FILE: 1.kt

package test

inline fun test(s: () -> Unit) {
    s()
}

// FILE: 2.kt

import test.*

fun box(): String {
    test {
        {
            val p = object {}
            // Check that Java reflection doesn't crash. Actual values are tested in bytecodeListing/inline/enclosingInfo/.
            p.javaClass.enclosingMethod.declaringClass
            {
                val q = object {}
                q.javaClass.enclosingMethod.declaringClass
            }()
        }()
    }

    return "OK"
}
