package com.diewland.usbcamera101

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.face.Face
import com.serenegiant.usb.widget.UVCCameraTextureView

const val TAG = "USBCAM_MAIN"

class MainActivity : AppCompatActivity() {

    lateinit var usbCam: USBCamera
    lateinit var ivPreview: ImageView
    lateinit var tvFps: TextView
    lateinit var p: Paint

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val camView = findViewById<UVCCameraTextureView>(R.id.camera_view)
        ivPreview = findViewById(R.id.iv_preview)
        tvFps = findViewById(R.id.tv_fps)

        // define paint
        p = Paint()
        p.style = Paint.Style.STROKE
        p.color = Color.YELLOW
        p.strokeWidth = 5f

        // init usb cam
        usbCam = USBCamera(this, camView, { bmp, faces, fps ->
            detectSuccess(bmp, faces, fps)
        }, {
            Log.e(TAG, it.stackTraceToString())
        })

        // bind screen buttons
        findViewById<Button>(R.id.btn_open).setOnClickListener { usbCam.open() }
        findViewById<Button>(R.id.btn_capture).setOnClickListener { usbCam.capture() }
        findViewById<Button>(R.id.btn_close).setOnClickListener { usbCam.close() }
    }

    override fun onStart() { // TODO remove ?
        super.onStart()
        usbCam.onStart()
    }

    override fun onStop() { // TODO remove ?
        super.onStop()
        usbCam.onStop()
    }

    override fun onDestroy() { // TODO remove ?
        super.onDestroy()
        usbCam.onDestroy()
    }

    private fun detectSuccess(bmp: Bitmap, faces: List<Face>, fps: Float) {
        // show fps
        tvFps.text = "FPS: %.2f".format(fps)
        // draw face box
        if (faces.isNotEmpty()) {
            val canvas = Canvas(bmp)
            faces.forEach { face ->
                // update frame color from box size
                p.color = when {
                    face.boundingBox.width() > 300 -> Color.GREEN
                    else -> Color.YELLOW
                }
                canvas.drawRect(face.boundingBox, p)
            }
        }
        // update image view
        ivPreview.setImageBitmap(bmp)
    }

}