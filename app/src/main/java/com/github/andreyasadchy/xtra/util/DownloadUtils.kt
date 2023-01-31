package com.github.andreyasadchy.xtra.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Environment
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.offline.Downloadable
import com.github.andreyasadchy.xtra.model.offline.OfflineVideo
import com.github.andreyasadchy.xtra.model.offline.Request
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.download.BaseDownloadDialog.Storage
import com.github.andreyasadchy.xtra.ui.download.DownloadService
import com.github.andreyasadchy.xtra.ui.download.DownloadService.Companion.KEY_REQUEST
import java.io.File
import java.io.FileOutputStream

object DownloadUtils {

    val isExternalStorageAvailable: Boolean
        get() = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED

    fun download(context: Context, request: Request) {
        val intent = Intent(context, DownloadService::class.java)
                .putExtra(KEY_REQUEST, request)
        context.startService(intent)
        DownloadService.activeRequests.add(request.offlineVideoId)
    }

    fun prepareDownload(context: Context, downloadable: Downloadable, url: String, path: String, duration: Long?, startPosition: Long?, segmentFrom: Int? = null, segmentTo: Int? = null): OfflineVideo {
        val offlinePath = if (downloadable is Video) {
            "$path${System.currentTimeMillis()}.m3u8"
        } else {
            "$path.mp4"
        }
        return with(downloadable) {
            if (!id.isNullOrBlank()) {
                try {
                    Glide.with(context)
                        .asBitmap()
                        .load(thumbnail)
                        .into(object: CustomTarget<Bitmap>() {
                            override fun onLoadCleared(placeholder: Drawable?) {

                            }

                            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                savePng(context, "thumbnails", id!!, resource)
                            }
                        })
                } catch (e: Exception) {

                }
            }
            if (!channelId.isNullOrBlank()) {
                try {
                    Glide.with(context)
                        .asBitmap()
                        .load(channelLogo)
                        .into(object: CustomTarget<Bitmap>() {
                            override fun onLoadCleared(placeholder: Drawable?) {

                            }

                            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                savePng(context, "profile_pics", channelId!!, resource)
                            }
                        })
                } catch (e: Exception) {

                }
            }
            val downloadedThumbnail = id?.let { File(context.filesDir.toString() + File.separator + "thumbnails" + File.separator + "${it}.png").absolutePath }
            val downloadedLogo = channelId?.let { File(context.filesDir.toString() + File.separator + "profile_pics" + File.separator + "${it}.png").absolutePath }
            OfflineVideo(
                url = offlinePath,
                sourceUrl = url,
                sourceStartPosition = startPosition,
                name = title,
                channelId = channelId,
                channelLogin = channelLogin,
                channelName = channelName,
                channelLogo = downloadedLogo,
                thumbnail = downloadedThumbnail,
                gameId = gameId,
                gameName = gameName,
                duration = duration,
                uploadDate = uploadDate?.let { TwitchApiHelper.parseIso8601Date(it) },
                downloadDate = System.currentTimeMillis(),
                progress = OfflineVideo.STATUS_PENDING,
                maxProgress = if (segmentTo != null && segmentFrom != null) segmentTo - segmentFrom + 1 else 100,
                type = type,
                videoId = id
            )
        }
    }


    fun getAvailableStorage(context: Context): List<Storage> {
        val storage = ContextCompat.getExternalFilesDirs(context, ".downloads")
        val list = mutableListOf<Storage>()
        for (i in storage.indices) {
            val storagePath = storage[i]?.absolutePath ?: continue
            val name = if (i == 0) {
                context.getString(R.string.internal_storage)
            } else {
                val endRootIndex = storagePath.indexOf("/Android/data")
                if (endRootIndex < 0) continue
                val startRootIndex = storagePath.lastIndexOf(File.separatorChar, endRootIndex - 1)
                storagePath.substring(startRootIndex + 1, endRootIndex)
            }
            list.add(Storage(i, name, storagePath))
        }
        return list
    }

    fun savePng(context: Context, folder: String, fileName: String, bitmap: Bitmap) {
        val outputStream: FileOutputStream
        try {
            val path = context.filesDir.toString() + File.separator + folder + File.separator + "$fileName.png"
            File(context.filesDir, folder).mkdir()
            outputStream = FileOutputStream(File(path))
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.close()
        } catch (e: Exception) {

        }
    }
}
