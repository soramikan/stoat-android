package chat.stoat.api.routes.microservices.autumn

import chat.stoat.api.HitRateLimitException
import chat.stoat.api.StoatAPI
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.routes.misc.getRootRoute
import chat.stoat.core.model.data.STOAT_FILES
import chat.stoat.core.model.schemas.AutumnError
import chat.stoat.core.model.schemas.AutumnId
import io.ktor.client.plugins.onUpload
import io.ktor.client.statement.HttpResponse
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.min

const val MAX_ATTACHMENTS_PER_MESSAGE = 5
private const val DEFAULT_UPLOAD_CHUNK_SIZE = 50L * 1024L * 1024L

data class FileArgs(
    val file: File,
    val filename: String,
    val contentType: String,
    val spoiler: Boolean = false,
    val pickerIdentifier: String? = null,
)

@Serializable
private data class CompleteUploadPayload(
    val filename: String,
    val total_chunks: Int,
    val total_size: Long,
    val sha256: String
)

private fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)

    file.inputStream().use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }

    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun readChunk(input: java.io.InputStream, size: Int): ByteArray {
    val chunk = ByteArray(size)
    var readTotal = 0

    while (readTotal < size) {
        val read = input.read(chunk, readTotal, size - readTotal)
        if (read == -1) break
        readTotal += read
    }

    return if (readTotal == size) chunk else chunk.copyOf(readTotal)
}

private suspend fun ensureAutumnSuccess(response: HttpResponse) {
    if (response.status.value in 200..299) return

    val body = response.bodyAsText()
    val error = runCatching {
        StoatJson.decodeFromString(AutumnError.serializer(), body)
    }.getOrNull()

    if (error != null) {
        throw Exception(error.type)
    }

    if (response.status == HttpStatusCode.TooManyRequests) {
        throw HitRateLimitException()
    }
    if (response.status == HttpStatusCode.PayloadTooLarge) {
        throw Exception("File too large")
    }
    throw Exception("Unknown error")
}

private suspend fun decodeAutumnIdOrThrow(response: HttpResponse): String {
    val body = response.bodyAsText()

    try {
        val autumnId = StoatJson.decodeFromString(AutumnId.serializer(), body)
        return autumnId.id
    } catch (e: Exception) {
        val error = runCatching {
            StoatJson.decodeFromString(AutumnError.serializer(), body)
        }.getOrNull()

        if (error != null) {
            throw Exception(error.type)
        }

        if (response.status == HttpStatusCode.TooManyRequests) {
            throw HitRateLimitException()
        }
        if (response.status == HttpStatusCode.PayloadTooLarge) {
            throw Exception("File too large")
        }
        throw Exception("Unknown error")
    }
}

suspend fun uploadToAutumn(
    file: File,
    name: String,
    tag: String,
    contentType: ContentType,
    onProgress: (Long, Long) -> Unit = { _, _ -> }
): String {
    val uploadUrl = "$STOAT_FILES/$tag"
    val uploadId = UUID.randomUUID().toString()
    val chunkSize = runCatching {
        getRootRoute().features.limits?.global?.chunkUploadSize
    }.getOrNull()?.takeIf { it > 0 } ?: DEFAULT_UPLOAD_CHUNK_SIZE
    val totalSize = file.length()
    val totalChunks = maxOf(1, ((totalSize + chunkSize - 1) / chunkSize).toInt())
    val checksum = sha256Hex(file)
    var uploadedBefore = 0L

    file.inputStream().use { input ->
        repeat(totalChunks) { chunkIndex ->
            val remaining = totalSize - uploadedBefore
            val expectedSize = min(chunkSize, remaining).toInt()
            val chunk = readChunk(input, expectedSize)
            val chunkOffset = uploadedBefore

            val response = StoatHttp.post("$uploadUrl/chunks") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("upload_id", uploadId)
                            append("chunk_index", chunkIndex.toString())
                            append("total_chunks", totalChunks.toString())
                            append("total_size", totalSize.toString())
                            append(
                                "chunk",
                                chunk,
                                Headers.build {
                                    append(HttpHeaders.ContentType, contentType.toString())
                                    append(HttpHeaders.ContentDisposition, "filename=\"$name\"")
                                }
                            )
                        }
                    )
                )
                header(StoatAPI.TOKEN_HEADER_NAME, StoatAPI.sessionToken)
                onUpload { bytesSentTotal, _ ->
                    onProgress(
                        min(totalSize, chunkOffset + min(bytesSentTotal, chunk.size.toLong())),
                        totalSize
                    )
                }
            }

            ensureAutumnSuccess(response)
            uploadedBefore += chunk.size
            onProgress(uploadedBefore, totalSize)
        }
    }

    val completeResponse = StoatHttp.post("$uploadUrl/chunks/$uploadId/complete") {
        contentType(ContentType.Application.Json)
        header(StoatAPI.TOKEN_HEADER_NAME, StoatAPI.sessionToken)
        setBody(
            CompleteUploadPayload(
                filename = name,
                total_chunks = totalChunks,
                total_size = totalSize,
                sha256 = checksum
            )
        )
    }

    return decodeAutumnIdOrThrow(completeResponse)
}
