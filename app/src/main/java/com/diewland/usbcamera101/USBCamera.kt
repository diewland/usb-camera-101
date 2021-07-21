package com.diewland.usbcamera101

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.usb.UsbDevice
import android.os.Handler
import android.os.Looper.getMainLooper
import android.text.TextUtils
import android.util.Log
import android.view.Surface
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.jiangdg.usbcamera.UVCCameraHelper
import com.jiangdg.usbcamera.utils.FileUtils
import com.serenegiant.usb.common.AbstractUVCCameraHandler.OnCaptureListener
import com.serenegiant.usb.widget.CameraViewInterface
import com.serenegiant.usb.widget.UVCCameraTextureView

class USBCamera(act: MainActivity, camView: UVCCameraTextureView) {

    private val TAG = "USBCAM"

    private val act = act
    private val mUVCCameraView = camView
    lateinit var mCameraHelper: UVCCameraHelper

    private var isInit = false
    private var isRequest = false
    private var isPreview = false

    private var lastRenderTime = System.currentTimeMillis()
    private val p: Paint = Paint()

    init {
        // define paint
        p.style = Paint.Style.STROKE
        p.color = Color.YELLOW
        p.strokeWidth = 5f
    }

    fun open() {
        initCamHelper()
        onStart()
    }

    fun close() {
        onStop()
        onDestroy()
    }

    fun capture() {
        if (mCameraHelper == null || !mCameraHelper.isCameraOpened) {
            showShortMsg("sorry,camera open failed")
            return
        }
        val picPath =
            (UVCCameraHelper.ROOT_PATH + "USBCamera" + "/images/"
                    + System.currentTimeMillis().toString() + UVCCameraHelper.SUFFIX_JPEG)

        mCameraHelper.capturePicture(picPath,
            OnCaptureListener { path ->
                if (TextUtils.isEmpty(path)) {
                    return@OnCaptureListener
                }
                Handler(getMainLooper()).post {
                    Toast.makeText(
                        act,
                        "save path:$path",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    // ---------- LIFE CYCLE ----------

    fun onStart() {
        if (!isInit) return

        // step.2 register USB event broadcast
        if (mCameraHelper != null) {
            mCameraHelper.registerUSB()
        }
    }

    fun onStop() {
        if (!isInit) return

        // step.3 unregister USB event broadcast
        if (mCameraHelper != null) {
            mCameraHelper.unregisterUSB()
        }
    }

    fun onDestroy() {
        if (!isInit) return

        FileUtils.releaseFile()
        // step.4 release uvc camera resources
        if (mCameraHelper != null) {
            mCameraHelper.release()
        }
        resetState()
    }

    // ---------- CAMERA ----------

    private fun initCamHelper() {
        if (isInit) return

        // step.1 initialize UVCCameraHelper
        mUVCCameraView.setCallback(mCallback)
        mCameraHelper = UVCCameraHelper.getInstance()

        // set default preview size
        mCameraHelper.setDefaultPreviewSize(Config.CAMERA_WIDTH, Config.CAMERA_HEIGHT)

        // set default frame format，default is UVCCameraHelper.Frame_FORMAT_MPEG
        // if using mpeg can not record mp4,please try yuv
        // mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_YUYV);
        // mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_MJPEG)

        mCameraHelper.initUSBMonitor(act, mUVCCameraView, mDevConnectListener)

        mCameraHelper.setOnPreviewFrameListener { nv21Yuv ->
            // Log.d(TAG, "onPreviewResult: " + nv21Yuv.size)

            // convert from nv21Yuv BA to Bitmap
            // https://stackoverflow.com/a/43551798/466693
            act.aIn.copyFrom(nv21Yuv)
            act.yuvToRgbIntrinsic.forEach(act.aOut)
            act.aOut.copyTo(act.bmpOut)

            // detection
            val image = InputImage.fromBitmap(act.bmpOut, 0)
            act.detector.process(image)
                .addOnSuccessListener {
                    if (it.size > 0) {
                        val canvas = Canvas(act.bmpOut)
                        it.forEach { face ->
                            // update frame color from box size
                            p.color = when {
                                face.boundingBox.width() > 300 -> Color.GREEN
                                else -> Color.YELLOW
                            }
                            canvas.drawRect(face.boundingBox, p)
                        }
                    }
                    act.ivPreview.setImageBitmap(act.bmpOut)

                    // calc fps
                    val now = System.currentTimeMillis()
                    val diff = now - lastRenderTime
                    if (Config.MAX_FPS != null) {
                        val limit = 1000 / Config.MAX_FPS
                        if (diff < limit) return@addOnSuccessListener
                        lastRenderTime = now
                    }
                    lastRenderTime = now
                    act.tvFps.text = "fps: %.2f".format(1000f/diff)
                }
                .addOnFailureListener {
                    Log.e(TAG, it.stackTraceToString())
                }
        }

        isInit = true
    }

    private val mCallback = object: CameraViewInterface.Callback {
        override fun onSurfaceCreated(view: CameraViewInterface?, surface: Surface?) {
            // must have
            if (!isPreview && mCameraHelper.isCameraOpened) {
                mCameraHelper.startPreview(mUVCCameraView);
                isPreview = true;
            }
        }

        override fun onSurfaceChanged(
            view: CameraViewInterface?,
            surface: Surface?,
            width: Int,
            height: Int
        ) {
            // pass
        }

        override fun onSurfaceDestroy(view: CameraViewInterface?, surface: Surface?) {
            // must have
            if (isPreview && mCameraHelper.isCameraOpened) {
                mCameraHelper.stopPreview();
                isPreview = false;
            }
        }
    }

    private val mDevConnectListener = object: UVCCameraHelper.OnMyDevConnectListener {
        override fun onAttachDev(device: UsbDevice?) {
            // request open permission(must have)
            if (!isRequest) {
                isRequest = true;
                if (mCameraHelper != null) {
                    // scan logitech device (vendorId == 1133) TODO hardcode
                    val idx = mCameraHelper.usbDeviceList.indexOfFirst { it.vendorId == 1133 }
                    mCameraHelper.requestPermission(idx);
                }
            }
        }

        override fun onDettachDev(device: UsbDevice?) {
            // close camera(must have)
            if (isRequest) {
                isRequest = false;
                mCameraHelper.closeCamera();
                showShortMsg(device!!.deviceName + " is out")
            }
        }

        override fun onConnectDev(device: UsbDevice?, isConnected: Boolean) {
            /*
            if (!isConnected) {
                showShortMsg("fail to connect,please check resolution params")
                isPreview = false
            } else {
                isPreview = true
                showShortMsg("connecting")
                // initialize seekbar
                // need to wait UVCCamera initialize over
                Thread {
                    try {
                        Thread.sleep(2500)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                    Looper.prepare()
                    if (mCameraHelper != null && mCameraHelper.isCameraOpened) {
                        mSeekBrightness.setProgress(mCameraHelper.getModelValue(UVCCameraHelper.MODE_BRIGHTNESS))
                        mSeekContrast.setProgress(mCameraHelper.getModelValue(UVCCameraHelper.MODE_CONTRAST))
                    }
                    Looper.loop()
                }.start()
            }
            */
        }

        override fun onDisConnectDev(device: UsbDevice?) {
            showShortMsg("disconnecting")
        }
    }

    // ---------- TOOL ----------

    private fun resetState() {
        isInit = false
        isRequest = false
        isPreview = false
    }

    private fun showShortMsg(msg: String) {
        Log.d(TAG, msg)
    }

}