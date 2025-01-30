package com.example.feetmap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class HeatmapView extends View {
    private final Paint heatmapPaint;
    private final Paint textPaint;
    private final Paint imagePaint;
    private final Paint maskPaint;
    private Bitmap footImage;
    private Bitmap maskBitmap;
    private Canvas maskCanvas;
    private float[] fsrValues = new float[3]; // [heel, mid, toe]
    private RectF imageRect;
    private Matrix scaleMatrix;

    private final Path heelSection;
    private final Path midSection;
    private final Path toeSection;

    public HeatmapView(Context context, AttributeSet attrs) {
        super(context, attrs);

        imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        heatmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        heatmapPaint.setStyle(Paint.Style.FILL);

        maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(40f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        heelSection = new Path();
        midSection = new Path();
        toeSection = new Path();
        imageRect = new RectF();
        scaleMatrix = new Matrix();

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setFootImage(int resourceId) {
        footImage = BitmapFactory.decodeResource(getResources(), resourceId);
        requestLayout();
        invalidate();
    }

    public void updateValues(float[] values) {
        fsrValues = values.clone();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (footImage == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        float imageRatio = (float) footImage.getWidth() / footImage.getHeight();
        float viewRatio = (float) width / height;

        int finalWidth;
        int finalHeight;

        if (imageRatio > viewRatio) {
            finalWidth = width;
            finalHeight = (int) (width / imageRatio);
        } else {
            finalHeight = height;
            finalWidth = (int) (height * imageRatio);
        }

        setMeasuredDimension(finalWidth, finalHeight);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (footImage != null) {
            updateImageRect(w, h);
            createFootSections();

            // Create mask bitmap at view size
            maskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            maskCanvas = new Canvas(maskBitmap);
        }
    }

    private void updateImageRect(int viewWidth, int viewHeight) {
        float imageRatio = (float) footImage.getWidth() / footImage.getHeight();
        float viewRatio = (float) viewWidth / viewHeight;

        float left, top, right, bottom;

        if (imageRatio > viewRatio) {
            left = 0;
            right = viewWidth;
            float height = viewWidth / imageRatio;
            top = (viewHeight - height) / 2f;
            bottom = top + height;
        } else {
            top = 0;
            bottom = viewHeight;
            float width = viewHeight * imageRatio;
            left = (viewWidth - width) / 2f;
            right = left + width;
        }

        imageRect.set(left, top, right, bottom);

        scaleMatrix.reset();
        scaleMatrix.setRectToRect(
                new RectF(0, 0, footImage.getWidth(), footImage.getHeight()),
                imageRect,
                Matrix.ScaleToFit.CENTER
        );
    }

    private void createFootSections() {
        float heelY = imageRect.top + (imageRect.height() * 0.7f);
        float midY = imageRect.top + (imageRect.height() * 0.35f);

        heelSection.reset();
        heelSection.addRect(
                imageRect.left,
                heelY,
                imageRect.right,
                imageRect.bottom,
                Path.Direction.CW
        );

        midSection.reset();
        midSection.addRect(
                imageRect.left,
                midY,
                imageRect.right,
                heelY,
                Path.Direction.CW
        );

        toeSection.reset();
        toeSection.addRect(
                imageRect.left,
                imageRect.top,
                imageRect.right,
                midY,
                Path.Direction.CW
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (footImage == null || maskBitmap == null) return;

        // Draw the foot image
        canvas.drawBitmap(footImage, scaleMatrix, imagePaint);

        // Clear the mask bitmap
        maskBitmap.eraseColor(Color.TRANSPARENT);

        // Draw the heat map sections into the mask bitmap
        int sc = maskCanvas.saveLayer(0, 0, getWidth(), getHeight(), null);

        // Draw colored sections
        drawSection(maskCanvas, heelSection, fsrValues[0], "Heel", false);
        drawSection(maskCanvas, midSection, fsrValues[1], "Mid", false);
        drawSection(maskCanvas, toeSection, fsrValues[2], "Toe", false);

        // Apply the foot image as a mask
        maskCanvas.drawBitmap(footImage, scaleMatrix, maskPaint);
        maskCanvas.restoreToCount(sc);

        // Draw the masked heat map onto the main canvas
        canvas.drawBitmap(maskBitmap, 0, 0, imagePaint);

        // Draw the percentages
        drawSection(canvas, heelSection, fsrValues[0], "Heel", true);
        drawSection(canvas, midSection, fsrValues[1], "Mid", true);
        drawSection(canvas, toeSection, fsrValues[2], "Toe", true);
    }

    private void drawSection(Canvas canvas, Path sectionPath, float value, String label, boolean textOnly) {
        if (!textOnly) {
            heatmapPaint.setColor(getHeatmapColor(value));
            canvas.drawPath(sectionPath, heatmapPaint);
        } else {
            RectF bounds = new RectF();
            sectionPath.computeBounds(bounds, true);
            float textX = bounds.centerX();
            float textY = bounds.centerY() + textPaint.getTextSize() / 3;

            String percentage = String.format("%.1f%%", value * 100);
            canvas.drawText(percentage, textX, textY, textPaint);
        }
    }

    private int getHeatmapColor(float value) {
        int alpha = (int) (255 * value * 0.7f); // Max 70% opacity
        return Color.argb(alpha, 255, 0, 0); // Red with varying transparency
    }
}