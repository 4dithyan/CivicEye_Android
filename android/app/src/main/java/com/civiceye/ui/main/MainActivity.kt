package com.civiceye.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.civiceye.R
import com.civiceye.databinding.ActivityMainBinding
import com.civiceye.ui.auth.AuthActivity
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    @Inject
    lateinit var auth: FirebaseAuth

    @Inject
    lateinit var masterDataRepository: com.civiceye.data.repository.MasterDataRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Seed departments if missing
        lifecycleScope.launch {
            masterDataRepository.seedDepartments()
        }
        
        // Check authentication
        if (auth.currentUser == null) {
            navigateToAuth()
            return
        }
        
        setupNavigation()
    }
    
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        // Let setupWithNavController handle all navigation automatically
        // (IDs in bottom_nav_menu.xml must match IDs in nav_main.xml)
        binding.bottomNav.setupWithNavController(navController)
    }
    
    private fun navigateToAuth() {
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
    }
}
