package pt.ipt.mystreaks

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import pt.ipt.mystreaks.databinding.FragmentListsBinding

class ListsFragment : Fragment(R.layout.fragment_lists) {

    private var _binding: FragmentListsBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentListsBinding.bind(view)

        // Botão de adicionar lista (por agora só dá um aviso)
        binding.fabAddList.setOnClickListener {
            Toast.makeText(requireContext(), "Criação de listas em breve!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}