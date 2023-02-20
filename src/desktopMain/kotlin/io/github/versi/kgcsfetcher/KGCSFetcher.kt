package io.github.versi.kgcsfetcher

import io.github.versi.kgcsfetcher.jwt.aud
import io.github.versi.kurl.KUrl
import io.github.versi.kurl.KUrlOptions

interface KGCSFetcher {

    suspend fun fetchFile(urlEncodedObjectName: String): ByteArray

    companion object {

        fun create(
            config: Config,
            connectOptions: ConnectOptions = ConnectOptions()
        ): KGCSFetcher = KGSCFetcherImpl(
            config, connectOptions
        )
    }

    data class Config(
        val bucketName: String,
        val iss: String,
        val key: String
    )

    data class ConnectOptions(
        val connectTimeoutSec: Long = 3,
        val transferTimeoutSec: Long = 12,
        val withConnectionSharing: Boolean = false,
    )
}

/**
 * Based on: https://cloud.google.com/storage/docs/downloading-objects#rest-download-object
 */
internal class KGSCFetcherImpl(
    config: KGCSFetcher.Config,
    connectOptions: KGCSFetcher.ConnectOptions
) : KGCSFetcher {

    private val kUrlOptions = KUrlOptions(
        connectTimeoutSec = connectOptions.connectTimeoutSec,
        transferTimeoutSec = connectOptions.transferTimeoutSec,
        withConnectionSharing = connectOptions.withConnectionSharing
    )
    private val authTokenProvider = AuthTokenProvider.create(config.iss, config.key, aud)
    private val fileBasePath = "https://storage.googleapis.com/storage/v1/b/${config.bucketName}/o/"

    override suspend fun fetchFile(urlEncodedObjectName: String): ByteArray {
        val token = authTokenProvider.getToken()
        val headers = listOf("Authorization: Bearer $token")
        val fileUrl = "$fileBasePath$urlEncodedObjectName?alt=media"
        val curl = KUrl.forBytes(fileUrl, kUrlOptions)
        try {
            return curl.fetch(headers = headers) as ByteArray
        } finally {
            curl.close()
        }
    }
}