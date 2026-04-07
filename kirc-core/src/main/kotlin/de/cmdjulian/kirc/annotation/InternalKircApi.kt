package de.cmdjulian.kirc.annotation

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is an internal kirc API. It may change at any time without notice.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class InternalKircApi
