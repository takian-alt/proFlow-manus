package com.neuroflow.app.presentation.launcher.domain

import android.content.Context
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Processes app icons to apply consistent shape masking.
 * Handles both AdaptiveIconDrawable (API 26+) and legacy icons.
 */
object AdaptiveIconProcessor {

    /**
     * Process a drawable to apply the selected icon shape mask.
     *
     * @param drawable The source icon drawable
     * @param shape The icon shape to apply
     * @param sizePx The output size in pixels
     * @param context Android context for accessing resources
     * @return Bitmap with shape mask applied
     */
    fun process(
        drawable: Drawable,
        shape: IconShape,
        sizePx: Int,
        context: Context
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable) {
            // Process adaptive icon with shape mask
            processAdaptiveIcon(drawable, shape, sizePx, canvas, context)
        } else {
            // Process legacy icon - wrap with background and apply mask
            processLegacyIcon(drawable, shape, sizePx, canvas, context)
        }

        return bitmap
    }

    /**
     * Process an AdaptiveIconDrawable by applying the shape mask.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun processAdaptiveIcon(
        drawable: AdaptiveIconDrawable,
        shape: IconShape,
        sizePx: Int,
        canvas: Canvas,
        context: Context
    ) {
        val mask = getMask(shape, sizePx, context)

        // Create a temporary bitmap for the icon layers
        val iconBitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val iconCanvas = Canvas(iconBitmap)

        // Draw background layer
        drawable.background?.let { bg ->
            bg.setBounds(0, 0, sizePx, sizePx)
            bg.draw(iconCanvas)
        }

        // Draw foreground layer
        drawable.foreground?.let { fg ->
            fg.setBounds(0, 0, sizePx, sizePx)
            fg.draw(iconCanvas)
        }

        // Apply mask
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)

        canvas.drawBitmap(iconBitmap, 0f, 0f, null)
        canvas.drawPath(mask, paint)
    }

    /**
     * Process a legacy (non-adaptive) icon by wrapping it with a background
     * and applying the shape mask.
     */
    private fun processLegacyIcon(
        drawable: Drawable,
        shape: IconShape,
        sizePx: Int,
        canvas: Canvas,
        context: Context
    ) {
        val mask = getMask(shape, sizePx, context)

        // Draw white background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        bgPaint.color = Color.WHITE
        canvas.drawPath(mask, bgPaint)

        // Draw the icon centered with some padding (80% of size)
        val iconSize = (sizePx * 0.8f).toInt()
        val offset = (sizePx - iconSize) / 2

        drawable.setBounds(offset, offset, offset + iconSize, offset + iconSize)
        drawable.draw(canvas)

        // Apply mask to clip the icon
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)

        val maskBitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ALPHA_8)
        val maskCanvas = Canvas(maskBitmap)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        fillPaint.color = Color.BLACK
        maskCanvas.drawPath(mask, fillPaint)

        canvas.drawBitmap(maskBitmap, 0f, 0f, maskPaint)
    }

    /**
     * Get the Path for the specified icon shape.
     *
     * @param shape The icon shape
     * @param sizePx The size in pixels
     * @param context Android context for accessing system resources
     * @return Path defining the shape mask
     */
    fun getMask(shape: IconShape, sizePx: Int, context: Context): Path {
        val path = Path()
        val size = sizePx.toFloat()
        val center = size / 2f

        when (shape) {
            IconShape.CIRCLE -> {
                // Perfect circle
                path.addCircle(center, center, center, Path.Direction.CW)
            }

            IconShape.SQUIRCLE -> {
                // Squircle (superellipse with n=5)
                // Approximated using cubic bezier curves
                val radius = size / 2f
                val control = radius * 0.55228f // Magic number for circle approximation
                val squircleControl = radius * 0.45f // Adjusted for squircle

                path.moveTo(center, 0f)
                path.cubicTo(
                    center + squircleControl, 0f,
                    size, center - squircleControl,
                    size, center
                )
                path.cubicTo(
                    size, center + squircleControl,
                    center + squircleControl, size,
                    center, size
                )
                path.cubicTo(
                    center - squircleControl, size,
                    0f, center + squircleControl,
                    0f, center
                )
                path.cubicTo(
                    0f, center - squircleControl,
                    center - squircleControl, 0f,
                    center, 0f
                )
            }

            IconShape.ROUNDED_SQUARE -> {
                // Rounded square with 20% corner radius
                val cornerRadius = size * 0.2f
                val rect = RectF(0f, 0f, size, size)
                path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
            }

            IconShape.TEARDROP -> {
                // Teardrop shape (circle with pointed bottom)
                val radius = size / 2f
                val control = radius * 0.55228f

                // Top arc (semicircle)
                path.moveTo(center, 0f)
                path.cubicTo(
                    center + control, 0f,
                    size, center - control,
                    size, center
                )

                // Right side to point
                path.lineTo(size, center + radius * 0.3f)
                path.cubicTo(
                    size, center + radius * 0.6f,
                    center + radius * 0.3f, size,
                    center, size
                )

                // Left side from point
                path.cubicTo(
                    center - radius * 0.3f, size,
                    0f, center + radius * 0.6f,
                    0f, center + radius * 0.3f
                )
                path.lineTo(0f, center)

                // Left arc
                path.cubicTo(
                    0f, center - control,
                    center - control, 0f,
                    center, 0f
                )
            }

            IconShape.SYSTEM_DEFAULT -> {
                // Use standard adaptive icon mask shape
                // The standard Android adaptive icon mask is a squircle-like shape
                // defined by the system. We'll approximate it here.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Standard adaptive icon mask approximation (squircle)
                    // This matches the default system mask used by most Android versions
                    val radius = size / 2f
                    val control = radius * 0.45f // Squircle control point

                    path.moveTo(center, 0f)
                    path.cubicTo(
                        center + control, 0f,
                        size, center - control,
                        size, center
                    )
                    path.cubicTo(
                        size, center + control,
                        center + control, size,
                        center, size
                    )
                    path.cubicTo(
                        center - control, size,
                        0f, center + control,
                        0f, center
                    )
                    path.cubicTo(
                        0f, center - control,
                        center - control, 0f,
                        center, 0f
                    )
                } else {
                    // Pre-O fallback to rounded square
                    val cornerRadius = size * 0.2f
                    val rect = RectF(0f, 0f, size, size)
                    path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
                }
            }
        }

        return path
    }
}
