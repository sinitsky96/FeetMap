package com.example.tutorial6;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class RunTimelineView extends View {
    private static final int TIMELINE_HEIGHT = 60;
    private static final int MARKER_HEIGHT = 40;
    private final Paint timelinePaint = new Paint();
    private final Paint markerPaint = new Paint();
    private final Paint selectedPaint = new Paint();
    private float timelineWidth;
    private long totalDuration;
    private List<TimelineSegment> segments = new ArrayList<>();
    private float selectedPosition = -1;
    private OnTimeSelectListener listener;

    public static class TimelineSegment {
        public final long startTime;
        public final long endTime;
        public final FSRType type;
        public final float intensity;

        public TimelineSegment(long startTime, long endTime, FSRType type, float intensity) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.type = type;
            this.intensity = intensity;
        }
    }

    public enum FSRType {
        HEEL(Color.RED),
        MID(Color.GREEN),
        TOE(Color.BLUE);

        public final int color;
        FSRType(int color) {
            this.color = color;
        }
    }

    public RunTimelineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        timelinePaint.setColor(Color.GRAY);
        timelinePaint.setStyle(Paint.Style.STROKE);
        timelinePaint.setStrokeWidth(2f);

        markerPaint.setStyle(Paint.Style.FILL);

        selectedPaint.setColor(Color.WHITE);
        selectedPaint.setStyle(Paint.Style.STROKE);
        selectedPaint.setStrokeWidth(4f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        timelineWidth = w - getPaddingLeft() - getPaddingRight();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float baseY = getHeight() / 2f;

        // Draw base timeline
        canvas.drawLine(getPaddingLeft(), baseY,
                getWidth() - getPaddingRight(), baseY,
                timelinePaint);

        // Draw segments
        for (TimelineSegment segment : segments) {
            float startX = timeToX(segment.startTime);
            float endX = timeToX(segment.endTime);
            markerPaint.setColor(segment.type.color);
            markerPaint.setAlpha((int)(255 * segment.intensity));

            canvas.drawRect(startX,
                    baseY - MARKER_HEIGHT/2f,
                    endX,
                    baseY + MARKER_HEIGHT/2f,
                    markerPaint);
        }

        // Draw selection marker
        if (selectedPosition >= 0) {
            canvas.drawLine(selectedPosition, baseY - MARKER_HEIGHT,
                    selectedPosition, baseY + MARKER_HEIGHT,
                    selectedPaint);
        }
    }

    private float timeToX(long time) {
        return getPaddingLeft() + (time / (float) totalDuration) * timelineWidth;
    }

    private long xToTime(float x) {
        return (long) (((x - getPaddingLeft()) / timelineWidth) * totalDuration);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_MOVE) {
            selectedPosition = Math.min(Math.max(event.getX(), getPaddingLeft()),
                    getWidth() - getPaddingRight());
            if (listener != null) {
                listener.onTimeSelected(xToTime(selectedPosition));
            }
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }

    public void setData(List<RunningDataPoint> data) {
        segments.clear();
        if (data.isEmpty()) return;

        totalDuration = data.get(data.size() - 1).timestamp;

        // Process data to create segments
        FSRType currentType = null;
        long segmentStart = 0;
        float maxIntensity = 0;

        for (int i = 0; i < data.size(); i++) {
            RunningDataPoint point = data.get(i);
            FSRType dominantType = getDominantFSR(point);
            float intensity = getMaxFSRValue(point);

            if (dominantType != currentType && intensity > 0.3) {
                if (currentType != null) {
                    segments.add(new TimelineSegment(segmentStart,
                            point.timestamp,
                            currentType,
                            maxIntensity));
                }
                currentType = dominantType;
                segmentStart = point.timestamp;
                maxIntensity = intensity;
            } else {
                maxIntensity = Math.max(maxIntensity, intensity);
            }
        }

        invalidate();
    }

    private FSRType getDominantFSR(RunningDataPoint point) {
        if (point.fsr1 > point.fsr2 && point.fsr1 > point.fsr3) return FSRType.HEEL;
        if (point.fsr2 > point.fsr1 && point.fsr2 > point.fsr3) return FSRType.MID;
        return FSRType.TOE;
    }

    private float getMaxFSRValue(RunningDataPoint point) {
        return Math.max(Math.max(point.fsr1, point.fsr2), point.fsr3);
    }

    public interface OnTimeSelectListener {
        void onTimeSelected(long timestamp);
    }

    public void setOnTimeSelectListener(OnTimeSelectListener listener) {
        this.listener = listener;
    }
}

