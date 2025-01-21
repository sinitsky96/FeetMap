package com.example.tutorial6;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class FootHeatmapView extends View {
    private final Paint paint;
    private float[] fsrValues = new float[3];
    private final float[] fsrPositionsX = {0.5f, 0.5f, 0.5f}; // X positions for heel, mid, toe
    private final float[] fsrPositionsY = {0.8f, 0.5f, 0.2f}; // Y positions for heel, mid, toe

    public FootHeatmapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setAntiAlias(true);
    }

    public void updateValues(float[] values) {
        fsrValues = values.clone();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        for (int i = 0; i < 3; i++) {
            float centerX = width * fsrPositionsX[i];
            float centerY = height * fsrPositionsY[i];
            float radius = Math.min(width, height) * 0.15f;

            // Normalize FSR value (assuming max value of 1023)
            float normalizedValue = Math.min(fsrValues[i] / 1023f, 1.0f);

            // Create gradient for heatmap effect
            RadialGradient gradient = new RadialGradient(
                    centerX, centerY, radius,
                    new int[]{getHeatmapColor(normalizedValue), Color.TRANSPARENT},
                    new float[]{0.1f, 1.0f},
                    Shader.TileMode.CLAMP
            );

            paint.setShader(gradient);
            canvas.drawCircle(centerX, centerY, radius, paint);
            paint.setShader(null);
        }
    }

    private int getHeatmapColor(float value) {
        // Blue (cold) to Red (hot) gradient
        int blue = (int) (255 * (1 - value));
        int red = (int) (255 * value);
        return Color.argb(180, red, 0, blue);
    }
}
