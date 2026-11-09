@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.example.boschlaser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlin.native.ref.WeakReference
import platform.Foundation.*
import platform.CoreGraphics.*
import platform.UIKit.*
import platform.Photos.*
import platform.PhotosUI.*
import platform.UniformTypeIdentifiers.*
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

// Snapshot state lets resource reads in Compose update without rebuilding services or editors.
internal var appLanguage by androidx.compose.runtime.mutableStateOf("")
    private set

internal fun selectAppLanguage(tag: String, preferences: NSUserDefaults) {
    appLanguage = tag.takeIf { it in listOf("zh-Hans", "zh-Hant", "ja", "ko", "en") }.orEmpty()
    preferences.setObject(appLanguage, "appLanguage")
}

internal fun label(key: String): String {
    val language = appLanguage.ifEmpty {
        NSBundle.preferredLocalizationsFromArray(listOf("en", "zh-Hans", "zh-Hant", "ja", "ko"))
            .firstOrNull() as? String ?: "en"
    }
    val bundle = NSBundle.mainBundle.pathForResource(language, "lproj")?.let { NSBundle.bundleWithPath(it) }
        ?: NSBundle.mainBundle
    return bundle.localizedStringForKey(key, key, null)
}
internal fun now(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
internal fun uuid(): String = NSUUID().UUIDString
internal fun ByteArray.nsData(): NSData = if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
internal fun NSData.byteArray(): ByteArray {
    require(length <= 256uL * 1024uL * 1024uL)
    return ByteArray(length.toInt()).also { out -> if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) } }
}
internal fun normalizedPhoto(image: UIImage, maximumDimension: Double = 4096.0): ByteArray {
    val dimensions = image.size.useContents { width * image.scale to height * image.scale }
    require(dimensions.first > 0 && dimensions.second > 0 && dimensions.first * dimensions.second <= 40_000_000)
    val ratio = minOf(1.0, maximumDimension / maxOf(dimensions.first, dimensions.second))
    val width = dimensions.first * ratio; val height = dimensions.second * ratio
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(width, height), true, 1.0)
    try {
        UIColor.whiteColor.setFill()
        UIRectFill(CGRectMake(0.0, 0.0, width, height))
        image.drawInRect(CGRectMake(0.0, 0.0, width, height))
        return (UIImageJPEGRepresentation(checkNotNull(UIGraphicsGetImageFromCurrentImageContext()), .95)
            ?: error("Photo conversion failed")).byteArray()
    } finally { UIGraphicsEndImageContext() }
}

/** Delegates are retained until completion; every asynchronous completion returns to the main queue. */
internal class IosPlatform {
    private var owner: WeakReference<UIViewController>? = null
    var controller: UIViewController
        get() = checkNotNull(owner?.get())
        set(value) { owner = WeakReference(value) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    fun close() { delegate = null; owner = null; scope.cancel() }
    private var delegate: NSObject? = null
    fun importProject(onResult: (ByteArray) -> Unit, onError: () -> Unit) {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = true)
        val handler = object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                controller.dismissViewControllerAnimated(true, null)
                try {
                    val url = didPickDocumentsAtURLs.first() as NSURL
                    onResult(readProjectUrl(url))
                } catch (_: Exception) { onError() }
                delegate = null
            }
            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) { delegate = null }
        }
        delegate = handler; picker.delegate = handler
        controller.presentViewController(picker, true, null)
    }
    fun pickPhoto(onResult: (ByteArray) -> Unit, onError: () -> Unit) {
        val configuration = PHPickerConfiguration().apply { selectionLimit = 1; filter = PHPickerFilter.imagesFilter }
        val picker = PHPickerViewController(configuration)
        val handler = object : NSObject(), PHPickerViewControllerDelegateProtocol {
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                picker.dismissViewControllerAnimated(true, null)
                val result = didFinishPicking.firstOrNull() as? PHPickerResult
                if (result == null) { delegate = null; return }
                result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, error ->
                    scope.launch {
                        try {
                            check(error == null)
                            val bytes = withContext(Dispatchers.Default) {
                                normalizedPhoto(checkNotNull(UIImage.imageWithData(checkNotNull(data))))
                            }
                            onResult(bytes)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { onError() }
                        finally { delegate = null }
                    }
                }
            }
        }
        delegate = handler; picker.delegate = handler
        controller.presentViewController(picker, true, null)
    }
    fun share(bytes: ByteArray, extension: String) {
        require(extension in listOf("lsk", "png", "csv"))
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + "LaserSketch-${uuid()}.$extension")
        check(bytes.nsData().writeToURL(url, true))
        val sheet = UIActivityViewController(listOf(url), null)
        sheet.popoverPresentationController?.let { it.sourceView = controller.view; it.sourceRect = controller.view.bounds }
        sheet.completionWithItemsHandler = { _, _, _, _ -> NSFileManager.defaultManager.removeItemAtURL(url, null) }
        controller.presentViewController(sheet, true, null)
    }
    fun savePhoto(bytes: ByteArray, completion: (Boolean) -> Unit) {
        PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { status ->
            if (status != PHAuthorizationStatusAuthorized && status != PHAuthorizationStatusLimited) {
                dispatch_async(dispatch_get_main_queue()) { completion(false) }
            } else {
                val image = UIImage.imageWithData(bytes.nsData())
                if (image == null) dispatch_async(dispatch_get_main_queue()) { completion(false) }
                else PHPhotoLibrary.sharedPhotoLibrary().performChanges({
                    PHAssetChangeRequest.creationRequestForAssetFromImage(image)
                }) { success, _ -> dispatch_async(dispatch_get_main_queue()) { completion(success) } }
            }
        }
    }
}

internal fun iosPalette(): List<ColorOption> {
    val file = checkNotNull(NSBundle.mainBundle.pathForResource("Palette", "plist"))
    val values = checkNotNull(NSDictionary.dictionaryWithContentsOfFile(file))
    return listOf("black", "red", "blue", "green", "orange").map {
        ColorOption((values[it] as String).toLong(16).toInt(), label(it))
    }
}

/** Read only the explicitly picked URL, within scope, and reject oversized input before allocation. */
internal fun readProjectUrl(url: NSURL): ByteArray {
    val scoped = url.startAccessingSecurityScopedResource()
    try {
        val size = NSFileManager.defaultManager.attributesOfItemAtPath(checkNotNull(url.path), null)?.get(NSFileSize) as? NSNumber
        require(size != null && size.longLongValue in 1..256L * 1024 * 1024)
        return checkNotNull(NSData.dataWithContentsOfURL(url)).byteArray()
    } finally { if (scoped) url.stopAccessingSecurityScopedResource() }
}
