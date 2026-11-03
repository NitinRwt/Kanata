package app.kanata.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import app.kanata.BottomSheetDialogFragment
import app.kanata.databinding.BottomSheetDevelopersBinding

class DevelopersDialogFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetDevelopersBinding? = null
    private val binding get() = _binding!!

    private val developers = arrayOf(
        Developer("nitinrawat","https://avatars.githubusercontent.com/u/110294741?s=120&v=4","Owner · Kanata","https://github.com/NitinRwt"),
        // Kanata is built on their work
        Developer("kemuriXD","https://github.com/kemuriXD.png?size=120","Creator · Saikou TV","https://github.com/kemuriXD/SaikouTV"),
        Developer("Saikou team","https://github.com/saikou-app.png?size=120","Creators · Saikou","https://github.com/saikou-app"),
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetDevelopersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.devsRecyclerView.adapter = DevelopersAdapter(developers)
        binding.devsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }
}