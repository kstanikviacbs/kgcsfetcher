package io.github.versi.kgcsfetcher

import io.github.versi.kgcsfetcher.jwt.*
import io.github.versi.kurl.KUrl
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.system.getTimeMillis

interface AuthTokenProvider {

    suspend fun getToken(reset: Boolean = false): String?

    companion object {

        fun create(iss: String, key: String, tokenIssuerUrl: String): AuthTokenProvider =
            AuthTokenProviderImpl(iss, key, tokenIssuerUrl)
    }
}

/**
 * Based on: https://cloud.google.com/docs/authentication/token-types#access
 */
internal class AuthTokenProviderImpl(
    private val iss: String,
    private val key: String,
    private val tokenIssuerUrl: String,
    private val expLeeway: Long = 0
) : AuthTokenProvider, SynchronizedObject() {

    private val googleJWT = GoogleJWT.create()

    private var tokenData: TokenData? = null

    override suspend fun getToken(reset: Boolean): String? {
        synchronized(this) {
            val token = tokenData
            if (token == null || reset || shouldResetToken(token.expirationTimestamp)) {
                reloadToken()
            }

            return tokenData?.value
        }
    }

    private fun shouldResetToken(expirationTimestamp: Long?) =
        expirationTimestamp != null && getTimeMillis() - expLeeway >= expirationTimestamp

    private fun reloadToken() {
        val jwt = googleJWT.generate(iss, key)
        val tokenJsonString = KUrl.forString(tokenIssuerUrl)
            .post("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=$jwt") as String
        val accessTokenResponse: AccessTokenResponse = Json.decodeFromString(tokenJsonString)
        println(accessTokenResponse)
        tokenData = TokenData(accessTokenResponse.token, getTimeMillis() + accessTokenResponse.expiresIn * 1000)
    }
}

@Serializable
private data class TokenData(val value: String, val expirationTimestamp: Long)

@Serializable
data class AccessTokenResponse(
    @SerialName("access_token")
    val token: String,
    @SerialName("expires_in")
    val expiresIn: Int,
    val scope: String? = null,
    @SerialName("token_type")
    val tokenType: String
)