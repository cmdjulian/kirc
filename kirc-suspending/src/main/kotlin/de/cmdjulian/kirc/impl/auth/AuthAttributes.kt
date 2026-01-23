package de.cmdjulian.kirc.impl.auth

import io.ktor.util.AttributeKey

object AuthAttributes {
    val SCOPE_REPO = AttributeKey<String>("AuthScopeRepo")
    val SCOPE_TYPE = AttributeKey<String>("AuthScopeType")
    val SESSION_ID = AttributeKey<String>("AuthSessionId")
    val SKIP_REFRESH = AttributeKey<Boolean>("SkipAuthRefresh")
}
