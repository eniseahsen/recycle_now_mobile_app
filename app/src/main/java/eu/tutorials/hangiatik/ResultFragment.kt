package eu.tutorials.hangiatik

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import eu.tutorials.hangiatik.databinding.FragmentResultBinding


class ResultFragment : Fragment() {
    private lateinit var binding: FragmentResultBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View{
        binding = FragmentResultBinding.inflate(inflater, container, false)
        binding.btnBackResult.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?){
        super.onViewCreated(view, savedInstanceState)
        val imageUri = arguments?.getString("imageUri")
        val result = arguments?.getString("resultText")


        binding.imageViewResult.setImageURI(Uri.parse(imageUri))

        binding.textResult.text = result
    }
}