package example.com.data

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.StorageOptions
import com.google.gson.Gson

object GcsUtils {
    private val gson = Gson()
    private val bucketName = "kotr-test.appspot.com"

    fun uploadJsonToGcs(fileName: String, data: Any?) {
        val storage = StorageOptions.getDefaultInstance().service
        val blobId = BlobId.of(bucketName, fileName)
        val blobInfo = BlobInfo.newBuilder(blobId).setContentType("application/json").build()
        val jsonData = gson.toJson(data)
        storage.create(blobInfo, jsonData.toByteArray())
        println("File $fileName uploaded to $bucketName.")
    }

    fun downloadJsonFromGcs(fileName: String): String? {
        val storage = StorageOptions.getDefaultInstance().service
        val blob = storage.get(BlobId.of(bucketName, fileName))
        return blob?.getContent()?.let { String(it) }
    }
}