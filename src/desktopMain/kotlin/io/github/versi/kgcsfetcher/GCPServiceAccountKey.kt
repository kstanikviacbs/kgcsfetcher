package io.github.versi.kgcsfetcher

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class GCPServiceAccountKey(
    val type: String,
    @SerialName("project_id")
    val projectId: String,
    @SerialName("private_key_id")
    val privateKeyId: String,
    @SerialName("private_key")
    val privateKey: String,
    @SerialName("client_email")
    val clientEmail: String,
    @SerialName("client_id")
    val clientId: String,
    @SerialName("auth_uri")
    val authUri: String,
    @SerialName("token_uri")
    val tokenUri: String,
    @SerialName("auth_provider_x509_cert_url")
    val authProviderX509CertUrl: String,
    @SerialName("client_x509_cert_url")
    val clientX509CertUrl: String
) {

    companion object {

        fun parse(content: String): GCPServiceAccountKey = Json.decodeFromString(content)
    }
}