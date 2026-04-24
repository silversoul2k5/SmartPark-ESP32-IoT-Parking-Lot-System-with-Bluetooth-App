package de.kai_morich.simple_bluetooth_terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Locale;
import java.util.Random;

public class ParkingMapView extends View {

    private static final int SLOT_COUNT = 4;
    private static final long FRAME_DELAY_MS = 33L;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roadLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint routeShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint routePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint routeDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint slotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint slotStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint carPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint carWindowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pinHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ParkingState.SlotState[] slotStates = new ParkingState.SlotState[SLOT_COUNT];

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Runnable animator = new Runnable() {
        @Override
        public void run() {
            animateCar();
            invalidate();
            handler.postDelayed(this, FRAME_DELAY_MS);
        }
    };

    private boolean expanded;
    private boolean animating;
    private float carProgress;
    private float carSpeed = 0.006f;
    private int targetSlot = 1;
    private int preferredSlot = 1; // Priority slot preference
    private int assignedSlot = 1; // Actual assigned slot based on availability

    public ParkingMapView(Context context) {
        super(context);
        init();
    }

    public ParkingMapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ParkingMapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    void setExpanded(boolean expanded) {
        this.expanded = expanded;
        invalidate();
    }

    void setSlotStates(List<ParkingState.SlotState> states) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (states != null && i < states.size()) {
                slotStates[i] = states.get(i);
            } else {
                slotStates[i] = ParkingState.SlotState.UNKNOWN;
            }
        }
        // Update assigned slot based on availability
        updateAssignedSlot();
        invalidate();
    }

    /**
     * Set the preferred slot priority (1-4)
     * Navigation will use this slot or fall back to the next available one
     */
    void setPreferredSlot(int slot) {
        if (slot >= 1 && slot <= SLOT_COUNT) {
            this.preferredSlot = slot;
            updateAssignedSlot();
            carProgress = 0f; // Reset animation when slot changes
            invalidate();
        }
    }

    /**
     * Determine the assigned slot based on priority and availability
     * Priority order: 1 -> 2 -> 3 -> 4
     * If preferred slot is occupied, try the next in sequence
     */
    private void updateAssignedSlot() {
        // Try slots in priority order starting from preferred slot
        for (int i = 0; i < SLOT_COUNT; i++) {
            int slotToCheck = ((preferredSlot - 1 + i) % SLOT_COUNT);
            ParkingState.SlotState state = slotStates[slotToCheck];
            
            // Assign to this slot if it's free or unknown (assume free)
            if (state == ParkingState.SlotState.FREE || state == ParkingState.SlotState.UNKNOWN) {
                assignedSlot = slotToCheck + 1;
                targetSlot = assignedSlot;
                return;
            }
        }
        
        // Fallback: if all slots occupied, show slot 1
        assignedSlot = 1;
        targetSlot = 1;
    }

    private void init() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            slotStates[i] = ParkingState.SlotState.UNKNOWN;
        }

        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        backgroundPaint.setColor(Color.rgb(18, 20, 24));

        roadPaint.setColor(Color.rgb(34, 36, 43));
        roadPaint.setStyle(Paint.Style.STROKE);
        roadPaint.setStrokeCap(Paint.Cap.ROUND);
        roadPaint.setStrokeWidth(dp(22));

        roadLinePaint.setColor(Color.rgb(237, 192, 57));
        roadLinePaint.setStyle(Paint.Style.STROKE);
        roadLinePaint.setStrokeWidth(dp(1.5f));

        routeShadowPaint.setColor(Color.argb(85, 125, 117, 255));
        routeShadowPaint.setStyle(Paint.Style.STROKE);
        routeShadowPaint.setStrokeCap(Paint.Cap.ROUND);
        routeShadowPaint.setStrokeJoin(Paint.Join.ROUND);
        routeShadowPaint.setStrokeWidth(dp(15));
        routeShadowPaint.setPathEffect(new CornerPathEffect(dp(18)));

        routePaint.setColor(Color.rgb(132, 124, 255));
        routePaint.setStyle(Paint.Style.STROKE);
        routePaint.setStrokeCap(Paint.Cap.ROUND);
        routePaint.setStrokeJoin(Paint.Join.ROUND);
        routePaint.setStrokeWidth(dp(7));
        routePaint.setPathEffect(new CornerPathEffect(dp(18)));

        routeDotPaint.setColor(Color.rgb(247, 231, 51));
        routeDotPaint.setStyle(Paint.Style.FILL);

        slotPaint.setStyle(Paint.Style.FILL);
        slotStrokePaint.setColor(ContextCompat.getColor(getContext(), R.color.cardStroke));
        slotStrokePaint.setStyle(Paint.Style.STROKE);
        slotStrokePaint.setStrokeWidth(dp(1.5f));

        textPaint.setColor(ContextCompat.getColor(getContext(), R.color.textPrimary));
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(sp(expanded ? 17 : 13));

        smallTextPaint.setColor(ContextCompat.getColor(getContext(), R.color.textSecondary));
        smallTextPaint.setTextSize(sp(expanded ? 13 : 10));

        carPaint.setColor(Color.rgb(247, 196, 39));
        carPaint.setStyle(Paint.Style.FILL);

        carWindowPaint.setColor(Color.rgb(40, 38, 35));
        carWindowPaint.setStyle(Paint.Style.FILL);

        pinPaint.setColor(Color.rgb(132, 124, 255));
        pinPaint.setStyle(Paint.Style.FILL);

        pinHaloPaint.setColor(Color.argb(88, 132, 124, 255));
        pinHaloPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimationLoop();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimationLoop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        drawMapBase(canvas, width, height);

        Path route = buildRoute(assignedSlot, width, height);
        canvas.drawPath(route, routeShadowPaint);
        canvas.drawPath(route, routePaint);

        drawStartAndTarget(canvas, width, height);
        drawSlots(canvas, width, height);
        drawMovingCar(canvas, route);
        drawFloatingCars(canvas, width, height);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!expanded || event.getAction() != MotionEvent.ACTION_DOWN) {
            return false;
        }

        RectF[] slots = getSlotRects(getWidth(), getHeight());
        for (int i = 0; i < slots.length; i++) {
            if (slots[i].contains(event.getX(), event.getY())) {
                setPreferredSlot(i + 1);
                return true;
            }
        }
        return true;
    }

    private void drawMapBase(Canvas canvas, float width, float height) {
        RectF bounds = new RectF(0, 0, width, height);
        canvas.drawRoundRect(bounds, dp(16), dp(16), backgroundPaint);

        drawRoad(canvas, width * 0.08f, height * 0.18f, width * 0.94f, height * 0.18f);
        drawRoad(canvas, width * 0.10f, height * 0.50f, width * 0.90f, height * 0.50f);
        drawRoad(canvas, width * 0.15f, height * 0.82f, width * 0.86f, height * 0.82f);
        drawRoad(canvas, width * 0.18f, height * 0.08f, width * 0.18f, height * 0.92f);
        drawRoad(canvas, width * 0.50f, height * 0.05f, width * 0.50f, height * 0.94f);
        drawRoad(canvas, width * 0.80f, height * 0.10f, width * 0.68f, height * 0.92f);
        drawRoad(canvas, width * 0.08f, height * 0.92f, width * 0.92f, height * 0.08f);

        textPaint.setTextSize(sp(expanded ? 15 : 10));
        textPaint.setFakeBoldText(false);
        textPaint.setColor(Color.argb(150, 246, 244, 236));
        canvas.save();
        canvas.rotate(-22, width * 0.36f, height * 0.35f);
        canvas.drawText("Main Lane", width * 0.22f, height * 0.37f, textPaint);
        canvas.restore();
        canvas.save();
        canvas.rotate(88, width * 0.72f, height * 0.42f);
        canvas.drawText("Bay Road", width * 0.55f, height * 0.43f, textPaint);
        canvas.restore();
        textPaint.setFakeBoldText(true);
        textPaint.setColor(ContextCompat.getColor(getContext(), R.color.textPrimary));
    }

    private void drawRoad(Canvas canvas, float sx, float sy, float ex, float ey) {
        canvas.drawLine(sx, sy, ex, ey, roadPaint);
        canvas.drawLine(sx, sy, ex, ey, roadLinePaint);
    }

    private Path buildRoute(int slotNumber, float width, float height) {
        RectF target = getSlotRects(width, height)[slotNumber - 1];
        float targetX = target.centerX();
        float targetY = target.centerY();

        Path path = new Path();
        path.moveTo(width * 0.50f, height * 0.92f);
        path.cubicTo(width * 0.50f, height * 0.76f, width * 0.36f, height * 0.72f, width * 0.36f, height * 0.58f);
        path.cubicTo(width * 0.36f, height * 0.44f, width * 0.58f, height * 0.46f, width * 0.58f, height * 0.34f);
        path.cubicTo(width * 0.58f, height * 0.24f, targetX, height * 0.24f, targetX, targetY);
        return path;
    }

    private void drawStartAndTarget(Canvas canvas, float width, float height) {
        float startX = width * 0.50f;
        float startY = height * 0.92f;
        RectF target = getSlotRects(width, height)[assignedSlot - 1];
        float targetX = target.centerX();
        float targetY = target.centerY();

        canvas.drawCircle(startX, startY, dp(18), pinHaloPaint);
        canvas.drawCircle(startX, startY, dp(8), pinPaint);
        canvas.drawCircle(targetX, targetY, dp(20), pinHaloPaint);
        canvas.drawCircle(targetX, targetY, dp(9), pinPaint);

        smallTextPaint.setTextSize(sp(expanded ? 13 : 10));
        canvas.drawText("Gate", startX + dp(14), startY + dp(4), smallTextPaint);
        
        // Show priority preference and actual assigned slot
        String routeText = "Route to Slot " + assignedSlot;
        if (preferredSlot != assignedSlot) {
            routeText += " (Preferred: " + preferredSlot + ")";
        }
        canvas.drawText(routeText, dp(14), dp(24), smallTextPaint);
    }

    private void drawSlots(Canvas canvas, float width, float height) {
        RectF[] slots = getSlotRects(width, height);
        for (int i = 0; i < slots.length; i++) {
            ParkingState.SlotState state = slotStates[i];
            slotPaint.setColor(getSlotColor(state));
            canvas.drawRoundRect(slots[i], dp(10), dp(10), slotPaint);
            
            // Draw special border for assigned slot
            if (i + 1 == assignedSlot) {
                Paint assignedBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                assignedBorderPaint.setColor(Color.rgb(132, 124, 255)); // Purple border
                assignedBorderPaint.setStyle(Paint.Style.STROKE);
                assignedBorderPaint.setStrokeWidth(dp(3));
                canvas.drawRoundRect(slots[i], dp(10), dp(10), assignedBorderPaint);
            } else {
                canvas.drawRoundRect(slots[i], dp(10), dp(10), slotStrokePaint);
            }

            textPaint.setTextSize(sp(expanded ? 16 : 12));
            textPaint.setFakeBoldText(true);
            canvas.drawText("Slot " + (i + 1), slots[i].left + dp(9), slots[i].top + dp(expanded ? 24 : 20), textPaint);

            smallTextPaint.setTextSize(sp(expanded ? 12 : 9));
            String status = i == 0 ? state.getLabel() : "Future";
            if (i + 1 == preferredSlot && i + 1 != assignedSlot) {
                status = "Priority"; // Show priority if not assigned
            }
            canvas.drawText(status, slots[i].left + dp(9), slots[i].bottom - dp(10), smallTextPaint);
        }
    }

    private void drawMovingCar(Canvas canvas, Path route) {
        PathMeasure measure = new PathMeasure(route, false);
        float[] position = new float[2];
        float[] tangent = new float[2];
        measure.getPosTan(measure.getLength() * carProgress, position, tangent);

        float angle = (float) Math.toDegrees(Math.atan2(tangent[1], tangent[0]));
        float jitter = (float) Math.sin(System.currentTimeMillis() / 290.0) * dp(2);
        position[0] += jitter;

        canvas.save();
        canvas.translate(position[0], position[1]);
        canvas.rotate(angle);
        RectF body = new RectF(-dp(15), -dp(8), dp(15), dp(8));
        RectF window = new RectF(-dp(5), -dp(6), dp(6), dp(6));
        canvas.drawRoundRect(body, dp(5), dp(5), carPaint);
        canvas.drawRoundRect(window, dp(3), dp(3), carWindowPaint);
        canvas.restore();
    }

    private void drawFloatingCars(Canvas canvas, float width, float height) {
        drawSmallCar(canvas, width * 0.18f, height * 0.28f, -18);
        drawSmallCar(canvas, width * 0.78f, height * 0.72f, 12);
        if (expanded) {
            drawSmallCar(canvas, width * 0.30f, height * 0.80f, 3);
            drawSmallCar(canvas, width * 0.72f, height * 0.18f, 90);
        }
    }

    private void drawSmallCar(Canvas canvas, float x, float y, float angle) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(angle);
        RectF body = new RectF(-dp(10), -dp(5), dp(10), dp(5));
        canvas.drawRoundRect(body, dp(4), dp(4), carPaint);
        canvas.restore();
    }

    private RectF[] getSlotRects(float width, float height) {
        float slotWidth = expanded ? width * 0.24f : width * 0.26f;
        float slotHeight = expanded ? height * 0.12f : height * 0.14f;
        return new RectF[]{
                new RectF(width * 0.08f, height * 0.10f, width * 0.08f + slotWidth, height * 0.10f + slotHeight),
                new RectF(width * 0.66f, height * 0.10f, width * 0.66f + slotWidth, height * 0.10f + slotHeight),
                new RectF(width * 0.08f, height * 0.58f, width * 0.08f + slotWidth, height * 0.58f + slotHeight),
                new RectF(width * 0.66f, height * 0.58f, width * 0.66f + slotWidth, height * 0.58f + slotHeight)
        };
    }

    private int getSlotColor(ParkingState.SlotState state) {
        switch (state) {
            case FREE:
                return ContextCompat.getColor(getContext(), R.color.slotFree);
            case OCCUPIED:
                return ContextCompat.getColor(getContext(), R.color.slotOccupied);
            default:
                return ContextCompat.getColor(getContext(), R.color.slotUnknown);
        }
    }

    private void animateCar() {
        carProgress += carSpeed;
        if (carProgress >= 1f) {
            carProgress = 1f; // Keep at destination
            // Animation can optionally loop - current behavior keeps car at destination
            // Uncomment below to loop animation:
            // carProgress = 0f;
            // updateAssignedSlot(); // Recalculate based on current slot states
            // carSpeed = 0.0045f + random.nextFloat() * 0.0045f;
        }
    }

    private void startAnimationLoop() {
        if (!animating) {
            animating = true;
            handler.post(animator);
        }
    }

    private void stopAnimationLoop() {
        animating = false;
        handler.removeCallbacks(animator);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
