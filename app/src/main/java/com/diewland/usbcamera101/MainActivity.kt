package com.diewland.usbcamera101

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    lateinit var usbCam: USBCamera

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usbCam = USBCamera(this, findViewById(R.id.camera_view))

        // bind screen buttons
        findViewById<Button>(R.id.btn_open).setOnClickListener { usbCam.open() }
        findViewById<Button>(R.id.btn_capture).setOnClickListener { usbCam.capture() }
        findViewById<Button>(R.id.btn_close).setOnClickListener { usbCam.close() }
    }

    override fun onStart() {
        super.onStart()
        usbCam.onStart()
    }

    override fun onStop() {
        super.onStop()
        usbCam.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        usbCam.onDestroy()
    }

}