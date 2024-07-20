package example.com.data

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.StorageOptions
import com.google.gson.Gson

object GcsUtils {
    private val gson = Gson()
    private val bucketName = System.getenv("BUCKET_NAME")

    fun uploadJsonToGcs(fullPath: String, data: Any?) {
        val storage = StorageOptions.getDefaultInstance().service
        val blobId = BlobId.of(bucketName, fullPath)
        val blobInfo = BlobInfo.newBuilder(blobId).setContentType("application/json").build()
        val jsonData = gson.toJson(data)
        storage.create(blobInfo, jsonData.toByteArray())
        println("File $fullPath uploaded to $bucketName.")
    }

    fun downloadJsonFromGcs(fullPath: String): String? {
        val storage = StorageOptions.getDefaultInstance().service
        val blob = storage.get(BlobId.of(bucketName, fullPath))
        return blob?.getContent()?.let { String(it) }
    }

    fun downloadIcoFromGcs(fullPath: String): ByteArray? {
        val storage = StorageOptions.getDefaultInstance().service
        val blob = storage.get(BlobId.of(System.getenv("BUCKET_NAME"), fullPath))
        return blob?.getContent()
    }
}
