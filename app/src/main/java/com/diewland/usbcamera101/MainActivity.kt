package com.diewland.usbcamera101

import android.graphics.Bitmap
import android.os.Bundle
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector

class MainActivity : AppCompatActivity() {

    lateinit var usbCam: USBCamera
    lateinit var ivPreview: ImageView
    lateinit var tvFps: TextView

    lateinit var yuvToRgbIntrinsic: ScriptIntrinsicYuvToRGB
    lateinit var aIn: Allocation
    lateinit var aOut: Allocation
    lateinit var bmpOut: Bitmap

    lateinit var detector: FaceDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usbCam = USBCamera(this, findViewById(R.id.camera_view))
        ivPreview = findViewById(R.id.iv_preview)
        tvFps = findViewById(R.id.tv_fps)

        // clone camera stream to image view
        // https://stackoverflow.com/a/43551798/466693
        val count = Config.CAMERA_WIDTH * Config.CAMERA_HEIGHT * 3 / 2 // this is 12 bit per pixel
        val rs = RenderScript.create(this)
        yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
        aIn = Allocation.createSized(rs, Element.U8(rs), count)
        bmpOut = Bitmap.createBitmap(Config.CAMERA_WIDTH, Config.CAMERA_HEIGHT, Bitmap.Config.ARGB_8888)
        aOut = Allocation.createFromBitmap(rs, bmpOut)
        yuvToRgbIntrinsic.setInput(aIn)

        // fact detection
        detector = FaceDetection.getClient()

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