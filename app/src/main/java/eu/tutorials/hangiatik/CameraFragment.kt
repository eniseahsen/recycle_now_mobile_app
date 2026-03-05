package eu.tutorials.hangiatik

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private lateinit var cameraPreview: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    private val CAMERA_PERMISSION_REQUEST = 1001
    private val PICK_IMAGE_REQUEST = 101

    private lateinit var tflite: Interpreter
    private var progressBar: ProgressBar? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_camera, container, false)
        cameraPreview = view.findViewById(R.id.camera_preview)
        progressBar = view.findViewById(R.id.progress_bar)

        loadYolo()
        cameraExecutor = Executors.newSingleThreadExecutor()

        val captureBtn = view.findViewById<ImageButton>(R.id.btnCapture)
        captureBtn.isEnabled = false
        captureBtn.setOnClickListener {
            progressBar?.visibility = View.VISIBLE
            takePhoto()
        }

        val galleryBtn = view.findViewById<ImageButton>(R.id.btnGallery)
        galleryBtn.setOnClickListener { openGallery() }

        val backBtn = view.findViewById<ImageButton>(R.id.btnBack)
        backBtn.setOnClickListener {
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            requireActivity().finish()
        }

        if (allPermissionsGranted()) {
            startCamera(captureBtn)
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        }

        return view
    }

    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera(captureBtn: ImageButton) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(cameraPreview.surfaceProvider)

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                captureBtn.isEnabled = true
            } catch (e: Exception) {
                Log.e("CameraFragment", "Camera bind error", e)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }


    // YOLO MODEL YÜKLEME

    private fun loadModelFile(modelName: String): ByteBuffer {
        val fd = requireContext().assets.openFd(modelName)
        val inputStream = FileInputStream(fd.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    private fun loadYolo() {
        val modelBuffer = loadModelFile("best_float32.tflite")
        tflite = Interpreter(modelBuffer)
    }

    // -----------------------------
    // YOLO ÇALIŞTIR
    // -----------------------------
    data class Detection(
        val rect: RectF,
        val score: Float,
        val classId: Int
    )

    private fun iou(a: RectF, b: RectF): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)

        val interArea = maxOf(0f, right - left) * maxOf(0f, bottom - top)
        val unionArea = a.width() * a.height() + b.width() * b.height() - interArea
        return if (unionArea <= 0f) 0f else interArea / unionArea
    }

    private fun nms(
        detections: List<Detection>,
        iouThreshold: Float = 0.5f
    ): List<Detection> {
        val sorted = detections.sortedByDescending { it.score }.toMutableList()
        val results = mutableListOf<Detection>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            results.add(best)

            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val other = iterator.next()
                if (iou(best.rect, other.rect) > iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return results
    }

    private val classColors = mapOf(
        0 to Color.rgb(33, 150, 243),   // cam
        1 to Color.rgb(255, 235, 59),   // kagit
        2 to Color.rgb(96, 125, 139),   // metal
        3 to Color.rgb(244, 67, 54),    // pil
        4 to Color.rgb(76, 175, 80)     // plastik
    )

    private fun runYolo(bitmap: Bitmap): Triple<Bitmap, String, List<Int>> {
        val inputSize = 640
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val px = resized.getPixel(x, y)
                inputBuffer.putFloat(Color.red(px) / 255f)
                inputBuffer.putFloat(Color.green(px) / 255f)
                inputBuffer.putFloat(Color.blue(px) / 255f)
            }
        }
        inputBuffer.rewind()

        val output = Array(1) { Array(9) { FloatArray(8400) } }
        tflite.run(inputBuffer, output)

        val classNames = arrayOf("cam", "kagit", "metal", "pil", "plastik")
        val detections = mutableListOf<Detection>()

        val outputTensor = output[0]

        for (i in 0 until 8400) {
            val x = outputTensor[0][i]
            val y = outputTensor[1][i]
            val w = outputTensor[2][i]
            val h = outputTensor[3][i]

            var bestClass = -1
            var bestScore = 0f
            for (c in classNames.indices) {
                val score = outputTensor[4 + c][i]
                if (score > bestScore) {
                    bestScore = score
                    bestClass = c
                }
            }

            if (bestScore < 0.6f) continue

            val left = (x - w / 2f) * bitmap.width
            val top = (y - h / 2f) * bitmap.height
            val right = (x + w / 2f) * bitmap.width
            val bottom = (y + h / 2f) * bitmap.height

            detections.add(
                Detection(
                    rect = RectF(left, top, right, bottom),
                    score = bestScore,
                    classId = bestClass
                )
            )
        }

        val finalDetections = nms(detections, 0.5f)

        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val resultTextBuilder = StringBuilder()

        for (det in finalDetections) {
            val boxPaint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 16f
                color = classColors[det.classId] ?: Color.WHITE
            }

            val boxHeight = det.rect.height()
            textPaint.textSize = (boxHeight * 0.12f).coerceIn(32f, 64f)

            val textX = det.rect.left
            val textY = maxOf(det.rect.top - 10f, textPaint.textSize)

            canvas.drawRect(det.rect, boxPaint)
            canvas.drawText(classNames[det.classId], textX, textY, textPaint)

            resultTextBuilder.append(classNames[det.classId]).append("\n")
        }

        val resultText =
            if (finalDetections.isEmpty()) "Nesne bulunamadı" else resultTextBuilder.toString()

        val detectedClassIds = finalDetections.map { it.classId }
        return Triple(mutableBitmap, resultText, detectedClassIds)
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }


    // FOTOĞRAF ÇEKME

    private fun takePhoto() {
        val imgCapture = imageCapture ?: return

        val photoFile = File(
            requireActivity().cacheDir,
            "captured_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imgCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraX", "Photo failed: ${exc.message}")
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val bitmapRaw = BitmapFactory.decodeFile(photoFile.absolutePath)
                    val rotation = 90
                    val bitmap = rotateBitmapIfNeeded(bitmapRaw, rotation)

                    val (outBitmap, _, detectedClassIds) = runYolo(bitmap)
                    showResult(outBitmap, detectedClassIds)
                }
            }
        )
    }

    private fun fixBitmapOrientationFromUri(uri: Uri): Bitmap {
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        val exifStream = requireContext().contentResolver.openInputStream(uri)
        val exif = androidx.exifinterface.media.ExifInterface(exifStream!!)
        val orientation = exif.getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        )
        exifStream.close()

        val rotation = when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

        return rotateBitmapIfNeeded(bitmap, rotation)
    }


    // GALERİDEN FOTOĞRAF SEÇME

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    private fun showResult(bitmap: Bitmap, detectedClassIds: List<Int>) {
        val file = File(requireActivity().cacheDir, "temp.jpg")
        file.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
        }

        val bundle = Bundle().apply {
            putString("imageUri", Uri.fromFile(file).toString())
            putIntegerArrayList("detectedClassIds", ArrayList(detectedClassIds))
        }

        requireActivity().runOnUiThread {
            progressBar?.visibility = View.GONE
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ResultFragment().apply {
                    arguments = bundle
                })
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == CAMERA_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            val captureBtn = view?.findViewById<ImageButton>(R.id.btnCapture)
            if (captureBtn != null) startCamera(captureBtn)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PICK_IMAGE_REQUEST &&
            resultCode == AppCompatActivity.RESULT_OK
        ) {
            val uri = data?.data ?: return
            val bitmap = fixBitmapOrientationFromUri(uri)

            val (outBitmap, _, detectedClassIds) = runYolo(bitmap)
            showResult(outBitmap, detectedClassIds)
        }
    }

}  