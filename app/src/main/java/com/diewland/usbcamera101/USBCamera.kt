package com.diewland.usbcamera101

import android.app.Activity
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.os.Handler
import android.os.Looper.getMainLooper
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.text.TextUtils
import android.util.Log
import android.view.Surface
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.jiangdg.usbcamera.UVCCameraHelper
import com.jiangdg.usbcamera.utils.FileUtils
import com.serenegiant.usb.common.AbstractUVCCameraHandler.OnCaptureListener
import com.serenegiant.usb.widget.CameraViewInterface
import com.serenegiant.usb.widget.UVCCameraTextureView

class USBCamera(private val act: Activity,
                private val mUVCCameraView: UVCCameraTextureView,
                private val successCallback: (Bitmap, List<Face>, Float) -> Unit,
                private val failCallback: ((Exception) -> Unit)?=null,
                private val width: Int = 640,
                private val height: Int = 480,
                private val maxFps: Int = 10) {

    // state
    private var isInit = false
    private var isRequest = false
    private var isPreview = false
    private var lastRenderTime = System.currentTimeMillis()

    // helper
    lateinit var mCameraHelper: UVCCameraHelper

    // converter
    private var yuvToRgbIntrinsic: ScriptIntrinsicYuvToRGB
    private var aIn: Allocation
    private var aOut: Allocation
    private var bmpOut: Bitmap

    // detection
    private var detector: FaceDetector

    init {
        // prepare image converter
        // https://stackoverflow.com/a/43551798/466693
        val count = width * height * 3 / 2 // this is 12 bit per pixel
        val rs = RenderScript.create(act)
        yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
        aIn = Allocation.createSized(rs, Element.U8(rs), count)
        bmpOut = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        aOut = Allocation.createFromBitmap(rs, bmpOut)
        yuvToRgbIntrinsic.setInput(aIn)

        // initial fact detector
        detector = FaceDetection.getClient()
    }

    fun open() { // TODO crash on first time open
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
        mCameraHelper.setDefaultPreviewSize(width, height)

        // set default frame format，default is UVCCameraHelper.Frame_FORMAT_MPEG
        // if using mpeg can not record mp4,please try yuv
        // mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_YUYV);
        // mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_MJPEG)

        mCameraHelper.initUSBMonitor(act, mUVCCameraView, mDevConnectListener)

        mCameraHelper.setOnPreviewFrameListener { nv21Yuv ->
            // convert from nv21Yuv BA to Bitmap
            aIn.copyFrom(nv21Yuv)
            yuvToRgbIntrinsic.forEach(aOut)
            aOut.copyTo(bmpOut)

            // detect face
            val image = InputImage.fromBitmap(bmpOut, 0)
            detector.process(image)
                .addOnSuccessListener {
                    // calc fps
                    val now = System.currentTimeMillis()
                    val diff = now - lastRenderTime
                    val limit = 1000f / maxFps // TODO
                    if (diff < limit) return@addOnSuccessListener
                    lastRenderTime = now
                    val fps = 1000f / diff // TODO

                    successCallback(bmpOut, it, fps)
                }
                .addOnFailureListener {
                    failCallback?.invoke(it)
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
        Log.d("USBCAM", msg)
    }

}