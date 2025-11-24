package eu.tutorials.hangiatik
import  eu.tutorials.hangiatik.ResultFragment
import android.Manifest
import android.R.attr.homeLayout
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private lateinit var cameraPreview: PreviewView
    private val CAMERA_PERMISSION_REQUEST = 1001
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private val PICK_IMAGE_REQUEST = 101
    private var progressBar: ProgressBar? = null



    // Permission kontrolü
    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_camera, container, false)
        cameraPreview = view.findViewById(R.id.camera_preview)

        cameraExecutor = Executors.newSingleThreadExecutor()
        val captureBtn = view.findViewById<ImageButton>(R.id.btnCapture)
        captureBtn.isEnabled = false
        captureBtn.setOnClickListener {
            takePhoto()
            progressBar?.visibility = View.VISIBLE

        }

        val galleryBtn = view.findViewById<ImageButton>(R.id.btnGallery)
        galleryBtn.setOnClickListener { openGallery() }

        progressBar = view.findViewById(R.id.progress_bar)

        val backBtn = view.findViewById<ImageButton>(R.id.btnBack)
        backBtn.setOnClickListener {
            // CameraFragment'i kapat
            requireActivity().supportFragmentManager.popBackStack()

            // MainActivity'deki home_layout'u görünür yap
            val homeLayout = requireActivity().findViewById<LinearLayout>(R.id.home_layout)
            homeLayout.visibility = View.VISIBLE

            // Fragment container'ı gizle
            val fragmentContainer = requireActivity().findViewById<FrameLayout>(R.id.fragment_container)
            fragmentContainer.visibility = View.GONE
        }


        if (allPermissionsGranted()) {
            startCamera(captureBtn)
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        }

        return view
    }

    // Kamera başlatma
    private fun startCamera(captureBtn : ImageButton) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(cameraPreview.surfaceProvider)


            //fotoğraf çekme
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(requireActivity().windowManager.defaultDisplay.rotation)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA



            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview,imageCapture)
                captureBtn.isEnabled = true
            } catch (e: Exception) {
                Log.e("CameraFragment", "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    // Bitmap'i döndürmek için yardımcı fonksiyon
    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }


    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
            requireActivity().cacheDir,
            "captured_${
                System.currentTimeMillis()
            }.jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object: ImageCapture.OnImageSavedCallback{
                override fun onError(exc: ImageCaptureException){
                    Log.e("CameraX", "Photo captured Failed: ${exc.message}",exc)

                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // Bitmap oluştur
                    var bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)

                    // EXIF rotasyonunu al ve bitmap'i döndür
                    val exif = androidx.exifinterface.media.ExifInterface(photoFile.absolutePath)
                    val rotation = exif.getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                    )
                    bitmap = when (rotation) {
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                        else -> bitmap
                    }

                    sendToModel(bitmap)
                    progressBar?.visibility = View.VISIBLE
                }
            })


    }

    private fun openGallery(){
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }


    private fun sendToModel(bitmap: Bitmap){
        Log.d("CameraX", "Fotoğraf modele gitti")

        val file = File(requireActivity().cacheDir, "temp_${System.currentTimeMillis()}.jpg")
        file.outputStream().use{
            bitmap.compress(Bitmap.CompressFormat.JPEG,100,it)


        }

        val uri = Uri.fromFile(file)
        val result = "Sınıf: " //sınıf
        requireActivity().runOnUiThread {
            openResultFragment(uri, result)
        }
        /*requireActivity().runOnUiThread {
            Toast.makeText(requireContext(),"Photo saved!",Toast.LENGTH_SHORT).show()
        }*/
    }

    // Kullanıcı izin verdikten sonra kamera başlat
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {

            val captureBtn = view?.findViewById<ImageButton>(R.id.btnCapture)
            if (captureBtn != null) {
                startCamera(captureBtn) // Buton parametresi ile başlat
            }
        }}


    override fun onDestroy(){
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == AppCompatActivity.RESULT_OK){
            data?.data?.let {uri ->
                val bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                sendToModel(bitmap)
            }
        }
    }

    private fun openResultFragment(imageUri: Uri, resultText: String){
        val bundle = Bundle()
        bundle.putString("imageUri", imageUri.toString())
        bundle.putString("resultText", resultText)

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ResultFragment().apply { arguments = bundle })
            .addToBackStack(null)
            .commit()
    }

}