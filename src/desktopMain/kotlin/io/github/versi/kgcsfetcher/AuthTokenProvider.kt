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

        fun create(iss: String, key: String, tokenIssuerUrl: String, expLeeway: Long): AuthTokenProvider =
            ServiceAccountAuthTokenProvider(iss, key, tokenIssuerUrl, expLeeway)

        fun create(expLeeway: Long, serviceAccountName: String = "default"): AuthTokenProvider =
            WorkloadIdentityAuthTokenProvider(serviceAccountName = serviceAccountName, expLeeway = expLeeway)
    }
}

/** Based on: https://cloud.google.com/compute/docs/access/create-enable-service-accounts-for-instances
 *  https://cloud.google.com/kubernetes-engine/docs/concepts/workload-identity#metadata_server
 *  https://cloud.google.com/kubernetes-engine/docs/how-to/workload-identity
 */
internal class WorkloadIdentityAuthTokenProvider(
    metadataServerUrl: String = "http://metadata.google.internal/computeMetadata/v1/instance/",
    serviceAccountName: String = "default",
    expLeeway: Long = 0
) : BaseAccountAuthTokenProvider(expLeeway) {

    private val tokenIssuerUrl = "$metadataServerUrl$serviceAccountName/token"

    override fun reloadToken() {
        val tokenStringCurl = KUrl.forString(tokenIssuerUrl)
        try {
            val tokenJsonString = tokenStringCurl.fetch() as String
            reloadToken(tokenJsonString)
        } finally {
            tokenStringCurl.close()
        }
    }
}

/**
 * Based on: https://cloud.google.com/docs/authentication/token-types#access
 */
internal class ServiceAccountAuthTokenProvider(
    private val iss: String,
    private val key: String,
    private val tokenIssuerUrl: String,
    expLeeway: Long
) : BaseAccountAuthTokenProvider(expLeeway) {

    private val googleJWT = GoogleJWT.create()

    override fun reloadToken() {
        val jwt = googleJWT.generate(iss, key)
        val tokenStringCurl = KUrl.forString(tokenIssuerUrl)
        try {
            val tokenJsonString =
                tokenStringCurl.post("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=$jwt") as String
            reloadToken(tokenJsonString)
        } finally {
            tokenStringCurl.close()
        }
    }
}

internal abstract class BaseAccountAuthTokenProvider(private val expLeeway: Long = 0) : AuthTokenProvider,
    SynchronizedObject() {

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

    protected fun reloadToken(tokenJsonString: String) {
        val accessTokenResponse: AccessTokenResponse = Json.decodeFromString(tokenJsonString)
        println(accessTokenResponse)
        tokenData = TokenData(accessTokenResponse.token, getTimeMillis() + accessTokenResponse.expiresIn * 1000)
    }

    protected abstract fun reloadToken()
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