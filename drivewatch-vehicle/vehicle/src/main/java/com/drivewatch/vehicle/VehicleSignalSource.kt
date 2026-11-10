package com.drivewatch.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.drivewatch.core.Gear
import com.drivewatch.core.collect.BrakeState
import com.drivewatch.core.collect.GearState
import com.drivewatch.core.collect.SignalEvent
import com.drivewatch.core.collect.SignalSource
import com.drivewatch.core.collect.SpeedSample
import com.drivewatch.core.collect.SteeringSample

/**
 * 차량 신호를 받아 [SignalEvent]로 넘긴다.
 * 기어·브레이크·조향각은 Pleos Vehicle SDK에서, 차속은 AAOS Car API에서 받는다(Vehicle SDK 2.3.2에는 차속이 없다).
 * 받은 신호는 모두 메인 스레드에서 sink로 넘긴다. onIssue는 못 받는 신호가 있을 때 한 줄로 알려 준다.
 */
class VehicleSignalSource(
    context: Context,
    private val onIssue: (String) -> Unit = {},
) : SignalSource {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var sink: ((SignalEvent) -> Unit)? = null
    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var properties: CarPropertyManager? = null

    private fun now() = SystemClock.elapsedRealtime()
    private fun emit(make: (Long) -> SignalEvent) {
        val time = now()
        main.post { sink?.invoke(make(time)) }
    }
    private fun issue(text: String) = main.post { onIssue(text) }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = emit { GearState(it, gear.toCore()) }
        override fun onFailed(e: Exception) { issue("기어 신호를 받지 못했어요: ${e.message}") }
    }
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
            if (pressed != null) emit { BrakeState(it, pressed) }
        }
        override fun onFailed(e: Exception) { issue("브레이크 신호를 받지 못했어요: ${e.message}") }
    }
    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) {
            if (angle != null) emit { SteeringSample(it, angle.toDouble()) }
        }
        override fun onFailed(e: Exception) { issue("조향각 신호를 받지 못했어요: ${e.message}") }
    }
    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val speed = (value.value as? Float)?.toDouble() ?: return
            if (value.status == CarPropertyValue.STATUS_AVAILABLE) emit { SpeedSample(it, speed) }
        }
        override fun onErrorEvent(propertyId: Int, zone: Int) { issue("차속 신호에 오류가 있어요") }
    }

    override fun start(sink: (SignalEvent) -> Unit) {
        stop()
        this.sink = sink
        val created = Vehicle(appContext)
        vehicle = created
        try {
            created.initialize()
        } catch (e: Exception) {
            issue("차량 서비스에 연결하지 못했어요: ${e.message}")
            return
        }
        // initialize() 직후에는 차량 서비스 연결이 끝나지 않았을 수 있다.
        main.postDelayed({ if (vehicle === created) connect(created) }, CONNECT_DELAY_MS)
    }

    private fun connect(vehicle: Vehicle) {
        val gear = vehicle.getGear()
        gear.getCurrentGearSelectionState({ current -> emit { GearState(it, current.toCore()) } }, { issue("현재 기어를 읽지 못했어요: ${it.message}") })
        gear.registerGearSelection(gearListener)

        val brake = vehicle.getBrake()
        brake.getCurrentBrakePedalStatus({ pressed -> if (pressed != null) emit { BrakeState(it, pressed) } }, { issue("브레이크 상태를 읽지 못했어요: ${it.message}") })
        brake.registerBrakePedalStatus(brakeListener)

        vehicle.getSteeringWheel().registerSteeringWheelAngle(steeringListener)

        if (appContext.checkSelfPermission(SPEED_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            issue("차속 권한이 없어요. 허용해야 강한 제동을 셀 수 있어요.")
            return
        }
        try {
            val created = Car.createCar(appContext)
            car = created
            val manager = created.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            properties = manager
            manager.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
        } catch (e: Throwable) {
            issue("차속을 받지 못했어요: ${e.message}")
        }
    }

    override fun stop() {
        sink = null
        main.removeCallbacksAndMessages(null)
        vehicle?.let {
            runCatching { it.getGear().unregisterGearSelection(gearListener) }
            runCatching { it.getBrake().unregisterBrakePedalStatus(brakeListener) }
            runCatching { it.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener) }
            runCatching { it.release() }
        }
        vehicle = null
        runCatching { properties?.unregisterCallback(speedCallback) }
        properties = null
        runCatching { car?.disconnect() }
        car = null
    }

    private fun VehicleGear?.toCore(): Gear? = when (this) {
        VehicleGear.GEAR_PARK -> Gear.P
        VehicleGear.GEAR_REVERSE -> Gear.R
        VehicleGear.GEAR_NEUTRAL -> Gear.N
        null, VehicleGear.GEAR_UNKNOWN -> null
        else -> Gear.D
    }

    companion object {
        const val SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
        private const val CONNECT_DELAY_MS = 2_000L
    }
}
