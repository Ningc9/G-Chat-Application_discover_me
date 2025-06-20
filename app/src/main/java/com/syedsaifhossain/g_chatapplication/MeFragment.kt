package com.syedsaifhossain.g_chatapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.syedsaifhossain.g_chatapplication.databinding.FragmentMeBinding

class MeFragment : Fragment() {
    private var _binding: FragmentMeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        setupUserInfo()

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupUserInfo() {
        binding.tvUsername.text = "Your Name"
    }

    private fun setupClickListeners() {
        binding.apply {
            layoutProfile.setOnClickListener {
                findNavController().navigate(R.id.profileFragment)
            }
            layoutVouchers.setOnClickListener {
                startActivity(Intent(requireContext(), VouchersActivity::class.java))
            }

            layoutWallet.setOnClickListener {
                findNavController().navigate(R.id.walletFragment)
            }

            layoutFavorites.setOnClickListener {
                startActivity(Intent(requireContext(), FavoritesActivity::class.java))
            }

            layoutSettings.setOnClickListener {
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
            }
        }
    }
}