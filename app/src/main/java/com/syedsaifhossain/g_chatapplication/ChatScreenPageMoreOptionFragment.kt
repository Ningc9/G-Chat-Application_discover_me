package com.syedsaifhossain.g_chatapplication

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.syedsaifhossain.g_chatapplication.databinding.FragmentChatScreenPageMoreOptionBinding

class ChatScreenPageMoreOptionFragment : Fragment() {
    private lateinit var binding: FragmentChatScreenPageMoreOptionBinding
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {

        binding = FragmentChatScreenPageMoreOptionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        binding.moreOptionBackImg.setOnClickListener {
            findNavController().popBackStack()
        }
    }

}