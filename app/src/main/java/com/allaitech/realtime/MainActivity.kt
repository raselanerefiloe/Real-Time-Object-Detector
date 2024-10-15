package com.allaitech.realtime

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import com.allaitech.realtime.ml.SsdMobilenetV1TfliteMetadataV2
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

class MainActivity : AppCompatActivity() {
    val paint = Paint()
    lateinit var imageProcessor: ImageProcessor
    lateinit var model:SsdMobilenetV1TfliteMetadataV2
    lateinit var bitmap: Bitmap
    lateinit var imageView: ImageView
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        get_permission()

        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = SsdMobilenetV1TfliteMetadataV2.newInstance(this)

        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        imageView = findViewById(R.id.imageView)

        textureView = findViewById(R.id.textureView)

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                open_camera()
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {

            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
               return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                bitmap = textureView.bitmap!!

                // Get the original dimensions of the bitmap.
                val originalWidth = bitmap.width
                val originalHeight = bitmap.height

                // Create inputs for reference.
                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)

                // Runs model inference and gets results.
                val outputs = model.process(image)

                // Create a mutable bitmap to draw the bounding box on.
                val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutableBitmap)

                // Iterate over detection results if there are multiple detections
                for (detectionResult in outputs.detectionResultList) {
                    // Get the score to filter low-confidence detections
                    val score = detectionResult.scoreAsFloat
                    if (score < 0.5) continue // Change the threshold value as needed
                    // Gets result from DetectionResult.
                    val location = detectionResult.locationAsRectF

                    // Convert the bounding box coordinates to match the original bitmap size
                    val scaledLeft = location.left * originalWidth / 300 // Assuming input size is 300
                    val scaledTop = location.top * originalHeight / 300
                    val scaledRight = location.right * originalWidth / 300
                    val scaledBottom = location.bottom * originalHeight / 300

                    // Create a RectF using the scaled coordinates
                    val scaledLocation = android.graphics.RectF(scaledLeft, scaledTop, scaledRight, scaledBottom)

                    // Set up the Paint object for drawing the bounding box and text.
                    paint.color = android.graphics.Color.RED  // Set the color of the bounding box
                    paint.style = Paint.Style.STROKE          // Set the paint to draw outlines (stroke)
                    paint.strokeWidth = 5f                    // Set the thickness of the bounding box

                    // Draw the bounding box around the detected object.
                    canvas.drawRect(scaledLocation, paint)

                    // Set up Paint for drawing the label text.
                    paint.style = Paint.Style.FILL            // Change to fill style to draw text
                    paint.textSize = 50f                      // Set the text size
                    paint.color = android.graphics.Color.WHITE // Set the text color

                    // Draw the category and score above the bounding box.
                    canvas.drawText("${detectionResult.categoryAsString}: ${"%.2f".format(score)}", scaledLocation.left, scaledLocation.top - 10, paint)
                }

                // Update the ImageView with the new bitmap containing the bounding box and label.
                imageView.setImageBitmap(mutableBitmap)
            }


        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Releases model resources if no longer used.
        model.close()
    }

    @SuppressLint("MissingPermission")
    fun open_camera(){

        cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback(){
            @SuppressLint("Recycle")
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera

                var surfaceTexture = textureView.surfaceTexture
                var surface = Surface(surfaceTexture)

                var captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface),object : CameraCaptureSession.StateCallback(){
                    override fun onConfigured(session: CameraCaptureSession) {
                        session.setRepeatingRequest(captureRequest.build(),null,null)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {

                    }
                }, handler)
            }

            override fun onDisconnected(camera: CameraDevice) {

            }

            override fun onError(camera: CameraDevice, error: Int) {

            }

        }, handler)
    }
    private fun get_permission(){
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED){
            get_permission()
        }
    }
}

