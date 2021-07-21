package com.diewland.usbcamera101

import android.graphics.Bitmap
import android.os.Bundle
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    lateinit var usbCam: USBCamera
    lateinit var ivPreview: ImageView

    lateinit var yuvToRgbIntrinsic: ScriptIntrinsicYuvToRGB
    lateinit var aIn: Allocation
    lateinit var aOut: Allocation
    lateinit var bmpOut: Bitmap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usbCam = USBCamera(this, findViewById(R.id.camera_view))
        ivPreview = findViewById(R.id.iv_preview)

        // clone camera stream to image view
        // https://stackoverflow.com/a/43551798/466693
        val camWidth = 640
        val camHeight = 480
        val rs = RenderScript.create(this)
        yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
        aIn = Allocation.createSized(rs, Element.U8(rs), camWidth * camHeight * 3 / 2) // this is 12 bit per pixel
        bmpOut = Bitmap.createBitmap(camWidth, camHeight, Bitmap.Config.ARGB_8888)
        aOut = Allocation.createFromBitmap(rs, bmpOut)
        yuvToRgbIntrinsic.setInput(aIn)

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