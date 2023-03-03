package io.github.versi.kgcsfetcher

import io.github.versi.kgcsfetcher.jwt.aud
import io.github.versi.kurl.KUrl
import io.github.versi.kurl.KUrlOptions
import io.github.versi.kurl.CUrlUnauthorizedException

interface KGCSFetcher {

    suspend fun fetchFile(urlEncodedObjectName: String): ByteArray

    companion object {

        fun create(
            config: Config,
            connectOptions: ConnectOptions = ConnectOptions()
        ): KGCSFetcher {
            return when (config.serviceAccountData) {
                is Config.TextContent -> {
                    val serviceAccount = GCPServiceAccountKey.parse(config.serviceAccountData.content)
                    val authTokenProvider = AuthTokenProvider.create(
                        serviceAccount.clientEmail,
                        serviceAccount.privateKey,
                        aud,
                        connectOptions.tokenExpLeeway
                    )
                    KGSCFetcherImpl(authTokenProvider, config.bucketName, connectOptions)
                }

                is Config.Name -> {
                    KGSCFetcherImpl(
                        AuthTokenProvider.create(connectOptions.tokenExpLeeway, config.serviceAccountData.value),
                        config.bucketName,
                        connectOptions
                    )
                }
            }
        }
    }

    data class Config(
        val bucketName: String,
        val filesDir: String,
        val serviceAccountData: ServiceAccountData,
        val tokenExpLeeway: Long
    ) {

        sealed class ServiceAccountData
        data class Name(val value: String = "default") : ServiceAccountData()
        data class TextContent(val content: String) : ServiceAccountData()
    }

    data class ConnectOptions(
        val connectTimeoutSec: Long = 3,
        val transferTimeoutSec: Long = 12,
        val withConnectionSharing: Boolean = false,
        val tokenExpLeeway: Long = 0
    )
}

/**
 * Based on: https://cloud.google.com/storage/docs/downloading-objects#rest-download-object
 */
internal class KGSCFetcherImpl(
    private val authTokenProvider: AuthTokenProvider,
    bucketName: String,
    connectOptions: KGCSFetcher.ConnectOptions
) : KGCSFetcher {

    private val kUrlOptions = KUrlOptions(
        connectTimeoutSec = connectOptions.connectTimeoutSec,
        transferTimeoutSec = connectOptions.transferTimeoutSec,
        withConnectionSharing = connectOptions.withConnectionSharing
    )
    private val fileBasePath = "https://storage.googleapis.com/storage/v1/b/${bucketName}/o/"

    override suspend fun fetchFile(urlEncodedObjectName: String): ByteArray {
        return fetchFile(urlEncodedObjectName, refreshToken = false)
    }

    private suspend fun fetchFile(urlEncodedObjectName: String, refreshToken: Boolean): ByteArray {
        val token = authTokenProvider.getToken(refreshToken)
        val headers = listOf("Authorization: Bearer $token")
        val fileUrl = "$fileBasePath$urlEncodedObjectName?alt=media"
        val curl = KUrl.forBytes(fileUrl, kUrlOptions)
        return try {
            curl.fetch(headers = headers) as ByteArray
        } catch (unauthorizedException: CUrlUnauthorizedException) {
            // attempts once again with refreshed token in case 401 received
            if (!refreshToken) {
                fetchFile(urlEncodedObjectName, refreshToken = true)
            } else {
                throw unauthorizedException
            }
        } finally {
            curl.close()
        }
    }
}