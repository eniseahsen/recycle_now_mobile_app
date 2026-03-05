package eu.tutorials.hangiatik

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import eu.tutorials.hangiatik.databinding.FragmentResultBinding

class ResultFragment : Fragment() {

    private lateinit var binding: FragmentResultBinding

    private val classNames = arrayOf(
        "Cam Atık",
        "Kağıt Atık",
        "Metal Atık",
        "Pil Atık",
        "Plastik Atık"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentResultBinding.inflate(inflater, container, false)

        binding.btnBackResult.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imageUri = arguments?.getString("imageUri")
        val detectedClassIds = arguments?.getIntegerArrayList("detectedClassIds") ?: arrayListOf()

        //Sonuç görseli
        imageUri?.let {
            binding.imageViewResult.setImageURI(Uri.parse(it))
        }

        //Sonuç yazısı (birden fazla tespit varsa hepsini yaz)
        binding.textResult.text =
            if (detectedClassIds.isNotEmpty())
                detectedClassIds
                    .distinct() // tekrarlayanları kaldır
                    .joinToString("\n") { id ->
                        classNames.getOrNull(id) ?: "Bilinmeyen"
                    }
            else
                "Nesne bulunamadı"


        binding.textResult.visibility = View.VISIBLE

        //Tespit edilen kutuları göster ve glow animasyon başlat
        highlightDetectedBoxes(detectedClassIds)
    }

    private fun highlightDetectedBoxes(detectedClassIds: List<Int>) {
        val boxes = listOf(
            binding.boxCam,
            binding.boxKagit,
            binding.boxMetal,
            binding.boxPil,
            binding.boxPlastik
        )

        //Önce tüm kutuları gizle
        boxes.forEach { it.visibility = View.GONE }

        //Glow animasyonu yükle
        val anim = AnimationUtils.loadAnimation(requireContext(), R.anim.glow)

        //Tespit edilen sınıfları göster ve animasyonu başlat
        detectedClassIds.forEach { classId ->
            val box = boxes.getOrNull(classId)
            box?.visibility = View.VISIBLE
            box?.startAnimation(anim)
        }
    }
}
