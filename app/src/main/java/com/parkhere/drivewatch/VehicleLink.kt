package com.parkhere.drivewatch

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Real vehicle signals. Gear, brake and turn signal come from the Pleos Vehicle SDK; speed comes from
 * the AAOS Car API (Vehicle SDK 2.3.2 has no speed). Every callback is delivered on the main thread.
 * On a device without Pleos services nothing arrives, and the caller falls back to the demo script.
 */
class VehicleLink(context: Context, private val listener: Listener) {
    interface Listener {
        fun onGear(drive: Boolean, park: Boolean)
        fun onTurnSignal(on: Boolean)
        fun onBrake(pressed: Boolean)
        fun onSpeed(mps: Double)
    }

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var props: CarPropertyManager? = null

    private val gear = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = post { report(gear) }
        override fun onFailed(e: Exception) {}
    }
    private val brake = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) { if (pressed != null) post { listener.onBrake(pressed) } }
        override fun onFailed(e: Exception) {}
    }
    private val turn = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) { post { listener.onTurnSignal(value != null && value != TurnSignalValue.OFF) } }
        override fun onFailed(e: Exception) {}
    }
    private val speed = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val mps = (value.value as? Float)?.toDouble() ?: return
            if (value.status == CarPropertyValue.STATUS_AVAILABLE) post { listener.onSpeed(mps) }
        }
        override fun onErrorEvent(propertyId: Int, zone: Int) {}
    }

    private fun post(block: () -> Unit) { main.post(block) }
    private fun report(g: VehicleGear?) {
        if (g == null || g == VehicleGear.GEAR_UNKNOWN) return
        listener.onGear(drive = g == VehicleGear.GEAR_DRIVE, park = g == VehicleGear.GEAR_PARK)
    }

    fun start() {
        val v = runCatching { Vehicle(app).also { it.initialize() } }.getOrNull() ?: return
        vehicle = v
        // The vehicle service may not be bound right after initialize(); 광채 found 2 s is enough.
        main.postDelayed({
            if (vehicle !== v) return@postDelayed
            runCatching {
                v.getGear().getCurrentGearSelectionState({ g -> post { report(g) } }, {})
                v.getGear().registerGearSelection(gear)
                v.getBrake().registerBrakePedalStatus(brake)
                v.getTurnSignal().registerTurnSignalState(turn)
            }
            if (app.checkSelfPermission(SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED) runCatching {
                val c = Car.createCar(app).also { car = it }
                val m = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                props = m
                m.registerCallback(speed, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
            }
        }, 2_000)
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        vehicle?.let { v ->
            runCatching { v.getGear().unregisterGearSelection(gear) }
            runCatching { v.getBrake().unregisterBrakePedalStatus(brake) }
            runCatching { v.getTurnSignal().unregisterTurnSignalState(turn) }
            runCatching { v.release() }
        }
        vehicle = null
        runCatching { props?.unregisterCallback(speed) }
        props = null
        runCatching { car?.disconnect() }
        car = null
    }

    companion object { const val SPEED_PERMISSION = "android.car.permission.CAR_SPEED" }
}
