package com.example.tutorial6;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;

public class FSRCircleView extends View {
    private Paint paint;
    private float[] values = new float[3]; // Three FSR values
    private static final float MAX_VALUE = 1000;
    private Drawable backgroundImage;
    // Position circles vertically at 30%, 50%, and 70% of height
    private float[] circlePositionsX = {0.3f, 0.5f, 0.5f}; // All circles centered horizontally
    private float[] circlePositionsY = {0.3f, 0.5f, 0.9f}; // Vertical positions as percentage of height

    public FSRCircleView(Context context) {
        super(context);
        init();
    }

    public FSRCircleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        // Load the background image
        backgroundImage = getResources().getDrawable(R.drawable.pic);
    }

    public void setValue(int index, float newValue) {
        if (index >= 0 && index < 3) {
            values[index] = Math.min(Math.max(newValue, 0), MAX_VALUE);
            invalidate(); // Trigger redraw
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw background image
        if (backgroundImage != null) {
            backgroundImage.setBounds(0, 0, getWidth(), getHeight());
            backgroundImage.draw(canvas);
        }

        // Calculate the radius (12% of the smallest dimension for slightly smaller circles)
        float radius = Math.min(getWidth(), getHeight()) * 0.12f;

        // Draw three circles
        for (int i = 0; i < 3; i++) {
            // Calculate center coordinates for each circle
            float centerX = getWidth() * circlePositionsX[i];
            float centerY = getHeight() * circlePositionsY[i];

            // Calculate color based on value
            float intensity = values[i] / MAX_VALUE;
            int red = (int) (255 * intensity);
            int green = (int) (255 * (1 - intensity));

            // Draw filled circle
            paint.setStyle(Paint.Style.FILL);
            paint.setARGB(180, red, green, 0); // Semi-transparent
            canvas.drawCircle(centerX, centerY, radius, paint);

            // Draw border
            paint.setStyle(Paint.Style.STROKE);
            paint.setARGB(255, 100, 100, 100);
            paint.setStrokeWidth(4);
            canvas.drawCircle(centerX, centerY, radius, paint);
        }
    }
} 