//public class RunTimelineView extends View {
//    private Paint linePaint;
//    private Paint markerPaint;
//    private Paint selectedPaint;
//    private List<TimelineMarker> markers = new ArrayList<>();
//    private long duration;
//    private long selectedTimestamp = -1;
//    private OnMarkerSelectedListener listener;
//    private GestureDetector gestureDetector;
//
//    public static class TimelineMarker {
//        public final long timestamp;
//        public final double energyWaste;
//
//        public TimelineMarker(long timestamp, double energyWaste) {
//            this.timestamp = timestamp;
//            this.energyWaste = energyWaste;
//        }
//    }
//
//    public interface OnMarkerSelectedListener {
//        void onMarkerSelected(long timestamp);
//    }
//
//    public RunTimelineView(Context context, AttributeSet attrs) {
//        super(context, attrs);
//        init();
//    }
//
//    private void init() {
//        linePaint = new Paint();
//        linePaint.setColor(Color.GRAY);
//        linePaint.setStrokeWidth(2f);
//
//        markerPaint = new Paint();
//        markerPaint.setColor(Color.RED);
//        markerPaint.setStyle(Paint.Style.FILL);
//
//        selectedPaint = new Paint();
//        selectedPaint.setColor(Color.YELLOW);
//        selectedPaint.setStyle(Paint.Style.STROKE);
//        selectedPaint.setStrokeWidth(3f);
//
//        gestureDetector = new GestureDetector(getContext(),
//                new GestureDetector.SimpleOnGestureListener() {
//                    @Override
//                    public boolean onSingleTapUp(MotionEvent e) {
//                        handleTap(e.getX());
//                        return true;
//                    }
//                });
//    }
//
//    public void setData(List<TimelineMarker> markers, long duration) {
//        this.markers = markers;
//        this.duration = duration;
//        invalidate();
//    }
//
//    public void setOnMarkerSelectedListener(OnMarkerSelectedListener listener) {
//        this.listener = listener;
//    }
//
//    @Override
//    protected void onDraw(Canvas canvas) {
//        super.onDraw(canvas);
//
//        // Draw timeline
//        float y = getHeight() / 2f;
//        canvas.drawLine(0, y, getWidth(), y, linePaint);
//
//        // Draw markers
//        for (TimelineMarker marker : markers) {
//            float x = (float) marker.timestamp / duration * getWidth();
//            float radius = 5f + (float) (marker.energyWaste * 5f); // Size based on energy waste
//            canvas.drawCircle(x, y, radius, markerPaint);
//
//            if (marker.timestamp == selectedTimestamp) {
//                canvas.drawCircle(x, y, radius + 2f, selectedPaint);
//            }
//        }
//    }
//
//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
//        return gestureDetector.onTouchEvent(event);
//    }
//
//    private void handleTap(float x) {
//        float closestDistance = Float.MAX_VALUE;
//        long closestTimestamp = -1;
//
//        for (TimelineMarker marker : markers) {
//            float markerX = (float) marker.timestamp / duration * getWidth();
//            float distance = Math.abs(markerX - x);
//
//            if (distance < closestDistance && distance < 30) { // 30px touch threshold
//                closestDistance = distance;
//                closestTimestamp = marker.timestamp;
//            }
//        }
//
//        if (closestTimestamp != -1) {
//            selectedTimestamp = closestTimestamp;
//            if (listener != null) {
//                listener.onMarkerSelected(closestTimestamp);
//            }
//            invalidate();
//        }
//    }
//}