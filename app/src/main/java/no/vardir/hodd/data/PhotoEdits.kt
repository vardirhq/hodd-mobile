package no.vardir.hodd.data

import android.graphics.*
import androidx.exifinterface.media.ExifInterface
import no.vardir.hodd.core.PhotoCrop
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.floor

/** Bounded local decoding and immutable exports; no source file is modified. */
internal object PhotoEdits {
    fun decode(file: File, maxEdge: Int = 2560): Bitmap {
        require(file.length() in 1..20L * 1024 * 1024) { "Choose a photo smaller than 20 MB." }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 200_000_000) { "This photo could not be opened. Try another image." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
        val decoded = requireNotNull(BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })) { "This photo could not be opened." }
        try {
            val exif = ExifInterface(file)
            val matrix = Matrix().apply {
                setRotate(exif.rotationDegrees.toFloat())
                if (exif.isFlipped) postScale(-1f, 1f)
            }
            val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (upright !== decoded) decoded.recycle()
            return upright
        } catch (error: Exception) { decoded.recycle(); throw error }
    }

    fun colorMatrix(brightness: Float, contrast: Float): FloatArray {
        require(brightness.isFinite() && brightness in -1f..1f && contrast.isFinite() && contrast in 0.5f..2f)
        val offset = 128f * (1 - contrast) + brightness * 255
        return floatArrayOf(contrast, 0f, 0f, 0f, offset, 0f, contrast, 0f, 0f, offset,
            0f, 0f, contrast, 0f, offset, 0f, 0f, 0f, 1f, 0f)
    }

    fun rotate(bitmap: Bitmap, quarterTurns: Int): Bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
        Matrix().apply { setRotate(((quarterTurns % 4 + 4) % 4) * 90f) }, true)

    fun render(bitmap: Bitmap, crop: PhotoCrop, brightness: Float, contrast: Float): Bitmap {
        val left = floor(crop.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = floor(crop.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val right = ceil(crop.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = ceil(crop.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
        val result = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(colorMatrix(brightness, contrast)) }
        Canvas(result).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, Rect(left, top, right, bottom), Rect(0, 0, result.width, result.height), paint)
        }
        return result
    }

    fun export(source: File, target: File, crop: PhotoCrop, brightness: Float, contrast: Float, turns: Int) {
        require(source.canonicalPath != target.canonicalPath) { "Photo edits must use a new file." }
        val original = decode(source)
        var rotated: Bitmap? = null
        var output: Bitmap? = null
        try {
            rotated = rotate(original, turns)
            val rendered = render(rotated, crop, brightness, contrast)
            output = rendered
            require(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "Could not prepare photo storage." }
            FileOutputStream(target).use { stream ->
                require(rendered.compress(Bitmap.CompressFormat.JPEG, 92, stream)) { "Could not save the edited photo." }
                stream.fd.sync()
            }
            val verify = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.absolutePath, verify)
            check(verify.outWidth == rendered.width && verify.outHeight == rendered.height) { "The edited photo could not be verified." }
        } catch (error: Exception) { target.delete(); throw error }
        finally { output?.recycle(); if (rotated !== original) rotated?.recycle(); original.recycle() }
    }
}
