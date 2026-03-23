package com.spaceexplorer

import android.util.Log
import android.view.MotionEvent
import com.google.android.filament.Camera
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CameraController(private val camera: Camera) {

    companion object {
        private const val TAG               = "CameraController"
        private const val MIN_RADIUS        = 1.8f
        private const val MAX_RADIUS        = 80.0f
        private const val MAX_PHI_DEG       = 88.0f
        private const val MIN_PHI_DEG       = -88.0f
        private const val ORBIT_SENSITIVITY = 0.25f
        private const val ZOOM_SENSITIVITY  = 0.010f
        private const val PAN_SENSITIVITY   = 0.004f
        private const val SMOOTHING_FACTOR  = 0.12f
        private const val INERTIA_DECAY     = 0.88f
    }

    private var desiredTheta  = 0f;    private var currentTheta  = 0f
    private var desiredPhi    = 25f;   private var currentPhi    = 25f
    private var desiredRadius = 5.0f;  private var currentRadius = 5.0f

    private var targetX = 0f;  private var desiredTargetX = 0f
    private var targetY = 0f;  private var desiredTargetY = 0f
    private var targetZ = 0f;  private var desiredTargetZ = 0f

    private var thetaVelocity = 0f
    private var phiVelocity   = 0f

    private var activeGesture: Gesture = Gesture.NONE
    private var prevX1 = 0f;  private var prevY1 = 0f
    private var prevX2 = 0f;  private var prevY2 = 0f
    private var ptr0Id = -1;   private var ptr1Id = -1
    private var prevSpan = 0f
    private var prevMidX = 0f; private var prevMidY = 0f
    private var prevDeltaTheta = 0f
    private var prevDeltaPhi   = 0f

    private enum class Gesture { NONE, ORBIT, ZOOM_PAN }

    fun setInitialPosition(
        thetaDeg: Float = 0f, phiDeg: Float = 25f, radius: Float = 5.0f,
        targetX: Float = 0f,  targetY: Float = 0f,  targetZ: Float = 0f
    ) {
        desiredTheta = thetaDeg;  currentTheta = thetaDeg
        desiredPhi   = phiDeg;    currentPhi   = phiDeg
        desiredRadius = radius;   currentRadius = radius
        this.targetX = targetX;   desiredTargetX = targetX
        this.targetY = targetY;   desiredTargetY = targetY
        this.targetZ = targetZ;   desiredTargetZ = targetZ
        applyToCamera()
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                ptr0Id = event.getPointerId(0)
                prevX1 = event.getX(0); prevY1 = event.getY(0)
                activeGesture = Gesture.ORBIT
                thetaVelocity = 0f; phiVelocity = 0f
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    ptr0Id = event.getPointerId(0); ptr1Id = event.getPointerId(1)
                    val idx0 = event.findPointerIndex(ptr0Id)
                    val idx1 = event.findPointerIndex(ptr1Id)
                    prevX1 = event.getX(idx0); prevY1 = event.getY(idx0)
                    prevX2 = event.getX(idx1); prevY2 = event.getY(idx1)
                    prevSpan = fingerSpan(prevX1, prevY1, prevX2, prevY2)
                    prevMidX = (prevX1 + prevX2) / 2f
                    prevMidY = (prevY1 + prevY2) / 2f
                    activeGesture = Gesture.ZOOM_PAN
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (activeGesture) {
                    Gesture.ORBIT    -> handleOrbit(event)
                    Gesture.ZOOM_PAN -> handleZoomPan(event)
                    else             -> {}
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                thetaVelocity = prevDeltaTheta * INERTIA_DECAY
                phiVelocity   = prevDeltaPhi   * INERTIA_DECAY
                activeGesture = Gesture.NONE
                ptr0Id = -1; ptr1Id = -1
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val liftedId  = event.getPointerId(event.actionIndex)
                if (liftedId == ptr0Id || liftedId == ptr1Id) {
                    val remaining = if (liftedId == ptr0Id) ptr1Id else ptr0Id
                    ptr0Id = remaining; ptr1Id = -1
                    val idx = event.findPointerIndex(remaining)
                    if (idx >= 0) { prevX1 = event.getX(idx); prevY1 = event.getY(idx) }
                    activeGesture = Gesture.ORBIT
                }
            }
        }
        return true
    }

    fun update() {
        if (activeGesture == Gesture.NONE) {
            desiredTheta  += thetaVelocity
            desiredPhi    += phiVelocity
            thetaVelocity *= INERTIA_DECAY
            phiVelocity   *= INERTIA_DECAY
            if (kotlin.math.abs(thetaVelocity) < 0.01f) thetaVelocity = 0f
            if (kotlin.math.abs(phiVelocity)   < 0.01f) phiVelocity   = 0f
        }
        desiredPhi    = desiredPhi.coerceIn(MIN_PHI_DEG, MAX_PHI_DEG)
        desiredRadius = desiredRadius.coerceIn(MIN_RADIUS, MAX_RADIUS)
        currentTheta  = lerp(currentTheta,  desiredTheta,  SMOOTHING_FACTOR)
        currentPhi    = lerp(currentPhi,    desiredPhi,    SMOOTHING_FACTOR)
        currentRadius = lerp(currentRadius, desiredRadius, SMOOTHING_FACTOR)
        targetX       = lerp(targetX,       desiredTargetX, SMOOTHING_FACTOR)
        targetY       = lerp(targetY,       desiredTargetY, SMOOTHING_FACTOR)
        targetZ       = lerp(targetZ,       desiredTargetZ, SMOOTHING_FACTOR)
        applyToCamera()
    }

    private fun handleOrbit(event: MotionEvent) {
        val idx = event.findPointerIndex(ptr0Id); if (idx < 0) return
        val x = event.getX(idx); val y = event.getY(idx)
        val dTheta = -(x - prevX1) * ORBIT_SENSITIVITY
        val dPhi   =  (y - prevY1) * ORBIT_SENSITIVITY
        desiredTheta   += dTheta; desiredPhi += dPhi
        prevDeltaTheta  = dTheta; prevDeltaPhi = dPhi
        prevX1 = x; prevY1 = y
    }

    private fun handleZoomPan(event: MotionEvent) {
        val idx0 = event.findPointerIndex(ptr0Id); val idx1 = event.findPointerIndex(ptr1Id)
        if (idx0 < 0 || idx1 < 0) return
        val x1 = event.getX(idx0); val y1 = event.getY(idx0)
        val x2 = event.getX(idx1); val y2 = event.getY(idx1)
        val span = fingerSpan(x1, y1, x2, y2)
        desiredRadius += (prevSpan - span) * ZOOM_SENSITIVITY * (currentRadius / MAX_RADIUS * 4f + 0.5f)
        prevSpan = span
        val midX = (x1 + x2) / 2f; val midY = (y1 + y2) / 2f
        val panScale = currentRadius * PAN_SENSITIVITY
        val thetaRad = Math.toRadians(currentTheta.toDouble()).toFloat()
        desiredTargetX += (-(midX - prevMidX) * (-sin(thetaRad))) * panScale
        desiredTargetZ += (-(midX - prevMidX) *  cos(thetaRad))  * panScale
        desiredTargetY +=  (midY - prevMidY)  * panScale
        prevMidX = midX; prevMidY = midY
    }

    private fun applyToCamera() {
        val tRad   = Math.toRadians(currentTheta.toDouble())
        val pRad   = Math.toRadians(currentPhi.toDouble())
        val cosPhi = cos(pRad).toFloat(); val sinPhi = sin(pRad).toFloat()
        val cosThe = cos(tRad).toFloat(); val sinThe = sin(tRad).toFloat()
        val eyeX   = targetX + currentRadius * cosPhi * sinThe
        val eyeY   = targetY + currentRadius * sinPhi
        val eyeZ   = targetZ + currentRadius * cosPhi * cosThe
        camera.lookAt(
            eyeX.toDouble(), eyeY.toDouble(), eyeZ.toDouble(),
            targetX.toDouble(), targetY.toDouble(), targetZ.toDouble(),
            0.0, 1.0, 0.0
        )
    }

    private fun fingerSpan(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    fun zoom(delta: Float) {
        desiredRadius = (desiredRadius - delta).coerceIn(MIN_RADIUS, MAX_RADIUS)
    }

    fun reset() {
        desiredTheta = 0f;  currentTheta = 0f
        desiredPhi   = 25f; currentPhi   = 25f
        desiredRadius = 5f; currentRadius = 5f
        desiredTargetX = 0f; targetX = 0f
        desiredTargetY = 0f; targetY = 0f
        desiredTargetZ = 0f; targetZ = 0f
        thetaVelocity = 0f; phiVelocity = 0f
        Log.d(TAG, "Camera reset")
    }

    val radius: Float get() = currentRadius
}
