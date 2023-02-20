package io.github.versi.kgcsfetcher.jwt

import com.paramount.kjwt.JWTTokenGenerator
import com.paramount.kjwt.RS256
import com.paramount.kjwt.TokenData
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

const val aud = "https://oauth2.googleapis.com/token"
private const val scope = "https://www.googleapis.com/auth/devstorage.read_only"

interface GoogleJWT {

    fun generate(iss: String, key: String): String

    companion object {

        fun create(): GoogleJWT = GoogleJWTImpl
    }
}

/**
 * Based on: https://developers.google.com/identity/protocols/oauth2/service-account
 */
private object GoogleJWTImpl : GoogleJWT {

    private val tokenGenerator = JWTTokenGenerator.create()

    override fun generate(iss: String, key: String): String {
        val iat = Clock.System.now()
        val exp = iat.plus(30, DateTimeUnit.MINUTE) // half an hour of validity
        val claims = mapOf(
            "iss" to iss,
            "scope" to scope,
            "aud" to aud,
            "exp" to (exp.toEpochMilliseconds() / 1000).toString(),
            "iat" to (iat.toEpochMilliseconds() / 1000).toString()
        )
        val tokenData = TokenData(
            RS256,
            claims
        )
        return tokenGenerator.generate(key, tokenData)
    }
}

//typealias CFunction = () -> Int
//
//private fun CFunction.invoke(exceptionMessage: String) {
//    if (this.invoke() != 0) {
//        throw Exception(exceptionMessage)
//    }
//}
