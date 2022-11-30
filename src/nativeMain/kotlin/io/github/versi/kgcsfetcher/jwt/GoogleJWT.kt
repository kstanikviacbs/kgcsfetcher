package io.github.versi.kgcsfetcher.jwt

import kotlinx.cinterop.*
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.posix.__stderrp

const val aud = "https://oauth2.googleapis.com/token"
private const val scope = "https://www.googleapis.com/auth/devstorage.read_only"

interface GoogleJWT {

    fun generate(iss: String, key: String): String

    companion object {

        fun create(): GoogleJWT = GoogleJWTImpl()
    }
}

/**
 * Based on: https://developers.google.com/identity/protocols/oauth2/service-account
 */
internal class GoogleJWTImpl(private val memScope: MemScope = MemScope()) : GoogleJWT {

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun generate(iss: String, key: String): String {
        val jwtPointer = memScope.allocPointerTo<jwt_t>()
        var result: CPointer<ByteVar>? = null
        try {
            if (jwt_new(jwtPointer.ptr) != 0) {
                throw Exception("Failed to init JWT")
            }

            val iat = Clock.System.now()
            val exp = iat.plus(30, DateTimeUnit.MINUTE) // half an hour of validity
            val claims = Claims(iss, scope, aud, exp.toEpochMilliseconds() / 1000, iat.toEpochMilliseconds() / 1000)
            val claimsJson = Json.encodeToString(claims)
            if (jwt_add_grants_json(jwtPointer.value, claimsJson) != 0) {
                throw Exception("Failed to set JWT claims")
            }
            val keyStr = key.encodeToByteArray().toUByteArray()

            if (jwt_set_alg(jwtPointer.value, JWT_ALG_RS256, keyStr.refTo(0), keyStr.size) < 0) {
                throw Exception("Failed to set JWT alg")
            }
//        if (jwt_set_alg(jwtPointer.value, JWT_ALG_NONE, null, 0) < 0) {
//            throw Exception("Failed to set JWT alg")
//        }
            result = jwt_encode_str(jwtPointer.value)
//            jwt_dump_fp(jwtPointer.value, __stderrp, 1)
            return result?.toKString() ?: throw Exception("Failed to encode JWT")
        } finally {
            result?.let {
                jwt_free_str(it)
            }
            jwt_free(jwtPointer.value)
        }
    }
}

@Serializable
private data class Claims(
    val iss: String,
    val scope: String,
    val aud: String,
    val exp: Long,
    val iat: Long
)

//typealias CFunction = () -> Int
//
//private fun CFunction.invoke(exceptionMessage: String) {
//    if (this.invoke() != 0) {
//        throw Exception(exceptionMessage)
//    }
//}
